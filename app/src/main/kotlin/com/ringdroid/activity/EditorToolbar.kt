package com.ringdroid.activity

import androidx.activity.ComponentActivity
import androidx.core.view.isVisible
import com.ringdroid.R
import com.ringdroid.databinding.EditorToolbarBinding
import com.ringdroid.dialog.AmplifyDialog
import com.ringdroid.util.RingdroidUtils
import com.ringdroid.viewmodel.EditorViewModel

/** The editor's top bar: the song title and the back, amplify, reset and save buttons. */
internal class EditorToolbar(
    private val toolbar: EditorToolbarBinding,
    activity: ComponentActivity,
    private val viewModel: EditorViewModel,
    onReset: () -> Unit,
    onSave: () -> Unit,
) {
    init {
        RingdroidUtils.applyStatusBarInsets(toolbar.toolbarContainer)
        toolbar.btnBack.setOnClickListener { activity.finish() }
        toolbar.btnAmplify.setOnClickListener {
            AmplifyDialog.show(activity, viewModel.amplifyFactor) { factor -> viewModel.amplifyFactor = factor }
        }
        toolbar.btnReset.setOnClickListener { onReset() }
        toolbar.btnSave.setOnClickListener { onSave() }
        showTitle()
    }

    fun showTitle() {
        val title = viewModel.songTitle
        val artist = viewModel.songArtist
        if (title.isNullOrEmpty()) {
            toolbar.toolbarTitle.setText(R.string.edit_intent)
            toolbar.toolbarSubtitle.isVisible = false
        } else {
            toolbar.toolbarTitle.text = title
            toolbar.toolbarSubtitle.text = artist
            toolbar.toolbarSubtitle.isVisible = !artist.isNullOrEmpty()
        }
    }
}
