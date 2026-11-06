package com.moustayt.cloudvideo.generator.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** NeoVids brand palette. */
object Brand {
    val Violet = Color(0xFF7C5CFF)
    val Cyan = Color(0xFF22D3EE)
    val Pink = Color(0xFFF472B6)
    val Night = Color(0xFF0E0B24)
    val Gold = Color(0xFFFFD36E)

    val gradient = Brush.linearGradient(listOf(Cyan, Violet, Pink))
    val horizontal = Brush.horizontalGradient(listOf(Violet, Pink))
}

private val Dark = darkColorScheme(
    primary = Color(0xFFA996FF), onPrimary = Color(0xFF1C0D63),
    primaryContainer = Color(0xFF3A2A8C), onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFF5EE3F5), onSecondary = Color(0xFF00363D),
    secondaryContainer = Color(0xFF123F4A), onSecondaryContainer = Color(0xFFB9F3FB),
    tertiary = Color(0xFFFF9BCF), onTertiary = Color(0xFF5B1140),
    background = Color(0xFF0B0A17), onBackground = Color(0xFFEDEAF7),
    surface = Color(0xFF0B0A17), onSurface = Color(0xFFEDEAF7),
    surfaceVariant = Color(0xFF221F35), onSurfaceVariant = Color(0xFFB8B2D3),
    surfaceContainerLowest = Color(0xFF08070F),
    surfaceContainerLow = Color(0xFF12101F),
    surfaceContainer = Color(0xFF171528),
    surfaceContainerHigh = Color(0xFF1E1B33),
    surfaceContainerHighest = Color(0xFF26223E),
    outline = Color(0xFF4A4566), outlineVariant = Color(0xFF2E2A45),
    error = Color(0xFFFF8A9A), errorContainer = Color(0xFF4A1020), onErrorContainer = Color(0xFFFFD9DE),
)

private val Light = lightColorScheme(
    primary = Color(0xFF5B3CE0), onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DEFF), onPrimaryContainer = Color(0xFF1C0D63),
    secondary = Color(0xFF00839A), secondaryContainer = Color(0xFFCDF5FB), onSecondaryContainer = Color(0xFF00363D),
    tertiary = Color(0xFFC0287F),
    background = Color(0xFFFAF8FF), onBackground = Color(0xFF1A1830),
    surface = Color(0xFFFAF8FF), onSurface = Color(0xFF1A1830),
    surfaceVariant = Color(0xFFE9E5F7), onSurfaceVariant = Color(0xFF55506E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F1FD),
    surfaceContainer = Color(0xFFEFEBFB),
    surfaceContainerHigh = Color(0xFFE9E5F7),
    surfaceContainerHighest = Color(0xFFE3DEF3),
    outline = Color(0xFF8C86A8), outlineVariant = Color(0xFFD4CFE8),
)

private val NeoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private fun typography(): Typography {
    val t = Typography()
    return t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun NeoTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && mode == "dynamic" ->
            if (isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, shapes = NeoShapes, typography = typography(), content = content)
}
