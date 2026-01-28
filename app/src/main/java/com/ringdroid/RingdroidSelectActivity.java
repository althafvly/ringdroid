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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
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
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.ringdroid.databinding.MediaSelectBinding;
import com.ringdroid.soundfile.SoundFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Main screen that shows up when you launch Ringdroid. Handles selecting an
 * audio file or using an intent to record a new one, and then launches
 * RingdroidEditActivity from here.
 */
public class RingdroidSelectActivity extends Activity {
    // Result codes
    private static final int REQUEST_CODE_EDIT = 1;
    private static final int REQUEST_CODE_CHOOSE_CONTACT = 2;
    // Context menu
    private static final int CMD_EDIT = 4;
    private static final int CMD_DELETE = 5;
    private static final int CMD_SET_AS_DEFAULT = 6;
    private static final int CMD_SET_AS_CONTACT = 7;
    private static final String[] AUDIO_PROJECTION = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.MIME_TYPE,

            // Flags your UI uses:
            MediaStore.Audio.Media.IS_RINGTONE, MediaStore.Audio.Media.IS_ALARM, MediaStore.Audio.Media.IS_NOTIFICATION,
            MediaStore.Audio.Media.IS_MUSIC,

            // File path — will be non-null since we use MANAGE_EXTERNAL_STORAGE
            MediaStore.Audio.Media.DATA};
    private final String TAG = this.getClass().getName();
    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private SearchView mFilter;
    private SimpleCursorAdapter mAdapter;
    private boolean mWasGetContentIntent;
    private boolean mShowAll = false;
    private Thread mLoaderThread;
    private MediaSelectBinding binding;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

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

        ListView listView = binding.list;

        try {
            mAdapter = new SimpleCursorAdapter(this,
                    // Use a template that displays a text view
                    R.layout.media_select_row, null,
                    // Map from database columns...
                    new String[]{MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                            MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media._ID, MediaStore.Audio.Media._ID},
                    // To widget ids in the row layout...
                    new int[]{R.id.row_artist, R.id.row_album, R.id.row_title, R.id.row_icon, R.id.row_options_button},
                    0);

            listView.setAdapter(mAdapter);
            listView.setItemsCanFocus(true);

            // Normal click - open the editor
            listView.setOnItemClickListener((parent, view, position, id) -> startRingdroidEditor());

            loadAudioAsync(null);
        } catch (SecurityException | IllegalArgumentException e) {
            Log.e(TAG, e.toString());
        }

        mAdapter.setViewBinder((view, cursor, columnIndex) -> {
            if (view.getId() == R.id.row_options_button) {
                // Get the arrow ImageView and set the onClickListener to open the context menu.
                ImageView iv = (ImageView) view;
                iv.setOnClickListener(this::openContextMenu);
                return true;
            } else if (view.getId() == R.id.row_icon) {
                setSoundIconFromCursor((ImageView) view, cursor);
                return true;
            }

            return false;
        });

        // Long-press opens a context menu
        registerForContextMenu(listView);
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
            } catch (Exception e) {
                Log.e(TAG, "Loader error: " + e);
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
            selection = new StringBuilder("(" + selection + " AND (TITLE LIKE ? OR ARTIST LIKE ? OR ALBUM LIKE ?))");
            selectionArgsList.add(like);
            selectionArgsList.add(like);
            selectionArgsList.add(like);
        }

        String[] selectionArgs = selectionArgsList.toArray(new String[0]);

        return getContentResolver().query(baseUri, AUDIO_PROJECTION, selection.toString(), selectionArgs,
                MediaStore.Audio.Media.TITLE + " ASC");
    }

    private void updateUiWithCursors(Cursor internal, Cursor external) {
        TextView emptyView = binding.emptyText;

        if (internal.getCount() == 0 && external.getCount() == 0 && !mShowAll) {
            emptyView.setVisibility(View.VISIBLE);
            mAdapter.swapCursor(null);
            return;
        } else {
            emptyView.setVisibility(View.GONE);
        }

        Cursor merged = new MergeCursor(new Cursor[]{internal, external});
        mAdapter.swapCursor(merged);
    }

    private void setSoundIconFromCursor(ImageView view, Cursor cursor) {
        if (0 != cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_RINGTONE))) {
            view.setImageResource(R.drawable.baseline_call_24);
            ((View) view.getParent()).setBackgroundColor(getColorRes(R.color.type_bkgnd_ringtone));
        } else if (0 != cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_ALARM))) {
            view.setImageResource(R.drawable.baseline_access_alarm_24);
            ((View) view.getParent()).setBackgroundColor(getColorRes(R.color.type_bkgnd_alarm));
        } else if (0 != cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_NOTIFICATION))) {
            view.setImageResource(R.drawable.baseline_notifications_24);
            ((View) view.getParent()).setBackgroundColor(getColorRes(R.color.type_bkgnd_notification));
        } else if (0 != cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_MUSIC))) {
            view.setImageResource(R.drawable.baseline_music_note_24);
            ((View) view.getParent()).setBackgroundColor(getColorRes(R.color.type_bkgnd_music));
        }

        String filename = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
        if (!SoundFile.isFilenameSupported(filename)) {
            ((View) view.getParent()).setBackgroundColor(getColorRes(R.color.type_bkgnd_unsupported));
        }
    }

    @SuppressWarnings("deprecation")
    private int getColorRes(int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return getResources().getColor(color, null);
        } else {
            return getResources().getColor(color);
        }
    }

    /**
     * Called with an Activity we started with an Intent returns.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent dataIntent) {
        if (requestCode != REQUEST_CODE_EDIT || resultCode != RESULT_OK) {
            return;
        }

        setResult(RESULT_OK, dataIntent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.select_options, menu);

        mFilter = (SearchView) menu.findItem(R.id.action_search_filter).getActionView();
        if (mFilter != null) {
            mFilter.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                public boolean onQueryTextChange(String newText) {
                    refreshListView();
                    return true;
                }

                public boolean onQueryTextSubmit(String query) {
                    refreshListView();
                    return true;
                }
            });
        }

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.action_about).setVisible(true);
        menu.findItem(R.id.action_record).setVisible(true);
        menu.findItem(R.id.action_show_all_audio).setVisible(true);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_about) {
            RingdroidEditActivity.onAbout(this);
            return true;
        } else if (id == R.id.action_record) {
            onRecord();
            return true;
        } else if (id == R.id.action_show_all_audio) {
            boolean newState = !item.isChecked();
            item.setChecked(newState);
            mShowAll = newState;
            refreshListView();
            return true;
        } else if (id == R.id.action_permissions) {
            Intent intent = new Intent(this, PermissionActivity.class);
            intent.putExtra(PermissionActivity.EXTRA_FORCE_SHOW, true);
            startActivity(intent);
            return true;
        }

        return false;
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

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        Cursor c = mAdapter.getCursor();
        String title = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));

        menu.setHeaderTitle(title);

        menu.add(0, CMD_EDIT, 0, R.string.context_menu_edit);

        String filename = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));

        // Only show delete if we can delete
        if (!isProtectedDir(filename)) {
            menu.add(0, CMD_DELETE, 0, R.string.context_menu_delete);
        }

        // Add items to the context menu item based on file type
        if (0 != c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_RINGTONE))) {
            menu.add(0, CMD_SET_AS_DEFAULT, 0, R.string.context_menu_default_ringtone);
            menu.add(0, CMD_SET_AS_CONTACT, 0, R.string.context_menu_contact);
        } else if (0 != c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_NOTIFICATION))) {
            menu.add(0, CMD_SET_AS_DEFAULT, 0, R.string.context_menu_default_notification);
        }
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        return switch (item.getItemId()) {
            case CMD_EDIT -> {
                startRingdroidEditor();
                yield true;
            }
            case CMD_DELETE -> {
                confirmDelete();
                yield true;
            }
            case CMD_SET_AS_DEFAULT -> {
                setAsDefaultRingtoneOrNotification();
                yield true;
            }
            case CMD_SET_AS_CONTACT -> chooseContactForRingtone();
            default -> super.onContextItemSelected(item);
        };
    }

    private void setAsDefaultRingtoneOrNotification() {
        Cursor c = mAdapter.getCursor();

        // If the item is a ringtone then set the default ringtone,
        // otherwise it has to be a notification so set the default notification sound
        if (0 != c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_RINGTONE))) {
            RingdroidUtils.setDefaultRingTone(RingdroidSelectActivity.this, RingtoneManager.TYPE_RINGTONE, getUri(),
                    false);
        } else {
            RingdroidUtils.setDefaultRingTone(RingdroidSelectActivity.this, RingtoneManager.TYPE_NOTIFICATION, getUri(),
                    false);
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            int idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            long id = c.getLong(idCol);

            return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
        }

        int uriIndex = getUriIndex(c);
        if (uriIndex == -1) {
            return null;
        }

        String itemUri = c.getString(uriIndex) + "/" + c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));

        return Uri.parse(itemUri);
    }

    private boolean chooseContactForRingtone() {
        try {
            // Go to the choose contact activity
            Intent intent = new Intent(Intent.ACTION_EDIT, getUri());
            intent.setClass(this, ChooseContactActivity.class);
            startActivityForResult(intent, REQUEST_CODE_CHOOSE_CONTACT);
        } catch (Exception e) {
            Log.e(TAG, "Couldn't open Choose Contact window");
        }
        return true;
    }

    private void confirmDelete() {
        // See if the selected list item was created by Ringdroid to
        // determine which alert message to show
        Cursor c = mAdapter.getCursor();
        String artist = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST));
        CharSequence ringdroidArtist = getResources().getText(R.string.artist_name);

        CharSequence message;
        if (artist.contentEquals(ringdroidArtist)) {
            message = getResources().getText(R.string.confirm_delete_ringdroid);
        } else {
            message = getResources().getText(R.string.confirm_delete_non_ringdroid);
        }

        CharSequence title;
        if (0 != c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_RINGTONE))) {
            title = getResources().getText(R.string.delete_ringtone);
        } else if (0 != c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_ALARM))) {
            title = getResources().getText(R.string.delete_alarm);
        } else if (0 != c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_NOTIFICATION))) {
            title = getResources().getText(R.string.delete_notification);
        } else if (0 != c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_MUSIC))) {
            title = getResources().getText(R.string.delete_music);
        } else {
            title = getResources().getText(R.string.delete_audio);
        }

        new AlertDialog.Builder(RingdroidSelectActivity.this).setTitle(title).setMessage(message)
                .setPositiveButton(R.string.delete_ok_button, (dialog, whichButton) -> onDelete())
                .setNegativeButton(R.string.delete_cancel_button, (dialog, whichButton) -> {
                }).setCancelable(true).show();
    }

    private void onDelete() {
        Cursor c = mAdapter.getCursor();
        if (c == null)
            return;

        long id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));
        Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);

        // Legacy direct file delete (Android 9 and below)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            int dataIndex = c.getColumnIndex(MediaStore.Audio.Media.DATA);
            if (dataIndex != -1) {
                String filePath = c.getString(dataIndex);
                if (filePath != null) {
                    File file = new File(filePath);
                    boolean status = file.delete();
                    Log.d(TAG, "Delete file: " + file + "status:" + status);
                }
            }
        }

        try {
            if (getContentResolver().delete(contentUri, null, null) == 0) {
                showFinalAlert(getResources().getText(R.string.delete_failed));
            }
        } catch (Exception e) {
            showFinalAlert(getResources().getText(R.string.delete_failed));
        }
    }

    private void showFinalAlert(CharSequence message) {
        new AlertDialog.Builder(RingdroidSelectActivity.this)
                .setTitle(getResources().getText(R.string.alert_title_failure)).setMessage(message)
                .setPositiveButton(R.string.alert_ok_button, (dialog, whichButton) -> finish()).setCancelable(false)
                .show();
    }

    private void onRecord() {
        try {
            Intent intent = new Intent(Intent.ACTION_EDIT, Uri.parse("record"));
            intent.putExtra("was_get_content_intent", mWasGetContentIntent);
            intent.setClass(this, RingdroidEditActivity.class);
            startActivityForResult(intent, REQUEST_CODE_EDIT);
        } catch (Exception e) {
            Log.e(TAG, "Couldn't start editor");
        }
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
            File file = FilesUtil.getFileFromUri(this, audioUri);
            if (file != null) {
                startRingdroidEditor(Uri.fromFile(file));
            } else {
                Log.e(TAG, "Could not resolve file: " + audioUri);
            }
        }
    }

    private void startRingdroidEditor() {
        Cursor c = mAdapter.getCursor();
        int dataIndex = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
        String filename = c.getString(dataIndex);
        startRingdroidEditor(Uri.parse(filename));
    }

    private void startRingdroidEditor(Uri filename) {
        try {
            Intent intent = new Intent(Intent.ACTION_EDIT, filename);
            intent.putExtra("was_get_content_intent", mWasGetContentIntent);
            intent.setClass(this, RingdroidEditActivity.class);
            startActivityForResult(intent, REQUEST_CODE_EDIT);
        } catch (Exception e) {
            Log.e(TAG, "Couldn't start editor");
        }
    }

    private void refreshListView() {
        String filter = mFilter != null ? mFilter.getQuery().toString() : null;
        loadAudioAsync(filter);
    }
}
