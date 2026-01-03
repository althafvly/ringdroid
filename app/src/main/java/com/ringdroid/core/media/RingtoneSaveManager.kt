package com.ringdroid.core.media

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import com.ringdroid.R
import com.ringdroid.core.permissions.PermissionUtils
import com.ringdroid.feature.contact.ChooseContactActivity
import com.ringdroid.soundfile.SoundFile
import com.ringdroid.ui.dialog.DialogController
import com.ringdroid.ui.dialog.FileSaveDialogDefaults
import com.ringdroid.ui.waveform.WaveformView
import java.io.File
import java.io.PrintWriter
import java.io.RandomAccessFile
import java.io.StringWriter

class RingtoneSaveManager(
    private val activity: Activity,
    private val handler: Handler,
    private val callback: Callback,
    private val chooseContactLauncher: ActivityResultLauncher<Intent>?,
    private val dialogController: DialogController,
) {
    interface Callback {
        fun onInfo(infoText: String)

        fun onShowFinalAlert(e: Exception?, message: CharSequence)
    }

    private var saveSoundFileThread: Thread? = null

    fun saveRingtone(
        title: CharSequence,
        soundFile: SoundFile,
        waveformView: WaveformView,
        startPos: Int,
        endPos: Int,
        wasGetContentIntent: Boolean,
        newFileKind: Int,
    ) {
        val startTime = waveformView.pixelsToSeconds(startPos)
        val endTime = waveformView.pixelsToSeconds(endPos)
        val startFrame = waveformView.secondsToFrames(startTime)
        val endFrame = waveformView.secondsToFrames(endTime)
        val duration = (endTime - startTime + 0.5).toInt()

        dialogController.showProgressDialog(
            titleRes = R.string.progress_dialog_saving,
            cancelable = false,
        )

        val values =
            ContentValues().apply {
                put(
                    MediaStore.Audio.Media.IS_RINGTONE,
                    newFileKind == FileSaveDialogDefaults.FILE_KIND_RINGTONE,
                )
                put(
                    MediaStore.Audio.Media.IS_NOTIFICATION,
                    newFileKind == FileSaveDialogDefaults.FILE_KIND_NOTIFICATION,
                )
                put(
                    MediaStore.Audio.Media.IS_ALARM,
                    newFileKind == FileSaveDialogDefaults.FILE_KIND_ALARM,
                )
                put(
                    MediaStore.Audio.Media.IS_MUSIC,
                    newFileKind == FileSaveDialogDefaults.FILE_KIND_MUSIC,
                )
                put(MediaStore.Audio.Media.RELATIVE_PATH, getSubDir(newFileKind))
            }

        saveSoundFileThread =
            Thread {
                    var outPath: String? = makeRingtoneFilename(title, ".m4a", newFileKind)
                    var outUri: Uri? = null
                    if (outPath == null) {
                        handler.post {
                            dialogController.hideProgressDialog()
                            callback.onShowFinalAlert(
                                Exception(),
                                activity.getString(R.string.no_unique_filename),
                            )
                        }
                        return@Thread
                    }
                    var outFile = File(outPath)
                    var fallbackToWAV = false
                    try {
                        if (PermissionUtils.hasMediaAudioPermission(activity)) {
                            values.put(MediaStore.Audio.Media.DISPLAY_NAME, "$title.m4a")
                            values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4a-latm")
                            outUri =
                                activity.contentResolver.insert(
                                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                    values,
                                )
                            soundFile.WriteFile(activity, outUri, startFrame, endFrame - startFrame)
                        } else {
                            soundFile.WriteFile(outFile, startFrame, endFrame - startFrame)
                        }
                    } catch (e: Exception) {
                        if (outFile.exists()) {
                            val status = outFile.delete()
                            Log.d(TAG, "Delete file: $outPath status:$status")
                        }
                        val writer = StringWriter()
                        e.printStackTrace(PrintWriter(writer))
                        Log.e(TAG, "Error: Failed to create $outPath")
                        Log.e(TAG, writer.toString())
                        fallbackToWAV = true
                    }

                    if (fallbackToWAV) {
                        outPath = makeRingtoneFilename(title, ".wav", newFileKind)
                        if (outPath == null) {
                            handler.post {
                                dialogController.hideProgressDialog()
                                callback.onShowFinalAlert(
                                    Exception(),
                                    activity.getString(R.string.no_unique_filename),
                                )
                            }
                            return@Thread
                        }
                        outFile = File(outPath)
                        try {
                            if (PermissionUtils.hasMediaAudioPermission(activity)) {
                                values.put(MediaStore.Audio.Media.DISPLAY_NAME, "$title.wav")
                                values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                                outUri =
                                    activity.contentResolver.insert(
                                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                        values,
                                    )
                                soundFile.WriteWAVFile(
                                    activity,
                                    outUri,
                                    startFrame,
                                    endFrame - startFrame,
                                )
                            } else {
                                soundFile.WriteWAVFile(outFile, startFrame, endFrame - startFrame)
                            }
                        } catch (e: Exception) {
                            handler.post { dialogController.hideProgressDialog() }
                            if (outFile.exists()) {
                                val status = outFile.delete()
                                Log.d(TAG, "Delete file: $outPath status:$status")
                            }
                            handler.post { callback.onInfo(e.toString()) }
                            val errorMessage: CharSequence
                            var exception: Exception? = e
                            if (exception?.message == "No space left on device") {
                                errorMessage = activity.resources.getText(R.string.no_space_error)
                                exception = null
                            } else {
                                errorMessage = activity.resources.getText(R.string.write_error)
                            }
                            val finalException = exception
                            handler.post { callback.onShowFinalAlert(finalException, errorMessage) }
                            return@Thread
                        }
                    }

                    try {
                        val listener = SoundFile.ProgressListener { false }
                        if (PermissionUtils.hasMediaAudioPermission(activity) && outUri != null) {
                            SoundFile.uriExists(activity, outUri)
                        } else {
                            SoundFile.create(outPath, listener)
                        }
                    } catch (e: Exception) {
                        handler.post { dialogController.hideProgressDialog() }
                        Log.e(TAG, "Unexpected error: ${e.message}", e)
                        handler.post { callback.onInfo(e.toString()) }
                        handler.post {
                            callback.onShowFinalAlert(
                                e,
                                activity.resources.getText(R.string.write_error),
                            )
                        }
                        return@Thread
                    }

                    handler.post { dialogController.hideProgressDialog() }

                    val finalOutPath = outPath
                    val finalOutUri = outUri
                    handler.post {
                        afterSavingRingtone(
                            title,
                            finalOutPath,
                            finalOutUri,
                            duration,
                            wasGetContentIntent,
                            newFileKind,
                        )
                    }
                }
                .also { it.start() }
    }

    fun stop() {
        closeThread(saveSoundFileThread)
        dialogController.hideProgressDialog()
    }

    private fun afterSavingRingtone(
        title: CharSequence,
        outPath: String?,
        outUri: Uri?,
        duration: Int,
        wasGetContentIntent: Boolean,
        newFileKind: Int,
    ) {
        var newUri = outUri
        if (PermissionUtils.hasMediaAudioPermission(activity) || outUri == null) {
            if (outPath == null) return
            val outFile = File(outPath)
            val fileSize = outFile.length()
            if (fileSize <= 512) {
                val status = outFile.delete()
                if (status) {
                    handler.post {
                        callback.onShowFinalAlert(
                            Exception(),
                            activity.getText(R.string.too_small_error),
                        )
                    }
                }
                return
            }

            val mimeType =
                when {
                    outPath.endsWith(".m4a") -> "audio/mp4a-latm"
                    outPath.endsWith(".wav") -> "audio/wav"
                    else -> "audio/mpeg"
                }

            val artist = "${activity.resources.getText(R.string.artist_name)}"

            val values =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DATA, outPath)
                    put(MediaStore.MediaColumns.TITLE, title.toString())
                    put(MediaStore.MediaColumns.SIZE, fileSize)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.Audio.Media.ARTIST, artist)
                    put(MediaStore.Audio.Media.DURATION, duration)
                    put(
                        MediaStore.Audio.Media.IS_RINGTONE,
                        newFileKind == FileSaveDialogDefaults.FILE_KIND_RINGTONE,
                    )
                    put(
                        MediaStore.Audio.Media.IS_NOTIFICATION,
                        newFileKind == FileSaveDialogDefaults.FILE_KIND_NOTIFICATION,
                    )
                    put(
                        MediaStore.Audio.Media.IS_ALARM,
                        newFileKind == FileSaveDialogDefaults.FILE_KIND_ALARM,
                    )
                    put(
                        MediaStore.Audio.Media.IS_MUSIC,
                        newFileKind == FileSaveDialogDefaults.FILE_KIND_MUSIC,
                    )
                }

            newUri =
                activity.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        }

        if (newUri == null) {
            callback.onShowFinalAlert(Exception(), activity.getText(R.string.write_error))
            return
        }

        if (wasGetContentIntent) {
            activity.setResult(Activity.RESULT_OK, Intent().apply { data = newUri })
            activity.finish()
            return
        }

        if (newFileKind == FileSaveDialogDefaults.FILE_KIND_NOTIFICATION) {
            dialogController.showNotificationPrompt(
                onConfirm = {
                    RingdroidUtils.setDefaultRingTone(
                        activity,
                        RingtoneManager.TYPE_NOTIFICATION,
                        newUri,
                        true,
                    )
                },
                onDismiss = { activity.finish() },
            )
            return
        }

        dialogController.showAfterSaveActionDialog(
            onMakeDefault = {
                RingdroidUtils.setDefaultRingTone(
                    activity,
                    RingtoneManager.TYPE_RINGTONE,
                    newUri,
                    true,
                )
            },
            onChooseContact = { chooseContactForRingtone(newUri) },
            onDoNothing = { activity.finish() },
        )
    }

    private fun chooseContactForRingtone(uri: Uri) {
        try {
            val intent =
                Intent(Intent.ACTION_EDIT, uri)
                    .setClass(activity, ChooseContactActivity::class.java)
            if (chooseContactLauncher != null) {
                chooseContactLauncher.launch(intent)
            } else {
                activity.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't open Choose Contact window")
        }
    }

    private fun getSubDir(newFileKind: Int): String {
        val saveDir =
            if (PermissionUtils.hasMediaAudioPermission(activity)) {
                Environment.DIRECTORY_RINGTONES
            } else {
                "media/audio"
            }
        val subdir = StringBuilder(saveDir)
        when (newFileKind) {
            FileSaveDialogDefaults.FILE_KIND_MUSIC -> subdir.append("/music/")
            FileSaveDialogDefaults.FILE_KIND_ALARM -> subdir.append("/alarms/")
            FileSaveDialogDefaults.FILE_KIND_NOTIFICATION -> subdir.append("/notifications/")
            FileSaveDialogDefaults.FILE_KIND_RINGTONE -> subdir.append("/ringtones/")
            else -> subdir.append("/others/")
        }
        return subdir.toString()
    }

    private fun makeRingtoneFilename(
        title: CharSequence,
        extension: String,
        newFileKind: Int,
    ): String? {
        var externalRootDir = Environment.getExternalStorageDirectory().path
        if (!externalRootDir.endsWith("/")) {
            externalRootDir += "/"
        }

        var parentdir = externalRootDir + getSubDir(newFileKind)

        val parentDirFile = File(parentdir)
        val status = parentDirFile.mkdirs()
        Log.d(TAG, "Created folder: $parentdir status:$status")

        if (!parentDirFile.isDirectory) {
            parentdir = externalRootDir
        }

        val filename = StringBuilder()
        for (i in title.indices) {
            if (Character.isLetterOrDigit(title[i])) {
                filename.append(title[i])
            }
        }

        var path: String? = null
        for (i in 0 until 100) {
            val testPath =
                if (i > 0) {
                    "$parentdir$filename$i$extension"
                } else {
                    "$parentdir$filename$extension"
                }

            try {
                RandomAccessFile(File(testPath), "r").close()
            } catch (_: Exception) {
                path = testPath
                break
            }
        }

        return path
    }

    private fun closeThread(thread: Thread?) {
        if (thread != null && thread.isAlive) {
            try {
                thread.join()
            } catch (_: InterruptedException) {}
        }
    }

    companion object {
        private val TAG = RingtoneSaveManager::class.java.simpleName
    }
}
