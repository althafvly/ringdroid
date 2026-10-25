package com.ringdroid.testing

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry

/** Grants runtime permissions to the app under test. */
object TestPermissions {
    private const val GRANT_TIMEOUT_MS = 5000L
    private const val GRANT_POLL_MS = 100L

    fun grant(vararg permissions: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        for (permission in permissions) {
            ParcelFileDescriptor
                .AutoCloseInputStream(
                    instrumentation.uiAutomation.executeShellCommand(
                        "pm grant $packageName $permission",
                    ),
                ).use { it.readBytes() }
        }
    }

    /** Before Android 10 writing shared storage needs both storage permissions. */
    fun grantStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return
        }
        grant(READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE)
        val deadline = SystemClock.uptimeMillis() + GRANT_TIMEOUT_MS
        while (!Environment.getExternalStorageDirectory().canWrite() &&
            SystemClock.uptimeMillis() < deadline
        ) {
            SystemClock.sleep(GRANT_POLL_MS)
        }
    }
}
