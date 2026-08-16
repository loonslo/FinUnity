package com.finunity.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
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

// 衡仓原型设计令牌：深藏青背景、低对比面板、红涨绿跌。
private val FinNavy = Color(0xFF12141F)
private val FinPanel = Color(0xFF1A1E2E)
private val FinPanel2 = Color(0xFF222740)
private val FinInk = Color(0xFFF2F4F8)
private val FinSub = Color(0xFF8B93A7)
private val FinFaint = Color(0xFF5C6478)
private val FinUp = Color(0xFFE5484D)
private val FinDown = Color(0xFF3FB68B)
private val FinPrimary = Color(0xFF5B8DEF)
private val FinSecondary = Color(0xFF9EC1FF)

private val DarkColorScheme = darkColorScheme(
    primary = FinPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF26385F),
    onPrimaryContainer = FinInk,
    secondary = FinSecondary,
    onSecondary = FinNavy,
    tertiary = Color(0xFFF0883E),
    onTertiary = FinNavy,
    error = FinUp,
    onError = Color.White,
    errorContainer = Color(0xFF4A2028),
    onErrorContainer = Color(0xFFFFB4AB),
    background = FinNavy,
    onBackground = FinInk,
    surface = FinPanel,
    onSurface = FinInk,
    surfaceVariant = FinPanel2,
    onSurfaceVariant = FinSub,
    outline = Color.White.copy(alpha = 0.10f),
    inverseSurface = FinInk,
    inverseOnSurface = FinNavy,
    inversePrimary = Color(0xFFB9CEFF)
)

val FinTypography = Typography(
    displayLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, color = FinInk, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = FinInk),
    displaySmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = FinInk),
    headlineLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = FinInk),
    headlineMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = FinInk),
    headlineSmall = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = FinInk),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, color = FinInk),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = FinInk),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = FinInk),
    bodyLarge = TextStyle(fontSize = 16.sp, color = FinInk),
    bodyMedium = TextStyle(fontSize = 14.sp, color = FinSub),
    bodySmall = TextStyle(fontSize = 12.sp, color = FinSub),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = FinSub),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = FinSub),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = FinSub)
)

@Composable
fun FinUnityTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DarkColorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = FinTypography,
        content = content
    )
}

object FinColors {
    // 中国市场习惯：上涨红、下跌绿。
    val Profit = FinUp
    val Loss = FinDown
    val Muted = FinSub
    val Primary = FinPrimary
    val Secondary = FinSecondary
    val PageBg = FinNavy
    val Surface = FinPanel
    val SurfaceElevated = FinPanel2
    val TextPrimary = FinInk
    val TextSecondary = FinSub
    val TextTertiary = FinFaint
    val Disabled = FinFaint
    val Number = FinInk
    val Outline = Color.White.copy(alpha = 0.10f)
    val Accent = FinPrimary
    val SoftGreen = Color(0xFF26385F)

    // 原型三桶颜色；保命桶在原型展示层并入稳健桶，底层仍保留四象限。
    val Aggressive = Color(0xFFF0883E)
    val Conservative = Color(0xFF5B8DEF)
    val Insurance = Color(0xFF9E8FBE)
    val Cash = Color(0xFFD9A441)
}

object FinShapes {
    val sm = RoundedCornerShape(12.dp)
    val md = RoundedCornerShape(16.dp)
    val lg = RoundedCornerShape(20.dp)
    val xl = RoundedCornerShape(24.dp)
}

object FinSizes {
    val buttonHeight = 48.dp
    val iconSize = 40.dp
    val sectionSpacing = 16.dp
}
