package com.finunity.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// 简洁绿色系主题
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1B5E20),      // 深绿色
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4CAF50),  // 中绿色
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF81C784),    // 浅绿色
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F5E9),  // 极浅绿
    onSecondaryContainer = Color(0xFF1B5E20),
    tertiary = Color(0xFF00897B),      // 青色
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB2DFDB),
    onTertiaryContainer = Color(0xFF004D40),
    error = Color(0xFFE53935),
    onError = Color.White,
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = Color(0xFFB71C1C),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1C1C1C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1C1C),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF757575),
    outline = Color(0xFFBDBDBD),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF003300),
    primaryContainer = Color(0xFF2E7D32),
    onPrimaryContainer = Color(0xFFE8F5E9),
    secondary = Color(0xFFA5D6A7),
    onSecondary = Color(0xFF003300),
    secondaryContainer = Color(0xFF388E3C),
    onSecondaryContainer = Color(0xFFE8F5E9),
    tertiary = Color(0xFF4DB6AC),
    onTertiary = Color(0xFF003330),
    tertiaryContainer = Color(0xFF00695C),
    onTertiaryContainer = Color(0xFFB2DFDB),
    error = Color(0xFFEF5350),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFFC62828),
    onErrorContainer = Color(0xFFFFEBEE),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFF9E9E9E),
    outline = Color(0xFF616161),
)

@Composable
fun FinUnityTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
