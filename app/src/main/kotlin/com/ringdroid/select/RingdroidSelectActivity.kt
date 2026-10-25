package com.ringdroid.select

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.content.res.ColorStateList
import android.database.Cursor
import android.database.MergeCursor
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.SimpleCursorAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.ringdroid.BuildConfig
import com.ringdroid.R
import com.ringdroid.databinding.MediaSelectBinding
import com.ringdroid.databinding.SelectOptionsPopupBinding
import com.ringdroid.editor.RingdroidEditActivity
import com.ringdroid.soundfile.SoundFile
import com.ringdroid.util.AboutDialog
import com.ringdroid.util.ContactRingtonePicker
import com.ringdroid.util.DefaultSoundSetter
import com.ringdroid.util.PermissionUtils
import com.ringdroid.util.RingdroidUtils
import java.io.File

/** Main screen: lists audio files, and opens the editor for a file or a new recording. */
class RingdroidSelectActivity : ComponentActivity() {
    private val uiHandler = Handler(Looper.getMainLooper())
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
    private var showAll = false
    private var internalRowCount = 0
    private var loadGeneration = 0

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
            showFinalAlert(getText(storageError))
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

        // Clear the extras to avoid TransactionTooLargeException when this activity is stopped.
        intent.replaceExtras(null as Bundle?)
    }

    override fun onDestroy() {
        adapter?.changeCursor(null)
        super.onDestroy()
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
            loadAudio(searchHelper?.activeQuery)
        }
    }

    private fun loadAudio(filter: String?) {
        val generation = ++loadGeneration
        RingdroidUtils.BACKGROUND.execute {
            var internal: Cursor? = null
            var external: Cursor? = null

            fun failed(e: Exception) {
                Log.e(TAG, "Loader error", e)
                internal?.close()
                external?.close()
            }
            try {
                val query = AudioQuery.build(showAll, filter, SoundFile.getSupportedExtensions(), storageRoots())
                internal = query.run(contentResolver, MediaStore.Audio.Media.INTERNAL_CONTENT_URI)
                external = query.run(contentResolver, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
            } catch (e: SecurityException) {
                failed(e)
                return@execute
            } catch (e: IllegalArgumentException) {
                failed(e)
                return@execute
            } catch (e: IllegalStateException) {
                failed(e)
                return@execute
            } catch (e: UnsupportedOperationException) {
                failed(e)
                return@execute
            }
            val internalResult = internal
            val externalResult = external
            uiHandler.post {
                // Drop results of a search that has since been replaced.
                if (generation != loadGeneration || isDestroyed) {
                    internalResult?.close()
                    externalResult?.close()
                } else {
                    showCursors(internalResult, externalResult)
                }
            }
        }
    }

    private fun storageRoots(): List<String> {
        val roots = mutableListOf(Environment.getExternalStorageDirectory().path)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getSystemService<StorageManager>()?.storageVolumes?.mapNotNullTo(roots) { it.directory?.absolutePath }
        }
        return roots
    }

    private fun showCursors(
        internal: Cursor?,
        external: Cursor?,
    ) {
        val b = binding ?: return
        val cursorAdapter = adapter ?: return
        val internalCount = internal?.count ?: 0
        val externalCount = external?.count ?: 0
        if (internalCount == 0 && externalCount == 0 && !showAll) {
            b.emptyText.isVisible = true
            internal?.close()
            external?.close()
            internalRowCount = 0
            cursorAdapter.changeCursor(null)
            return
        }
        b.emptyText.isVisible = false
        internalRowCount = internalCount
        cursorAdapter.changeCursor(MergeCursor(listOfNotNull(internal, external).toTypedArray()))
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
        b.btnRecord.setOnClickListener { startEditor(RingdroidEditActivity.RECORD.toUri()) }
        b.btnMore.setOnClickListener { showOptionsMenu(it) }
    }

    private fun showOptionsMenu(anchor: View) {
        val popupBinding = SelectOptionsPopupBinding.inflate(layoutInflater)
        popupBinding.switchShowAll.isChecked = showAll
        popupBinding.menuAboutVersion.text = getString(R.string.version_label, BuildConfig.VERSION_NAME)
        val widthDp = 280
        val popup = Popups.create(popupBinding.root, widthDp)
        popupBinding.menuItemShowAll.setOnClickListener {
            showAll = !showAll
            popupBinding.switchShowAll.isChecked = showAll
            refreshListView()
        }
        popupBinding.menuItemAbout.setOnClickListener {
            popup.dismiss()
            AboutDialog.show(this)
        }
        Popups.showAlignedRight(popup, anchor, widthDp, 8)
    }

    /**
     * Built-in sounds can't be deleted. From Android 10 MediaStore decides (asking the user where
     * needed); before that the folder must be writable.
     */
    private fun canDelete(
        position: Int,
        path: String?,
    ): Boolean {
        if (path == null || position < internalRowCount) {
            return false
        }
        val file = File(path)
        if (!file.exists()) {
            return false
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || file.parentFile?.canWrite() == true
    }

    private fun showRowOptions(
        anchor: View,
        position: Int,
    ) {
        val c = adapter?.cursor ?: return
        if (c.isClosed || !c.moveToPosition(position)) {
            return
        }
        val title = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))
        val path = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
        val isRingtone = c.isFlagSet(MediaStore.Audio.Media.IS_RINGTONE)
        val isNotification = c.isFlagSet(MediaStore.Audio.Media.IS_NOTIFICATION)
        val uri = rowUri(c)
        RowOptionsPopup.show(
            anchor,
            title,
            isRingtone,
            isNotification,
            canDelete(position, path),
            object : RowOptionsPopup.Actions {
                override fun onEdit() {
                    if (path != null) {
                        startEditor(path.toUri())
                    }
                }

                override fun onSetDefault() {
                    defaultSoundSetter.set(if (isRingtone) RingtoneManager.TYPE_RINGTONE else RingtoneManager.TYPE_NOTIFICATION, uri)
                }

                override fun onAssignContact() {
                    try {
                        contactPicker.pick(uri)
                    } catch (e: ActivityNotFoundException) {
                        Log.e(TAG, "Couldn't open the contact picker", e)
                    }
                }

                override fun onDelete() {
                    deleter.delete(uri, path, title)
                }
            },
        )
    }

    /** The MediaStore URI of the cursor's current row; internal rows come first. */
    private fun rowUri(c: Cursor): Uri {
        val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
        val collection =
            if (c.position < internalRowCount) MediaStore.Audio.Media.INTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        return ContentUris.withAppendedId(collection, id)
    }

    private fun showFinalAlert(message: CharSequence) {
        if (isFinishing || isDestroyed) {
            return
        }
        AlertDialog
            .Builder(this)
            .setTitle(R.string.alert_title_failure)
            .setMessage(message)
            .setPositiveButton(R.string.alert_ok_button) { _, _ -> finish() }
            .setCancelable(false)
            .show()
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
        val c = adapter?.cursor ?: return
        if (c.isClosed || !c.moveToPosition(position)) {
            return
        }
        c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))?.let { startEditor(it.toUri()) }
    }

    private fun startEditor(data: Uri) {
        try {
            val intent =
                Intent(Intent.ACTION_EDIT, data, this, RingdroidEditActivity::class.java)
                    .putExtra(RingdroidEditActivity.EXTRA_WAS_GET_CONTENT_INTENT, wasGetContentIntent)
                    // Passes on access to a content URI shared with this activity.
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            editActivityLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Couldn't start editor", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Couldn't start editor", e)
        }
    }

    private companion object {
        const val TAG = "RingdroidSelectActivity"

        fun Cursor.isFlagSet(column: String) = getInt(getColumnIndexOrThrow(column)) != 0
    }
}
