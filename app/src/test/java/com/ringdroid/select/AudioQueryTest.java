package com.ringdroid.select;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class AudioQueryTest {
    private static final String[] EXTENSIONS = {"mp3", "wav"};
    private static final List<String> ROOTS = Arrays.asList("/sdcard", "/storage/1234");

    @Test
    public void supportedFilesOnStorageRoots() {
        AudioQuery query = AudioQuery.build(false, null, EXTENSIONS, ROOTS);
        assertEquals("((_DATA LIKE ?) OR (_DATA LIKE ?)) AND (_DATA LIKE ? OR _DATA LIKE ?)",
                query.selection);
        assertArrayEquals(new String[]{"%.mp3", "%.wav", "/sdcard/%", "/storage/1234/%"},
                query.selectionArgs);
    }

    @Test
    public void showAllMatchesEverything() {
        AudioQuery query = AudioQuery.build(true, null, EXTENSIONS, ROOTS);
        assertEquals("(_DATA LIKE ?)", query.selection);
        assertArrayEquals(new String[]{"%"}, query.selectionArgs);
    }

    @Test
    public void filterMatchesTitleArtistAndAlbum() {
        AudioQuery query = AudioQuery.build(true, "rock", EXTENSIONS, ROOTS);
        assertEquals("((_DATA LIKE ?) AND (TITLE LIKE ? OR ARTIST LIKE ? OR ALBUM LIKE ?))",
                query.selection);
        assertArrayEquals(new String[]{"%", "%rock%", "%rock%", "%rock%"}, query.selectionArgs);
    }

    @Test
    public void emptyFilterIsIgnored() {
        assertEquals(AudioQuery.build(true, null, EXTENSIONS, ROOTS).selection,
                AudioQuery.build(true, "", EXTENSIONS, ROOTS).selection);
    }
}
