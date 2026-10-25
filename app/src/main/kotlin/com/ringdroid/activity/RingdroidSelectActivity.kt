package com.ringdroid.activity

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.database.Cursor
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.SimpleCursorAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.view.isVisible
import com.ringdroid.BuildConfig
import com.ringdroid.R
import com.ringdroid.data.AudioRow
import com.ringdroid.databinding.MediaSelectBinding
import com.ringdroid.databinding.SelectOptionsPopupBinding
import com.ringdroid.dialog.AboutDialog
import com.ringdroid.dialog.Popups
import com.ringdroid.dialog.RowOptionsPopup
import com.ringdroid.soundfile.SoundFile
import com.ringdroid.util.ContactRingtonePicker
import com.ringdroid.util.DefaultSoundSetter
import com.ringdroid.util.PermissionUtils
import com.ringdroid.util.RingdroidUtils
import com.ringdroid.util.showFinalAlert
import com.ringdroid.view.SearchToolbarHelper
import com.ringdroid.viewmodel.SelectViewModel

/** Main screen: lists audio files, and opens the editor for a file or a new recording. */
class RingdroidSelectActivity :
    ComponentActivity(),
    SelectViewModel.Listener {
    private val viewModel: SelectViewModel by viewModels()
    private val editActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // When picking content for another app, hand the saved file straight back.
            if (result.resultCode == RESULT_OK && wasGetContentIntent) {
                setResult(RESULT_OK, result.data)
                finish()
            }
        }
    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onPermissionChanged() }
    private val deleter = AudioDeleter(this) { refreshListView() }
    private val contactPicker = ContactRingtonePicker(this) {}
    private val defaultSoundSetter = DefaultSoundSetter(this)

    private var binding: MediaSelectBinding? = null
    private var adapter: SimpleCursorAdapter? = null
    private var searchHelper: SearchToolbarHelper? = null
    private var wasGetContentIntent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!PermissionUtils.hasStoragePermission(this)) {
            storagePermissionLauncher.launch(PermissionUtils.storagePermissions)
        }
        if (savedInstanceState == null) {
            handleIncoming(intent)
        }

        val status = Environment.getExternalStorageState()
        val storageError =
            when (status) {
                Environment.MEDIA_MOUNTED -> null
                Environment.MEDIA_MOUNTED_READ_ONLY -> R.string.sdcard_readonly
                Environment.MEDIA_SHARED -> R.string.sdcard_shared
                else -> R.string.no_sdcard
            }
        if (storageError != null) {
            showFinalAlert(this, getText(storageError))
            return
        }
        wasGetContentIntent = intent.action == Intent.ACTION_GET_CONTENT

        val b = MediaSelectBinding.inflate(layoutInflater)
        binding = b
        setContentView(b.root)
        setupToolbar(b)

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
        cursorAdapter.setViewBinder { view, cursor, columnIndex -> bindRowView(view, cursor, columnIndex) }
        adapter = cursorAdapter
        b.list.adapter = cursorAdapter
        b.list.itemsCanFocus = true
        b.list.setOnItemClickListener { _, _, position, _ -> startEditorForRow(position) }
        b.list.setOnItemLongClickListener { _, view, position, _ ->
            showRowOptions(view.findViewById(R.id.row_options_button) ?: view, position)
            true
        }

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

    private fun bindRowView(
        view: View,
        cursor: Cursor,
        columnIndex: Int,
    ): Boolean {
        when (view.id) {
            R.id.row_options_button -> {
                val position = cursor.position
                view.setOnClickListener { showRowOptions(it, position) }
            }

            R.id.row_icon -> {
                setSoundIcon(view as ImageView, cursor)
            }

            R.id.row_artist -> {
                bindOptionalText(view as TextView, cursor.getString(columnIndex), hideZero = false)
            }

            R.id.row_album -> {
                bindOptionalText(view as TextView, cursor.getString(columnIndex), hideZero = true)
            }

            else -> {
                return false
            }
        }
        return true
    }

    // MediaStore reports missing tags as "<unknown>" (and a missing album as "0").
    private fun bindOptionalText(
        view: TextView,
        text: String?,
        hideZero: Boolean,
    ) {
        val hidden = text.isNullOrEmpty() || text.equals("<unknown>", ignoreCase = true) || (hideZero && text == "0")
        view.isVisible = !hidden
        if (!hidden) {
            view.text = text
        }
    }

    private fun refreshListView() {
        if (binding != null) {
            viewModel.load(searchHelper?.activeQuery)
        }
    }

    private fun setSoundIcon(
        view: ImageView,
        cursor: Cursor,
    ) {
        val (iconRes, iconColorRes, containerColorRes) =
            when {
                cursor.isFlagSet(MediaStore.Audio.Media.IS_RINGTONE) -> {
                    Triple(R.drawable.baseline_call_24, R.color.category_icon_ringtone, R.color.category_container_ringtone)
                }

                cursor.isFlagSet(MediaStore.Audio.Media.IS_ALARM) -> {
                    Triple(R.drawable.baseline_access_alarm_24, R.color.category_icon_alarm, R.color.category_container_alarm)
                }

                cursor.isFlagSet(MediaStore.Audio.Media.IS_NOTIFICATION) -> {
                    Triple(
                        R.drawable.baseline_notifications_24,
                        R.color.category_icon_notification,
                        R.color.category_container_notification,
                    )
                }

                else -> {
                    Triple(R.drawable.baseline_music_note_24, R.color.category_icon_music, R.color.category_container_music)
                }
            }
        val filename = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
        val supported = filename != null && SoundFile.isFilenameSupported(filename)
        view.setImageResource(iconRes)
        view.imageTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, if (supported) iconColorRes else R.color.category_icon_unsupported))
        view.backgroundTintList =
            ColorStateList.valueOf(
                ContextCompat.getColor(this, if (supported) containerColorRes else R.color.category_container_unsupported),
            )
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
        RingdroidUtils.applyNavigationBarInsets(b.list, 16)
        searchHelper = SearchToolbarHelper(this, b.toolbarNormal, b.toolbarSearch, b.btnSearch) { refreshListView() }
        b.btnRecord.setOnClickListener { startEditor(null) }
        b.btnMore.setOnClickListener { showOptionsMenu(it) }
    }

    private fun showOptionsMenu(anchor: View) {
        val popupBinding = SelectOptionsPopupBinding.inflate(layoutInflater)
        popupBinding.switchShowAll.isChecked = viewModel.showAll
        popupBinding.menuAboutVersion.text = getString(R.string.version_label, BuildConfig.VERSION_NAME)
        val widthDp = 280
        val popup = Popups.create(popupBinding.root, widthDp)
        popupBinding.menuItemShowAll.setOnClickListener {
            viewModel.showAll = !viewModel.showAll
            popupBinding.switchShowAll.isChecked = viewModel.showAll
            refreshListView()
        }
        popupBinding.menuItemAbout.setOnClickListener {
            popup.dismiss()
            AboutDialog.show(this)
        }
        Popups.showAlignedRight(popup, anchor, widthDp, 8)
    }

    /** The row at [position] of the list, or null if the list changed. */
    private fun rowAt(position: Int): AudioRow? {
        val c = adapter?.cursor ?: return null
        if (c.isClosed || !c.moveToPosition(position)) {
            return null
        }
        return AudioRow.from(c, isInternal = position < viewModel.internalRowCount)
    }

    private fun showRowOptions(
        anchor: View,
        position: Int,
    ) {
        val row = rowAt(position) ?: return
        RowOptionsPopup.show(
            anchor,
            row.title,
            row.isRingtone,
            row.isNotification,
            row.canDelete(),
            object : RowOptionsPopup.Actions {
                override fun onEdit() {
                    startEditor(row.uri)
                }

                override fun onSetDefault() {
                    val type = if (row.isRingtone) RingtoneManager.TYPE_RINGTONE else RingtoneManager.TYPE_NOTIFICATION
                    defaultSoundSetter.set(type, row.uri)
                }

                override fun onAssignContact() {
                    try {
                        contactPicker.pick(row.uri)
                    } catch (e: ActivityNotFoundException) {
                        Log.e(TAG, "Couldn't open the contact picker", e)
                    }
                }

                override fun onDelete() {
                    deleter.delete(row.uri, row.path, row.title)
                }
            },
        )
    }

    /** Opens the editor for audio viewed or shared from another app; the editor copies it if needed. */
    private fun handleIncoming(intent: Intent) {
        val uri =
            when (intent.action) {
                Intent.ACTION_VIEW -> intent.data
                Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                else -> null
            }
        if (uri != null) {
            startEditor(uri)
        }
    }

    private fun startEditorForRow(position: Int) {
        rowAt(position)?.let { startEditor(it.uri) }
    }

    private fun startEditor(data: Uri?) {
        try {
            editActivityLauncher.launch(RingdroidEditActivity.newIntent(this, data, wasGetContentIntent))
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Couldn't start editor", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Couldn't start editor", e)
        }
    }

    private companion object {
        const val TAG = "RingdroidSelectActivity"
        const val STATE_SEARCH_OPEN = "search_open"

        fun Cursor.isFlagSet(column: String) = getInt(getColumnIndexOrThrow(column)) != 0
    }
}
