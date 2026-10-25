package com.ringdroid.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ringdroid.dialog.FileSaveDialog
import com.ringdroid.soundfile.SoundFile
import com.ringdroid.soundfile.TestAudio
import com.ringdroid.testing.TestPermissions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RingtoneSaverTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var input: File
    private lateinit var soundFile: SoundFile

    @Before
    fun setUp() {
        TestPermissions.grantStorage()
        input = TestAudio.writeWav(File(context.cacheDir, "saver_input.wav"), 1, TestAudio.ramp(TestAudio.SAMPLE_RATE))
        soundFile = TestAudio.load(input)
    }

    @After
    fun tearDown() {
        soundFile.release()
        input.delete()
    }

    @Test
    fun savesEachFileKindWithItsFlagsAndFolder() {
        val kinds =
            listOf(
                Triple(FileSaveDialog.FILE_KIND_MUSIC, MediaStore.Audio.Media.IS_MUSIC, "music"),
                Triple(FileSaveDialog.FILE_KIND_ALARM, MediaStore.Audio.Media.IS_ALARM, "alarms"),
                Triple(FileSaveDialog.FILE_KIND_NOTIFICATION, MediaStore.Audio.Media.IS_NOTIFICATION, "notifications"),
                Triple(FileSaveDialog.FILE_KIND_RINGTONE, MediaStore.Audio.Media.IS_RINGTONE, "ringtones"),
            )
        for ((kind, flag, folder) in kinds) {
            val uri =
                RingtoneSaver(
                    context,
                    soundFile,
                    1.0f,
                    kind,
                ).save("RingtoneSaverTest" + System.nanoTime(), 0, 20, 464)
            try {
                assertSaved(uri, flag, folder)
            } finally {
                context.contentResolver.delete(uri, null, null)
            }
        }
    }

    private fun assertSaved(
        uri: Uri,
        flag: String,
        folder: String,
    ) {
        val scoped = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val projection =
            if (scoped) {
                arrayOf(
                    flag,
                    MediaStore.MediaColumns.MIME_TYPE,
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    MediaStore.MediaColumns.IS_PENDING,
                )
            } else {
                arrayOf(flag, MediaStore.MediaColumns.MIME_TYPE)
            }
        context.contentResolver.query(uri, projection, null, null, null).use { c ->
            assertNotNull(c)
            assertTrue(c!!.moveToFirst())
            assertEquals(flag, 1, c.getInt(0))
            assertTrue(c.getString(1).startsWith("audio/"))
            if (scoped) {
                // MediaStore only accepts standard top-level folders from Android 10.
                assertEquals("Ringtones/$folder/", c.getString(2))
                assertEquals("finished files are visible", 0, c.getInt(3))
            }
        }
        context.contentResolver.openFileDescriptor(uri, "r").use { pfd ->
            assertNotNull(pfd)
            assertTrue(pfd!!.statSize > 512)
        }
    }
}
