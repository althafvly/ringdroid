package com.ringdroid.viewmodel

import android.app.Application
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ringdroid.R
import com.ringdroid.soundfile.TestAudio
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class EditorViewModelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val viewModel = EditorViewModel(context.applicationContext as Application)
    private val files = mutableListOf<File>()

    @After
    fun tearDown() {
        viewModel.player?.release()
        viewModel.soundFile?.release()
        files.forEach { it.delete() }
    }

    @Test
    fun loadsAFileWithItsTitle() {
        val file = TestAudio.copyAsset(cacheFile("Editor tone.wav"))
        val events = Events()
        events.await(viewModel) { viewModel.load(Uri.fromFile(file)) }
        assertEquals(listOf("metadata", "loaded"), events.names)
        assertEquals("Editor tone", viewModel.songTitle)
        assertNotNull(viewModel.soundFile)
        assertNotNull(viewModel.player)
        assertEquals(EditorViewModel.Task.IDLE, viewModel.task)
        assertTrue(viewModel.started)
    }

    @Test
    fun unsupportedFilesFailWithTheReason() {
        val file = cacheFile("tone.xyz").apply { writeBytes(ByteArray(16)) }
        val events = Events()
        events.await(viewModel) { viewModel.load(Uri.fromFile(file)) }
        assertEquals("failed", events.names.last())
        assertEquals(context.getString(R.string.bad_extension_error) + " xyz", events.message)
        assertNull(viewModel.soundFile)
    }

    @Test
    fun missingFilesFail() {
        val events = Events()
        events.await(viewModel) { viewModel.load(Uri.fromFile(cacheFile("missing.wav"))) }
        assertEquals("failed", events.names.last())
        assertEquals(context.getText(R.string.read_error), events.message)
    }

    @Test
    fun cancelingStopsLoading() {
        val file =
            TestAudio.writeWav(
                cacheFile("long.wav"),
                1,
                TestAudio.ramp(TestAudio.SAMPLE_RATE * LONG_SECONDS),
            )
        val events = Events(cancelOnProgress = viewModel)
        events.await(viewModel) { viewModel.load(Uri.fromFile(file)) }
        assertEquals("canceled", events.names.last())
        assertNull(viewModel.soundFile)
    }

    private fun cacheFile(name: String) = File(context.cacheDir, name).also { files.add(it) }

    /** Records the view model's events until it finishes a job. */
    private class Events(private val cancelOnProgress: EditorViewModel? = null) :
        EditorViewModel.Listener {
        val names = mutableListOf<String>()
        var message: CharSequence? = null
        private val done = CountDownLatch(1)

        fun await(viewModel: EditorViewModel, start: () -> Unit) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                viewModel.events.listener = this
                start()
            }
            assertTrue(done.await(TIMEOUT_S, TimeUnit.SECONDS))
        }

        override fun onProgress(value: Double) {
            cancelOnProgress?.cancel()
        }

        override fun onMetadata() {
            names.add("metadata")
        }

        override fun onLoaded() {
            names.add("loaded")
            done.countDown()
        }

        override fun onFailed(cause: Throwable?, message: CharSequence) {
            names.add("failed")
            this.message = message
            done.countDown()
        }

        override fun onCanceled() {
            names.add("canceled")
            done.countDown()
        }
    }

    private companion object {
        const val LONG_SECONDS = 60
        const val TIMEOUT_S = 30L
    }
}
