package com.ringdroid.editor

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ringdroid.R
import com.ringdroid.soundfile.TestAudio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RingdroidEditActivityTest {
    @Test
    fun editorKeepsToolbarAndControlsWhenRotated() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val audioFile = TestAudio.copyAsset(File(context.cacheDir, "editor-rotation-test.wav"))
        val intent = Intent(context, RingdroidEditActivity::class.java).setData(Uri.fromFile(audioFile))
        try {
            ActivityScenario.launch<RingdroidEditActivity>(intent).use { scenario ->
                assertToolbarAndControlsPresent(scenario)
                rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
                assertToolbarAndControlsPresent(scenario)
                rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
                assertToolbarAndControlsPresent(scenario)
            }
        } finally {
            audioFile.delete()
        }
    }

    private fun assertToolbarAndControlsPresent(scenario: ActivityScenario<RingdroidEditActivity>) {
        scenario.onActivity { activity ->
            assertNotNull(activity.findViewById<View>(R.id.editor_toolbar))
            for (id in intArrayOf(R.id.btn_back, R.id.btn_amplify, R.id.btn_reset, R.id.btn_save)) {
                assertEquals(View.VISIBLE, activity.findViewById<View>(id).visibility)
            }
        }
    }

    private fun rotate(
        scenario: ActivityScenario<RingdroidEditActivity>,
        requested: Int,
        expected: Int,
    ) {
        scenario.onActivity { it.requestedOrientation = requested }
        val deadline = SystemClock.elapsedRealtime() + 5_000
        var orientation = -1
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { orientation = it.resources.configuration.orientation }
            if (orientation == expected) return
            SystemClock.sleep(100)
        }
        assertEquals(expected, orientation)
    }
}
