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

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

public class SongMetadataReader {
    private static final String TAG = "SongMetadataReader";

    public Uri GENRES_URI = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI;
    public Context mContext;
    public String mFilename;
    public String mTitle = "";
    public String mArtist = "";
    public String mAlbum = "";
    public String mGenre = "";
    public int mYear = -1;

    SongMetadataReader(Context context, String filename) {
        mContext = context;
        mFilename = filename;
        mTitle = getBasename(filename);
        try {
            ReadMetadata();
        } catch (SecurityException | IllegalArgumentException | IllegalStateException
                | UnsupportedOperationException e) {
            Log.e(TAG, "Failed to read song metadata", e);
        }
    }

    private void ReadMetadata() {
        Cursor c = queryAudio(RingdroidUtils.getExternalAudioCollectionUri());
        if (c == null || c.getCount() == 0) {
            if (c != null) {
                c.close();
            }
            c = queryAudio(RingdroidUtils.getInternalAudioCollectionUri());
        }
        if (c == null || c.getCount() == 0) {
            if (c != null) {
                c.close();
            }
            mTitle = getBasename(mFilename);
            mArtist = "";
            mAlbum = "";
            mYear = -1;
            return;
        }
        c.moveToFirst();
        mTitle = getStringFromColumn(c, MediaStore.Audio.Media.TITLE);
        if (mTitle == null || mTitle.isEmpty()) {
            mTitle = getBasename(mFilename);
        }
        mArtist = getStringFromColumn(c, MediaStore.Audio.Media.ARTIST);
        mAlbum = getStringFromColumn(c, MediaStore.Audio.Media.ALBUM);
        mYear = getIntegerFromColumn(c);
        c.close();
    }

    private String getStringFromColumn(Cursor c, String columnName) {
        int index = c.getColumnIndexOrThrow(columnName);
        String value = c.getString(index);
        if (value != null && !value.isEmpty()) {
            return value;
        } else {
            return null;
        }
    }

    private int getIntegerFromColumn(Cursor c) {
        int index = c.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.YEAR);
        return c.getInt(index);
    }

    private String getBasename(String filename) {
        int slashIndex = filename.lastIndexOf('/');
        int dotIndex = filename.lastIndexOf('.');
        int start = (slashIndex == -1) ? 0 : slashIndex + 1;
        int end = (dotIndex == -1 || dotIndex <= slashIndex) ? filename.length() : dotIndex;
        if (start >= end) {
            return filename.substring(start);
        }
        return filename.substring(start, end);
    }

    private Cursor queryAudio(Uri uri) {
        try {
            return mContext.getContentResolver().query(uri,
                    new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                            MediaStore.Audio.Media.YEAR, MediaStore.Audio.Media.DATA},
                    MediaStore.Audio.Media.DATA + " LIKE \"" + mFilename + "\"", null, null);
        } catch (SecurityException | IllegalArgumentException | IllegalStateException
                | UnsupportedOperationException e) {
            Log.e(TAG, "Failed to query audio from " + uri, e);
            return null;
        }
    }
}
