package com.ringdroid.feature.select

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ringdroid.R
import com.ringdroid.data.AudioItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingdroidSelectScreen(
    items: List<AudioItem>,
    query: String,
    onEdit: (AudioItem) -> Unit,
    onDelete: (AudioItem) -> Unit,
    onSetDefault: (AudioItem) -> Unit,
    onSetContact: (AudioItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    val filteredItems =
        remember(items, query) {
            if (query.isBlank()) {
                items
            } else {
                val q = query.trim().lowercase()
                items.filter {
                    it.title.lowercase().contains(q) ||
                        (it.artist?.lowercase()?.contains(q) == true) ||
                        (it.album?.lowercase()?.contains(q) == true)
                }
            }
        }

    LaunchedEffect(query) {
        if (filteredItems.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(filteredItems, key = { it.id }) { item ->
                AudioRow(
                    item = item,
                    onClick = { onEdit(item) },
                    onEdit = { onEdit(item) },
                    onDelete = { onDelete(item) },
                    onSetDefault = { onSetDefault(item) },
                    onSetContact = { onSetContact(item) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun AudioRow(
    item: AudioItem,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit,
    onSetContact: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClick = onClick)
                .background(
                    when {
                        item.isRingtone -> colorResource(R.color.type_bkgnd_ringtone)
                        item.isAlarm -> colorResource(R.color.type_bkgnd_alarm)
                        item.isNotification -> colorResource(R.color.type_bkgnd_notification)
                        item.isMusic -> colorResource(R.color.type_bkgnd_music)
                        else -> colorResource(R.color.type_bkgnd_unsupported)
                    }
                )
                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector =
                when {
                    item.isRingtone -> Icons.Default.Call
                    item.isAlarm -> Icons.Default.AccessAlarm
                    item.isNotification -> Icons.Default.Notifications
                    else -> Icons.Default.MusicNote
                },
            contentDescription = null,
            modifier = Modifier.padding(end = 12.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.artist ?: "",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                color = colorResource(R.color.row_artist_color),
            )

            Text(text = item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        }

        Text(
            text = item.album ?: "",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            color = colorResource(R.color.row_album_color),
        )

        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    modifier = Modifier.size(56.dp),
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(R.string.more_options),
                )
            }

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.context_menu_edit)) },
                    onClick = {
                        expanded = false
                        onEdit()
                    },
                )
                if (!item.isInternal) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.context_menu_delete)) },
                        onClick = {
                            expanded = false
                            onDelete()
                        },
                    )
                }
                if (item.isRingtone || item.isNotification) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (item.isRingtone) {
                                    stringResource(R.string.context_menu_default_ringtone)
                                } else {
                                    stringResource(R.string.context_menu_default_notification)
                                }
                            )
                        },
                        onClick = {
                            expanded = false
                            onSetDefault()
                        },
                    )
                }
                if (item.isRingtone) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.context_menu_contact)) },
                        onClick = {
                            expanded = false
                            onSetContact()
                        },
                    )
                }
            }
        }
    }
}
