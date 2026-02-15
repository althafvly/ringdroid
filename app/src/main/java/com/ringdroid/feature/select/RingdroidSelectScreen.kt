package com.ringdroid.feature.select

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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


