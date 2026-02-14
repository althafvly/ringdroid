package com.ringdroid.ui.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ringdroid.R

@Composable
fun RingdroidDialogs(dialogController: DialogController) {
    dialogController.progressDialogState?.let { state ->
        AlertDialog(
            onDismissRequest = {
                if (state.cancelable) {
                    dialogController.hideProgressDialog()
                    state.onCancel?.invoke()
                }
            },
            title = { Text(text = stringResource(state.titleRes)) },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.progress != null) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                if (state.cancelable) {
                    TextButton(
                        onClick = {
                            dialogController.hideProgressDialog()
                            state.onCancel?.invoke()
                        }
                    ) {
                        Text(text = stringResource(R.string.progress_dialog_cancel))
                    }
                }
            },
        )
    }

    dialogController.recordingDialogState?.let { state ->
        AlertDialog(
            onDismissRequest = {
                state.onCancel()
                dialogController.hideRecordingDialog()
            },
            title = { Text(stringResource(R.string.progress_dialog_recording)) },
            text = {
                Text(
                    text = state.timeText,
                    style =
                        MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 48.sp,
                        ),
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.onStop()
                        dialogController.hideRecordingDialog()
                    }
                ) {
                    Text(stringResource(R.string.progress_dialog_stop))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        state.onCancel()
                        dialogController.hideRecordingDialog()
                    }
                ) {
                    Text(stringResource(R.string.progress_dialog_cancel))
                }
            },
        )
    }

    dialogController.finalAlertState?.let { state ->
        AlertDialog(
            onDismissRequest = {
                dialogController.clearFinalAlert()
                state.onDismiss()
            },
            title = { Text(text = state.title.toString()) },
            text = { Text(text = state.message.toString()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialogController.clearFinalAlert()
                        state.onDismiss()
                    }
                ) {
                    Text(text = stringResource(R.string.alert_ok_button))
                }
            },
        )
    }

    dialogController.notificationPromptState?.let { state ->
        AlertDialog(
            onDismissRequest = {
                dialogController.clearNotificationPrompt()
                state.onDismiss()
            },
            title = { Text(text = stringResource(R.string.alert_title_success)) },
            text = { Text(text = stringResource(R.string.set_default_notification)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialogController.clearNotificationPrompt()
                        state.onConfirm()
                    }
                ) {
                    Text(text = stringResource(R.string.alert_yes_button))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        dialogController.clearNotificationPrompt()
                        state.onDismiss()
                    }
                ) {
                    Text(text = stringResource(R.string.alert_no_button))
                }
            },
        )
    }

    dialogController.afterSaveActionState?.let { state ->
        AfterSaveActionDialog(
            onDismissRequest = {
                dialogController.clearAfterSaveActionDialog()
                state.onDoNothing()
            },
            onMakeDefault = {
                dialogController.clearAfterSaveActionDialog()
                state.onMakeDefault()
            },
            onChooseContact = {
                dialogController.clearAfterSaveActionDialog()
                state.onChooseContact()
            },
            onDoNothing = {
                dialogController.clearAfterSaveActionDialog()
                state.onDoNothing()
            },
        )
    }

    dialogController.fileSaveDialogState?.let { state ->
        FileSaveDialog(
            originalName = state.originalName,
            onSave = { filename, type ->
                dialogController.clearFileSaveDialog()
                state.onSave(filename, type)
            },
            onDismiss = {
                dialogController.clearFileSaveDialog()
                state.onDismiss()
            },
        )
    }
}
