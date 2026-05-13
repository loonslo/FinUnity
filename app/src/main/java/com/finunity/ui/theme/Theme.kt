package com.finunity.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// FinUnity Design Tokens - 简洁克制
private val FinPrimary = Color(0xFF4CAF50)  // 有知有行风格浅绿
private val FinSecondary = Color(0xFF4B8C5E)  // 中绿
private val FinProfit = Color(0xFF00A86B)  // 盈绿色
private val FinLoss = Color(0xFFE53935)   // 亏红色
private val FinGray1 = Color(0xFF1F2933)   // 深灰（主文字）
private val FinGray2 = Color(0xFF6B7280)   // 中灰（次文字）
private val FinGray3 = Color(0xFF9CA3AF)   // 浅灰（辅助）
private val FinBg = Color(0xFFF7F8FA)       // 页面背景
private val FinSurface = Color(0xFFFFFFFF) // 卡片背景

private val LightColorScheme = lightColorScheme(
    primary = FinPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F5E9),
    onPrimaryContainer = FinPrimary,
    secondary = Color(0xFF81C784),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F5E9),
    onSecondaryContainer = FinPrimary,
    tertiary = Color(0xFF2563EB),
    onTertiary = Color.White,
    error = FinLoss,
    onError = Color.White,
    errorContainer = Color(0xFFFEECEA),
    onErrorContainer = FinLoss,
    background = FinBg,
    onBackground = FinGray1,
    surface = FinSurface,
    onSurface = FinGray1,
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = FinGray2,
    outline = Color(0xFFE5E7EB),
    inverseSurface = FinGray1,
    inverseOnSurface = Color.White,
    inversePrimary = Color(0xFF86EFAC),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF003300),
    primaryContainer = Color(0xFF2E7D32),
    onPrimaryContainer = Color(0xFFE8F5E9),
    secondary = Color(0xFFA5D6A7),
    onSecondary = Color(0xFF003300),
    tertiary = Color(0xFF4DB6AC),
    onTertiary = Color(0xFF003330),
    error = Color(0xFFEF5350),
    onError = Color(0xFF690005),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFF9E9E9E),
)

// Typography - 清晰层级
val FinTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        color = FinGray1,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = FinGray1
    ),
    displaySmall = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = FinGray1
    ),
    headlineLarge = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        color = FinGray1
    ),
    headlineMedium = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        color = FinGray1
    ),
    headlineSmall = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = FinGray1
    ),
    titleLarge = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        color = FinGray1
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        color = FinGray1
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = FinGray1
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        color = FinGray1
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        color = FinGray2
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = FinGray2
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = FinGray2
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = FinGray3
    ),
    labelSmall = TextStyle(
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = FinGray3
    )
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
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FinTypography,
        content = content
    )
}

// 通用颜色扩展
object FinColors {
    val Profit = FinProfit
    val Loss = FinLoss
    val Muted = FinGray3
    val Primary = FinPrimary
}