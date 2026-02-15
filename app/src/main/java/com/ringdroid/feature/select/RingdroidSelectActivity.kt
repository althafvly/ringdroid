package com.ringdroid.feature.select

import android.content.ContentUris
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.ringdroid.R
import com.ringdroid.core.media.RingdroidUtils
import com.ringdroid.data.AudioItem
import com.ringdroid.feature.contact.ChooseContactActivity
import com.ringdroid.feature.editor.RingdroidEditActivity
import com.ringdroid.soundfile.SoundFile
import com.ringdroid.theme.AppTheme
import com.ringdroid.ui.dialog.ConfirmDeleteDialog
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
class RingdroidSelectActivity : ComponentActivity() {

    private var items by mutableStateOf<List<AudioItem>>(emptyList())
    private var showAll by mutableStateOf(false)
    private var query by mutableStateOf("")
    private var searchActive by mutableStateOf(false)
    private var pendingDelete by mutableStateOf<AudioItem?>(null)
    private var menuExpanded by mutableStateOf(false)
    private var showAbout by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadAudio()

        setContent {
            AppTheme {
                Scaffold(
                    topBar = {
                        if (searchActive) {
                            SearchBar(
                                colors =
                                    SearchBarDefaults.colors(
                                        containerColor = Color.Transparent,
                                        dividerColor = Color.Transparent,
                                    ),
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                                inputField = {
                                    SearchBarDefaults.InputField(
                                        query = query,
                                        onQueryChange = {
                                            query = it
                                            loadAudio()
                                        },
                                        onSearch = { searchActive = false },
                                        expanded = searchActive,
                                        onExpandedChange = { searchActive = it },
                                        placeholder = {
                                            Text(stringResource(R.string.search_edit_box))
                                        },
                                        leadingIcon = {
                                            IconButton(
                                                onClick = {
                                                    searchActive = false
                                                    query = ""
                                                }
                                            ) {
                                                Icon(
                                                    imageVector =
                                                        Icons.AutoMirrored.Filled.ArrowBack,
                                                    contentDescription = null,
                                                )
                                            }
                                        },
                                        trailingIcon = {
                                            if (query.isNotEmpty()) {
                                                IconButton(
                                                    onClick = {
                                                        query = ""
                                                        loadAudio()
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = null,
                                                    )
                                                }
                                            }
                                        },
                                        colors =
                                            SearchBarDefaults.inputFieldColors(
                                                focusedContainerColor = Color.Transparent,
                                                unfocusedContainerColor = Color.Transparent,
                                                disabledContainerColor = Color.Transparent,
                                            ),
                                    )
                                },
                                expanded = false,
                                onExpandedChange = {},
                            ) {}
                        } else {
                            TopAppBar(
                                title = { Text(stringResource(R.string.ringdroid_app_name)) },
                                actions = {
                                    IconButton(onClick = { searchActive = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription =
                                                stringResource(R.string.search_edit_box),
                                        )
                                    }

                                    IconButton(onClick = ::onRecord) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription =
                                                stringResource(R.string.progress_dialog_recording),
                                        )
                                    }

                                    IconButton(onClick = { menuExpanded = true }) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription =
                                                stringResource(R.string.more_options),
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(stringResource(R.string.show_system_sounds))
                                            },
                                            trailingIcon = {
                                                if (showAll) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                    )
                                                }
                                            },
                                            onClick = {
                                                showAll = !showAll
                                                menuExpanded = false
                                                loadAudio()
                                            },
                                        )

                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.about_title)) },
                                            onClick = {
                                                menuExpanded = false
                                                showAbout = true
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    }
                ) { padding ->
                    RingdroidSelectScreen(
                        items = items,
                        query = query,
                        onEdit = ::openEditor,
                        onDelete = { pendingDelete = it },
                        onSetDefault = ::setAsDefault,
                        onSetContact = ::chooseContact,
                        modifier = Modifier.padding(padding),
                    )

                    if (showAbout) {
                        AboutDialog(onDismiss = { showAbout = false })
                    }

                    pendingDelete?.let { item ->
                        ConfirmDeleteDialog(
                            item = item,
                            onConfirm = {
                                pendingDelete = null
                                deleteItem(item)
                            },
                            onDismiss = { pendingDelete = null },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadAudio()
    }



    private fun loadAudio() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = queryMediaStore()
            withContext(Dispatchers.Main) { items = result }
        }
    }

    private fun openEditor(item: AudioItem) {
        startActivity(
            Intent(Intent.ACTION_EDIT, item.path?.toUri())
                .setClass(this, RingdroidEditActivity::class.java)
        )
    }

    private fun setAsDefault(item: AudioItem) {
        if (item.isInternal) return

        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, item.id)
        val type =
            if (item.isRingtone) RingtoneManager.TYPE_RINGTONE
            else RingtoneManager.TYPE_NOTIFICATION

        RingdroidUtils.setDefaultRingTone(this, type, uri, false)
    }

    private fun chooseContact(item: AudioItem) {
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, item.id)
        startActivity(
            Intent(Intent.ACTION_EDIT, uri).setClass(this, ChooseContactActivity::class.java)
        )
    }

    private fun deleteItem(item: AudioItem) {
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, item.id)

        lifecycleScope.launch(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                item.path?.let { File(it).delete() }
            }

            val deleted = contentResolver.delete(uri, null, null)

            withContext(Dispatchers.Main) {
                if (deleted > 0) {
                    items = items.filterNot { it.id == item.id }
                }
                loadAudio()
            }
        }
    }

    private fun onRecord() {
        startActivity(
            Intent(Intent.ACTION_EDIT, "record".toUri())
                .setClass(this, RingdroidEditActivity::class.java)
        )
    }

    private fun queryMediaStore(): List<AudioItem> {
        val external = queryOneStore(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        val internal = queryOneStore(MediaStore.Audio.Media.INTERNAL_CONTENT_URI)

        // Default: user audio only
        val result = external.toMutableList()
        if (showAll) {
            result += internal
        }

        return result.distinctBy { it.id }.sortedBy { it.title.lowercase() }
    }

    private fun queryOneStore(baseUri: Uri): List<AudioItem> {
        val result = mutableListOf<AudioItem>()

        val projection =
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.IS_RINGTONE,
                MediaStore.Audio.Media.IS_ALARM,
                MediaStore.Audio.Media.IS_NOTIFICATION,
                MediaStore.Audio.Media.IS_MUSIC,
                MediaStore.Audio.Media.DATA,
            )

        val selectionArgs = ArrayList<String>()
        val selection = StringBuilder()

        if (!showAll) {
            selection.append("(")
            SoundFile.getSupportedExtensions().forEachIndexed { i, ext ->
                if (i > 0) selection.append(" OR ")
                selection.append("${MediaStore.Audio.Media.DATA} LIKE ?")
                selectionArgs.add("%.$ext")
            }
            selection.append(")")
        }

        contentResolver
            .query(
                baseUri,
                projection,
                if (selection.isEmpty()) null else selection.toString(),
                if (selectionArgs.isEmpty()) null else selectionArgs.toTypedArray(),
                MediaStore.Audio.Media.TITLE + " ASC",
            )
            ?.use { c ->
                while (c.moveToNext()) {
                    result +=
                        AudioItem(
                            id = c.getLong(0),
                            title = c.getString(1),
                            artist = c.getString(2),
                            album = c.getString(3),
                            duration = c.getLong(4),
                            mimeType = c.getString(6),
                            path = c.getString(11),
                            isInternal = baseUri == MediaStore.Audio.Media.INTERNAL_CONTENT_URI,
                            isRingtone = c.getInt(7) != 0,
                            isAlarm = c.getInt(8) != 0,
                            isNotification = c.getInt(9) != 0,
                            isMusic = c.getInt(10) != 0,
                        )
                }
            }

        return result
    }
}
