package com.ringdroid.select;

import com.ringdroid.R;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;

import androidx.activity.ComponentActivity;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AppUiSmokeTest {
    private static final long ORIENTATION_TIMEOUT_MS = 5_000;

    @Test
    public void mediaSelectorSearchAndRotationDoNotCrash() {
        try (ActivityScenario<RingdroidSelectActivity> scenario = ActivityScenario
                .launch(RingdroidSelectActivity.class)) {
            scenario.onActivity(activity -> {
                assertVisible(activity.findViewById(R.id.toolbar_container));
                assertVisible(activity.findViewById(R.id.btn_search));
                activity.findViewById(R.id.btn_search).performClick();
                assertEquals(View.GONE, activity.findViewById(R.id.toolbar_normal).getVisibility());
                assertVisible(activity.findViewById(R.id.toolbar_search));

                EditText query = activity.findViewById(R.id.search_query_input);
                query.setText("audio");
                assertVisible(activity.findViewById(R.id.btn_search_clear));
                activity.findViewById(R.id.btn_search_clear).performClick();
                assertEquals("", query.getText().toString());
                activity.findViewById(R.id.btn_search_back).performClick();
                assertVisible(activity.findViewById(R.id.toolbar_normal));
            });
            rotateToLandscape(scenario);
            scenario.onActivity(
                    activity -> assertVisible(activity.findViewById(R.id.toolbar_container)));
        }
    }

    private static <T extends ComponentActivity> void rotateToLandscape(
            ActivityScenario<T> scenario) {
        scenario.onActivity(activity -> activity
                .setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        long deadline = SystemClock.elapsedRealtime() + ORIENTATION_TIMEOUT_MS;
        while (SystemClock.elapsedRealtime() < deadline) {
            final int[] orientation = new int[1];
            scenario.onActivity(activity -> orientation[0] = activity.getResources()
                    .getConfiguration().orientation);
            if (orientation[0] == Configuration.ORIENTATION_LANDSCAPE) {
                return;
            }
            SystemClock.sleep(100);
        }
        scenario.onActivity(activity -> assertEquals(Configuration.ORIENTATION_LANDSCAPE,
                activity.getResources().getConfiguration().orientation));
    }

    private static void assertVisible(View view) {
        assertNotNull(view);
        assertEquals(View.VISIBLE, view.getVisibility());
    }
}
