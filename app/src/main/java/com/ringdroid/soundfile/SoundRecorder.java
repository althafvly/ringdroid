package com.ringdroid.soundfile;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/** Records mono 16-bit audio from the microphone into a SoundFile. */
public final class SoundRecorder {
    private static final int SAMPLE_RATE = 44100;
    private static final int CHUNK_SAMPLES = 1024;

    private ByteBuffer mBytes;
    private ShortBuffer mSamples;

    private SoundRecorder() {
    }

    /**
     * Records until the listener asks to stop. The listener receives the elapsed time in seconds.
     *
     * @return the recording, or null if there is no listener or the microphone is unavailable
     */
    public static SoundFile record(SoundFile.ProgressListener listener) {
        if (listener == null) {
            return null;
        }
        return new SoundRecorder().recordInternal(listener);
    }

    @SuppressLint("MissingPermission")
    private SoundFile recordInternal(SoundFile.ProgressListener listener) {
        // The buffer holds at least one second of audio.
        int bufferSize = Math.max(SAMPLE_RATE * 2, AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT));
        AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release();
            return null;
        }

        allocate(20 * SAMPLE_RATE * 2);
        try {
            audioRecord.startRecording();
            recordLoop(audioRecord, listener);
        } finally {
            try {
                audioRecord.stop();
            } catch (IllegalStateException ignored) {
            }
            audioRecord.release();
        }
        int numSamples = mSamples.position();
        mBytes.rewind();
        return SoundFile.fromRecording(mBytes, SAMPLE_RATE, numSamples);
    }

    private void recordLoop(AudioRecord audioRecord, SoundFile.ProgressListener listener) {
        short[] buffer = new short[CHUNK_SAMPLES];
        while (true) {
            // Grow by 10 seconds when the next chunk would not fit.
            if (mSamples.remaining() < CHUNK_SAMPLES && !grow(10 * SAMPLE_RATE * 2)) {
                break;
            }
            int numRead = audioRecord.read(buffer, 0, buffer.length);
            if (numRead < 0) {
                break;
            }
            mSamples.put(buffer, 0, numRead);
            if (listener.reportProgress((float) mSamples.position() / SAMPLE_RATE)) {
                break;
            }
        }
    }

    private void allocate(int capacity) {
        mBytes = ByteBuffer.allocate(capacity).order(ByteOrder.LITTLE_ENDIAN);
        mSamples = mBytes.asShortBuffer();
    }

    private boolean grow(int extraBytes) {
        ByteBuffer oldBytes = mBytes;
        int position = mSamples.position();
        try {
            allocate(oldBytes.capacity() + extraBytes);
        } catch (OutOfMemoryError e) {
            return false;
        }
        oldBytes.rewind();
        mBytes.put(oldBytes).rewind();
        mSamples.position(position);
        return true;
    }
}
