package com.ringdroid.activity

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat

/**
 * Opens the editor from the list screen. When the list was opened to pick content for another app,
 * the saved file is handed straight back. Must be created while the activity is being initialized.
 */
internal class EditorLauncher(
    private val activity: ComponentActivity,
) {
    private val wasGetContentIntent: Boolean
        get() = activity.intent.action == Intent.ACTION_GET_CONTENT

    private val launcher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && wasGetContentIntent) {
                activity.setResult(Activity.RESULT_OK, result.data)
                activity.finish()
            }
        }

    /** Opens [data] in the editor, or starts a recording when it's null. */
    fun open(data: Uri?) {
        try {
            launcher.launch(RingdroidEditActivity.newIntent(activity, data, wasGetContentIntent))
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Couldn't start editor", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Couldn't start editor", e)
        }
    }

    /** Opens the editor for audio viewed or shared from another app; the editor copies it if needed. */
    fun openIncoming(intent: Intent) {
        val uri =
            when (intent.action) {
                Intent.ACTION_VIEW -> intent.data
                Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                else -> null
            }
        if (uri != null) {
            open(uri)
        }
    }

    private companion object {
        const val TAG = "EditorLauncher"
    }
}
