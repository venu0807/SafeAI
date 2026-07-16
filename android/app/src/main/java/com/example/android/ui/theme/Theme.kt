package com.example.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    secondary = PrimaryHover,
    tertiary = SuccessGreen,
    background = BackgroundDark,
    surface = BackgroundDark, // Glassmorphism will be handled via composable modifiers
    error = ErrorRed,
    onPrimary = TextMain,
    onSecondary = TextMain,
    onTertiary = TextMain,
    onBackground = TextMain,
    onSurface = TextMain,
    onError = TextMain
)

// We only enforce dark mode for the dashboard aesthetic
private val LightColorScheme = DarkColorScheme

@Composable
fun SafeGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Force our premium colors
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> DarkColorScheme // Enforce Dark Theme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = BackgroundDark.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
