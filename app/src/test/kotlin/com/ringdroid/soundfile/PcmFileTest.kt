package com.ringdroid.soundfile

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PcmFileTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun mappedBytesMatchAppendedBytes() {
        val pcm = PcmFile.create(folder.root)
        pcm.append(byteArrayOf(1, 2, 3, 4, 5), 3)
        pcm.append(byteArrayOf(6, 7), 2)

        val mapped = pcm.mapWritten()
        assertEquals(5, mapped.limit())
        assertEquals(listOf<Byte>(1, 2, 3, 6, 7), List(mapped.limit()) { mapped.get(it) })

        pcm.delete()
    }

    @Test
    fun emptyFileMapsToEmptyBuffer() {
        val pcm = PcmFile.create(folder.root)

        assertEquals(0, pcm.mapWritten().limit())

        pcm.delete()
    }
}
