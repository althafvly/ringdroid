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

package com.ringdroid.select;

import com.ringdroid.BuildConfig;
import com.ringdroid.R;
import com.ringdroid.editor.RingdroidEditActivity;
import com.ringdroid.soundfile.SoundFile;
import com.ringdroid.util.AboutDialog;
import com.ringdroid.util.ContactRingtonePicker;
import com.ringdroid.util.PermissionUtils;
import com.ringdroid.util.RingdroidUtils;

import java.util.List;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.database.MergeCursor;
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

import com.ringdroid.databinding.MediaSelectBinding;
import com.ringdroid.databinding.SelectOptionsPopupBinding;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Main screen that shows up when you launch Ringdroid. Handles selecting an audio file or using an
 * intent to record a new one, and then launches RingdroidEditActivity from here.
 */
public class RingdroidSelectActivity extends ComponentActivity {
    private static final String TAG = "RingdroidSelectActivity";
    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private final ActivityResultLauncher<Intent> mEditActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), this::onEditActivityResult);
    private SimpleCursorAdapter mAdapter;
    private final AudioDeleter mDeleter = new AudioDeleter(this, this::refreshListView);
    private boolean mWasGetContentIntent;
    private boolean mShowAll = false;
    private final ContactRingtonePicker mContactPicker = new ContactRingtonePicker(this, () -> {
    });
    private int mInternalRowCount = 0;
    private int mLoadGeneration;
    private final ActivityResultLauncher<String[]> mStoragePermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            granted -> onPermissionChanged());
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
            mStoragePermissionLauncher.launch(PermissionUtils.getStoragePermissions());
        }

        if (icicle == null) {
            handleIncoming(getIntent());
        }

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
    protected void onDestroy() {
        if (mAdapter != null) {
            mAdapter.changeCursor(null);
        }
        super.onDestroy();
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
        int generation = ++mLoadGeneration;
        RingdroidUtils.BACKGROUND.execute(() -> {
            Cursor internal = null;
            Cursor external = null;
            try {
                AudioQuery query = AudioQuery.build(mShowAll, filter,
                        SoundFile.getSupportedExtensions(), getStorageRoots());
                internal = query.run(getContentResolver(),
                        MediaStore.Audio.Media.INTERNAL_CONTENT_URI);
                external = query.run(getContentResolver(),
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
            } catch (SecurityException | IllegalArgumentException | IllegalStateException
                    | UnsupportedOperationException e) {
                Log.e(TAG, "Loader error", e);
                closeCursor(internal);
                closeCursor(external);
                return;
            }
            Cursor internalResult = internal;
            Cursor externalResult = external;
            mUiHandler.post(() -> {
                if (generation != mLoadGeneration) {
                    closeCursor(internalResult);
                    closeCursor(externalResult);
                    return;
                }
                updateUiWithCursors(internalResult, externalResult);
            });
        });
    }

    private List<String> getStorageRoots() {
        List<String> roots = new ArrayList<>();
        roots.add(Environment.getExternalStorageDirectory().getPath());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            StorageManager sm = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
            for (StorageVolume volume : sm.getStorageVolumes()) {
                File dir = volume.getDirectory();
                if (dir != null) {
                    roots.add(dir.getAbsolutePath());
                }
            }
        }
        return roots;
    }

    private static boolean isFlagSet(Cursor cursor, String column) {
        return cursor.getInt(cursor.getColumnIndexOrThrow(column)) != 0;
    }

    private static void closeCursor(Cursor cursor) {
        if (cursor != null) {
            cursor.close();
        }
    }

    private void updateUiWithCursors(Cursor internal, Cursor external) {
        if (isDestroyed()) {
            closeCursor(internal);
            closeCursor(external);
            return;
        }
        TextView emptyView = binding.emptyText;

        int internalCount = internal != null ? internal.getCount() : 0;
        int externalCount = external != null ? external.getCount() : 0;

        if (internalCount == 0 && externalCount == 0 && !mShowAll) {
            emptyView.setVisibility(View.VISIBLE);
            closeCursor(internal);
            closeCursor(external);
            mInternalRowCount = 0;
            mAdapter.changeCursor(null);
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
        mInternalRowCount = internalCount;
        mAdapter.changeCursor(merged);
    }

    private void setSoundIconFromCursor(ImageView view, Cursor cursor) {
        int iconRes = R.drawable.baseline_music_note_24;
        int iconColor = ContextCompat.getColor(this, R.color.category_icon_music);
        int containerColor = ContextCompat.getColor(this, R.color.category_container_music);

        if (isFlagSet(cursor, MediaStore.Audio.Media.IS_RINGTONE)) {
            iconRes = R.drawable.baseline_call_24;
            iconColor = ContextCompat.getColor(this, R.color.category_icon_ringtone);
            containerColor = ContextCompat.getColor(this, R.color.category_container_ringtone);
        } else if (isFlagSet(cursor, MediaStore.Audio.Media.IS_ALARM)) {
            iconRes = R.drawable.baseline_access_alarm_24;
            iconColor = ContextCompat.getColor(this, R.color.category_icon_alarm);
            containerColor = ContextCompat.getColor(this, R.color.category_container_alarm);
        } else if (isFlagSet(cursor, MediaStore.Audio.Media.IS_NOTIFICATION)) {
            iconRes = R.drawable.baseline_notifications_24;
            iconColor = ContextCompat.getColor(this, R.color.category_icon_notification);
            containerColor = ContextCompat.getColor(this, R.color.category_container_notification);
        } else if (isFlagSet(cursor, MediaStore.Audio.Media.IS_MUSIC)) {
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

        popupBinding.menuAboutVersion
                .setText(getString(R.string.version_label, BuildConfig.VERSION_NAME));

        int widthDp = 280;
        PopupWindow popupWindow = Popups.create(popupBinding.getRoot(), widthDp);

        popupBinding.menuItemShowAll.setOnClickListener(v -> {
            mShowAll = !mShowAll;
            popupBinding.switchShowAll.setChecked(mShowAll);
            refreshListView();
        });

        popupBinding.menuItemAbout.setOnClickListener(v -> {
            popupWindow.dismiss();
            AboutDialog.show(this);
        });

        Popups.showAlignedRight(popupWindow, anchor, widthDp, 8);
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
        if (c == null || c.isClosed() || !c.moveToPosition(position)) {
            return;
        }
        String title = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));
        String path = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
        boolean isRingtone = isFlagSet(c, MediaStore.Audio.Media.IS_RINGTONE);
        boolean isNotification = isFlagSet(c, MediaStore.Audio.Media.IS_NOTIFICATION);
        Uri uri = getUri();

        RowOptionsPopup.show(anchor, title, isRingtone, isNotification, !isProtectedDir(path),
                new RowOptionsPopup.Actions() {
                    @Override
                    public void onEdit() {
                        if (path != null) {
                            startRingdroidEditor(Uri.parse(path));
                        }
                    }

                    @Override
                    public void onSetDefault() {
                        RingdroidUtils.setDefaultRingTone(RingdroidSelectActivity.this,
                                isRingtone
                                        ? RingtoneManager.TYPE_RINGTONE
                                        : RingtoneManager.TYPE_NOTIFICATION,
                                uri, false);
                    }

                    @Override
                    public void onAssignContact() {
                        try {
                            mContactPicker.pick(uri);
                        } catch (ActivityNotFoundException e) {
                            Log.e(TAG, "Couldn't open the contact picker", e);
                        }
                    }

                    @Override
                    public void onDelete() {
                        mDeleter.delete(uri, path, title);
                    }
                });
    }

    private Uri getUri() {
        // Get the uri of the item that is in the row
        Cursor c = mAdapter.getCursor();
        if (c == null || c.isClosed())
            return null;

        long id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));
        Uri collection = c.getPosition() < mInternalRowCount
                ? MediaStore.Audio.Media.INTERNAL_CONTENT_URI
                : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        return ContentUris.withAppendedId(collection, id);
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
            Uri uri = audioUri;
            RingdroidUtils.BACKGROUND.execute(() -> {
                File file = RingdroidUtils.getFileFromUri(this, uri);
                mUiHandler.post(() -> {
                    if (file == null) {
                        Log.e(TAG, "Could not resolve file: " + uri);
                    } else if (!isDestroyed()) {
                        startRingdroidEditor(Uri.fromFile(file));
                    }
                });
            });
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
