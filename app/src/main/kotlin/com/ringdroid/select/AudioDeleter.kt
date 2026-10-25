package com.ringdroid.select

import android.app.Activity
import android.app.AlertDialog
import android.app.RecoverableSecurityException
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.ringdroid.R
import com.ringdroid.databinding.DialogConfirmDeleteBinding
import com.ringdroid.util.RingdroidUtils
import java.io.File

/**
 * Deletes audio files, asking for the system's consent where Android requires it. Must be created
 * while the activity is being initialized.
 */
internal class AudioDeleter(
    private val activity: ComponentActivity,
    private val onDeleted: () -> Unit,
) {
    private var pendingUri: Uri? = null
    private val consentLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val pending = pendingUri
            pendingUri = null
            if (result.resultCode == Activity.RESULT_OK) {
                if (pending != null) deleteFromMediaStore(pending) else onDeleted()
            }
        }

    fun delete(
        uri: Uri,
        filePath: String?,
        title: String?,
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }
        // From Android 11 the system shows its own confirmation.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val request = MediaStore.createDeleteRequest(activity.contentResolver, listOf(uri))
                consentLauncher.launch(IntentSenderRequest.Builder(request).build())
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Failed to request delete of $uri", e)
                showFailed()
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to request delete of $uri", e)
                showFailed()
            }
            return
        }

        val binding = DialogConfirmDeleteBinding.inflate(activity.layoutInflater)
        if (!title.isNullOrEmpty()) {
            binding.deleteMessage.text = activity.getString(R.string.confirm_delete_message, title)
        }
        val dialog =
            AlertDialog
                .Builder(activity)
                .setView(binding.root)
                .setCancelable(true)
                .create()
        RingdroidUtils.setTransparentBackground(dialog)
        binding.btnCancel.setOnClickListener { dialog.dismiss() }
        binding.btnConfirmDelete.setOnClickListener {
            dialog.dismiss()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && filePath != null) {
                val file = File(filePath)
                if (!file.delete()) {
                    Log.w(TAG, "Failed to delete $file")
                }
            }
            deleteFromMediaStore(uri)
        }
        dialog.show()
    }

    private fun deleteFromMediaStore(uri: Uri) {
        try {
            if (activity.contentResolver.delete(uri, null, null) == 0) {
                showFailed()
            } else {
                onDeleted()
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                pendingUri = uri
                consentLauncher.launch(IntentSenderRequest.Builder(e.userAction.actionIntent).build())
            } else {
                showFailed()
            }
        } catch (e: IllegalArgumentException) {
            showFailed()
        } catch (e: IllegalStateException) {
            showFailed()
        } catch (e: UnsupportedOperationException) {
            showFailed()
        }
    }

    private fun showFailed() {
        Toast.makeText(activity, R.string.delete_failed, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val TAG = "AudioDeleter"
    }
}
