/*
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ringdroid;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.database.MergeCursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.ringdroid.databinding.DialogConfirmDeleteBinding;
import com.ringdroid.databinding.MediaSelectBinding;
import com.ringdroid.databinding.RowOptionsPopupBinding;
import com.ringdroid.databinding.SelectOptionsPopupBinding;
import com.ringdroid.soundfile.SoundFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Main screen that shows up when you launch Ringdroid. Handles selecting an audio file or using an
 * intent to record a new one, and then launches RingdroidEditActivity from here.
 */
public class RingdroidSelectActivity extends ComponentActivity {
    private static final String[] AUDIO_PROJECTION = {MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.MIME_TYPE,

            // Flags your UI uses:
            MediaStore.Audio.Media.IS_RINGTONE, MediaStore.Audio.Media.IS_ALARM,
            MediaStore.Audio.Media.IS_NOTIFICATION, MediaStore.Audio.Media.IS_MUSIC,

            // File path — will be non-null since we use MANAGE_EXTERNAL_STORAGE
            MediaStore.Audio.Media.DATA};
    private static final String TAG = "RingdroidSelectActivity";
    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private final ActivityResultLauncher<Intent> mEditActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), this::onEditActivityResult);
    private SimpleCursorAdapter mAdapter;
    private boolean mWasGetContentIntent;
    private boolean mShowAll = false;
    private Thread mLoaderThread;
    private final ActivityResultLauncher<String> mStoragePermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> onPermissionChanged());
    private MediaSelectBinding binding;
    private SearchToolbarHelper mSearchHelper;
    private boolean mSettingsPermissionRequested = false;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (!PermissionUtils.hasStoragePermission(this)) {
            mStoragePermissionLauncher.launch(PermissionUtils.getStoragePermission());
        }

        handleIncoming(getIntent());

        String status = Environment.getExternalStorageState();
        if (status.equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
            showFinalAlert(getResources().getText(R.string.sdcard_readonly));
            return;
        }
        if (status.equals(Environment.MEDIA_SHARED)) {
            showFinalAlert(getResources().getText(R.string.sdcard_shared));
            return;
        }
        if (!status.equals(Environment.MEDIA_MOUNTED)) {
            showFinalAlert(getResources().getText(R.string.no_sdcard));
            return;
        }

        Intent intent = getIntent();
        mWasGetContentIntent = Objects.equals(intent.getAction(), Intent.ACTION_GET_CONTENT);

        // Inflate our UI from its XML layout description.
        binding = MediaSelectBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupToolbar();

        ListView listView = binding.list;

        try {
            mAdapter = new SimpleCursorAdapter(this,
                    // Use a template that displays a text view
                    R.layout.media_select_row, null,
                    // Map from database columns...
                    new String[]{MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                            MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media._ID,
                            MediaStore.Audio.Media._ID},
                    // To widget ids in the row layout...
                    new int[]{R.id.row_artist, R.id.row_album, R.id.row_title, R.id.row_icon,
                            R.id.row_options_button},
                    0);

            listView.setAdapter(mAdapter);
            listView.setItemsCanFocus(true);

            // Normal click - open the editor
            listView.setOnItemClickListener(
                    (parent, view, position, id) -> startRingdroidEditor(position));

            loadAudioAsync(null);
        } catch (SecurityException | IllegalArgumentException e) {
            Log.e(TAG, "Failed to initialize cursor adapter", e);
        }

        if (mAdapter != null) {
            mAdapter.setViewBinder((view, cursor, columnIndex) -> {
                if (view.getId() == R.id.row_options_button) {
                    ImageView iv = (ImageView) view;
                    int position = cursor.getPosition();
                    iv.setOnClickListener(v -> showRowOptions(v, position));
                    return true;
                } else if (view.getId() == R.id.row_icon) {
                    setSoundIconFromCursor((ImageView) view, cursor);
                    return true;
                } else if (view.getId() == R.id.row_artist) {
                    TextView tv = (TextView) view;
                    String artist = cursor.getString(columnIndex);
                    if (artist == null || artist.isEmpty()
                            || artist.equalsIgnoreCase("<unknown>")) {
                        tv.setVisibility(View.GONE);
                    } else {
                        tv.setVisibility(View.VISIBLE);
                        tv.setText(artist);
                    }
                    return true;
                } else if (view.getId() == R.id.row_album) {
                    TextView tv = (TextView) view;
                    String album = cursor.getString(columnIndex);
                    if (album == null || album.isEmpty() || album.equals("0")
                            || album.equalsIgnoreCase("<unknown>")) {
                        tv.setVisibility(View.GONE);
                    } else {
                        tv.setVisibility(View.VISIBLE);
                        tv.setText(album);
                    }
                    return true;
                }

                return false;
            });
        }

        // Long-press opens row options
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            View anchor = view.findViewById(R.id.row_options_button);
            if (anchor == null) {
                anchor = view;
            }
            showRowOptions(anchor, position);
            return true;
        });

        // Clear intent extras to prevent TransactionTooLargeException when this activity is stopped
        getIntent().replaceExtras((Bundle) null);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mSettingsPermissionRequested) {
            mSettingsPermissionRequested = false;
            onPermissionChanged();
            return;
        }

        // Refresh the list to show any newly saved files
        refreshListView();
    }

    private void loadAudioAsync(String filter) {
        // Cancel previous load
        if (mLoaderThread != null && mLoaderThread.isAlive()) {
            mLoaderThread.interrupt();
        }

        mLoaderThread = new Thread(() -> {
            try {
                Cursor internal = queryCursor(MediaStore.Audio.Media.INTERNAL_CONTENT_URI, filter);
                Cursor external = queryCursor(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, filter);

                if (Thread.interrupted())
                    return;

                mUiHandler.post(() -> updateUiWithCursors(internal, external));
            } catch (SecurityException | IllegalArgumentException | IllegalStateException
                    | UnsupportedOperationException e) {
                Log.e(TAG, "Loader error", e);
            }
        });

        mLoaderThread.start();
    }

    private Cursor queryCursor(Uri baseUri, String filter) {
        ArrayList<String> selectionArgsList = new ArrayList<>();
        StringBuilder selection = new StringBuilder();

        if (mShowAll) {
            selection.append("(_DATA LIKE ?)");
            selectionArgsList.add("%"); // match everything
        } else {
            // Match supported file extensions
            selection.append("(");
            for (String ext : SoundFile.getSupportedExtensions()) {
                if (selection.length() > 1)
                    selection.append(" OR ");
                selection.append("(_DATA LIKE ?)");
                selectionArgsList.add("%." + ext);
            }
            selection.append(")");

            // Internal + SD paths
            String internalPath = Environment.getExternalStorageDirectory().getPath();
            selection.append(" AND (");
            selection.append("_DATA LIKE ?");
            selectionArgsList.add(internalPath + "/%");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                StorageManager sm = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
                for (StorageVolume volume : sm.getStorageVolumes()) {
                    File dir = volume.getDirectory();
                    if (dir != null) {
                        String path = dir.getAbsolutePath();
                        selection.append(" OR _DATA LIKE ?");
                        selectionArgsList.add(path + "/%");
                    }
                }
            }

            selection.append(")");
        }

        // Apply search filter
        if (filter != null && !filter.isEmpty()) {
            String like = "%" + filter + "%";
            selection = new StringBuilder(
                    "(" + selection + " AND (TITLE LIKE ? OR ARTIST LIKE ? OR ALBUM LIKE ?))");
            selectionArgsList.add(like);
            selectionArgsList.add(like);
            selectionArgsList.add(like);
        }

        String[] selectionArgs = selectionArgsList.toArray(new String[0]);

        return getContentResolver().query(baseUri, AUDIO_PROJECTION, selection.toString(),
                selectionArgs, MediaStore.Audio.Media.TITLE + " ASC");
    }

    private void updateUiWithCursors(Cursor internal, Cursor external) {
        TextView emptyView = binding.emptyText;

        int internalCount = internal != null ? internal.getCount() : 0;
        int externalCount = external != null ? external.getCount() : 0;

        if (internalCount == 0 && externalCount == 0 && !mShowAll) {
            emptyView.setVisibility(View.VISIBLE);
            mAdapter.swapCursor(null);
            return;
        } else {
            emptyView.setVisibility(View.GONE);
        }

        ArrayList<Cursor> cursors = new ArrayList<>();
        if (internal != null)
            cursors.add(internal);
        if (external != null)
            cursors.add(external);

        Cursor merged = new MergeCursor(cursors.toArray(new Cursor[0]));
        mAdapter.swapCursor(merged);
    }

    private void setSoundIconFromCursor(ImageView view, Cursor cursor) {
        int iconRes = R.drawable.baseline_music_note_24;
        int iconColor = ContextCompat.getColor(this, R.color.category_icon_music);
        int containerColor = ContextCompat.getColor(this, R.color.category_container_music);

        if (0 != cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_RINGTONE))) {
            iconRes = R.drawable.baseline_call_24;
            iconColor = ContextCompat.getColor(this, R.color.category_icon_ringtone);
            containerColor = ContextCompat.getColor(this, R.color.category_container_ringtone);
        } else if (0 != cursor
                .getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_ALARM))) {
            iconRes = R.drawable.baseline_access_alarm_24;
            iconColor = ContextCompat.getColor(this, R.color.category_icon_alarm);
            containerColor = ContextCompat.getColor(this, R.color.category_container_alarm);
        } else if (0 != cursor
                .getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_NOTIFICATION))) {
            iconRes = R.drawable.baseline_notifications_24;
            iconColor = ContextCompat.getColor(this, R.color.category_icon_notification);
            containerColor = ContextCompat.getColor(this, R.color.category_container_notification);
        } else if (0 != cursor
                .getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_MUSIC))) {
            iconRes = R.drawable.baseline_music_note_24;
            iconColor = ContextCompat.getColor(this, R.color.category_icon_music);
            containerColor = ContextCompat.getColor(this, R.color.category_container_music);
        }

        String filename = cursor
                .getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
        if (!SoundFile.isFilenameSupported(filename)) {
            iconColor = ContextCompat.getColor(this, R.color.category_icon_unsupported);
            containerColor = ContextCompat.getColor(this, R.color.category_container_unsupported);
        }

        view.setImageResource(iconRes);
        view.setImageTintList(ColorStateList.valueOf(iconColor));
        view.setBackgroundTintList(ColorStateList.valueOf(containerColor));
    }

    private void onPermissionChanged() {
        if (PermissionUtils.hasStoragePermission(this)) {
            refreshListView();
        } else {
            Toast.makeText(this, R.string.required_storage_permission, Toast.LENGTH_LONG).show();
        }
    }

    private void onEditActivityResult(ActivityResult result) {
        if (result.getResultCode() != RESULT_OK) {
            return;
        }

        setResult(RESULT_OK, result.getData());
    }

    private void setupToolbar() {
        RingdroidUtils.applyStatusBarInsets(binding.toolbarContainer);
        RingdroidUtils.applyNavigationBarInsets(binding.list, 16);

        mSearchHelper = new SearchToolbarHelper(this, binding.toolbarNormal, binding.toolbarSearch,
                binding.btnSearch, query -> refreshListView());

        binding.btnRecord.setOnClickListener(v -> onRecord());
        binding.btnMore.setOnClickListener(this::showOptionsMenu);
    }

    private void showOptionsMenu(View anchor) {
        SelectOptionsPopupBinding popupBinding = SelectOptionsPopupBinding
                .inflate(getLayoutInflater());

        popupBinding.switchShowAll.setChecked(mShowAll);

        String versionName;
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            versionName = "3.1.0";
        }
        popupBinding.menuAboutVersion.setText("v" + versionName);

        int widthDp = 280;
        PopupWindow popupWindow = createPopupWindow(popupBinding.getRoot(), widthDp);

        popupBinding.menuItemShowAll.setOnClickListener(v -> {
            mShowAll = !mShowAll;
            popupBinding.switchShowAll.setChecked(mShowAll);
            refreshListView();
        });

        popupBinding.menuItemAbout.setOnClickListener(v -> {
            popupWindow.dismiss();
            RingdroidEditActivity.onAbout(this);
        });

        showPopupAlignedRight(popupWindow, anchor, widthDp, 8);
    }

    private PopupWindow createPopupWindow(View contentView, int widthDp) {
        int width = (int) (widthDp * getResources().getDisplayMetrics().density);
        PopupWindow popupWindow = new PopupWindow(contentView, width,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setElevation(12 * getResources().getDisplayMetrics().density);
        popupWindow.setAnimationStyle(android.R.style.Animation_Dialog);
        return popupWindow;
    }

    private void showPopupAlignedRight(PopupWindow popup, View anchor, int widthDp, int yOffsetDp) {
        int width = (int) (widthDp * getResources().getDisplayMetrics().density);
        int xOffset = anchor.getWidth() - width
                - (int) (8 * getResources().getDisplayMetrics().density);
        int yOffset = (int) (yOffsetDp * getResources().getDisplayMetrics().density);
        popup.showAsDropDown(anchor, xOffset, yOffset);
    }

    private boolean isProtectedDir(String path) {
        if (path == null)
            return true;

        // If file does not exist or can't be read → treat as protected
        File file = new File(path);
        if (!file.exists() || !file.canRead()) {
            return true;
        }

        // Check if directory itself is writable
        File parent = file.getParentFile();
        return parent == null || !parent.canWrite();
    }

    private void showRowOptions(View anchor, int position) {
        Cursor c = mAdapter.getCursor();
        if (c == null || c.isClosed()) {
            return;
        }
        if (!c.moveToPosition(position)) {
            return;
        }

        RowOptionsPopupBinding popupBinding = RowOptionsPopupBinding.inflate(getLayoutInflater());

        String title = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));
        popupBinding.rowMenuTitle.setText(title != null ? title : "");

        String filename = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
        boolean canDelete = !isProtectedDir(filename);

        int isRingtone = c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_RINGTONE));
        int isNotification = c
                .getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_NOTIFICATION));

        int widthDp = 260;
        PopupWindow popupWindow = createPopupWindow(popupBinding.getRoot(), widthDp);

        // Edit
        popupBinding.menuItemEdit.setOnClickListener(v -> {
            popupWindow.dismiss();
            startRingdroidEditor(position);
        });

        // Default Ringtone / Notification
        if (isRingtone != 0) {
            popupBinding.menuItemDefault.setVisibility(View.VISIBLE);
            popupBinding.menuDefaultIcon.setImageResource(R.drawable.baseline_call_24);
            popupBinding.menuDefaultText.setText(R.string.context_menu_default_ringtone);
            popupBinding.menuItemDefault.setOnClickListener(v -> {
                popupWindow.dismiss();
                if (c.moveToPosition(position)) {
                    setAsDefaultRingtoneOrNotification();
                }
            });
        } else if (isNotification != 0) {
            popupBinding.menuItemDefault.setVisibility(View.VISIBLE);
            popupBinding.menuDefaultIcon.setImageResource(R.drawable.baseline_notifications_24);
            popupBinding.menuDefaultText.setText(R.string.context_menu_default_notification);
            popupBinding.menuItemDefault.setOnClickListener(v -> {
                popupWindow.dismiss();
                if (c.moveToPosition(position)) {
                    setAsDefaultRingtoneOrNotification();
                }
            });
        } else {
            popupBinding.menuItemDefault.setVisibility(View.GONE);
        }

        // Contact
        if (isRingtone != 0) {
            popupBinding.menuItemContact.setVisibility(View.VISIBLE);
            popupBinding.menuItemContact.setOnClickListener(v -> {
                popupWindow.dismiss();
                if (c.moveToPosition(position)) {
                    chooseContactForRingtone();
                }
            });
        } else {
            popupBinding.menuItemContact.setVisibility(View.GONE);
        }

        // Delete
        if (canDelete) {
            popupBinding.deleteDivider.setVisibility(View.VISIBLE);
            popupBinding.menuItemDelete.setVisibility(View.VISIBLE);
            popupBinding.menuItemDelete.setOnClickListener(v -> {
                popupWindow.dismiss();
                if (c.moveToPosition(position)) {
                    confirmDelete();
                }
            });
        } else {
            popupBinding.deleteDivider.setVisibility(View.GONE);
            popupBinding.menuItemDelete.setVisibility(View.GONE);
        }

        showPopupAlignedRight(popupWindow, anchor, widthDp, 4);
    }

    private void setAsDefaultRingtoneOrNotification() {
        Cursor c = mAdapter.getCursor();
        if (c == null || c.isClosed())
            return;

        // If the item is a ringtone then set the default ringtone,
        // otherwise it has to be a notification so set the default notification sound
        if (0 != c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_RINGTONE))) {
            RingdroidUtils.setDefaultRingTone(RingdroidSelectActivity.this,
                    RingtoneManager.TYPE_RINGTONE, getUri(), false);
        } else {
            RingdroidUtils.setDefaultRingTone(RingdroidSelectActivity.this,
                    RingtoneManager.TYPE_NOTIFICATION, getUri(), false);
        }
    }

    private int getUriIndex(Cursor c) {
        int uriIndex;
        String[] columnNames = {MediaStore.Audio.Media.INTERNAL_CONTENT_URI.toString(),
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString()};

        for (String columnName : columnNames) {
            uriIndex = c.getColumnIndex(columnName);
            if (uriIndex >= 0) {
                return uriIndex;
            }
            // On some phones and/or Android versions, the column name includes the double
            // quotes.
            uriIndex = c.getColumnIndex("\"" + columnName + "\"");
            if (uriIndex >= 0) {
                return uriIndex;
            }
        }
        return -1;
    }

    private Uri getUri() {
        // Get the uri of the item that is in the row
        Cursor c = mAdapter.getCursor();
        if (c == null || c.isClosed())
            return null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            int idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            long id = c.getLong(idCol);

            return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
        }

        int uriIndex = getUriIndex(c);
        if (uriIndex == -1) {
            return null;
        }

        String itemUri = c.getString(uriIndex) + "/"
                + c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));

        return Uri.parse(itemUri);
    }

    private boolean chooseContactForRingtone() {
        try {
            Uri uri = getUri();
            if (uri == null)
                return false;
            // Go to the choose contact activity
            startActivity(ChooseContactActivity.createIntent(this, uri));
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.e(TAG, "Couldn't open Choose Contact window", e);
        }
        return true;
    }

    private void confirmDelete() {
        Cursor c = mAdapter.getCursor();
        if (c == null || c.isClosed()) {
            return;
        }

        if (isFinishing() || isDestroyed()) {
            return;
        }

        String title = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));
        DialogConfirmDeleteBinding dialogBinding = DialogConfirmDeleteBinding
                .inflate(getLayoutInflater());

        if (title != null && !title.isEmpty()) {
            dialogBinding.deleteMessage.setText(
                    getString(R.string.confirm_delete_ringdroid) + "\n\n\"" + title + "\"");
        }

        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogBinding.getRoot())
                .setCancelable(true).create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        dialogBinding.btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.btnConfirmDelete.setOnClickListener(v -> {
            dialog.dismiss();
            onDelete();
        });

        dialog.show();
    }

    private void onDelete() {
        Cursor c = mAdapter.getCursor();
        if (c == null || c.isClosed())
            return;

        Uri contentUri = getUri();
        if (contentUri == null) {
            return;
        }

        // Legacy direct file delete (Android 9 and below)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            int dataIndex = c.getColumnIndex(MediaStore.Audio.Media.DATA);
            if (dataIndex != -1) {
                String filePath = c.getString(dataIndex);
                if (filePath != null) {
                    File file = new File(filePath);
                    boolean status = file.delete();
                    Log.d(TAG, "Delete file: " + file + " status: " + status);
                }
            }
        }

        try {
            if (getContentResolver().delete(contentUri, null, null) == 0) {
                showFinalAlert(getResources().getText(R.string.delete_failed));
            } else {
                refreshListView();
            }
        } catch (SecurityException | IllegalArgumentException | IllegalStateException
                | UnsupportedOperationException e) {
            showFinalAlert(getResources().getText(R.string.delete_failed));
        }
    }

    private void showFinalAlert(CharSequence message) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        new AlertDialog.Builder(RingdroidSelectActivity.this)
                .setTitle(getResources().getText(R.string.alert_title_failure)).setMessage(message)
                .setPositiveButton(R.string.alert_ok_button, (dialog, whichButton) -> finish())
                .setCancelable(false).show();
    }

    private void onRecord() {
        startRingdroidEditor(Uri.parse("record"));
    }

    @SuppressWarnings("deprecation")
    private void handleIncoming(Intent intent) {
        Uri audioUri = null;
        String action = intent.getAction();

        if (Intent.ACTION_VIEW.equals(action)) {
            audioUri = intent.getData();
        } else if (Intent.ACTION_SEND.equals(action)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                audioUri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            } else {
                audioUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            }
        }

        if (audioUri != null) {
            File file = RingdroidUtils.getFileFromUri(this, audioUri);
            if (file != null) {
                startRingdroidEditor(Uri.fromFile(file));
            } else {
                Log.e(TAG, "Could not resolve file: " + audioUri);
            }
        }
    }

    private void startRingdroidEditor(int position) {
        Cursor c = mAdapter.getCursor();
        if (c == null || c.isClosed())
            return;
        c.moveToPosition(position);
        int dataIndex = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
        String filename = c.getString(dataIndex);
        if (filename != null) {
            startRingdroidEditor(Uri.parse(filename));
        }
    }

    private void startRingdroidEditor(Uri filename) {
        try {
            Intent intent = new Intent(Intent.ACTION_EDIT, filename);
            intent.putExtra("was_get_content_intent", mWasGetContentIntent);
            intent.setClass(this, RingdroidEditActivity.class);
            mEditActivityLauncher.launch(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.e(TAG, "Couldn't start editor", e);
        }
    }

    private void refreshListView() {
        loadAudioAsync(mSearchHelper != null ? mSearchHelper.getActiveQuery() : null);
    }
}
