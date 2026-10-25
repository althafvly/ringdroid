package com.ringdroid.activity

import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.SimpleCursorAdapter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.isVisible
import com.ringdroid.R
import com.ringdroid.data.AudioRow
import com.ringdroid.databinding.MediaSelectBinding
import com.ringdroid.dialog.OptionsPopup
import com.ringdroid.util.PermissionUtils
import com.ringdroid.util.RingdroidUtils
import com.ringdroid.util.showFinalAlert
import com.ringdroid.view.AudioRowBinder
import com.ringdroid.view.SearchToolbarHelper
import com.ringdroid.viewmodel.SelectViewModel

/** Main screen: lists audio files, and opens the editor for a file or a new recording. */
class RingdroidSelectActivity :
    ComponentActivity(),
    SelectViewModel.Listener {
    private val viewModel: SelectViewModel by viewModels()
    private val editorLauncher = EditorLauncher(this)
    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onPermissionChanged() }
    private val rowActions = RowActions(this, editorLauncher, ::rowAt) { refreshListView() }

    private var binding: MediaSelectBinding? = null
    private var adapter: SimpleCursorAdapter? = null
    private var searchHelper: SearchToolbarHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!PermissionUtils.hasStoragePermission(this)) {
            storagePermissionLauncher.launch(PermissionUtils.storagePermissions)
        }
        if (savedInstanceState == null) {
            editorLauncher.openIncoming(intent)
        }

        val storageError =
            when (Environment.getExternalStorageState()) {
                Environment.MEDIA_MOUNTED -> null
                Environment.MEDIA_MOUNTED_READ_ONLY -> R.string.sdcard_readonly
                Environment.MEDIA_SHARED -> R.string.sdcard_shared
                else -> R.string.no_sdcard
            }
        if (storageError != null) {
            showFinalAlert(this, getText(storageError))
            return
        }

        val b = MediaSelectBinding.inflate(layoutInflater)
        binding = b
        setContentView(b.root)
        setupToolbar(b)
        setupList(b)

        if (savedInstanceState?.getBoolean(STATE_SEARCH_OPEN) == true) {
            searchHelper?.openSearch()
        }
        viewModel.events.listener = this
        onAudioLoaded()

        // Clear the extras to avoid TransactionTooLargeException when this activity is stopped.
        intent.replaceExtras(null as Bundle?)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_SEARCH_OPEN, searchHelper?.isSearchOpen == true)
    }

    override fun onDestroy() {
        viewModel.events.listener = null
        // The view model owns and closes the cursor.
        adapter?.swapCursor(null)
        super.onDestroy()
    }

    override fun onAudioLoaded() {
        val b = binding ?: return
        b.emptyText.isVisible = viewModel.loaded && viewModel.cursor == null
        adapter?.swapCursor(viewModel.cursor)
    }

    override fun onResume() {
        super.onResume()
        // Show files saved in the editor.
        refreshListView()
    }

    private fun refreshListView() {
        if (binding != null) {
            viewModel.load(searchHelper?.activeQuery)
        }
    }

    private fun onPermissionChanged() {
        if (PermissionUtils.hasStoragePermission(this)) {
            refreshListView()
        } else {
            Toast.makeText(this, R.string.required_storage_permission, Toast.LENGTH_LONG).show()
        }
    }

    private fun setupToolbar(b: MediaSelectBinding) {
        RingdroidUtils.applyStatusBarInsets(b.toolbarContainer)
        RingdroidUtils.applyNavigationBarInsets(b.list, LIST_BOTTOM_PADDING_DP)
        searchHelper = SearchToolbarHelper(this, b.toolbarNormal, b.toolbarSearch, b.btnSearch) { refreshListView() }
        b.btnRecord.setOnClickListener { editorLauncher.open(null) }
        b.btnMore.setOnClickListener { anchor ->
            OptionsPopup.show(anchor, viewModel.showAll) { showAll ->
                viewModel.showAll = showAll
                refreshListView()
            }
        }
    }

    private fun setupList(b: MediaSelectBinding) {
        val cursorAdapter =
            SimpleCursorAdapter(
                this,
                R.layout.media_select_row,
                null,
                arrayOf(
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media._ID,
                ),
                intArrayOf(R.id.row_artist, R.id.row_album, R.id.row_title, R.id.row_icon, R.id.row_options_button),
                0,
            )
        cursorAdapter.viewBinder = AudioRowBinder(rowActions::show)
        adapter = cursorAdapter
        b.list.adapter = cursorAdapter
        b.list.itemsCanFocus = true
        b.list.setOnItemClickListener { _, _, position, _ -> rowAt(position)?.let { editorLauncher.open(it.uri) } }
        b.list.setOnItemLongClickListener { _, view, position, _ ->
            rowActions.show(view.findViewById(R.id.row_options_button) ?: view, position)
            true
        }
    }

    /** The row at [position] of the list, or null if the list changed. */
    private fun rowAt(position: Int): AudioRow? =
        adapter
            ?.cursor
            ?.takeIf { !it.isClosed && it.moveToPosition(position) }
            ?.let { AudioRow.from(it, isInternal = position < viewModel.internalRowCount) }

    private companion object {
        const val STATE_SEARCH_OPEN = "search_open"
        const val LIST_BOTTOM_PADDING_DP = 16
    }
}
