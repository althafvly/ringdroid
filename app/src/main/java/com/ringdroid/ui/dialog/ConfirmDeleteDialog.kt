package com.ringdroid.ui.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ringdroid.R
import com.ringdroid.data.AudioItem

@Composable
fun ConfirmDeleteDialog(item: AudioItem, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.context_menu_delete)) },
        text = {
            Text(
                if (item.artist == stringResource(R.string.artist_name))
                    stringResource(R.string.confirm_delete_ringdroid)
                else stringResource(R.string.confirm_delete_non_ringdroid)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete_ok_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.delete_cancel_button)) }
        },
    )
}
