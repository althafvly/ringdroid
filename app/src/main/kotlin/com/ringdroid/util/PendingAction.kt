package com.ringdroid.util

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.os.BundleCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * State for an action waiting on another screen (a picker, a permission or a consent prompt) that
 * must survive the activity being recreated or the process being killed. Must be created while
 * the activity is being initialized; values are restored when the activity is created.
 */
internal class PendingAction(activity: ComponentActivity, key: String) {
    var uri: Uri? = null
    var type = 0

    init {
        val registry = activity.savedStateRegistry
        registry.registerSavedStateProvider(key) {
            Bundle().apply {
                putParcelable(URI, uri)
                putInt(TYPE, type)
            }
        }
        activity.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onCreate(owner: LifecycleOwner) {
                    registry.consumeRestoredStateForKey(key)?.let {
                        uri = BundleCompat.getParcelable(it, URI, Uri::class.java)
                        type = it.getInt(TYPE)
                    }
                }
            },
        )
    }

    private companion object {
        const val URI = "uri"
        const val TYPE = "type"
    }
}
