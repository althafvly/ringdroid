package com.ringdroid.data

import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.IOException

/** Reads the title and artist tags of an audio file; the title falls back to the file name. */
internal class SongMetadataReader(filename: String) {
    val title: String
    val artist: String?

    init {
        var tagTitle: String? = null
        var tagArtist: String? = null
        val retriever = MediaMetadataRetriever()
        try {
            MediaExtractor().apply {
                try {
                    setDataSource(filename)
                } finally {
                    release()
                }
            }
            retriever.setDataSource(filename)
            tagTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            tagArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        } catch (e: IOException) {
            Log.w(TAG, "Failed to read metadata from $filename", e)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Failed to read metadata from $filename", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Failed to read metadata from $filename", e)
        } finally {
            try {
                retriever.release()
            } catch (ignored: IOException) {
            }
        }
        title = if (tagTitle.isNullOrEmpty()) basename(filename) else tagTitle
        artist = tagArtist
    }

    private companion object {
        const val TAG = "SongMetadataReader"

        fun basename(filename: String): String = filename.substringAfterLast('/').let { name ->
            name.substringBeforeLast('.', name).ifEmpty {
                name
            }
        }
    }
}
