package com.ringdroid.util

import android.Manifest
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RingdroidUtilsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun permissionsMatchTheAndroidVersion() {
        assertEquals(Manifest.permission.RECORD_AUDIO, PermissionUtils.micPermission)
        val storagePermissions =
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    listOf(
                        Manifest.permission.READ_MEDIA_AUDIO,
                    )
                }

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    listOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                    )
                }

                else -> {
                    listOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    )
                }
            }
        assertEquals(storagePermissions, PermissionUtils.storagePermissions.toList())
    }

    @Test
    fun collectionUrisAreAvailable() {
        val external =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
        assertEquals(external, RingdroidUtils.externalAudioCollectionUri)
        assertEquals(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_INTERNAL),
            RingdroidUtils.internalAudioCollectionUri,
        )
    }

    @Test
    fun fileUrisAndMissingArgumentsAreHandled() {
        val source = File(context.cacheDir, "audio.wav")
        assertNull(RingdroidUtils.getFileFromUri(null, Uri.fromFile(source)))
        assertNull(RingdroidUtils.getFileFromUri(context, null))
        assertEquals(
            source.absolutePath,
            RingdroidUtils.getFileFromUri(context, Uri.fromFile(source))?.absolutePath,
        )
    }

    @Test
    fun nullableViewHelpersAreNoOps() {
        RingdroidUtils.applyStatusBarInsets(null)
        RingdroidUtils.applyNavigationBarInsets(null, 16)
        RingdroidUtils.showKeyboard(null)
        RingdroidUtils.hideKeyboard(null)
    }
}
