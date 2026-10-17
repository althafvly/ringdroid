package com.ringdroid;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.Manifest;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(AndroidJUnit4.class)
public class RingdroidUtilsTest {
    @Test
    public void permissionContractsExposeExpectedPermissions() {
        assertArrayEquals(
                new String[]{Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS},
                PermissionUtils.getContactPermissions());
        assertEquals(Manifest.permission.RECORD_AUDIO, PermissionUtils.getMicPermission());
        assertEquals(Manifest.permission.READ_MEDIA_AUDIO, PermissionUtils.getStoragePermission());
    }

    @Test
    public void collectionUrisAreAvailable() {
        assertEquals(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                RingdroidUtils.getExternalAudioCollectionUri());
        assertEquals(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_INTERNAL),
                RingdroidUtils.getInternalAudioCollectionUri());
    }

    @Test
    public void fileUrisAndInvalidArgumentsAreHandledSafely() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File source = new File(context.getCacheDir(), "audio.wav");

        assertNull(RingdroidUtils.getFileFromUri(null, Uri.fromFile(source)));
        assertNull(RingdroidUtils.getFileFromUri(context, null));

        File result = RingdroidUtils.getFileFromUri(context, Uri.fromFile(source));
        assertNotNull(result);
        assertEquals(source.getAbsolutePath(), result.getAbsolutePath());
    }

    @Test
    public void nullableViewHelpersAreNoOps() {
        RingdroidUtils.applyStatusBarInsets(null);
        RingdroidUtils.applyNavigationBarInsets(null, 16);
        RingdroidUtils.showKeyboard(null);
        RingdroidUtils.hideKeyboard(null);
    }
}
