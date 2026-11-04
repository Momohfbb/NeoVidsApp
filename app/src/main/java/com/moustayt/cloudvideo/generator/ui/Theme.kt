package com.moustayt.cloudvideo.generator.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Dark = darkColorScheme(
    primary = Color(0xFFB69DFF), onPrimary = Color(0xFF2B0A73),
    secondary = Color(0xFF5CE1E6), tertiary = Color(0xFFFF8AD8),
    background = Color(0xFF0E0B16), surface = Color(0xFF14101F),
    surfaceVariant = Color(0xFF241C38),
)
private val Light = lightColorScheme(
    primary = Color(0xFF5B3CC4), secondary = Color(0xFF00838A), tertiary = Color(0xFFB0289A),
    background = Color(0xFFFCF9FF), surface = Color(0xFFFCF9FF),
)

@Composable
fun NeoTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && mode == "dynamic" ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
