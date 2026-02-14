package com.ringdroid.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val darkColors =
        darkColorScheme(
            primary = Cyan80,
            onPrimary = Color.Black,
            primaryContainer = Cyan40,
            onPrimaryContainer = Color.White,
            secondary = Purple80,
            onSecondary = Color.White,
            secondaryContainer = Purple40,
            onSecondaryContainer = Color.White,
            background = DarkBackground,
            onBackground = LightOnDark,
            surface = DarkSurface,
            onSurface = LightOnDark,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = GreyOnDark,
            error = ErrorRed,
            onError = Color.White,
        )

    MaterialTheme(colorScheme = darkColors, content = content)
}
