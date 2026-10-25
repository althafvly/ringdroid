package com.ringdroid.data

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ringdroid.R
import com.ringdroid.soundfile.TestAudio
import com.ringdroid.testing.SharedAudioProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SourceFilesTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun fileUrisResolveToTheFile() {
        val file = TestAudio.copyAsset(File(context.cacheDir, "source_file.wav"))
        try {
            assertEquals(
                file.canonicalPath,
                SourceFiles.resolve(context, Uri.fromFile(file))?.canonicalPath,
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun privateAppFilesAreRefused() {
        val file = TestAudio.copyAsset(File(context.filesDir, "private.wav"))
        try {
            assertNull(SourceFiles.resolve(context, Uri.fromFile(file)))
        } finally {
            file.delete()
        }
    }

    @Test
    fun otherSchemesAreRefused() {
        assertNull(SourceFiles.resolve(context, Uri.parse("https://example.com/tone.mp3")))
        assertNull(SourceFiles.resolve(context, Uri.parse("tone.mp3")))
    }

    @Test
    fun sharedContentIsCopiedWithItsName() {
        val uri = SharedAudioProvider.uri(instrumentation.context.packageName, "Shared tone.wav")
        val file = SourceFiles.resolve(context, uri)
        assertNotNull(file)
        assertEquals("Shared tone.wav", file!!.name)
        assertTrue(file.path.startsWith(context.cacheDir.path))
        TestAudio.load(file).release()
    }

    @Test
    fun sharedContentWithoutExtensionGetsOneFromItsType() {
        val uri = SharedAudioProvider.uri(instrumentation.context.packageName, "tone")
        assertEquals("tone.wav", SourceFiles.resolve(context, uri)?.name)
    }

    @Test
    fun sharedNamesCannotEscapeTheCache() {
        val uri = SharedAudioProvider.uri(instrumentation.context.packageName, "..")
        val file = SourceFiles.resolve(context, uri)
        assertNotNull(file)
        assertTrue(file!!.canonicalPath.startsWith(context.cacheDir.canonicalPath))
    }

    @Test
    fun unsupportedFilesExplainWhy() {
        assertEquals(
            context.getString(R.string.no_extension_error),
            SourceFiles.unsupportedMessage(context, File("tone")),
        )
        assertEquals(
            context.getString(R.string.bad_extension_error) + " xyz",
            SourceFiles.unsupportedMessage(context, File("Tone.XYZ")),
        )
    }
}
