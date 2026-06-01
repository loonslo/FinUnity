package com.finunity.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// FinUnity Design Tokens - 简洁克制
private val FinPrimary = Color(0xFF166B45)  // 按钮/主操作绿，保证白字可读
private val FinSecondary = Color(0xFF1E8E5A)  // 点缀绿
private val FinProfit = Color(0xFF1E8E5A)  // 盈绿色
private val FinLoss = Color(0xFFE53935)   // 亏红色
// 统一灰度体系（4 级）
private val FinGray1 = Color(0xFF111827)   // Primary 主文字
private val FinGray2 = Color(0xFF6B7280)   // Secondary 辅助文字
private val FinGray3 = Color(0xFF9CA3AF)   // Tertiary 弱文字
private val FinGray4 = Color(0xFFD1D5DB)   // Disabled 禁用/极弱
private val FinBg = Color(0xFFF7F8FA)       // 页面背景
private val FinSurface = Color(0xFFFFFFFF) // 卡片背景

private val LightColorScheme = lightColorScheme(
    primary = FinPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F5E9),
    onPrimaryContainer = FinPrimary,
    secondary = FinSecondary,
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
    val Secondary = FinSecondary
    val PageBg = FinBg
    val Surface = FinSurface
    val TextPrimary = FinGray1
    val TextSecondary = FinGray2
    val TextTertiary = FinGray3
    val Disabled = FinGray4
    val Number = Color(0xFF111827)
    val Outline = Color(0xFFE5E7EB)
    val Accent = FinPrimary
    val SoftGreen = Color(0xFFEAF7EF)  // 按钮浅绿色背景

    // Risk bucket colors（标普四象限）
    val Aggressive = Color(0xFF3D7A5C)     // 进取 绿
    val Conservative = Color(0xFF8DA7C7)   // 稳健 蓝
    val Insurance = Color(0xFF9E8FBE)      // 保命 柔紫
    val Cash = Color(0xFFD8B36A)           // 防守 金
}

// 标准化圆角
object FinShapes {
    val sm = RoundedCornerShape(12.dp)
    val md = RoundedCornerShape(16.dp)
    val lg = RoundedCornerShape(20.dp)
    val xl = RoundedCornerShape(24.dp)
}

// 标准化按钮高度
object FinSizes {
    val buttonHeight = 48.dp
    val iconSize = 40.dp
    val sectionSpacing = 16.dp
}
