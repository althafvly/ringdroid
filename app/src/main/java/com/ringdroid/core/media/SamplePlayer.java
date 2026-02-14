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

package com.ringdroid.core.media;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;

import com.ringdroid.soundfile.SoundFile;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

public class SamplePlayer {
    private final ShortBuffer mSamples;

    private final int mSampleRate;
    private final int mChannels;
    private final int mNumSamples; // Number of samples per channel.
    private final Uri mediaUri;
    private final MediaPlayer mediaPlayer;
    private File tempFile;
    private int mPlaybackStart; // Start offset, in milliseconds.
    private int mPlaybackEnd;
    private OnCompletionListener mListener;
    private boolean prepared;

    public SamplePlayer(Context context, ShortBuffer samples, int sampleRate, int channels, int numSamples,
            File inputFile) throws IOException {
        if (samples == null) {
            throw new IllegalArgumentException("Samples are required for playback");
        }
        mSamples = samples;
        mSampleRate = sampleRate;
        mChannels = channels;
        mNumSamples = numSamples;
        mPlaybackStart = 0;
        mPlaybackEnd = (int) (mNumSamples * (1000.0 / mSampleRate));

        Context appContext = context.getApplicationContext();
        mediaUri = resolveMediaUri(appContext, inputFile);
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        mediaPlayer.setOnCompletionListener(mp -> {
            stop();
            if (mListener != null) {
                mListener.onCompletion();
            }
        });
        mediaPlayer.setDataSource(appContext, mediaUri);
        mediaPlayer.prepare();
        prepared = true;
    }

    public SamplePlayer(Context context, SoundFile sf) throws IOException {
        this(context, sf.getSamples(), sf.getSampleRate(), sf.getChannels(), sf.getNumSamples(), sf.getInputFile());
    }

    public void setOnCompletionListener(OnCompletionListener listener) {
        mListener = listener;
    }

    public boolean isPlaying() {
        return mediaPlayer.isPlaying();
    }

    public boolean isPaused() {
        return prepared && !mediaPlayer.isPlaying();
    }

    public void play(int startMsec, int endMsec) {
        int maxMsec = (int) (mNumSamples * (1000.0 / mSampleRate));
        mPlaybackStart = Math.max(0, Math.min(startMsec, maxMsec));
        int desiredEnd = endMsec <= 0 ? maxMsec : endMsec;
        mPlaybackEnd = Math.max(mPlaybackStart + 1, Math.min(desiredEnd, maxMsec));
        mediaPlayer.seekTo(mPlaybackStart);
        mediaPlayer.start();
    }

    public void start() {
        if (isPlaying()) {
            return;
        }
        mediaPlayer.start();
    }

    public void pause() {
        if (isPlaying()) {
            mediaPlayer.pause();
        }
    }

    public void stop() {
        if (!prepared) {
            return;
        }
        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            }
            mediaPlayer.seekTo(mPlaybackStart);
        } catch (IllegalStateException ignored) {
        }
    }

    public void release() {
        stop();
        mediaPlayer.release();
        prepared = false;
        if (tempFile != null && tempFile.exists()) {
            // Best-effort cleanup for generated temp files.
            // noinspection ResultOfMethodCallIgnored
            tempFile.delete();
            tempFile = null;
        }
    }

    public void seekTo(int msec) {
        boolean wasPlaying = isPlaying();
        int maxMsec = Math.max(mPlaybackEnd, (int) (mNumSamples * (1000.0 / mSampleRate)));
        mPlaybackStart = Math.max(0, Math.min(msec, maxMsec));
        if (!prepared) {
            return;
        }
        try {
            mediaPlayer.seekTo(mPlaybackStart);
        } catch (IllegalStateException ignored) {
            return;
        }
        if (wasPlaying) {
            start();
        }
    }

    public int getCurrentPosition() {
        return mediaPlayer.getCurrentPosition();
    }

    private Uri resolveMediaUri(Context context, File inputFile) throws IOException {
        if (inputFile != null && inputFile.exists()) {
            return Uri.fromFile(inputFile);
        }
        tempFile = writeSamplesToWav(context);
        return Uri.fromFile(tempFile);
    }

    private File writeSamplesToWav(Context context) throws IOException {
        File output = File.createTempFile("ringdroid_preview_", ".wav", context.getCacheDir());
        int totalSamples = mNumSamples * mChannels;
        int dataSize = totalSamples * 2; // 16-bit PCM
        int byteRate = mSampleRate * mChannels * 2;
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put(new byte[]{'R', 'I', 'F', 'F'});
        header.putInt(36 + dataSize);
        header.put(new byte[]{'W', 'A', 'V', 'E'});
        header.put(new byte[]{'f', 'm', 't', ' '});
        header.putInt(16);
        header.putShort((short) 1); // PCM format
        header.putShort((short) mChannels);
        header.putInt(mSampleRate);
        header.putInt(byteRate);
        header.putShort((short) (mChannels * 2)); // block align
        header.putShort((short) 16); // bits per sample
        header.put(new byte[]{'d', 'a', 't', 'a'});
        header.putInt(dataSize);

        try (BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(output))) {
            outputStream.write(header.array());
            ShortBuffer buffer = mSamples.asReadOnlyBuffer();
            buffer.rewind();
            ByteBuffer chunk = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN);
            while (buffer.hasRemaining()) {
                chunk.clear();
                int samplesThisRound = Math.min(buffer.remaining(), chunk.capacity() / 2);
                for (int i = 0; i < samplesThisRound; i++) {
                    chunk.putShort(buffer.get());
                }
                outputStream.write(chunk.array(), 0, samplesThisRound * 2);
            }
            outputStream.flush();
        }
        return output;
    }

    public interface OnCompletionListener {
        void onCompletion();
    }
}
