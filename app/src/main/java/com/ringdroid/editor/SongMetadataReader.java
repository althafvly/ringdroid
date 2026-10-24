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

package com.ringdroid.editor;

import android.media.MediaMetadataRetriever;
import android.util.Log;

import java.io.IOException;

final class SongMetadataReader {
    private static final String TAG = "SongMetadataReader";

    final String mTitle;
    final String mArtist;

    SongMetadataReader(String filename) {
        String title = null;
        String artist = null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(filename);
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
        } catch (IllegalArgumentException | IllegalStateException e) {
            Log.w(TAG, "Failed to read metadata from " + filename, e);
        } finally {
            try {
                retriever.release();
            } catch (IOException ignored) {
            }
        }
        mTitle = title != null && !title.isEmpty() ? title : getBasename(filename);
        mArtist = artist;
    }

    private static String getBasename(String filename) {
        int slashIndex = filename.lastIndexOf('/');
        int dotIndex = filename.lastIndexOf('.');
        int start = slashIndex + 1;
        int end = dotIndex > slashIndex ? dotIndex : filename.length();
        return filename.substring(start, Math.max(start, end));
    }
}
