package com.ringdroid.viewmodel

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ringdroid.R
import com.ringdroid.data.Clip
import com.ringdroid.dialog.FileSaveDialog
import com.ringdroid.soundfile.SoundFile
import com.ringdroid.soundfile.TestAudio
import com.ringdroid.testing.TestPermissions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SaveViewModelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val viewModel = SaveViewModel(context.applicationContext as Application)
    private lateinit var input: File
    private lateinit var soundFile: SoundFile

    @Before
    fun setUp() {
        TestPermissions.grantStorage()
        input =
            TestAudio.writeWav(
                File(context.cacheDir, "save_vm_input.wav"),
                1,
                TestAudio.ramp(TestAudio.SAMPLE_RATE),
            )
        soundFile = TestAudio.load(input)
    }

    @After
    fun tearDown() {
        viewModel.savedUri?.let { context.contentResolver.delete(it, null, null) }
        soundFile.release()
        input.delete()
    }

    @Test
    fun savesInTheBackgroundAndKeepsTheResult() {
        save(Clip(0, FRAMES, DURATION_MS)) { assertTrue(viewModel.isSaving) }
        assertFalse(viewModel.isSaving)
        assertEquals(FileSaveDialog.FILE_KIND_NOTIFICATION, viewModel.fileKind)
        assertNotNull(viewModel.savedUri)
        assertNull(viewModel.takeError())
    }

    @Test
    fun failuresAreReportedOnce() {
        save(Clip(0, 0, 0))
        assertFalse(viewModel.isSaving)
        assertNull(viewModel.savedUri)
        assertEquals(R.string.write_error, viewModel.takeError())
        assertNull(viewModel.takeError())
    }

    private fun save(clip: Clip, whileSaving: () -> Unit = {}) {
        val done = CountDownLatch(1)
        instrumentation.runOnMainSync {
            viewModel.events.listener = SaveViewModel.Listener { done.countDown() }
            viewModel.save(
                soundFile,
                "SaveViewModelTest" + System.nanoTime(),
                1.0f,
                FileSaveDialog.FILE_KIND_NOTIFICATION,
                clip,
            )
            whileSaving()
        }
        assertTrue(done.await(TIMEOUT_S, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }

    private companion object {
        const val FRAMES = 20
        const val DURATION_MS = 464
        const val TIMEOUT_S = 30L
    }
}
