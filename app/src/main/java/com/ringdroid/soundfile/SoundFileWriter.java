package com.ringdroid.soundfile;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** Writes a range of a decoded SoundFile as M4A (AAC) or 16-bit PCM WAV. */
public final class SoundFileWriter {
    private final ByteBuffer mDecodedBytes;
    private final int mSampleRate;
    private final int mChannels;
    private final int mSamplesPerFrame;
    private final float mGain;

    public SoundFileWriter(SoundFile soundFile, float gain) {
        mDecodedBytes = soundFile.getDecodedBytes();
        if (mDecodedBytes == null) {
            throw new IllegalStateException("Sound file has no decoded samples");
        }
        mSampleRate = soundFile.getSampleRate();
        mChannels = soundFile.getChannels();
        mSamplesPerFrame = soundFile.getSamplesPerFrame();
        mGain = gain;
    }

    private float framesToSeconds(int frames) {
        return (float) frames * mSamplesPerFrame / mSampleRate;
    }

    // Positions mDecodedBytes at startTime and returns the number of samples per channel.
    private int seekToRange(float startTime, float endTime) throws IOException {
        int startOffset = (int) (startTime * mSampleRate) * 2 * mChannels;
        int numSamples = (int) ((endTime - startTime) * mSampleRate);
        if (numSamples <= 0) {
            throw new IOException("Invalid time bounds: " + startTime + " to " + endTime);
        }
        if (startOffset < 0 || startOffset > mDecodedBytes.limit()) {
            throw new IOException("Start offset out of bounds: " + startOffset);
        }
        mDecodedBytes.position(startOffset);
        return numSamples;
    }

    // Reads length bytes into buffer, padding with silence past the end, and applies the gain.
    private void readPadded(byte[] buffer, int length) {
        int n = Math.min(length, mDecodedBytes.remaining());
        mDecodedBytes.get(buffer, 0, n);
        Arrays.fill(buffer, n, length, (byte) 0);
        if (mGain != 1.0f) {
            applyGain(buffer, length);
        }
    }

    public void writeM4A(File outputFile, int startFrame, int numFrames) throws IOException {
        writeAAC(new MediaMuxer(outputFile.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4),
                framesToSeconds(startFrame), framesToSeconds(startFrame + numFrames));
    }

    @RequiresApi(Build.VERSION_CODES.O)
    public void writeM4A(Context context, Uri outputUri, int startFrame, int numFrames)
            throws IOException {
        try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(outputUri,
                "rw")) {
            if (pfd == null) {
                throw new IOException("Cannot open MediaStore output");
            }
            writeAAC(
                    new MediaMuxer(pfd.getFileDescriptor(),
                            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4),
                    framesToSeconds(startFrame), framesToSeconds(startFrame + numFrames));
        }
    }

    private void writeAAC(MediaMuxer muxer, float startTime, float endTime) throws IOException {
        String mimeType = MediaFormat.MIMETYPE_AUDIO_AAC;
        MediaCodec codec = null;
        try {
            int numSamples = seekToRange(startTime, endTime);
            codec = MediaCodec.createEncoderByType(mimeType);
            MediaFormat format = MediaFormat.createAudioFormat(mimeType, mSampleRate, mChannels);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 64000 * mChannels);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();

            int frameSize = 1024; // samples per channel in an AAC frame
            byte[] buffer = new byte[frameSize * mChannels * 2];
            // Two extra frames make up for the encoder priming delay.
            int numFramesLeft = (numSamples + frameSize - 1) / frameSize + 2;
            long numFramesQueued = 0;
            boolean doneReading = false;
            int track = -1;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (true) {
                if (!doneReading) {
                    int inputBufferIndex = codec.dequeueInputBuffer(100);
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer = codec.getInputBuffer(inputBufferIndex);
                        if (numFramesLeft == 0 || inputBuffer == null) {
                            codec.queueInputBuffer(inputBufferIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            doneReading = true;
                        } else {
                            inputBuffer.clear();
                            int length = Math.min(buffer.length, inputBuffer.remaining());
                            readPadded(buffer, length);
                            inputBuffer.put(buffer, 0, length);
                            long presentationTime = numFramesQueued++ * frameSize * 1000000L
                                    / mSampleRate;
                            codec.queueInputBuffer(inputBufferIndex, 0, length, presentationTime,
                                    0);
                            numFramesLeft--;
                        }
                    }
                }

                int outputBufferIndex = codec.dequeueOutputBuffer(info, 100);
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.getOutputFormat());
                    muxer.start();
                } else if (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferIndex);
                    boolean isConfig = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                    if (outputBuffer != null && track >= 0 && info.size > 0 && !isConfig) {
                        muxer.writeSampleData(track, outputBuffer, info);
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;
                    }
                }
            }
            if (track < 0) {
                throw new IOException("Encoder produced no output");
            }
            codec.stop();
            muxer.stop();
        } finally {
            if (codec != null) {
                codec.release();
            }
            muxer.release();
        }
    }

    private void applyGain(byte[] buffer, int length) {
        for (int i = 0; i < length; i += 2) {
            short sample = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
            int scaled = Math.round(sample * mGain);
            if (scaled > Short.MAX_VALUE)
                scaled = Short.MAX_VALUE;
            if (scaled < Short.MIN_VALUE)
                scaled = Short.MIN_VALUE;
            buffer[i] = (byte) (scaled & 0xFF);
            buffer[i + 1] = (byte) ((scaled >> 8) & 0xFF);
        }
    }

    public void writeWAV(File outputFile, int startFrame, int numFrames) throws IOException {
        writeWAVData(new FileOutputStream(outputFile), framesToSeconds(startFrame),
                framesToSeconds(startFrame + numFrames));
    }

    public void writeWAV(Context context, Uri outputUri, int startFrame, int numFrames)
            throws IOException {
        OutputStream outputStream = context.getContentResolver().openOutputStream(outputUri);
        if (outputStream == null) {
            throw new IOException("Cannot open MediaStore output stream");
        }
        writeWAVData(outputStream, framesToSeconds(startFrame),
                framesToSeconds(startFrame + numFrames));
    }

    private void writeWAVData(OutputStream outputStream, float startTime, float endTime)
            throws IOException {
        try (OutputStream out = outputStream) {
            int numSamples = seekToRange(startTime, endTime);
            out.write(WAVHeader.getWAVHeader(mSampleRate, mChannels, numSamples));
            byte[] buffer = new byte[1024 * mChannels * 2];
            int numBytesLeft = numSamples * mChannels * 2;
            while (numBytesLeft > 0) {
                int length = Math.min(buffer.length, numBytesLeft);
                readPadded(buffer, length);
                out.write(buffer, 0, length);
                numBytesLeft -= length;
            }
        }
    }
}
