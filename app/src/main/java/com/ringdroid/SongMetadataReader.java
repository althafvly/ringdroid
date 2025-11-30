/*
 * Copyright (C) 2009 Google Inc.
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
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;

public class SongMetadataReader {
    private final String TAG = this.getClass().getName();
    public Activity mActivity;
    public String mFilename;
    public String mTitle = "";
    public String mArtist = "";
    public String mAlbum = "";
    public int mYear = -1;

    SongMetadataReader(Activity activity, String filename) {
        mActivity = activity;
        mFilename = filename;
        mTitle = getBasename(filename);

        try {
            readMetadata();
        } catch (Exception e) {
            Log.e(TAG, "Metadata read failed", e);
        }
    }

    private void readMetadata() {
        Uri audioUri = getAudioContentUri();
        if (audioUri == null)
            return;

        String displayName = new File(mFilename).getName();

        Cursor c = mActivity.getContentResolver().query(audioUri,
                new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.YEAR, MediaStore.Audio.Media.DISPLAY_NAME,
                        MediaStore.Audio.Media.RELATIVE_PATH},
                MediaStore.Audio.Media.DISPLAY_NAME + "=?", new String[]{displayName}, null);

        if (c == null)
            return;

        if (!c.moveToFirst()) {
            c.close();
            fallbackPathQuery(audioUri);
            return;
        }

        extractMetadata(c);
        c.close();
    }

    private void fallbackPathQuery(Uri audioUri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return;
        }

        Cursor c = mActivity.getContentResolver().query(audioUri,
                new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.YEAR, MediaStore.Audio.Media.DATA},
                MediaStore.Audio.Media.DATA + "=?", new String[]{mFilename}, null);

        if (c == null)
            return;
        if (!c.moveToFirst()) {
            c.close();
            return;
        }

        extractMetadata(c);
        c.close();
    }

    private void extractMetadata(Cursor c) {
        mTitle = getString(c, MediaStore.Audio.Media.TITLE, getBasename(mFilename));
        mArtist = getString(c, MediaStore.Audio.Media.ARTIST, "");
        mAlbum = getString(c, MediaStore.Audio.Media.ALBUM, "");
        mYear = getInt(c);
    }

    private Uri getAudioContentUri() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        } else {
            return MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        }
    }

    private String getString(Cursor c, String column, String defaultValue) {
        int idx = c.getColumnIndex(column);
        if (idx == -1)
            return defaultValue;

        String s = c.getString(idx);
        if (s == null || s.trim().isEmpty())
            return defaultValue;

        return s;
    }

    private int getInt(Cursor c) {
        int idx = c.getColumnIndex(MediaStore.Audio.AudioColumns.YEAR);
        if (idx == -1)
            return -1;

        return c.getInt(idx);
    }

    private String getBasename(String filename) {
        try {
            return filename.substring(filename.lastIndexOf('/') + 1, filename.lastIndexOf('.'));
        } catch (Exception e) {
            return filename;
        }
    }
}
