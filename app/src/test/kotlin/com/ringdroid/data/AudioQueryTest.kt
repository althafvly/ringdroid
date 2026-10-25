package com.ringdroid.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioQueryTest {
    private val extensions = arrayOf("mp3", "wav")
    private val roots = listOf("/sdcard", "/storage/1234")

    @Test
    fun supportedFilesOnStorageRoots() {
        val query = AudioQuery.build(false, null, extensions, roots)
        assertEquals("((_DATA LIKE ?) OR (_DATA LIKE ?)) AND (_DATA LIKE ? OR _DATA LIKE ?)", query.selection)
        assertArrayEquals(arrayOf("%.mp3", "%.wav", "/sdcard/%", "/storage/1234/%"), query.selectionArgs)
    }

    @Test
    fun showAllMatchesEverything() {
        val query = AudioQuery.build(true, null, extensions, roots)
        assertEquals("(_DATA LIKE ?)", query.selection)
        assertArrayEquals(arrayOf("%"), query.selectionArgs)
    }

    @Test
    fun filterMatchesTitleArtistAndAlbum() {
        val query = AudioQuery.build(true, "rock", extensions, roots)
        assertEquals("((_DATA LIKE ?) AND (TITLE LIKE ? OR ARTIST LIKE ? OR ALBUM LIKE ?))", query.selection)
        assertArrayEquals(arrayOf("%", "%rock%", "%rock%", "%rock%"), query.selectionArgs)
    }

    @Test
    fun emptyFilterIsIgnored() {
        assertEquals(AudioQuery.build(true, null, extensions, roots).selection, AudioQuery.build(true, "", extensions, roots).selection)
    }
}
