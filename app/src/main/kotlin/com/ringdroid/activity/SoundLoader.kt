package com.ringdroid.activity

import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.ringdroid.R
import com.ringdroid.dialog.LoadingDialog
import com.ringdroid.dialog.RecordingDialog
import com.ringdroid.util.PermissionUtils
import com.ringdroid.viewmodel.EditorViewModel
import com.ringdroid.viewmodel.EditorViewModel.Task

/**
 * Shows the progress of the view model's load or record job and asks for the microphone
 * permission. Must be created while the activity is being initialized.
 */
internal class SoundLoader(
    private val activity: ComponentActivity,
    viewModelLazy: Lazy<EditorViewModel>,
    private val callbacks: Callbacks,
) : EditorViewModel.Listener {
    interface Callbacks {
        fun onMetadata()

        fun onLoaded()

        /** Shows [message] and closes the editor. */
        fun onFatalError(cause: Throwable?, message: CharSequence)
    }

    private val viewModel by viewModelLazy
    private var loadingDialog: LoadingDialog? = null
    private var recordingDialog: RecordingDialog? = null
    private val micPermissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (PermissionUtils.hasMicPermissions(activity)) {
                viewModel.record()
                showDialog()
            } else {
                Toast.makeText(activity, R.string.required_mic_permission, Toast.LENGTH_LONG).show()
                activity.finish()
            }
        }

    /** Loads [data], or records when it's null, or reconnects to a job from before recreation. */
    fun start(data: Uri?) {
        viewModel.events.listener = this
        if (!viewModel.started) {
            if (data == null) {
                if (!PermissionUtils.hasMicPermissions(activity)) {
                    micPermissionLauncher.launch(PermissionUtils.micPermission)
                    return
                }
                viewModel.record()
            } else {
                viewModel.load(data)
            }
        }
        showDialog()
    }

    /** Disconnects from the view model; a finishing editor also cancels the job. */
    fun detach() {
        if (activity.isFinishing) {
            viewModel.cancel()
        }
        viewModel.events.listener = null
        dismissDialogs()
    }

    private fun showDialog() {
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }
        when (viewModel.task) {
            Task.LOADING -> {
                loadingDialog =
                    LoadingDialog(activity) { viewModel.cancel() }.also {
                        it.setSubtitle(viewModel.songTitle)
                        it.show()
                    }
            }

            Task.RECORDING -> {
                recordingDialog =
                    RecordingDialog(
                        activity,
                        onStop = { viewModel.stopRecording() },
                        onCancel = { viewModel.cancel() },
                    ).also { it.show() }
            }

            Task.IDLE -> {}
        }
    }

    private fun dismissDialogs() {
        loadingDialog?.dismiss()
        loadingDialog = null
        recordingDialog?.dismiss()
        recordingDialog = null
    }

    override fun onProgress(value: Double) {
        loadingDialog?.setProgress(value)
        recordingDialog?.setElapsedSeconds(value)
    }

    override fun onMetadata() {
        loadingDialog?.setSubtitle(viewModel.songTitle)
        callbacks.onMetadata()
    }

    override fun onLoaded() {
        dismissDialogs()
        callbacks.onLoaded()
    }

    override fun onFailed(cause: Throwable?, message: CharSequence) {
        dismissDialogs()
        callbacks.onFatalError(cause, message)
    }

    override fun onCanceled() {
        dismissDialogs()
        activity.finish()
    }
}
