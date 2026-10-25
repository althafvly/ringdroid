package com.ringdroid.util

import android.os.Handler
import android.os.Looper

/**
 * Delivers events from background work to a listener on the main thread. Events posted while no
 * listener is attached (for example while an activity is being recreated) are queued.
 */
internal class ListenerEvents<L : Any> {
    private val handler = Handler(Looper.getMainLooper())
    private val pending = mutableListOf<(L) -> Unit>()

    var listener: L? = null
        set(value) {
            field = value
            if (value != null) {
                val events = pending.toList()
                pending.clear()
                events.forEach { it(value) }
            }
        }

    /** Runs [event] on the main thread with the listener, or queues it until one is attached. */
    fun post(event: (L) -> Unit) {
        handler.post {
            val l = listener
            if (l != null) event(l) else pending.add(event)
        }
    }

    /** Runs [block] on the main thread, in order with the posted events. */
    fun runOnMain(block: () -> Unit) {
        handler.post(block)
    }

    /** Runs [event] on the main thread only if a listener is attached then; for progress. */
    fun postIfAttached(event: (L) -> Unit) {
        handler.post { listener?.let(event) }
    }
}
