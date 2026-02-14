package com.ringdroid.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ringdroid.R

object FileSaveDialogDefaults {
    const val FILE_KIND_MUSIC = 0
    const val FILE_KIND_ALARM = 1
    const val FILE_KIND_NOTIFICATION = 2
    const val FILE_KIND_RINGTONE = 3
}

@Composable
fun FileSaveDialog(originalName: String, onSave: (String, Int) -> Unit, onDismiss: () -> Unit) {
    val typeArray =
        listOf(
            stringResource(R.string.type_music),
            stringResource(R.string.type_alarm),
            stringResource(R.string.type_notification),
            stringResource(R.string.type_ringtone),
        )
    val resources = LocalResources.current
    val formatFilename: (Int) -> String =
        remember(originalName, typeArray) {
            { typeIndex ->
                resources.getString(
                    R.string.filename_with_suffix,
                    originalName,
                    typeArray[typeIndex],
                )
            }
        }

    var selectedType by
        rememberSaveable(originalName) {
            mutableIntStateOf(FileSaveDialogDefaults.FILE_KIND_RINGTONE)
        }
    var filename by rememberSaveable(originalName) { mutableStateOf("") }
    var filenameEdited by rememberSaveable(originalName) { mutableStateOf(false) }
    var previousSelection by
        rememberSaveable(originalName) {
            mutableIntStateOf(FileSaveDialogDefaults.FILE_KIND_RINGTONE)
        }

    LaunchedEffect(originalName) {
        filenameEdited = false
        selectedType = FileSaveDialogDefaults.FILE_KIND_RINGTONE
        previousSelection = FileSaveDialogDefaults.FILE_KIND_RINGTONE
        filename = formatFilename(selectedType)
    }

    LaunchedEffect(selectedType) {
        val expectedText = formatFilename(previousSelection)
        if (!filenameEdited || filename == expectedText) {
            filename = formatFilename(selectedType)
            filenameEdited = false
        }
        previousSelection = selectedType
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 6.dp) {
            FileSaveContent(
                typeArray = typeArray,
                selectedType = selectedType,
                filename = filename,
                onTypeSelected = { selectedType = it },
                onFilenameChange = {
                    filename = it
                    filenameEdited = true
                },
                onSave = { onSave(filename, selectedType) },
                onCancel = onDismiss,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileSaveContent(
    typeArray: List<String>,
    selectedType: Int,
    filename: String,
    onTypeSelected: (Int) -> Unit,
    onFilenameChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.ringtone_type_label),
                style = MaterialTheme.typography.bodySmall,
            )

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = typeArray[selectedType],
                    onValueChange = {},
                    modifier =
                        Modifier.fillMaxWidth()
                            .menuAnchor(
                                type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                enabled = true,
                            ),
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    colors = ExposedDropdownMenuDefaults.textFieldColors(),
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    typeArray.forEachIndexed { index, label ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                expanded = false
                                onTypeSelected(index)
                            },
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.ringtone_name_label),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = filename,
                onValueChange = onFilenameChange,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                singleLine = true,
                placeholder = { Text(text = stringResource(R.string.ringtone_name_label)) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onSave, modifier = Modifier.weight(1f).width(0.dp)) {
                Text(text = stringResource(R.string.file_save_button_save))
            }
            Button(onClick = onCancel, modifier = Modifier.weight(1f).width(0.dp)) {
                Text(text = stringResource(R.string.file_save_button_cancel))
            }
        }
    }
}
