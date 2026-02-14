package com.ringdroid.core.media;

import com.ringdroid.ui.waveform.WaveformView;

/**
 * Small helper that owns playback state and conversions between waveform pixels
 * and playback times, so the activity only wires UI interactions.
 */
public class PlaybackController {
    private WaveformView waveformView;
    private SamplePlayer player;
    private boolean isPlaying;
    private int playStartMsec;
    private int playEndMsec;
    private Runnable completionCallback;

    public PlaybackController(WaveformView waveformView) {
        this.waveformView = waveformView;
    }

    public void setWaveformView(WaveformView waveformView) {
        this.waveformView = waveformView;
        if (!isPlaying && this.waveformView != null) {
            this.waveformView.setPlayback(-1);
        }
    }

    public void setCompletionCallback(Runnable callback) {
        completionCallback = callback;
    }

    public void setPlayer(SamplePlayer player) {
        stopCurrentPlayer();
        this.player = player;
        if (this.player != null) {
            this.player.setOnCompletionListener(() -> {
                if (completionCallback != null) {
                    completionCallback.run();
                }
            });
        }
        isPlaying = false;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public int getCurrentPosition() {
        if (player == null) {
            return 0;
        }
        return player.getCurrentPosition();
    }

    public int getPlayStartMsec() {
        return playStartMsec;
    }

    public int getPlayEndMsec() {
        return playEndMsec;
    }

    public void seekTo(int msec) {
        if (player != null) {
            player.seekTo(msec);
        }
    }

    public void play(int startPositionPx, int startSelectionPx, int endSelectionPx, int maxPosPx) {
        if (player == null || waveformView == null || !waveformView.isInitialized()) {
            return;
        }

        playStartMsec = waveformView.pixelsToMillisecs(startPositionPx);
        if (startPositionPx < startSelectionPx) {
            playEndMsec = waveformView.pixelsToMillisecs(startSelectionPx);
        } else if (startPositionPx > endSelectionPx) {
            playEndMsec = waveformView.pixelsToMillisecs(maxPosPx);
        } else {
            playEndMsec = waveformView.pixelsToMillisecs(endSelectionPx);
        }

        player.play(playStartMsec, playEndMsec);
        isPlaying = true;
    }

    public void pause() {
        if (player != null && player.isPlaying()) {
            player.pause();
        }
        if (waveformView != null) {
            waveformView.setPlayback(-1);
        }
        isPlaying = false;
    }

    public void release() {
        stopCurrentPlayer();
        if (player != null) {
            player.release();
            player = null;
        }
        isPlaying = false;
    }

    private void stopCurrentPlayer() {
        if (player != null && (player.isPlaying() || player.isPaused())) {
            player.stop();
        }
    }
}
