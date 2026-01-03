package com.ringdroid.feature.contact

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ringdroid.R
import com.ringdroid.data.ContactItem

@Composable
fun ChooseContactScreen(
    contacts: List<ContactItem>,
    filter: String,
    onFilterChange: (String) -> Unit,
    onContactClick: (ContactItem) -> Unit,
    modifier: Modifier,
) {
    Column {
        OutlinedTextField(
            value = filter,
            onValueChange = onFilterChange,
            modifier = modifier.fillMaxWidth().padding(8.dp),
            placeholder = { Text(stringResource(R.string.search_edit_box)) },
            singleLine = true,
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items = contacts, key = { it.id }) { contact ->
                ContactRow(contact = contact, onClick = { onContactClick(contact) })
                HorizontalDivider()
            }
        }
    }
}
