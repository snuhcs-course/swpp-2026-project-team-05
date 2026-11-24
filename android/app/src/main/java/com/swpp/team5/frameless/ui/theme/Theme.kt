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
    primary = Color(0xFFAEBBFF),
    onPrimary = Color(0xFF202124),
    secondary = Color(0xFFBCC5DB),
    tertiary = Color(0xFFE3B5CA),
    background = Color(0xFF202124),
    surface = Color(0xFF202124),
    surfaceVariant = Color(0xFF2B2D31),
    onBackground = Color(0xFFF4F4F5),
    onSurface = Color(0xFFF4F4F5),
    onSurfaceVariant = Color(0xFFB7BAC1),
    outline = Color(0xFF3B3E44),
    error = Color(0xFFFFB4AB)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4F5F9B),
    secondary = Color(0xFF5F677A),
    tertiary = Color(0xFF8D5D78),
    background = Color.White,
    surface = Color.White,
    surfaceVariant = Color(0xFFF3F4F6),
    outline = Color(0xFFD8DCE3),
    onBackground = Color(0xFF161A23),
    onSurface = Color(0xFF161A23)

    /* Other default colors to override
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    */
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
