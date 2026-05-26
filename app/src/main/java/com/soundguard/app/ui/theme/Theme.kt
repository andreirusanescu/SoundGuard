package com.soundguard.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val LightColors = lightColorScheme(
    primary = BrandIndigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = BrandIndigoDeep,
    secondary = BrandCyan,
    onSecondary = Color.White,
    tertiary = BrandViolet,
    onTertiary = Color.White,
    error = SeverityCritical,
    onError = Color.White,
    background = SurfaceLight,
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F5F9)
)

private val DarkColors = darkColorScheme(
    primary = BrandIndigo,
    onPrimary = Color.White,
    primaryContainer = BrandIndigoDeep,
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = BrandCyan,
    onSecondary = Color.Black,
    tertiary = BrandViolet,
    onTertiary = Color.White,
    error = SeverityCritical,
    onError = Color.White,
    background = SurfaceDark,
    surface = SurfaceDarkElevated,
    surfaceVariant = SurfaceDarkSubtle
)

// Maximum-contrast palette — black/white surfaces, severity colours boosted.
private val HighContrastLight = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFEB3B),
    onPrimaryContainer = Color.Black,
    secondary = Color.Black,
    onSecondary = Color.White,
    tertiary = Color.Black,
    onTertiary = Color.White,
    error = Color(0xFFCC0000),
    onError = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFEEEEEE),
    onSurfaceVariant = Color.Black,
    outline = Color.Black
)

private val HighContrastDark = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFFFFEB3B),
    onPrimaryContainer = Color.Black,
    secondary = Color.White,
    onSecondary = Color.Black,
    tertiary = Color.White,
    onTertiary = Color.Black,
    error = Color(0xFFFF5252),
    onError = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF111111),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF222222),
    onSurfaceVariant = Color.White,
    outline = Color.White
)

@Composable
fun SoundGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    highContrast: Boolean = false,
    largeText: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = when {
        highContrast && darkTheme -> HighContrastDark
        highContrast -> HighContrastLight
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    val density = if (largeText) {
        val current = LocalDensity.current
        Density(density = current.density, fontScale = current.fontScale * 1.30f)
    } else LocalDensity.current

    CompositionLocalProvider(LocalDensity provides density) {
        MaterialTheme(
            colorScheme = colors,
            typography = SoundGuardTypography,
            content = content
        )
    }
}
