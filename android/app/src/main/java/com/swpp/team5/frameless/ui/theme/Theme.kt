package com.swpp.team5.frameless.ui.theme

import android.app.Activity
import android.os.Build
import android.view.Window
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFF4F4F5),
    onPrimary = Color(0xFF202124),
    primaryContainer = Color(0xFF35373C),
    onPrimaryContainer = Color(0xFFF4F4F5),
    secondary = Color(0xFFC5C7CC),
    onSecondary = Color(0xFF202124),
    secondaryContainer = Color(0xFF303237),
    onSecondaryContainer = Color(0xFFF4F4F5),
    tertiary = Color(0xFFC5C7CC),
    onTertiary = Color(0xFF202124),
    tertiaryContainer = Color(0xFF303237),
    onTertiaryContainer = Color(0xFFF4F4F5),
    inverseSurface = Color(0xFFF4F4F5),
    inverseOnSurface = Color(0xFF202124),
    inversePrimary = Color(0xFF202124),
    background = Color(0xFF202124),
    surface = Color(0xFF202124),
    surfaceVariant = Color(0xFF2B2D31),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFF191A1D),
    surfaceContainerLow = Color(0xFF242629),
    surfaceContainer = Color(0xFF2B2D31),
    surfaceContainerHigh = Color(0xFF303237),
    surfaceContainerHighest = Color(0xFF35373C),
    onBackground = Color(0xFFF4F4F5),
    onSurface = Color(0xFFF4F4F5),
    onSurfaceVariant = Color(0xFFB7BAC1),
    outline = Color(0xFF3B3E44),
    outlineVariant = Color(0xFF33363B),
    error = Color(0xFFFFB4AB)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF202124),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDEEF0),
    onPrimaryContainer = Color(0xFF202124),
    secondary = Color(0xFF565A62),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0F1F3),
    onSecondaryContainer = Color(0xFF202124),
    tertiary = Color(0xFF565A62),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF0F1F3),
    onTertiaryContainer = Color(0xFF202124),
    inverseSurface = Color(0xFF2B2D31),
    inverseOnSurface = Color(0xFFF4F4F5),
    inversePrimary = Color(0xFFF4F4F5),
    background = Color.White,
    surface = Color.White,
    surfaceVariant = Color(0xFFF3F4F6),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFB),
    surfaceContainer = Color(0xFFF3F4F6),
    surfaceContainerHigh = Color(0xFFEEEFF1),
    surfaceContainerHighest = Color(0xFFE8E9EC),
    outline = Color(0xFFD8DCE3),
    outlineVariant = Color(0xFFE6E8EC),
    onBackground = Color(0xFF161A23),
    onSurface = Color(0xFF161A23),
    onSurfaceVariant = Color(0xFF62666F)
)

@Composable
fun FrameLESSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            updateSystemBarColors(window, colorScheme.background)
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Suppress("DEPRECATION")
private fun updateSystemBarColors(window: Window, background: Color) {
    window.statusBarColor = Color.Transparent.toArgb()
    window.navigationBarColor = background.toArgb()
}
