package com.ringdroid.util

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ringdroid.activity.RingdroidEditActivity
import com.ringdroid.soundfile.TestAudio
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DialogsTest {
    @Test
    fun dialogsCloseWithTheirActivity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val audio = TestAudio.copyAsset(File(context.cacheDir, "dialogs.wav"))
        val intent = Intent(context, RingdroidEditActivity::class.java).setData(Uri.fromFile(audio))
        lateinit var dialog: AlertDialog
        try {
            ActivityScenario.launch<RingdroidEditActivity>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    dialog = createDialog(activity, View(activity)).also { it.show() }
                    assertTrue(dialog.isShowing)
                }
                scenario.recreate()
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                assertFalse(dialog.isShowing)
            }
        } finally {
            audio.delete()
        }
    }
}
