package com.ringdroid.activity

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ringdroid.R
import com.ringdroid.testing.SharedAudioProvider
import com.ringdroid.view.WaveformView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The ways the editor is opened: from the list, from other apps and for recording. */
@RunWith(AndroidJUnit4::class)
class EditorEntryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val sharedUri =
        SharedAudioProvider.uri(
            instrumentation.context.packageName,
            "Entry tone.wav",
        )
    private val monitors = mutableListOf<Instrumentation.ActivityMonitor>()

    @After
    fun tearDown() {
        monitors.forEach { instrumentation.removeMonitor(it) }
    }

    @Test
    fun editIntentsCarryTheFileAndAccess() {
        val intent = RingdroidEditActivity.newIntent(context, sharedUri, wasGetContentIntent = true)
        assertEquals(Intent.ACTION_EDIT, intent.action)
        assertEquals(sharedUri, intent.data)
        assertTrue(
            intent.getBooleanExtra(RingdroidEditActivity.EXTRA_WAS_GET_CONTENT_INTENT, false),
        )
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(RingdroidEditActivity::class.java.name, intent.component?.className)
    }

    @Test
    fun recordIntentsHaveNoData() {
        val intent = RingdroidEditActivity.newIntent(context, null, wasGetContentIntent = false)
        assertEquals(MediaStore.Audio.Media.RECORD_SOUND_ACTION, intent.action)
        assertNull(intent.data)
        assertFalse(
            intent.getBooleanExtra(RingdroidEditActivity.EXTRA_WAS_GET_CONTENT_INTENT, true),
        )
    }

    @Test
    fun editorWithoutDataCloses() {
        val intent = Intent(Intent.ACTION_EDIT, null, context, RingdroidEditActivity::class.java)
        ActivityScenario.launch<RingdroidEditActivity>(intent).use { scenario ->
            waitUntil { scenario.state == Lifecycle.State.DESTROYED }
        }
    }

    @Test
    fun editorOpensAudioSharedByAnotherApp() {
        val intent =
            RingdroidEditActivity.newIntent(
                context,
                sharedUri,
                wasGetContentIntent = false,
            )
        ActivityScenario.launch<RingdroidEditActivity>(intent).use { scenario ->
            waitForSound(scenario)
            scenario.onActivity {
                assertEquals("Entry tone", it.findViewById<TextView>(R.id.toolbar_title).text)
            }
        }
    }

    @Test
    fun viewedAudioOpensInTheEditor() {
        assertForwardedToEditor(Intent(Intent.ACTION_VIEW, sharedUri))
    }

    @Test
    fun sharedAudioOpensInTheEditor() {
        assertForwardedToEditor(
            Intent(
                Intent.ACTION_SEND,
            ).setType("audio/x-wav").putExtra(Intent.EXTRA_STREAM, sharedUri),
        )
    }

    @Test
    fun selectionSurvivesRecreation() {
        val intent =
            RingdroidEditActivity.newIntent(
                context,
                sharedUri,
                wasGetContentIntent = false,
            )
        ActivityScenario.launch<RingdroidEditActivity>(intent).use { scenario ->
            waitForSound(scenario)
            scenario.onActivity { activity ->
                val start = activity.findViewById<EditText>(R.id.starttext)
                start.requestFocus()
                start.setText(START_SECONDS)
                activity.findViewById<EditText>(R.id.endtext).requestFocus()
            }
            val before = waitForTimes(scenario) { (start, _) -> start != "0.00" }
            scenario.recreate()
            waitForSound(scenario)
            assertEquals(before, waitForTimes(scenario) { it == before })
        }
    }

    private fun assertForwardedToEditor(intent: Intent) {
        val selectMonitor =
            instrumentation.addMonitor(
                RingdroidSelectActivity::class.java.name,
                null,
                false,
            )
        val editorMonitor =
            instrumentation.addMonitor(
                RingdroidEditActivity::class.java.name,
                null,
                false,
            )
        monitors.add(selectMonitor)
        monitors.add(editorMonitor)
        context.startActivity(
            intent
                .setClass(
                    context,
                    RingdroidSelectActivity::class.java,
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        val editor: Activity? = instrumentation.waitForMonitorWithTimeout(editorMonitor, TIMEOUT_MS)
        val select: Activity? = instrumentation.waitForMonitorWithTimeout(selectMonitor, TIMEOUT_MS)
        try {
            assertNotNull(editor)
            assertEquals(sharedUri, editor!!.intent.data)
            assertEquals(Intent.ACTION_EDIT, editor.intent.action)
        } finally {
            editor?.finish()
            select?.finish()
        }
    }

    private fun waitForSound(scenario: ActivityScenario<RingdroidEditActivity>) {
        waitUntil {
            var loaded = false
            scenario.onActivity {
                loaded =
                    it.findViewById<WaveformView>(R.id.waveform).isInitialized
            }
            loaded
        }
    }

    private fun waitForTimes(
        scenario: ActivityScenario<RingdroidEditActivity>,
        done: (Pair<String, String>) -> Boolean,
    ): Pair<String, String> {
        var times = "" to ""
        waitUntil {
            scenario.onActivity {
                times =
                    it.findViewById<EditText>(R.id.starttext).text.toString() to
                    it.findViewById<EditText>(R.id.endtext).text.toString()
            }
            done(times)
        }
        return times
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (!condition()) {
            assertTrue("timed out", SystemClock.uptimeMillis() < deadline)
            SystemClock.sleep(POLL_MS)
        }
    }

    private companion object {
        const val START_SECONDS = "0.5"
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 100L
    }
}
