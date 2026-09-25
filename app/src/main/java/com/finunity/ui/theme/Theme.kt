package com.finunity.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * 主题两个维度：配色方案 × 外观模式，对应 Web 端 finunity-color-tokens.css 的
 * data-theme-color / data-appearance 双轴设计（见 FinUnity-配色实现规范.md）。
 */
enum class FinThemeColor { MINIMAL, NAVY }
enum class FinAppearance { SYSTEM, LIGHT, DARK }

fun FinThemeColor.storageKey(): String = when (this) {
    FinThemeColor.MINIMAL -> "minimal"
    FinThemeColor.NAVY -> "navy"
}

fun finThemeColorFromKey(key: String): FinThemeColor = when (key) {
    "minimal" -> FinThemeColor.MINIMAL
    else -> FinThemeColor.NAVY
}

fun FinAppearance.storageKey(): String = when (this) {
    FinAppearance.SYSTEM -> "system"
    FinAppearance.LIGHT -> "light"
    FinAppearance.DARK -> "dark"
}

fun finAppearanceFromKey(key: String): FinAppearance = when (key) {
    "light" -> FinAppearance.LIGHT
    "dark" -> FinAppearance.DARK
    else -> FinAppearance.SYSTEM
}

/**
 * 一套主题×外观组合的完整视觉token集合，字段对应 finunity-color-tokens.css 的变量分组。
 *
 * 盈亏色（positive/negative）采用中国市场习惯：涨红、跌绿——与国际通行的"涨绿跌红"相反，
 * 是 Web、安卓两端统一的产品决策。danger/success 是通用 UI 状态色（错误/正常，如价格同步
 * 状态点），刻意不跟盈亏色复用同一组数值，避免"亏损"和"操作出错"这两个不同语境相互干扰。
 */
data class FinPalette(
    val isDark: Boolean,
    // 中性 UI：背景/卡片/文字层级
    val bg: Color,
    val bgElevated: Color,
    val bgInset: Color,
    val bgSunken: Color,
    val hairline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    // 盈亏（固定语义色，涨红跌绿，不随主题变化）
    val positive: Color,
    val negative: Color,
    val negativeBg: Color,
    // 三桶风险色（固定语义色，红黄绿交通灯逻辑，不随主题变化）
    val bucketDefensive: Color,
    val bucketDefensiveText: Color,
    val bucketDefensiveBg: Color,
    val bucketBalanced: Color,
    val bucketBalancedText: Color,
    val bucketBalancedBg: Color,
    val bucketAggressive: Color,
    val bucketAggressiveText: Color,
    val bucketAggressiveBg: Color,
    // 品牌色阶（主题相关）
    val brand700: Color,
    val brand600: Color,
    val brand100: Color,
    val brand050: Color,
    // 底部导航（对应 Web 端侧边栏 sidebar-*）
    val navBg: Brush,
    val navText: Color,
    val navTextDim: Color,
    val navActiveBg: Color,
    val navActiveText: Color,
    // 总资产卡（hero，对应 Web 端 .portfolio-hero）
    val heroBg: Brush,
    val heroGlow: Color?,
    val heroLabelColor: Color,
    val heroAmountColor: Color,
    val heroDeltaBg: Color,
    val heroDeltaColor: Color,
    val heroDeltaNegative: Color,
    val heroDeltaNegativeBg: Color,
    val heroMetaColor: Color,
    val heroMetaStrong: Color,
    val heroMetaBorder: Color,
    val heroBtnGhostBg: Color,
    val heroBtnGhostColor: Color,
    val heroBtnSolidBg: Color,
    val heroBtnSolidColor: Color,
    // 卡片描边（以描边为主、阴影极轻，见配色规范 Web 调研结论）
    val cardBorder: Color,
    // 通用 UI 状态色（价格同步状态点等，独立于盈亏色）
    val success: Color,
    val warning: Color,
    val danger: Color
)

// ---- 共享不变色（两套主题一致） ----
private val PositiveRed = Color(0xFFD8464B)   // 涨 / 盈利（中国市场习惯，红）
private val NegativeGreen = Color(0xFF269A57) // 跌 / 亏损（中国市场习惯，绿）
private val NegativeBgLight = Color(0xFFE6F6EC)

private val BucketDefensive = Color(0xFF2FA85A)      // 防守 — 绿
private val BucketDefensiveTextLight = Color(0xFF1E7A40)
private val BucketDefensiveBgLight = Color(0xFFE5F7EA)
private val BucketDefensiveTextDark = Color(0xFF6FD08C)
private val BucketBalanced = Color(0xFFF0A93B)       // 稳健 — 黄/琥珀
private val BucketBalancedTextLight = Color(0xFF9C6B1B)
private val BucketBalancedBgLight = Color(0xFFFDF1DC)
private val BucketBalancedTextDark = Color(0xFFF5C570)
private val BucketAggressive = Color(0xFFE2582A)     // 进取 — 红橙
private val BucketAggressiveTextLight = Color(0xFFA83A1A)
private val BucketAggressiveBgLight = Color(0xFFFBE7E0)
private val BucketAggressiveTextDark = Color(0xFFF08D6C)

private val DangerFixed = Color(0xFFD8464B)   // 通用错误/危险态，独立于盈亏色
private val SuccessFixed = Color(0xFF269A57)  // 通用成功/正常态，独立于盈亏色
private val WarningFixed = Color(0xFFF0B84B)

private val HeroDeltaNegativeOnDark = Color(0xFF6FD08C) // 深色 hero 卡上的"亏损"文字（提亮以保证可读）

// ---- 极简灰蓝 (minimal) ----
private val MinimalBrand700 = Color(0xFF4A7C94)
private val MinimalBrand600 = Color(0xFF3E6D82)
private val MinimalBrand100Light = Color(0xFFE7EEF1)
private val MinimalBrand050Light = Color(0xFFF3F7F8)
private val MinimalBrand100Dark = Color(0xFF4A7C94).copy(alpha = 0.18f)
private val MinimalBrand050Dark = Color(0xFF4A7C94).copy(alpha = 0.09f)

private fun minimalPalette(isDark: Boolean): FinPalette {
    val neutral = neutralTokens(isDark)
    return FinPalette(
        isDark = isDark,
        bg = neutral.bg, bgElevated = neutral.bgElevated, bgInset = neutral.bgInset, bgSunken = neutral.bgSunken,
        hairline = neutral.hairline, textPrimary = neutral.textPrimary, textSecondary = neutral.textSecondary, textTertiary = neutral.textTertiary,
        positive = PositiveRed, negative = NegativeGreen,
        negativeBg = if (isDark) NegativeGreen.copy(alpha = 0.16f) else NegativeBgLight,
        bucketDefensive = BucketDefensive,
        bucketDefensiveText = if (isDark) BucketDefensiveTextDark else BucketDefensiveTextLight,
        bucketDefensiveBg = if (isDark) BucketDefensive.copy(alpha = 0.20f) else BucketDefensiveBgLight,
        bucketBalanced = BucketBalanced,
        bucketBalancedText = if (isDark) BucketBalancedTextDark else BucketBalancedTextLight,
        bucketBalancedBg = if (isDark) BucketBalanced.copy(alpha = 0.20f) else BucketBalancedBgLight,
        bucketAggressive = BucketAggressive,
        bucketAggressiveText = if (isDark) BucketAggressiveTextDark else BucketAggressiveTextLight,
        bucketAggressiveBg = if (isDark) BucketAggressive.copy(alpha = 0.20f) else BucketAggressiveBgLight,
        brand700 = MinimalBrand700, brand600 = MinimalBrand600,
        brand100 = if (isDark) MinimalBrand100Dark else MinimalBrand100Light,
        brand050 = if (isDark) MinimalBrand050Dark else MinimalBrand050Light,
        navBg = if (isDark)
            Brush.verticalGradient(listOf(Color(0xFF171C1F), Color(0xFF202629), Color(0xFF293236)))
        else
            Brush.verticalGradient(listOf(Color(0xFFFAFBFB), Color(0xFFFAFBFB))),
        navText = if (isDark) Color.White.copy(alpha = 0.68f) else Color(0xFF78838C),
        navTextDim = if (isDark) Color.White.copy(alpha = 0.55f) else Color(0xFFA7B0B6),
        navActiveBg = if (isDark) Color.White.copy(alpha = 0.14f) else MinimalBrand100Light,
        navActiveText = if (isDark) Color.White else MinimalBrand600,
        heroBg = if (isDark)
            Brush.linearGradient(listOf(Color(0xFF232B30), Color(0xFF171C1F)))
        else
            Brush.linearGradient(listOf(Color(0xFFF1F5F6), Color(0xFFE5EBED))),
        heroGlow = null,
        heroLabelColor = if (isDark) Color.White.copy(alpha = 0.68f) else neutral.textSecondary,
        heroAmountColor = if (isDark) Color.White else neutral.textPrimary,
        heroDeltaBg = PositiveRed.copy(alpha = if (isDark) 0.20f else 0.12f),
        heroDeltaColor = PositiveRed,
        heroDeltaNegative = if (isDark) HeroDeltaNegativeOnDark else NegativeGreen,
        heroDeltaNegativeBg = NegativeGreen.copy(alpha = if (isDark) 0.20f else 0.12f),
        heroMetaColor = if (isDark) Color.White.copy(alpha = 0.55f) else neutral.textTertiary,
        heroMetaStrong = if (isDark) Color.White.copy(alpha = 0.82f) else neutral.textPrimary,
        heroMetaBorder = if (isDark) Color.White.copy(alpha = 0.14f) else neutral.hairline,
        heroBtnGhostBg = if (isDark) Color.White.copy(alpha = 0.10f) else neutral.bgElevated,
        heroBtnGhostColor = if (isDark) Color.White else neutral.textPrimary,
        heroBtnSolidBg = MinimalBrand600,
        heroBtnSolidColor = Color.White,
        cardBorder = neutral.hairline,
        success = SuccessFixed, warning = WarningFixed, danger = DangerFixed
    )
}

// ---- 藏青 (navy)：侧边栏/总资产卡恒暗，只有中性底色随外观切换 ----
private val NavyBrand900 = Color(0xFF0E1626)
private val NavyBrand800 = Color(0xFF16223A)
private val NavyBrand700 = Color(0xFF1E2D4A)
private val NavyBrand600 = Color(0xFF2A3C5C)
private val NavyGlow = Color(0xFF5CA6D6)
private val NavyBrand100Light = Color(0xFFE9EEF5)
private val NavyBrand050Light = Color(0xFFF3F6FA)
private val NavyBrand100Dark = Color(0xFF3B6EA5).copy(alpha = 0.22f)
private val NavyBrand050Dark = Color(0xFF3B6EA5).copy(alpha = 0.10f)

private fun navyPalette(isDark: Boolean): FinPalette {
    val neutral = neutralTokens(isDark)
    return FinPalette(
        isDark = isDark,
        bg = neutral.bg, bgElevated = neutral.bgElevated, bgInset = neutral.bgInset, bgSunken = neutral.bgSunken,
        hairline = neutral.hairline, textPrimary = neutral.textPrimary, textSecondary = neutral.textSecondary, textTertiary = neutral.textTertiary,
        positive = PositiveRed, negative = NegativeGreen,
        negativeBg = if (isDark) NegativeGreen.copy(alpha = 0.16f) else NegativeBgLight,
        bucketDefensive = BucketDefensive,
        bucketDefensiveText = if (isDark) BucketDefensiveTextDark else BucketDefensiveTextLight,
        bucketDefensiveBg = if (isDark) BucketDefensive.copy(alpha = 0.20f) else BucketDefensiveBgLight,
        bucketBalanced = BucketBalanced,
        bucketBalancedText = if (isDark) BucketBalancedTextDark else BucketBalancedTextLight,
        bucketBalancedBg = if (isDark) BucketBalanced.copy(alpha = 0.20f) else BucketBalancedBgLight,
        bucketAggressive = BucketAggressive,
        bucketAggressiveText = if (isDark) BucketAggressiveTextDark else BucketAggressiveTextLight,
        bucketAggressiveBg = if (isDark) BucketAggressive.copy(alpha = 0.20f) else BucketAggressiveBgLight,
        brand700 = NavyBrand700, brand600 = NavyBrand600,
        brand100 = if (isDark) NavyBrand100Dark else NavyBrand100Light,
        brand050 = if (isDark) NavyBrand050Dark else NavyBrand050Light,
        // 藏青主题的导航栏/总资产卡恒暗，不随外观模式变化（对齐 Web 端行为）
        navBg = Brush.verticalGradient(listOf(NavyBrand900, NavyBrand800, NavyBrand700)),
        navText = Color.White.copy(alpha = 0.68f),
        navTextDim = Color.White.copy(alpha = 0.55f),
        navActiveBg = Color.White.copy(alpha = 0.14f),
        navActiveText = Color.White,
        heroBg = Brush.linearGradient(listOf(NavyBrand700, NavyBrand900)),
        heroGlow = NavyGlow.copy(alpha = 0.35f),
        heroLabelColor = Color.White.copy(alpha = 0.68f),
        heroAmountColor = Color.White,
        heroDeltaBg = Color.White.copy(alpha = 0.12f),
        heroDeltaColor = Color(0xFFBFE0F5),
        heroDeltaNegative = HeroDeltaNegativeOnDark,
        heroDeltaNegativeBg = Color.White.copy(alpha = 0.12f),
        heroMetaColor = Color.White.copy(alpha = 0.55f),
        heroMetaStrong = Color.White.copy(alpha = 0.82f),
        heroMetaBorder = Color.White.copy(alpha = 0.14f),
        heroBtnGhostBg = Color.White.copy(alpha = 0.14f),
        heroBtnGhostColor = Color.White,
        heroBtnSolidBg = Color.White,
        heroBtnSolidColor = NavyBrand800,
        cardBorder = neutral.hairline,
        success = SuccessFixed, warning = WarningFixed, danger = DangerFixed
    )
}

private data class NeutralTokens(
    val bg: Color, val bgElevated: Color, val bgInset: Color, val bgSunken: Color,
    val hairline: Color, val textPrimary: Color, val textSecondary: Color, val textTertiary: Color
)

private fun neutralTokens(isDark: Boolean): NeutralTokens = if (isDark) {
    NeutralTokens(
        bg = Color(0xFF0E1310), bgElevated = Color(0xFF1A211D), bgInset = Color(0xFF212925), bgSunken = Color(0xFF171D1A),
        hairline = Color.White.copy(alpha = 0.08f),
        textPrimary = Color(0xFFF1F3F1), textSecondary = Color(0xFF9BA69F), textTertiary = Color(0xFF6F7A73)
    )
} else {
    NeutralTokens(
        bg = Color(0xFFF5F5F7), bgElevated = Color(0xFFFFFFFF), bgInset = Color(0xFFF7F8F6), bgSunken = Color(0xFFECEEEA),
        hairline = Color(0xFF141816).copy(alpha = 0.08f),
        textPrimary = Color(0xFF14181A), textSecondary = Color(0xFF6B7570), textTertiary = Color(0xFF9AA39D)
    )
}

fun resolveFinPalette(themeColor: FinThemeColor, appearance: FinAppearance, systemDark: Boolean): FinPalette {
    val isDark = when (appearance) {
        FinAppearance.SYSTEM -> systemDark
        FinAppearance.LIGHT -> false
        FinAppearance.DARK -> true
    }
    return when (themeColor) {
        FinThemeColor.MINIMAL -> minimalPalette(isDark)
        FinThemeColor.NAVY -> navyPalette(isDark)
    }
}

private val LocalFinPalette = staticCompositionLocalOf { navyPalette(isDark = true) }

val FinTypography = Typography(
    displayLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    displayMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun FinUnityTheme(
    themeColor: FinThemeColor = FinThemeColor.NAVY,
    appearance: FinAppearance = FinAppearance.DARK,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val palette = resolveFinPalette(themeColor, appearance, systemDark)

    val materialScheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.brand600,
            onPrimary = Color.White,
            primaryContainer = palette.brand700,
            onPrimaryContainer = palette.textPrimary,
            secondary = palette.brand600,
            onSecondary = Color.White,
            error = palette.danger,
            onError = Color.White,
            background = palette.bg,
            onBackground = palette.textPrimary,
            surface = palette.bgElevated,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.bgInset,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.hairline
        )
    } else {
        lightColorScheme(
            primary = palette.brand600,
            onPrimary = Color.White,
            primaryContainer = palette.brand100,
            onPrimaryContainer = palette.brand600,
            secondary = palette.brand600,
            onSecondary = Color.White,
            error = palette.danger,
            onError = Color.White,
            background = palette.bg,
            onBackground = palette.textPrimary,
            surface = palette.bgElevated,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.bgInset,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.hairline
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = palette.bg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !palette.isDark
        }
    }

    CompositionLocalProvider(LocalFinPalette provides palette) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = FinTypography,
            content = content
        )
    }
}

/**
 * 兼容既有调用写法（`FinColors.Profit` 等 ~100 处调用点不变）：每个属性都是
 * `@Composable get()`，从 [LocalFinPalette] 按当前主题解析，因此主题切换会
 * 自动传导到所有引用点，无需逐处修改。仅有极少数当前不在 Composable 上下文里
 * 的调用点（枚举常量、顶层 val）需要单独处理，见各自文件内的说明。
 */
object FinColors {
    val Profit: Color @Composable get() = LocalFinPalette.current.positive
    val Loss: Color @Composable get() = LocalFinPalette.current.negative
    val Success: Color @Composable get() = LocalFinPalette.current.success
    val Warning: Color @Composable get() = LocalFinPalette.current.warning
    val Danger: Color @Composable get() = LocalFinPalette.current.danger
    val Muted: Color @Composable get() = LocalFinPalette.current.textSecondary
    val Primary: Color @Composable get() = LocalFinPalette.current.brand600
    val Secondary: Color @Composable get() = LocalFinPalette.current.brand700
    val PageBg: Color @Composable get() = LocalFinPalette.current.bg
    val Surface: Color @Composable get() = LocalFinPalette.current.bgElevated
    val SurfaceElevated: Color @Composable get() = LocalFinPalette.current.bgInset
    val BgSunken: Color @Composable get() = LocalFinPalette.current.bgSunken
    val TextPrimary: Color @Composable get() = LocalFinPalette.current.textPrimary
    val TextSecondary: Color @Composable get() = LocalFinPalette.current.textSecondary
    val TextTertiary: Color @Composable get() = LocalFinPalette.current.textTertiary
    val Disabled: Color @Composable get() = LocalFinPalette.current.textTertiary
    val Number: Color @Composable get() = LocalFinPalette.current.textPrimary
    val Outline: Color @Composable get() = LocalFinPalette.current.hairline
    val Accent: Color @Composable get() = LocalFinPalette.current.brand600
    val SoftGreen: Color @Composable get() = LocalFinPalette.current.brand700

    // 三桶风险色：防守绿 / 稳健黄 / 进取红橙（三桶模型见 RiskBucket，本项目没有第四桶）
    val Defensive: Color @Composable get() = LocalFinPalette.current.bucketDefensive
    val DefensiveText: Color @Composable get() = LocalFinPalette.current.bucketDefensiveText
    val DefensiveBg: Color @Composable get() = LocalFinPalette.current.bucketDefensiveBg
    val Balanced: Color @Composable get() = LocalFinPalette.current.bucketBalanced
    val BalancedText: Color @Composable get() = LocalFinPalette.current.bucketBalancedText
    val BalancedBg: Color @Composable get() = LocalFinPalette.current.bucketBalancedBg
    val Aggressive: Color @Composable get() = LocalFinPalette.current.bucketAggressive
    val AggressiveText: Color @Composable get() = LocalFinPalette.current.bucketAggressiveText
    val AggressiveBg: Color @Composable get() = LocalFinPalette.current.bucketAggressiveBg

    // 旧四桶命名的兼容别名（Conservative/Cash 曾对应稳健/防守，见 PrototypeBucket 映射）
    val Conservative: Color @Composable get() = LocalFinPalette.current.bucketBalanced
    val Cash: Color @Composable get() = LocalFinPalette.current.bucketDefensive
}

/** Hero 卡（总资产卡）与导航栏用到的非纯色 token（渐变），不适合塞进 FinColors。 */
object FinChrome {
    val HeroBrush: Brush @Composable get() = LocalFinPalette.current.heroBg
    val HeroGlow: Color? @Composable get() = LocalFinPalette.current.heroGlow
    val HeroLabelColor: Color @Composable get() = LocalFinPalette.current.heroLabelColor
    val HeroAmountColor: Color @Composable get() = LocalFinPalette.current.heroAmountColor
    val HeroDeltaBg: Color @Composable get() = LocalFinPalette.current.heroDeltaBg
    val HeroDeltaColor: Color @Composable get() = LocalFinPalette.current.heroDeltaColor
    val HeroDeltaNegative: Color @Composable get() = LocalFinPalette.current.heroDeltaNegative
    val HeroDeltaNegativeBg: Color @Composable get() = LocalFinPalette.current.heroDeltaNegativeBg
    val HeroMetaColor: Color @Composable get() = LocalFinPalette.current.heroMetaColor
    val HeroMetaStrong: Color @Composable get() = LocalFinPalette.current.heroMetaStrong
    val HeroMetaBorder: Color @Composable get() = LocalFinPalette.current.heroMetaBorder
    val HeroBtnGhostBg: Color @Composable get() = LocalFinPalette.current.heroBtnGhostBg
    val HeroBtnGhostColor: Color @Composable get() = LocalFinPalette.current.heroBtnGhostColor
    val HeroBtnSolidBg: Color @Composable get() = LocalFinPalette.current.heroBtnSolidBg
    val HeroBtnSolidColor: Color @Composable get() = LocalFinPalette.current.heroBtnSolidColor
    val NavBg: Brush @Composable get() = LocalFinPalette.current.navBg
    val NavText: Color @Composable get() = LocalFinPalette.current.navText
    val NavTextDim: Color @Composable get() = LocalFinPalette.current.navTextDim
    val NavActiveBg: Color @Composable get() = LocalFinPalette.current.navActiveBg
    val NavActiveText: Color @Composable get() = LocalFinPalette.current.navActiveText
    val CardBorder: Color @Composable get() = LocalFinPalette.current.cardBorder
}

object FinShapes {
    val xs = RoundedCornerShape(10.dp)  // 输入框、小按钮
    val sm = RoundedCornerShape(14.dp)  // 列表行、导航项
    val md = RoundedCornerShape(14.dp)  // 兼容旧调用点，等同 sm
    val lg = RoundedCornerShape(24.dp)  // 标准卡片
    val xl = RoundedCornerShape(28.dp)  // 总资产卡 / 大卡片
}

object FinSizes {
    val buttonHeight = 48.dp
    val iconSize = 40.dp
    val sectionSpacing = 16.dp
}
