package com.ringdroid.activity

import com.ringdroid.R
import com.ringdroid.databinding.EditorBinding
import com.ringdroid.view.MarkerView
import com.ringdroid.viewmodel.EditorViewModel

/**
 * Plays from a waveform position to the next marker or the end of the sound, and runs the play,
 * rewind, fast-forward and mark buttons.
 */
internal class EditorPlayback(
    private val binding: EditorBinding,
    private val viewModel: EditorViewModel,
    private val markerInput: MarkerInput,
    private val onChanged: () -> Unit,
    private val onError: (RuntimeException) -> Unit,
) {
    var isPlaying = false
        private set
    private var startMsec = 0
    private var endMsec = 0

    init {
        binding.play.setOnClickListener { toggle(viewModel.selection.startPos) }
        binding.rew.setOnClickListener { skip(-SKIP_MSEC, binding.startmarker) }
        binding.ffwd.setOnClickListener { skip(SKIP_MSEC, binding.endmarker) }
        binding.markStart.setOnClickListener { markAtPlayback(isStart = true) }
        binding.markEnd.setOnClickListener { markAtPlayback(isStart = false) }
        showState()
    }

    fun toggle(startPosition: Int) {
        if (isPlaying) {
            pause()
        } else {
            play(startPosition)
        }
    }

    fun pause() {
        viewModel.player?.let { if (it.isPlaying) it.pause() }
        binding.waveform.playbackPos = -1
        isPlaying = false
        showState()
    }

    /** Seeks to a tapped position while playing, stops outside the range, or plays from there. */
    fun tap(pos: Int) {
        if (!isPlaying) {
            play(pos)
            return
        }
        val seekMsec = binding.waveform.scale.pixelsToMillisecs(pos)
        if (seekMsec in startMsec until endMsec) {
            viewModel.player?.seekTo(seekMsec)
        } else {
            pause()
        }
    }

    /** Moves the playback line and keeps it in view; stops at the end of the playing range. */
    fun update() {
        val player = viewModel.player?.takeIf { isPlaying } ?: return
        val now = player.currentPosition
        val pos = binding.waveform.scale.millisecsToPixels(now)
        binding.waveform.playbackPos = pos
        viewModel.selection.scroll.scrollToCenter(pos)
        if (now >= endMsec) {
            pause()
        }
    }

    fun release() {
        pause()
        viewModel.player?.onCompletionListener = null
    }

    private fun play(startPosition: Int) {
        val player = viewModel.player ?: return
        val selection = viewModel.selection
        val scale = binding.waveform.scale
        try {
            startMsec = scale.pixelsToMillisecs(startPosition)
            endMsec =
                scale.pixelsToMillisecs(
                    when {
                        startPosition < selection.startPos -> selection.startPos
                        startPosition > selection.endPos -> selection.scroll.maxPos
                        else -> selection.endPos
                    },
                )
            player.onCompletionListener = { pause() }
            isPlaying = true
            player.seekTo(startMsec)
            player.start()
            onChanged()
            showState()
        } catch (e: IllegalStateException) {
            onError(e)
        } catch (e: IllegalArgumentException) {
            onError(e)
        }
    }

    // While playing, skips within the playing range; otherwise moves the focus to [marker].
    private fun skip(deltaMsec: Int, marker: MarkerView) {
        val player = viewModel.player
        if (isPlaying && player != null) {
            val target = player.currentPosition + deltaMsec
            player.seekTo(if (deltaMsec < 0) maxOf(target, startMsec) else minOf(target, endMsec))
        } else {
            marker.requestFocus()
            markerInput.markerFocus(marker)
        }
    }

    private fun markAtPlayback(isStart: Boolean) {
        val player = viewModel.player?.takeIf { isPlaying } ?: return
        val pos = binding.waveform.scale.millisecsToPixels(player.currentPosition)
        if (isStart) {
            viewModel.selection.startPos = pos
            onChanged()
        } else {
            viewModel.selection.endPos = pos
            onChanged()
            pause()
        }
    }

    private fun showState() {
        binding.play.setImageResource(
            if (isPlaying) R.drawable.baseline_pause_24 else R.drawable.baseline_play_arrow_24,
        )
        binding.play.contentDescription =
            binding.root.context.getText(if (isPlaying) R.string.stop else R.string.play)
    }

    private companion object {
        const val SKIP_MSEC = 5000
    }
}
