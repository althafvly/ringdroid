package com.ringdroid.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current

    val colors =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(context)
        } else {
            darkColorScheme()
        }

    MaterialTheme(colorScheme = colors, content = content)
}
