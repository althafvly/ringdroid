package com.ringdroid.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ringdroid.R

@Composable
fun AfterSaveActionDialog(
    onDismissRequest: () -> Unit,
    onMakeDefault: () -> Unit,
    onChooseContact: () -> Unit,
    onDoNothing: () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 6.dp) {
            AfterSaveActionContent(
                onMakeDefault = onMakeDefault,
                onChooseContact = onChooseContact,
                onDoNothing = onDoNothing,
            )
        }
    }
}

@Composable
private fun AfterSaveActionContent(
    onMakeDefault: () -> Unit,
    onChooseContact: () -> Unit,
    onDoNothing: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = stringResource(R.string.what_to_do_with_ringtone),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        )
        Button(onClick = onMakeDefault, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.make_default_ringtone_button))
        }
        Button(onClick = onChooseContact, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(text = stringResource(R.string.choose_contact_ringtone_button))
        }
        Button(onClick = onDoNothing, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text(text = stringResource(R.string.do_nothing_with_ringtone_button))
        }
    }
}
