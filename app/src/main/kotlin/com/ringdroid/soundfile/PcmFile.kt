package com.ringdroid.soundfile

import android.util.Log
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

internal class PcmFile private constructor(private val file: File) {
    private val raf = RandomAccessFile(file, "rw")
    private val channel: FileChannel = raf.channel
    private var written = 0L

    fun append(bytes: ByteArray, length: Int) {
        val buffer = ByteBuffer.wrap(bytes, 0, length)
        while (buffer.hasRemaining()) {
            written += channel.write(buffer, written)
        }
    }

    fun mapWritten(): MappedByteBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, written)

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

        fun create(cacheDir: File): PcmFile {
            val file =
                synchronized(activeFiles) {
                    deleteStaleFiles(cacheDir)
                    File.createTempFile(PREFIX, ".raw", cacheDir).also { activeFiles.add(it) }
                }
            try {
                return PcmFile(file)
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
