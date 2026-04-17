package com.stockwatchdog.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.stockwatchdog.app.data.prefs.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF16A34A),
    onPrimary = Color.White,
    secondary = Color(0xFF2563EB),
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF22C55E),
    onPrimary = Color(0xFF062E14),
    secondary = Color(0xFF60A5FA),
    background = Color(0xFF0B1220),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFE5E7EB)
)

val PositiveGreen = Color(0xFF16A34A)
val NegativeRed = Color(0xFFDC2626)

@Composable
fun StockWatchdogTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val context = LocalContext.current
    val useDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colors = when {
        useDynamic && darkTheme -> dynamicDarkColorScheme(context)
        useDynamic && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                colors.background.luminance() > 0.5f
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content
    )
}
