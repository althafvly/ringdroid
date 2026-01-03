package com.ringdroid.ui.dialog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class ProgressDialogState(
    val titleRes: Int,
    val progress: Float?,
    val cancelable: Boolean,
    val onCancel: (() -> Unit)?,
)

data class RecordingDialogState(
    val timeText: String,
    val onStop: () -> Unit,
    val onCancel: () -> Unit,
)

data class FinalAlertState(
    val title: CharSequence,
    val message: CharSequence,
    val onDismiss: () -> Unit,
)

data class NotificationPromptState(val onConfirm: () -> Unit, val onDismiss: () -> Unit)

data class AfterSaveActionState(
    val onMakeDefault: () -> Unit,
    val onChooseContact: () -> Unit,
    val onDoNothing: () -> Unit,
)

data class FileSaveDialogState(
    val originalName: String,
    val onSave: (String, Int) -> Unit,
    val onDismiss: () -> Unit,
)

class DialogController {
    var progressDialogState by mutableStateOf<ProgressDialogState?>(null)
        private set

    var recordingDialogState by mutableStateOf<RecordingDialogState?>(null)
        private set

    var finalAlertState by mutableStateOf<FinalAlertState?>(null)
        private set

    var notificationPromptState by mutableStateOf<NotificationPromptState?>(null)
        private set

    var afterSaveActionState by mutableStateOf<AfterSaveActionState?>(null)
        private set

    var fileSaveDialogState by mutableStateOf<FileSaveDialogState?>(null)
        private set

    fun showProgressDialog(
        titleRes: Int,
        progress: Float? = null,
        cancelable: Boolean = false,
        onCancel: (() -> Unit)? = null,
    ) {
        progressDialogState =
            ProgressDialogState(
                titleRes = titleRes,
                progress = progress,
                cancelable = cancelable,
                onCancel = onCancel,
            )
    }

    fun updateProgressDialog(progress: Float) {
        progressDialogState = progressDialogState?.copy(progress = progress)
    }

    fun hideProgressDialog() {
        progressDialogState = null
    }

    fun showRecordingDialog(timeText: String, onStop: () -> Unit, onCancel: () -> Unit) {
        recordingDialogState =
            RecordingDialogState(timeText = timeText, onStop = onStop, onCancel = onCancel)
    }

    fun updateRecordingTime(timeText: String) {
        recordingDialogState = recordingDialogState?.copy(timeText = timeText)
    }

    fun hideRecordingDialog() {
        recordingDialogState = null
    }

    fun showFinalAlert(title: CharSequence, message: CharSequence, onDismiss: () -> Unit) {
        finalAlertState = FinalAlertState(title, message, onDismiss)
    }

    fun clearFinalAlert() {
        finalAlertState = null
    }

    fun showNotificationPrompt(onConfirm: () -> Unit, onDismiss: () -> Unit) {
        notificationPromptState = NotificationPromptState(onConfirm, onDismiss)
    }

    fun clearNotificationPrompt() {
        notificationPromptState = null
    }

    fun showAfterSaveActionDialog(
        onMakeDefault: () -> Unit,
        onChooseContact: () -> Unit,
        onDoNothing: () -> Unit,
    ) {
        afterSaveActionState = AfterSaveActionState(onMakeDefault, onChooseContact, onDoNothing)
    }

    fun clearAfterSaveActionDialog() {
        afterSaveActionState = null
    }

    fun showFileSaveDialog(
        originalName: String,
        onSave: (String, Int) -> Unit,
        onDismiss: () -> Unit,
    ) {
        fileSaveDialogState = FileSaveDialogState(originalName, onSave, onDismiss)
    }

    fun clearFileSaveDialog() {
        fileSaveDialogState = null
    }
}
