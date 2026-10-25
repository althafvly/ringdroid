package com.ringdroid.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import com.ringdroid.R
import com.ringdroid.dialog.FileSaveDialog
import com.ringdroid.soundfile.SoundFile
import com.ringdroid.soundfile.SoundFileWriter
import com.ringdroid.util.RingdroidUtils
import java.io.File
import java.io.IOException

/** Saves part of a sound file as M4A, falling back to WAV, and registers it in MediaStore. */
internal class RingtoneSaver(
    context: Context,
    soundFile: SoundFile,
    gain: Float,
    private val fileKind: Int,
) {
    class SaveException(val messageId: Int, cause: Throwable?) : IOException(cause)

    private val context = context.applicationContext
    private val writer = SoundFileWriter(soundFile, gain)

    fun save(title: CharSequence, startFrame: Int, numFrames: Int, durationMs: Int): Uri {
        var error: Exception? = null
        for (wav in booleanArrayOf(false, true)) {
            fun failed(e: Exception) {
                Log.e(TAG, "Failed to save " + if (wav) "WAV" else "M4A", e)
                error = e
            }
            try {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveToMediaStore(wav, title, startFrame, numFrames)
                } else {
                    saveToFile(wav, title, startFrame, numFrames, durationMs)
                }
            } catch (e: SaveException) {
                throw e
            } catch (e: IOException) {
                failed(e)
            } catch (e: SecurityException) {
                failed(e)
            } catch (e: IllegalArgumentException) {
                failed(e)
            } catch (e: IllegalStateException) {
                failed(e)
            }
        }
        val message = error?.message.orEmpty()
        val noSpace = message.contains("ENOSPC") || message.contains("No space left on device")
        throw SaveException(if (noSpace) R.string.no_space_error else R.string.write_error, error)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToMediaStore(
        wav: Boolean,
        title: CharSequence,
        startFrame: Int,
        numFrames: Int,
    ): Uri {
        val values =
            fileKindValues().apply {
                put(MediaStore.MediaColumns.RELATIVE_PATH, subDir)
                put(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    title.toString() + if (wav) ".wav" else ".m4a",
                )
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType(wav))
                // Hidden from other apps until the file is complete.
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val resolver = context.contentResolver
        val uri =
            resolver.insert(RingdroidUtils.externalAudioCollectionUri, values)
                ?: throw IOException("MediaStore insert failed")
        var written = false
        try {
            if (wav) {
                writer.writeWAV(context, uri, startFrame, numFrames)
            } else {
                writer.writeM4A(context, uri, startFrame, numFrames)
            }
            resolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                },
                null,
                null,
            )
            written = true
        } finally {
            if (!written) {
                deleteQuietly(uri)
            }
        }
        return uri
    }

    private fun deleteQuietly(uri: Uri) {
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (e: SecurityException) {
            Log.w(TAG, "Failed to delete incomplete entry: $uri", e)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Failed to delete incomplete entry: $uri", e)
        } catch (e: UnsupportedOperationException) {
            Log.w(TAG, "Failed to delete incomplete entry: $uri", e)
        }
    }

    private fun saveToFile(
        wav: Boolean,
        title: CharSequence,
        startFrame: Int,
        numFrames: Int,
        durationMs: Int,
    ): Uri {
        val path =
            makeRingtoneFilename(title, if (wav) ".wav" else ".m4a")
                ?: throw SaveException(R.string.no_unique_filename, null)
        val file = File(path)
        var saved = false
        try {
            if (wav) {
                writer.writeWAV(file, startFrame, numFrames)
            } else {
                writer.writeM4A(file, startFrame, numFrames)
            }
            verifyReadable(file)
            val uri = insertFile(file, title, wav, durationMs)
            saved = true
            return uri
        } finally {
            if (!saved && file.exists() && !file.delete()) {
                Log.w(TAG, "Failed to delete $file")
            }
        }
    }

    private fun insertFile(file: File, title: CharSequence, wav: Boolean, durationMs: Int): Uri {
        val values =
            fileKindValues().apply {
                put(MediaStore.MediaColumns.DATA, file.path)
                put(MediaStore.MediaColumns.DISPLAY_NAME, title.toString())
                put(MediaStore.MediaColumns.SIZE, file.length())
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType(wav))
                put(MediaStore.Audio.Media.TITLE, title.toString())
                put(MediaStore.Audio.Media.ARTIST, context.getString(R.string.artist_name))
                put(MediaStore.Audio.Media.DURATION, durationMs)
            }
        return context.contentResolver.insert(RingdroidUtils.externalAudioCollectionUri, values)
            ?: throw SaveException(R.string.write_error, null)
    }

    private fun verifyReadable(file: File) {
        try {
            SoundFile.create(context.cacheDir, file.path) { false }?.release()
        } catch (e: SoundFile.InvalidInputException) {
            throw IOException("Saved file is unreadable", e)
        }
        if (file.length() <= MIN_FILE_SIZE) {
            throw SaveException(R.string.too_small_error, null)
        }
    }

    private fun fileKindValues() = ContentValues().apply {
        put(MediaStore.Audio.Media.IS_RINGTONE, fileKind == FileSaveDialog.FILE_KIND_RINGTONE)
        put(
            MediaStore.Audio.Media.IS_NOTIFICATION,
            fileKind == FileSaveDialog.FILE_KIND_NOTIFICATION,
        )
        put(MediaStore.Audio.Media.IS_ALARM, fileKind == FileSaveDialog.FILE_KIND_ALARM)
        put(MediaStore.Audio.Media.IS_MUSIC, fileKind == FileSaveDialog.FILE_KIND_MUSIC)
    }

    // MediaStore only accepts standard top-level folders such as Ringtones from Android 10.
    private val subDir: String
        get() {
            val saveDir =
                if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.Q
                ) {
                    Environment.DIRECTORY_RINGTONES
                } else {
                    "media/audio"
                }
            val kindDir =
                when (fileKind) {
                    FileSaveDialog.FILE_KIND_MUSIC -> "music"
                    FileSaveDialog.FILE_KIND_ALARM -> "alarms"
                    FileSaveDialog.FILE_KIND_NOTIFICATION -> "notifications"
                    FileSaveDialog.FILE_KIND_RINGTONE -> "ringtones"
                    else -> "others"
                }
            return "$saveDir/$kindDir/"
        }

    private fun makeRingtoneFilename(title: CharSequence, extension: String): String? {
        var externalRootDir = Environment.getExternalStorageDirectory().path
        if (!externalRootDir.endsWith("/")) {
            externalRootDir += "/"
        }
        // Fall back to the storage root if the folder can't be created.
        var parentDir = externalRootDir + subDir
        val parentDirFile = File(parentDir)
        if (!parentDirFile.mkdirs() && !parentDirFile.isDirectory) {
            parentDir = externalRootDir
        }
        val filename = title.filter { it.isLetterOrDigit() }
        return (0 until MAX_FILENAME_ATTEMPTS)
            .map { i -> parentDir + filename + (if (i > 0) i.toString() else "") + extension }
            .firstOrNull { !File(it).exists() }
    }

    private companion object {
        const val TAG = "RingtoneSaver"
        const val MIN_FILE_SIZE = 512
        const val MAX_FILENAME_ATTEMPTS = 100

        fun mimeType(wav: Boolean) = if (wav) "audio/wav" else "audio/mp4a-latm"
    }
}
