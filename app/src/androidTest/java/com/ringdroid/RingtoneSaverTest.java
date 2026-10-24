package com.ringdroid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.ringdroid.soundfile.SoundFile;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

@RunWith(AndroidJUnit4.class)
public class RingtoneSaverTest {
    @Test
    public void savesRingtoneToMediaStore() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File input = new File(context.getCacheDir(), "saver_input.wav");
        try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext().getAssets()
                .open("test_audio.wav"); FileOutputStream out = new FileOutputStream(input)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
        SoundFile soundFile = SoundFile.create(context.getCacheDir(), input.getPath(),
                frac -> false);
        assertNotNull(soundFile);

        String title = "RingtoneSaverTest" + System.currentTimeMillis();
        RingtoneSaver saver = new RingtoneSaver(context, soundFile, 1.0f,
                FileSaveDialog.FILE_KIND_RINGTONE);
        Uri uri = saver.save(title, 0, 20, 464);
        assertNotNull(uri);
        try (Cursor c = context.getContentResolver().query(uri,
                new String[]{MediaStore.Audio.Media.IS_RINGTONE, MediaStore.MediaColumns.MIME_TYPE},
                null, null, null)) {
            assertNotNull(c);
            assertTrue(c.moveToFirst());
            assertEquals(1, c.getInt(0));
            assertTrue(c.getString(1).startsWith("audio/"));
            try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri,
                    "r")) {
                assertNotNull(pfd);
                assertTrue(pfd.getStatSize() > 512);
            }
        } finally {
            context.getContentResolver().delete(uri, null, null);
            soundFile.release();
            input.delete();
        }
    }
}
