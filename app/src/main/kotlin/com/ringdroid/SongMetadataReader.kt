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
package com.ringdroid

import android.app.Activity
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore

class SongMetadataReader internal constructor(
    var mActivity: Activity,
    var mFilename: String,
) {
    var genResUri: Uri = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI

    @JvmField
    var mTitle: String? = ""

    @JvmField
    var mArtist: String? = ""
    var mAlbum: String? = ""
    var mGenre: String? = ""
    var mYear: Int = -1

    init {
        mTitle = getBasename(mFilename)
        try {
            readMetadata()
        } catch (ignored: Exception) {
        }
    }

    private fun readMetadata() {
        // Get a map from genre ids to names
        val genreIdMap = HashMap<String?, String?>()
        var c: Cursor? =
            checkNotNull(
                mActivity.contentResolver.query(
                    genResUri,
                    arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
                    null,
                    null,
                    null,
                ),
            )
        c!!.moveToFirst()
        while (!c.isAfterLast) {
            genreIdMap[c.getString(0)] = c.getString(1)
            c.moveToNext()
        }
        c.close()
        mGenre = ""
        for (genreId in genreIdMap.keys) {
            c =
                mActivity.contentResolver.query(
                    makeGenreUri(genreId)!!,
                    arrayOf<String>(MediaStore.Audio.Media.DATA),
                    MediaStore.Audio.Media.DATA + " LIKE \"" + mFilename + "\"",
                    null,
                    null,
                )
            checkNotNull(c)
            if (c.count != 0) {
                mGenre = genreIdMap.get(genreId)
                break
            }
            c.close()
            c = null
        }

        val uri = MediaStore.Audio.Media.getContentUriForPath(mFilename)
        c =
            mActivity.contentResolver.query(
                uri!!,
                arrayOf<String>(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.YEAR,
                    MediaStore.Audio.Media.DATA,
                ),
                MediaStore.Audio.Media.DATA + " LIKE \"" + mFilename + "\"",
                null,
                null,
            )
        checkNotNull(c)
        if (c.count == 0) {
            mTitle = getBasename(mFilename)
            mArtist = ""
            mAlbum = ""
            mYear = -1
            return
        }
        c.moveToFirst()
        mTitle = getStringFromColumn(c, MediaStore.Audio.Media.TITLE)
        if (mTitle == null || mTitle!!.isEmpty()) {
            mTitle = getBasename(mFilename)
        }
        mArtist = getStringFromColumn(c, MediaStore.Audio.Media.ARTIST)
        mAlbum = getStringFromColumn(c, MediaStore.Audio.Media.ALBUM)
        mYear = getIntegerFromColumn(c)
        c.close()
    }

    private fun makeGenreUri(genreId: String?): Uri? {
        val contentDir = MediaStore.Audio.Genres.Members.CONTENT_DIRECTORY
        return Uri.parse("$genResUri/$genreId/$contentDir")
    }

    private fun getStringFromColumn(
        c: Cursor,
        columnName: String?,
    ): String? {
        val index = c.getColumnIndexOrThrow(columnName)
        val value = c.getString(index)
        return if (value != null && !value.isEmpty()) {
            value
        } else {
            null
        }
    }

    private fun getIntegerFromColumn(c: Cursor): Int {
        val index = c.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.YEAR)
        return c.getInt(index)
    }

    private fun getBasename(filename: String): String = filename.substring(filename.lastIndexOf('/') + 1, filename.lastIndexOf('.'))
}
