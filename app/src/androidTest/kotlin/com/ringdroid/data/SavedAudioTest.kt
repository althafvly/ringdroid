package com.ringdroid.data

import android.app.Application
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ringdroid.dialog.FileSaveDialog
import com.ringdroid.soundfile.SoundFile
import com.ringdroid.soundfile.TestAudio
import com.ringdroid.testing.TestPermissions
import com.ringdroid.viewmodel.SelectViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** A saved ringtone can be opened again and is listed, on every storage model. */
@RunWith(AndroidJUnit4::class)
class SavedAudioTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var input: File
    private lateinit var soundFile: SoundFile
    private var saved: Uri? = null

    @Before
    fun setUp() {
        TestPermissions.grantStorage()
        input =
            TestAudio.writeWav(
                File(context.cacheDir, "saved_input.wav"),
                1,
                TestAudio.ramp(TestAudio.SAMPLE_RATE),
            )
        soundFile = TestAudio.load(input)
    }

    @After
    fun tearDown() {
        saved?.let { context.contentResolver.delete(it, null, null) }
        soundFile.release()
        input.delete()
    }

    @Test
    fun savedRingtoneOpensAgain() {
        val uri = save("SavedAudioTest" + System.nanoTime())
        val file = SourceFiles.resolve(context, uri)
        assertNotNull(file)
        val reopened = SoundFile.create(context.cacheDir, file!!.path) { false }
        assertNotNull(reopened)
        try {
            assertEquals(1, reopened!!.channels)
            assertTrue("frames ${reopened.numFrames}", reopened.numFrames in FRAMES..FRAMES + 4)
        } finally {
            reopened?.release()
        }
    }

    @Test
    fun savedRingtoneIsListedAndDeletable() {
        val title = "SavedAudioTest" + System.nanoTime()
        val uri = save(title)
        val viewModel = SelectViewModel(context.applicationContext as Application)
        val loaded = CountDownLatch(1)
        instrumentation.runOnMainSync {
            viewModel.events.listener = SelectViewModel.Listener { loaded.countDown() }
            viewModel.load(title)
        }
        assertTrue(loaded.await(LOAD_TIMEOUT_S, TimeUnit.SECONDS))
        instrumentation.runOnMainSync {
            val cursor = viewModel.cursor
            assertNotNull(cursor)
            assertEquals(1, cursor!!.count)
            assertTrue(cursor.moveToFirst())
            val row = AudioRow.from(cursor, isInternal = false)
            assertEquals(uri.lastPathSegment, row.uri.lastPathSegment)
            assertTrue(row.isRingtone)
            assertFalse(row.isNotification)
            assertTrue(row.canDelete())
            viewModel.events.listener = null
        }
    }

    private fun save(title: String): Uri =
        RingtoneSaver(context, soundFile, 1.0f, FileSaveDialog.FILE_KIND_RINGTONE)
            .save(title, 0, FRAMES, DURATION_MS)
            .also { saved = it }

    private companion object {
        const val FRAMES = 20
        const val DURATION_MS = 464
        const val LOAD_TIMEOUT_S = 10L
    }
}
