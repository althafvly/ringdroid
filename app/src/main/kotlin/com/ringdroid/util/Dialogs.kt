package com.ringdroid.util

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.view.View
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.ringdroid.R

/** Creates a dialog for [view] with a transparent window, so the layout draws its own card. */
fun createDialog(
    context: Context,
    view: View,
    cancelable: Boolean = true,
): AlertDialog =
    AlertDialog.Builder(context).setView(view).setCancelable(cancelable).create().dismissOnDestroy().apply {
        window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
    }

/** Dismisses the dialog when its activity is destroyed, so recreating the activity doesn't leak its window. */
fun <D : Dialog> D.dismissOnDestroy(): D =
    apply {
        val owner =
            generateSequence(context) {
                (it as? ContextWrapper)?.baseContext
            }.firstNotNullOfOrNull { it as? LifecycleOwner }
        owner?.lifecycle?.addObserver(
            LifecycleEventObserver { _, event ->
                if (event ==
                    Lifecycle.Event.ON_DESTROY
                ) {
                    dismiss()
                }
            },
        )
    }

/** Shows an error that closes [activity] when dismissed. */
fun showFinalAlert(
    activity: Activity,
    message: CharSequence,
) {
    if (activity.isFinishing || activity.isDestroyed) {
        return
    }
    AlertDialog
        .Builder(activity)
        .setTitle(R.string.alert_title_failure)
        .setMessage(message)
        .setPositiveButton(R.string.alert_ok_button) { _, _ -> activity.finish() }
        .setCancelable(false)
        .show()
        .dismissOnDestroy()
}

/** Shows an error that leaves the current screen open. */
fun showErrorAlert(
    activity: Activity,
    messageId: Int,
) {
    if (activity.isFinishing || activity.isDestroyed) {
        return
    }
    AlertDialog
        .Builder(activity)
        .setTitle(R.string.alert_title_failure)
        .setMessage(messageId)
        .setPositiveButton(R.string.alert_ok_button, null)
        .show()
        .dismissOnDestroy()
}
