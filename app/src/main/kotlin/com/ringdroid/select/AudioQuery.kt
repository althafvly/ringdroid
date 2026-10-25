package com.ringdroid.select

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore

/** The MediaStore query that lists audio files, optionally filtered by a search term. */
internal class AudioQuery private constructor(
    val selection: String,
    val selectionArgs: Array<String>,
) {
    fun run(
        resolver: ContentResolver,
        collection: Uri,
    ): Cursor? = resolver.query(collection, PROJECTION, selection, selectionArgs, MediaStore.Audio.Media.TITLE + " ASC")

    companion object {
        val PROJECTION =
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.IS_RINGTONE,
                MediaStore.Audio.Media.IS_ALARM,
                MediaStore.Audio.Media.IS_NOTIFICATION,
                MediaStore.Audio.Media.IS_MUSIC,
                MediaStore.Audio.Media.DATA,
            )

        /**
         * @param showAll list every audio file instead of supported files on [storageRoots]
         * @param filter search term matched against title, artist and album, or null
         */
        fun build(
            showAll: Boolean,
            filter: String?,
            extensions: Array<String>,
            storageRoots: List<String>,
        ): AudioQuery {
            val args = ArrayList<String>()
            val selection = StringBuilder()
            if (showAll) {
                selection.append("(_DATA LIKE ?)")
                args.add("%")
            } else {
                selection.append(extensions.joinToString(" OR ", "(", ")") { "(_DATA LIKE ?)" })
                extensions.mapTo(args) { "%.$it" }
                selection.append(storageRoots.joinToString(" OR ", " AND (", ")") { "_DATA LIKE ?" })
                storageRoots.mapTo(args) { "$it/%" }
            }
            if (!filter.isNullOrEmpty()) {
                selection.insert(0, '(').append(" AND (TITLE LIKE ? OR ARTIST LIKE ? OR ALBUM LIKE ?))")
                repeat(3) { args.add("%$filter%") }
            }
            return AudioQuery(selection.toString(), args.toTypedArray())
        }
    }
}
