package com.ringdroid.util

import org.junit.Assert.assertEquals
import org.junit.Test

class RingdroidUtilsUnitTest {
    @Test
    fun safeFileNameStripsPaths() {
        assertEquals("song.mp3", RingdroidUtils.toSafeFileName("song.mp3", "fallback"))
        assertEquals("c.mp3", RingdroidUtils.toSafeFileName("a/b/c.mp3", "fallback"))
        assertEquals("x.xml", RingdroidUtils.toSafeFileName("../../shared_prefs/x.xml", "fallback"))
    }

    @Test
    fun safeFileNameRejectsEmptyAndDotNames() {
        for (name in listOf(null, "", "dir/", ".", "..", "a/..")) {
            assertEquals("fallback", RingdroidUtils.toSafeFileName(name, "fallback"))
        }
    }
}
