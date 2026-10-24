package com.ringdroid;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RingdroidUtilsUnitTest {
    @Test
    public void safeFileNameStripsPaths() {
        assertEquals("song.mp3", RingdroidUtils.toSafeFileName("song.mp3", "fallback"));
        assertEquals("c.mp3", RingdroidUtils.toSafeFileName("a/b/c.mp3", "fallback"));
        assertEquals("x.xml",
                RingdroidUtils.toSafeFileName("../../shared_prefs/x.xml", "fallback"));
    }

    @Test
    public void safeFileNameRejectsEmptyAndDotNames() {
        assertEquals("fallback", RingdroidUtils.toSafeFileName(null, "fallback"));
        assertEquals("fallback", RingdroidUtils.toSafeFileName("", "fallback"));
        assertEquals("fallback", RingdroidUtils.toSafeFileName("dir/", "fallback"));
        assertEquals("fallback", RingdroidUtils.toSafeFileName(".", "fallback"));
        assertEquals("fallback", RingdroidUtils.toSafeFileName("..", "fallback"));
        assertEquals("fallback", RingdroidUtils.toSafeFileName("a/..", "fallback"));
    }
}
