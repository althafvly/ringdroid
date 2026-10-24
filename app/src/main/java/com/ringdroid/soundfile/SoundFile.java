/*
 * Copyright (C) 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ringdroid.soundfile;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Serial;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.io.FileNotFoundException;

public class SoundFile {
    private static final String TAG = "SoundFile";
    private static final String PCM_PREFIX = "ringdroid_pcm_";
    // Temp files in use by this process; any other ones were left behind by a killed process.
    private static final Set<File> sActivePcmFiles = Collections.synchronizedSet(new HashSet<>());
    private ProgressListener mProgressListener = null;
    private File mInputFile = null;
    // Member variables representing frame data
    private String mFileType;
    private int mFileSize;
    private int mAvgBitRate; // Average bit rate in kbps.
    private int mSampleRate;
    private int mChannels;
    private int mNumSamples; // total number of samples per channel in audio file
    private ByteBuffer mDecodedBytes; // Raw audio data (memory-mapped from disk for large files)
    // mDecodedSamples has the following format:
    // {s1c1, s1c2, ..., s1cM, s2c1, ..., s2cM, ..., sNc1, ..., sNcM}
    // where sicj is the ith sample of the jth channel (a sample is a signed short)
    // M is the number of channels (e.g. 2 for stereo) and N is the number of
    // samples per channel.
    private ShortBuffer mDecodedSamples; // shared buffer with mDecodedBytes.
    // Temp file backing the memory-mapped PCM buffer (null for short files).
    private File mPcmTempFile = null;
    private RandomAccessFile mPcmRaf = null;
    private FileChannel mPcmChannel = null;
    // Member variables for hack (making it work with old version, until app just
    // uses the samples).
    private int mNumFrames;
    private int[] mFrameGains;

    // A SoundFile object should only be created using the static methods create()
    // and record().
    private SoundFile() {
    }

    // TODO(nfaralli): what is the real list of supported extensions? Is it device
    // dependent?
    public static String[] getSupportedExtensions() {
        return new String[]{"mp3", "wav", "3gpp", "3gp", "amr", "aac", "m4a", "ogg", "flac", "opus",
                "wma", "mkv"};
    }

    public static boolean isFilenameSupported(String filename) {
        String[] extensions = getSupportedExtensions();
        for (String extension : extensions) {
            if (filename.endsWith("." + extension)) {
                return true;
            }
        }
        return false;
    }

    // Create and return a SoundFile object using the file fileName.
    public static SoundFile create(File cacheDir, String fileName,
            ProgressListener progressListener) throws IOException, InvalidInputException {
        // First check that the file exists and that its extension is supported.
        File f = new File(fileName);
        if (!f.exists()) {
            throw new FileNotFoundException(fileName);
        }
        String name = f.getName().toLowerCase(Locale.ROOT);
        String[] components = name.split("\\.");
        if (components.length < 2) {
            return null;
        }
        if (!Arrays.asList(getSupportedExtensions()).contains(components[components.length - 1])) {
            return null;
        }
        SoundFile soundFile = new SoundFile();
        soundFile.setProgressListener(progressListener);
        boolean readCompleted = false;
        try {
            soundFile.readFile(cacheDir, f);
            readCompleted = true;
            // Decoding can return early when canceled by the progress listener.
            // In that case, ensure temp PCM resources are freed and report no file loaded.
            if (soundFile.mDecodedSamples == null) {
                soundFile.release();
                return null;
            }
            return soundFile;
        } finally {
            if (!readCompleted) {
                soundFile.release();
            }
        }
    }

    // Create and return a SoundFile object by recording a mono audio stream.
    public static SoundFile record(ProgressListener progressListener) {
        if (progressListener == null) {
            // must have a progessListener to stop the recording.
            return null;
        }
        SoundFile soundFile = new SoundFile();
        soundFile.setProgressListener(progressListener);
        if (!soundFile.recordAudio()) {
            return null;
        }
        return soundFile;
    }

    public String getFiletype() {
        return mFileType;
    }

    public int getAvgBitrateKbps() {
        return mAvgBitRate;
    }

    public int getSampleRate() {
        return mSampleRate;
    }

    public int getChannels() {
        return mChannels;
    }

    public int getNumSamples() {
        return mNumSamples; // Number of samples per channel.
    }

    // Should be removed when the app will use directly the samples instead of the
    // frames.
    public int getNumFrames() {
        return mNumFrames;
    }

    // Should be removed when the app will use directly the samples instead of the
    // frames.
    public int getSamplesPerFrame() {
        return 1024; // just a fixed value here...
    }

    // Should be removed when the app will use directly the samples instead of the
    // frames.
    public int[] getFrameGains() {
        return mFrameGains;
    }

    ByteBuffer getDecodedBytes() {
        return mDecodedBytes != null ? mDecodedBytes.duplicate() : null;
    }

    public ShortBuffer getSamples() {
        if (mDecodedSamples != null) {
            return mDecodedSamples.asReadOnlyBuffer();
        } else {
            return null;
        }
    }

    private void setProgressListener(ProgressListener progressListener) {
        mProgressListener = progressListener;
    }

    /**
     * Release the decoded audio data to free up memory. Closes and deletes any temporary PCM file
     * used for disk-backed decoding. Should be called when the SoundFile is no longer needed.
     */
    public void release() {
        mDecodedBytes = null;
        mDecodedSamples = null;
        mFrameGains = null;
        if (mPcmChannel != null) {
            try {
                mPcmChannel.close();
            } catch (IOException ignored) {
            }
            mPcmChannel = null;
        }
        if (mPcmRaf != null) {
            try {
                mPcmRaf.close();
            } catch (IOException ignored) {
            }
            mPcmRaf = null;
        }
        if (mPcmTempFile != null) {
            sActivePcmFiles.remove(mPcmTempFile);
            if (!mPcmTempFile.delete() && mPcmTempFile.exists()) {
                Log.w(TAG,
                        "Failed to delete temporary PCM file: " + mPcmTempFile.getAbsolutePath());
            }
            mPcmTempFile = null;
        }
    }

    private static void deleteStalePcmFiles(File dir) {
        File[] files = dir.listFiles((d, name) -> name.startsWith(PCM_PREFIX));
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!sActivePcmFiles.contains(file) && !file.delete()) {
                Log.w(TAG, "Failed to delete stale PCM file: " + file);
            }
        }
    }

    private void readFile(File cacheDir, File inputFile)
            throws IOException, InvalidInputException {
        MediaExtractor extractor = new MediaExtractor();
        MediaFormat format = null;
        int i;

        mInputFile = inputFile;
        String[] components = mInputFile.getPath().split("\\.");
        mFileType = components[components.length - 1];
        mFileSize = (int) mInputFile.length();
        extractor.setDataSource(mInputFile.getPath());
        int numTracks = extractor.getTrackCount();
        // find and select the first audio track present in the file.
        for (i = 0; i < numTracks; i++) {
            format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                extractor.selectTrack(i);
                break;
            }
        }
        if (i == numTracks || format == null) {
            throw new InvalidInputException("No audio track found in " + mInputFile);
        }
        String mime = format.getString(MediaFormat.KEY_MIME);
        if (mime == null) {
            throw new InvalidInputException("Audio track has no MIME type in " + mInputFile);
        }
        if (!format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                || !format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            throw new InvalidInputException(
                    "Audio track missing channel count or sample rate in " + mInputFile);
        }
        mChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        mSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);

        // Compute the expected decoded PCM size to pre-allocate the disk-backed buffer.
        // Uses long arithmetic to avoid int overflow for files > ~24 min at 44100Hz.
        long durationUs = format.containsKey(MediaFormat.KEY_DURATION)
                ? format.getLong(MediaFormat.KEY_DURATION)
                : 0L;
        long expectedNumSamples = (long) ((durationUs / 1000000.f) * mSampleRate + 0.5f);
        long expectedMemory = expectedNumSamples * mChannels * 2;

        MediaCodec codec = null;
        try {
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(format, null, null, 0);
            codec.start();

            int decodedSamplesSize = 0; // size of the output buffer containing decoded samples.
            byte[] decodedSamples = null;
            int sampleSize;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long presentationTime;
            int totalSizeRead = 0;
            boolean doneReading = false;

            // Use disk-backed memory-mapped buffer so files of any size can be decoded
            // without being limited by Java heap. The OS handles paging automatically.
            File pcmParentDir = cacheDir;
            if (pcmParentDir == null) {
                throw new IOException("Cannot determine app cache directory for PCM temp buffer");
            }
            synchronized (sActivePcmFiles) {
                deleteStalePcmFiles(pcmParentDir);
                mPcmTempFile = File.createTempFile(PCM_PREFIX, ".raw", pcmParentDir);
                sActivePcmFiles.add(mPcmTempFile);
            }
            long initialSize = Math.max(expectedMemory, 1L << 20); // at least 1MB
            mPcmRaf = new RandomAccessFile(mPcmTempFile, "rw");
            mPcmRaf.setLength(initialSize);
            mPcmChannel = mPcmRaf.getChannel();
            MappedByteBuffer mappedBuffer = mPcmChannel.map(FileChannel.MapMode.READ_WRITE, 0,
                    initialSize);
            mDecodedBytes = mappedBuffer;
            boolean firstSampleData = true;
            boolean outputFormatUpdated = false;
            while (true) {

                // read data from file and feed it to the decoder input buffers.
                int inputBufferIndex = codec.dequeueInputBuffer(100);
                if (!doneReading && inputBufferIndex >= 0) {
                    ByteBuffer inputBuffer = codec.getInputBuffer(inputBufferIndex);
                    if (inputBuffer != null) {
                        inputBuffer.clear();
                        sampleSize = extractor.readSampleData(inputBuffer, 0);
                        if (firstSampleData && Objects
                                .equals(format.getString(MediaFormat.KEY_MIME), "audio/mp4a-latm")
                                && sampleSize == 2) {
                            // For some reasons on some devices (e.g. the Samsung S3) you should not
                            // provide the first two bytes of an AAC stream, otherwise the
                            // MediaCodec will
                            // crash. These two bytes do not contain music data but basic info on
                            // the
                            // stream (e.g. channel configuration and sampling frequency), and
                            // skipping them
                            // seems OK with other devices (MediaCodec has already been configured
                            // and
                            // already knows these parameters).
                            extractor.advance();
                            totalSizeRead += sampleSize;
                        } else if (sampleSize < 0) {
                            // All samples have been read.
                            codec.queueInputBuffer(inputBufferIndex, 0, 0, -1,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            doneReading = true;
                        } else {
                            presentationTime = extractor.getSampleTime();
                            codec.queueInputBuffer(inputBufferIndex, 0, sampleSize,
                                    presentationTime, 0);
                            extractor.advance();
                            totalSizeRead += sampleSize;
                            if (mProgressListener != null) {
                                if (mProgressListener
                                        .reportProgress((float) (totalSizeRead) / mFileSize)) {
                                    return;
                                }
                            }
                        }
                        firstSampleData = false;
                    }
                }

                // Get decoded stream from the decoder output buffers.
                int outputBufferIndex = codec.dequeueOutputBuffer(info, 100);
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED
                        && !outputFormatUpdated) {
                    MediaFormat outputFormat = codec.getOutputFormat();

                    mSampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    mChannels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

                    Log.d(TAG, "Decoder output format: " + mSampleRate + " Hz, " + mChannels
                            + " channels");

                    outputFormatUpdated = true;
                    continue;
                }

                if (outputBufferIndex >= 0 && info.size > 0) {
                    ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferIndex);
                    if (outputBuffer != null) {
                        if (decodedSamplesSize < info.size) {
                            decodedSamplesSize = info.size;
                            decodedSamples = new byte[decodedSamplesSize];
                        }
                        outputBuffer.get(decodedSamples, 0, info.size);
                        outputBuffer.clear();
                        // Check if buffer is big enough. Remap file if needed.
                        if (mDecodedBytes.remaining() < info.size) {
                            int position = mDecodedBytes.position();
                            long newSize = (long) ((position * (1.0 * mFileSize / totalSizeRead))
                                    * 1.2);
                            if (newSize - position < info.size + 5L * (1 << 20)) {
                                newSize = position + info.size + 5L * (1 << 20);
                            }
                            // Extend the file and remap.
                            mPcmRaf.setLength(newSize);
                            MappedByteBuffer newMap = mPcmChannel
                                    .map(FileChannel.MapMode.READ_WRITE, 0, newSize);
                            newMap.position(position);
                            mDecodedBytes = newMap;
                        }
                        mDecodedBytes.put(decodedSamples, 0, info.size);
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false);
                }
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        || (expectedNumSamples > 0 && (mDecodedBytes.position()
                                / (2 * mChannels)) >= expectedNumSamples)) {
                    // We got all the decoded data from the decoder. Stop here.
                    // Theoretically dequeueOutputBuffer(info, ...) should have set info.flags to
                    // MediaCodec.BUFFER_FLAG_END_OF_STREAM. However some phones (e.g. Samsung S3)
                    // won't do that for some files (e.g. with mono AAC files), in which case
                    // subsequent
                    // calls to dequeueOutputBuffer may result in the application crashing, without
                    // even an exception being thrown... Hence the second check.
                    // (for mono AAC files, the S3 will actually double each sample, as if the
                    // stream
                    // was stereo. The resulting stream is half what it's supposed to be and with a
                    // much
                    // lower pitch.)
                    break;
                }
            }
            mNumSamples = mDecodedBytes.position() / (mChannels * 2); // One sample = 2 bytes.
            mDecodedBytes.rewind();
            mDecodedBytes.order(ByteOrder.LITTLE_ENDIAN);
            mDecodedSamples = mDecodedBytes.asShortBuffer();
            mAvgBitRate = (int) ((mFileSize * 8) * ((float) mSampleRate / mNumSamples) / 1000);

            computeFrameGains();
        } finally {
            if (codec != null) {
                try {
                    codec.stop();
                } catch (IllegalStateException ignored) {
                }
                codec.release();
            }
            extractor.release();
        }
    }

    @SuppressLint("MissingPermission")
    private boolean recordAudio() {
        if (mProgressListener == null) {
            // A progress listener is mandatory here, as it will let us know when to stop
            // recording.
            return false;
        }
        mInputFile = null;
        mFileType = "raw";
        mFileSize = 0;
        mSampleRate = 44100;
        mChannels = 1; // record mono audio.
        int minBufferSize = AudioRecord.getMinBufferSize(mSampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        // make sure minBufferSize can contain at least 1 second of audio (16 bits
        // sample).
        if (minBufferSize < mSampleRate * 2) {
            minBufferSize = mSampleRate * 2;
        }
        AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, mSampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBufferSize);
        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release();
            return false;
        }

        // Allocate memory for 20 seconds first. Reallocate later if more is needed.
        mDecodedBytes = ByteBuffer.allocate(20 * mSampleRate * 2);
        mDecodedBytes.order(ByteOrder.LITTLE_ENDIAN);
        mDecodedSamples = mDecodedBytes.asShortBuffer();
        try {
            audioRecord.startRecording();
            recordLoop(audioRecord);
        } finally {
            try {
                audioRecord.stop();
            } catch (IllegalStateException ignored) {
            }
            audioRecord.release();
        }
        mNumSamples = mDecodedSamples.position();
        mDecodedSamples.rewind();
        mDecodedBytes.rewind();
        mAvgBitRate = mSampleRate * 16 / 1000;

        computeFrameGains();
        return true;
    }

    private void recordLoop(AudioRecord audioRecord) {
        short[] buffer = new short[1024]; // 1 mono frame of 1024 16-bit samples
        while (true) {
            // check if mDecodedSamples can contain 1024 additional samples.
            if (mDecodedSamples.remaining() < 1024) {
                // Try to allocate memory for 10 additional seconds.
                int newCapacity = mDecodedBytes.capacity() + 10 * mSampleRate * 2;
                ByteBuffer newDecodedBytes;
                try {
                    newDecodedBytes = ByteBuffer.allocate(newCapacity);
                } catch (OutOfMemoryError oome) {
                    break;
                }
                int position = mDecodedSamples.position();
                mDecodedBytes.rewind();
                newDecodedBytes.put(mDecodedBytes);
                mDecodedBytes = newDecodedBytes;
                mDecodedBytes.order(ByteOrder.LITTLE_ENDIAN);
                mDecodedBytes.rewind();
                mDecodedSamples = mDecodedBytes.asShortBuffer();
                mDecodedSamples.position(position);
            }
            // TODO(nfaralli): maybe use the read method that takes a direct ByteBuffer
            // argument.
            int numRead = audioRecord.read(buffer, 0, buffer.length);
            if (numRead < 0) {
                break;
            }
            mDecodedSamples.put(buffer, 0, numRead);
            // Let the progress listener know how many seconds have been recorded.
            // The returned value tells us if we should keep recording or stop.
            if (mProgressListener
                    .reportProgress((float) (mDecodedSamples.position()) / mSampleRate)) {
                break;
            }
        }
    }

    private void computeFrameGains() {
        int samplesPerFrame = getSamplesPerFrame();
        mNumFrames = (mNumSamples + samplesPerFrame - 1) / samplesPerFrame;
        mFrameGains = new int[mNumFrames];
        mDecodedSamples.rewind();
        for (int i = 0; i < mNumFrames; i++) {
            int gain = 0;
            for (int j = 0; j < samplesPerFrame; j++) {
                int value = 0;
                for (int k = 0; k < mChannels && mDecodedSamples.hasRemaining(); k++) {
                    value += Math.abs(mDecodedSamples.get());
                }
                gain = Math.max(gain, value / mChannels);
            }
            mFrameGains[i] = (int) Math.sqrt(gain);
        }
        mDecodedSamples.rewind();
    }

    // Progress listener interface.
    public interface ProgressListener {
        /**
         * Will be called by the SoundFile class periodically with values between 0.0 and 1.0.
         * Return false to continue loading the file or recording the audio, and true to cancel or
         * stop recording.
         */
        boolean reportProgress(double fractionComplete);
    }

    // Custom exception for invalid inputs.
    public static class InvalidInputException extends Exception {
        // Serial version ID generated by Eclipse.
        @Serial
        private static final long serialVersionUID = -2505698991597837165L;

        public InvalidInputException(String message) {
            super(message);
        }
    }
}
