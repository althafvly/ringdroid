package com.ringdroid.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListenerEventsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun eventsWaitForAListenerAndKeepTheirOrder() {
        val events = ListenerEvents<MutableList<String>>()
        val received = mutableListOf<String>()
        events.post { it.add("first") }
        events.runOnMain { received.add("main") }
        events.post { it.add("second") }
        instrumentation.waitForIdleSync()
        assertEquals(listOf("main"), received)

        instrumentation.runOnMainSync { events.listener = received }
        assertEquals(listOf("main", "first", "second"), received)
    }

    @Test
    fun progressIsDroppedWithoutAListener() {
        val events = ListenerEvents<MutableList<String>>()
        val received = mutableListOf<String>()
        events.postIfAttached { it.add("dropped") }
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync { events.listener = received }
        events.postIfAttached { it.add("delivered") }
        instrumentation.waitForIdleSync()
        assertEquals(listOf("delivered"), received)
    }
}
