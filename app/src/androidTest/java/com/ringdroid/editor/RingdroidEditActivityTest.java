package com.ringdroid.editor;

import com.ringdroid.R;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

@RunWith(AndroidJUnit4.class)
public class RingdroidEditActivityTest {
    private static final long ORIENTATION_TIMEOUT_MS = 5_000;

    @Test
    public void editorKeepsToolbarAndControlsWhenRotated() throws Exception {
        Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File audioFile = copyTestAudio(targetContext);
        Intent intent = new Intent(targetContext, RingdroidEditActivity.class)
                .setData(Uri.fromFile(audioFile));

        try (ActivityScenario<RingdroidEditActivity> scenario = ActivityScenario.launch(intent)) {
            assertToolbarAndControlsPresent(scenario);

            scenario.onActivity(activity -> activity
                    .setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            waitForOrientation(scenario, Configuration.ORIENTATION_LANDSCAPE);
            assertToolbarAndControlsPresent(scenario);

            scenario.onActivity(activity -> activity
                    .setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            waitForOrientation(scenario, Configuration.ORIENTATION_PORTRAIT);
            assertToolbarAndControlsPresent(scenario);
        } finally {
            audioFile.delete();
        }
    }

    private static void assertToolbarAndControlsPresent(
            ActivityScenario<RingdroidEditActivity> scenario) {
        scenario.onActivity(activity -> {
            assertNotNull(activity.findViewById(R.id.editor_toolbar));
            assertVisible(activity.findViewById(R.id.btn_back));
            assertVisible(activity.findViewById(R.id.btn_amplify));
            assertVisible(activity.findViewById(R.id.btn_reset));
            assertVisible(activity.findViewById(R.id.btn_save));
        });
    }

    private static void assertVisible(View view) {
        assertNotNull(view);
        assertEquals(View.VISIBLE, view.getVisibility());
    }

    private static void waitForOrientation(ActivityScenario<RingdroidEditActivity> scenario,
            int expectedOrientation) {
        long deadline = SystemClock.elapsedRealtime() + ORIENTATION_TIMEOUT_MS;
        while (SystemClock.elapsedRealtime() < deadline) {
            final int[] orientation = new int[1];
            scenario.onActivity(activity -> orientation[0] = activity.getResources()
                    .getConfiguration().orientation);
            if (orientation[0] == expectedOrientation) {
                return;
            }
            SystemClock.sleep(100);
        }
        scenario.onActivity(activity -> assertEquals(expectedOrientation,
                activity.getResources().getConfiguration().orientation));
    }

    private static File copyTestAudio(Context targetContext) throws IOException {
        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        File audioFile = new File(targetContext.getCacheDir(), "editor-rotation-test.wav");
        try (InputStream input = testContext.getAssets().open("test_audio.wav");
                FileOutputStream output = new FileOutputStream(audioFile)) {
            byte[] buffer = new byte[4_096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
        }
        return audioFile;
    }
}
