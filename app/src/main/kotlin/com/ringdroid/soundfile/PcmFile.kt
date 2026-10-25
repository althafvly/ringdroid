package com.ringdroid.soundfile

import android.util.Log
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/** A temporary file holding decoded PCM, memory-mapped and grown as decoding needs more room. */
internal class PcmFile private constructor(
    private val file: File,
    initialSize: Long,
) {
    private val raf = RandomAccessFile(file, "rw")

    var buffer: MappedByteBuffer
        private set

    init {
        raf.setLength(initialSize)
        buffer = raf.channel.map(FileChannel.MapMode.READ_WRITE, 0, initialSize)
    }

    /** Grows the file to [newSize] bytes and returns the new mapping at the same position. */
    fun grow(newSize: Long): MappedByteBuffer {
        val position = buffer.position()
        raf.setLength(newSize)
        buffer = raf.channel.map(FileChannel.MapMode.READ_WRITE, 0, newSize)
        buffer.position(position)
        return buffer
    }

    fun delete() {
        try {
            raf.close()
        } catch (e: IOException) {
            Log.w(TAG, "Failed to close $file", e)
        }
        synchronized(activeFiles) { activeFiles.remove(file) }
        if (!file.delete() && file.exists()) {
            Log.w(TAG, "Failed to delete temporary PCM file: ${file.absolutePath}")
        }
    }

    companion object {
        private const val TAG = "PcmFile"
        private const val PREFIX = "ringdroid_pcm_"

        // Temp files in use by this process; any other ones were left behind by a killed process.
        private val activeFiles = mutableSetOf<File>()

        fun create(
            cacheDir: File,
            initialSize: Long,
        ): PcmFile {
            val file =
                synchronized(activeFiles) {
                    deleteStaleFiles(cacheDir)
                    File.createTempFile(PREFIX, ".raw", cacheDir).also { activeFiles.add(it) }
                }
            try {
                return PcmFile(file, initialSize)
            } catch (e: IOException) {
                synchronized(activeFiles) { activeFiles.remove(file) }
                file.delete()
                throw e
            }
        }

        private fun deleteStaleFiles(dir: File) {
            dir.listFiles { _, name -> name.startsWith(PREFIX) }?.forEach { file ->
                if (file !in activeFiles && !file.delete()) {
                    Log.w(TAG, "Failed to delete stale PCM file: $file")
                }
            }
        }
    }
}
