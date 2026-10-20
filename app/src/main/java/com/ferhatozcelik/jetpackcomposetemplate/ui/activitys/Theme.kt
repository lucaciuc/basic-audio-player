package com.ferhatozcelik.jetpackcomposetemplate.ui.activitys

import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily

/**
 * Pixel look: Material You colors taken from the wallpaper (Android 12+), light or dark
 * with the system, and the Google Sans fonts on devices that ship them.
 */
@Composable
fun PixelTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> FallbackDark
        else -> FallbackLight
    }
    MaterialTheme(colorScheme = colors, typography = PixelTypography, content = content)
}

/** Pixel phones ship Google Sans as named system fonts; other devices keep their default. */
private fun systemFont(name: String): FontFamily {
    val typeface = Typeface.create(name, Typeface.NORMAL)
    return if (typeface == Typeface.DEFAULT) FontFamily.Default else FontFamily(typeface)
}

private val PixelTypography = Typography().run {
    val display = systemFont("google-sans")
    val text = systemFont("google-sans-text")
    copy(
        displayLarge = displayLarge.copy(fontFamily = display),
        displayMedium = displayMedium.copy(fontFamily = display),
        displaySmall = displaySmall.copy(fontFamily = display),
        headlineLarge = headlineLarge.copy(fontFamily = display),
        headlineMedium = headlineMedium.copy(fontFamily = display),
        headlineSmall = headlineSmall.copy(fontFamily = display),
        titleLarge = titleLarge.copy(fontFamily = display),
        titleMedium = titleMedium.copy(fontFamily = display),
        titleSmall = titleSmall.copy(fontFamily = display),
        bodyLarge = bodyLarge.copy(fontFamily = text),
        bodyMedium = bodyMedium.copy(fontFamily = text),
        bodySmall = bodySmall.copy(fontFamily = text),
        labelLarge = labelLarge.copy(fontFamily = text),
        labelMedium = labelMedium.copy(fontFamily = text),
        labelSmall = labelSmall.copy(fontFamily = text),
    )
}

// Before Android 12 there are no wallpaper colors. These are the Material tonal-spot
// schemes for #1B6EF3, the seed Pixel uses when the wallpaper gives none.
private val FallbackLight = lightColorScheme(
    primary = Color(0xFF475D92),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E2FF),
    onPrimaryContainer = Color(0xFF2F4578),
    inversePrimary = Color(0xFFB0C6FF),
    secondary = Color(0xFF575E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE2F9),
    onSecondaryContainer = Color(0xFF404659),
    tertiary = Color(0xFF725572),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFDD7FA),
    onTertiaryContainer = Color(0xFF593D59),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44464F),
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF1F0F7),
    outline = Color(0xFF757780),
    outlineVariant = Color(0xFFC5C6D0),
    surfaceBright = Color(0xFFFAF8FF),
    surfaceDim = Color(0xFFDAD9E0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F3FA),
    surfaceContainer = Color(0xFFEEEDF4),
    surfaceContainerHigh = Color(0xFFE8E7EF),
    surfaceContainerHighest = Color(0xFFE2E2E9),
)

private val FallbackDark = darkColorScheme(
    primary = Color(0xFFB0C6FF),
    onPrimary = Color(0xFF152E60),
    primaryContainer = Color(0xFF2F4578),
    onPrimaryContainer = Color(0xFFD9E2FF),
    inversePrimary = Color(0xFF475D92),
    secondary = Color(0xFFC0C6DC),
    onSecondary = Color(0xFF2A3042),
    secondaryContainer = Color(0xFF404659),
    onSecondaryContainer = Color(0xFFDCE2F9),
    tertiary = Color(0xFFE0BBDD),
    onTertiary = Color(0xFF412742),
    tertiaryContainer = Color(0xFF593D59),
    onTertiaryContainer = Color(0xFFFDD7FA),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44464F),
    onSurfaceVariant = Color(0xFFC5C6D0),
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF2F3036),
    outline = Color(0xFF8F9099),
    outlineVariant = Color(0xFF44464F),
    surfaceBright = Color(0xFF38393F),
    surfaceDim = Color(0xFF121318),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF1A1B20),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33343A),
)
