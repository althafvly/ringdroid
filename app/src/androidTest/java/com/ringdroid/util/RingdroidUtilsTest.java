package com.ringdroid.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.Manifest;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
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
        assertEquals(Manifest.permission.RECORD_AUDIO, PermissionUtils.getMicPermission());
        String[] storagePermissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            storagePermissions = new String[]{Manifest.permission.READ_MEDIA_AUDIO};
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            storagePermissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        } else {
            storagePermissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE};
        }
        assertArrayEquals(storagePermissions, PermissionUtils.getStoragePermissions());
    }

    @Test
    public void collectionUrisAreAvailable() {
        Uri external = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        assertEquals(external, RingdroidUtils.getExternalAudioCollectionUri());
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
