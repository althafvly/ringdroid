package com.ringdroid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.ringdroid.soundfile.SoundFile;
import com.ringdroid.soundfile.TestAudio;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(AndroidJUnit4.class)
public class RingtoneSaverTest {
    private Context mContext;
    private File mInput;
    private SoundFile mSoundFile;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mInput = TestAudio.writeWav(new File(mContext.getCacheDir(), "saver_input.wav"), 1,
                TestAudio.ramp(TestAudio.SAMPLE_RATE));
        mSoundFile = TestAudio.load(mInput);
    }

    @After
    public void tearDown() {
        mSoundFile.release();
        mInput.delete();
    }

    @Test
    public void savesEachFileKindWithItsFlagsAndFolder() throws Exception {
        String[] flags = {MediaStore.Audio.Media.IS_MUSIC, MediaStore.Audio.Media.IS_ALARM,
                MediaStore.Audio.Media.IS_NOTIFICATION, MediaStore.Audio.Media.IS_RINGTONE};
        String[] folders = {"music", "alarms", "notifications", "ringtones"};
        int[] kinds = {FileSaveDialog.FILE_KIND_MUSIC, FileSaveDialog.FILE_KIND_ALARM,
                FileSaveDialog.FILE_KIND_NOTIFICATION, FileSaveDialog.FILE_KIND_RINGTONE};
        for (int i = 0; i < kinds.length; i++) {
            Uri uri = new RingtoneSaver(mContext, mSoundFile, 1.0f, kinds[i])
                    .save("RingtoneSaverTest" + System.nanoTime(), 0, 20, 464);
            assertNotNull(uri);
            try {
                assertSaved(uri, flags[i], folders[i]);
            } finally {
                mContext.getContentResolver().delete(uri, null, null);
            }
        }
    }

    private void assertSaved(Uri uri, String flag, String folder) throws Exception {
        boolean scoped = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
        String[] projection = scoped
                ? new String[]{flag, MediaStore.MediaColumns.MIME_TYPE,
                        MediaStore.MediaColumns.RELATIVE_PATH}
                : new String[]{flag, MediaStore.MediaColumns.MIME_TYPE};
        try (Cursor c = mContext.getContentResolver().query(uri, projection, null, null, null)) {
            assertNotNull(c);
            assertTrue(c.moveToFirst());
            assertEquals(flag, 1, c.getInt(0));
            assertTrue(c.getString(1).startsWith("audio/"));
            if (scoped) {
                // MediaStore only accepts standard top-level folders from Android 10.
                assertEquals("Ringtones/" + folder + "/", c.getString(2));
            }
        }
        try (ParcelFileDescriptor pfd = mContext.getContentResolver().openFileDescriptor(uri,
                "r")) {
            assertNotNull(pfd);
            assertTrue(pfd.getStatSize() > 512);
        }
    }
}
