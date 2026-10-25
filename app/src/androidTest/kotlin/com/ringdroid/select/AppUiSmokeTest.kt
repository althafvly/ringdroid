package com.ringdroid.select

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.view.View
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ringdroid.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUiSmokeTest {
    @Test
    fun mediaSelectorSearchAndRotationDoNotCrash() {
        ActivityScenario.launch(RingdroidSelectActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertVisible(activity.findViewById(R.id.toolbar_container))
                assertVisible(activity.findViewById(R.id.btn_search))
                activity.findViewById<View>(R.id.btn_search).performClick()
                assertEquals(View.GONE, activity.findViewById<View>(R.id.toolbar_normal).visibility)
                assertVisible(activity.findViewById(R.id.toolbar_search))

                val query = activity.findViewById<EditText>(R.id.search_query_input)
                query.setText("audio")
                assertVisible(activity.findViewById(R.id.btn_search_clear))
                activity.findViewById<View>(R.id.btn_search_clear).performClick()
                assertEquals("", query.text.toString())
                activity.findViewById<View>(R.id.btn_search_back).performClick()
                assertVisible(activity.findViewById(R.id.toolbar_normal))
            }
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            val deadline = SystemClock.elapsedRealtime() + 5_000
            var orientation = -1
            while (SystemClock.elapsedRealtime() < deadline && orientation != Configuration.ORIENTATION_LANDSCAPE) {
                scenario.onActivity { orientation = it.resources.configuration.orientation }
                SystemClock.sleep(100)
            }
            assertEquals(Configuration.ORIENTATION_LANDSCAPE, orientation)
            scenario.onActivity { assertVisible(it.findViewById(R.id.toolbar_container)) }
        }
    }

    private fun assertVisible(view: View?) {
        assertEquals(View.VISIBLE, view?.visibility)
    }
}
