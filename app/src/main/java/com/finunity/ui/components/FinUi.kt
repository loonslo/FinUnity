package com.finunity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.finunity.ui.theme.FinChrome
import com.finunity.ui.theme.FinColors
import java.util.Locale
import com.finunity.ui.theme.FinShapes
import com.finunity.ui.theme.FinSizes

// 间距系统（基于 8dp）
val FinSpacing = object {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

// 统一面板：24dp 圆角、细边、无阴影——以描边为主而不是投影分层，对齐 Web 端 .panel。
@Composable
fun FinCard(
    modifier: Modifier = Modifier,
    containerColor: Color = FinColors.Surface,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = FinShapes.lg,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, FinChrome.CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

// FinSectionLabel - 简洁标签
@Composable
fun FinSectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// FinPill - 无边框，改用背景色。选中态用品牌色而不是反色技巧，浅色/深色模式下都成立。
@Composable
fun FinPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = if (selected) FinColors.Primary else Color.Transparent
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else FinColors.TextSecondary
        )
    }
}

// FinTextField - 简洁输入框
@Composable
fun FinTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    supportingText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        // 单行字段标准高度为 56dp；有校验提示时可自然扩展，避免文字被裁切。
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            letterSpacing = 0.sp,
            fontFeatureSettings = "\"tnum\""
        ),
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = FinShapes.xs,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = FinColors.Primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = FinColors.Primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

// FinSoftButton - 主按钮，默认品牌色；containerColor/contentColor 可覆盖（如买卖操作需要红/绿）
@Composable
fun FinSoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = FinColors.Primary,
    contentColor: Color = Color.White
) {
    Surface(
        modifier = modifier
            .height(FinSizes.buttonHeight)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        // 禁用态用半透明底色而非浅灰，保证文字始终清晰可读
        color = if (enabled) containerColor else containerColor.copy(alpha = 0.45f),
        contentColor = contentColor
    ) {
        Box(
            modifier = Modifier.fillMaxHeight(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// 盈亏颜色
@Composable
fun profitColor(value: Double): Color = when {
    value > 0 -> FinColors.Profit
    value < 0 -> FinColors.Loss
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
fun profitText(value: Double): String = when {
    value > 0 -> "+${String.format(Locale.US, "%.2f", value)}"
    else -> String.format(Locale.US, "%.2f", value)
}

@Composable
fun profitPercent(value: Double): String = when {
    value > 0 -> "+${String.format(Locale.US, "%.2f", value * 100)}%"
    else -> "${String.format(Locale.US, "%.2f", value * 100)}%"
}

// FinTopBar - 统一返回栏（背景 PageBg、返回箭头与标题同色系）
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = { Text(title, color = FinColors.TextPrimary) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    tint = FinColors.TextSecondary
                )
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = FinColors.PageBg
        )
    )
}

/** 分组表单里的单行字段：标签固定在左侧，避免浮动标签和描边框混用。 */
@Composable
fun FinInlineField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(label, color = FinColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(88.dp))
            Box(Modifier.weight(1f)) {
                if (value.isBlank() && placeholder.isNotBlank()) {
                    Text(
                        placeholder,
                        color = FinColors.TextTertiary,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = FinColors.TextPrimary,
                        fontSize = 16.sp,
                        letterSpacing = 0.sp,
                        fontFeatureSettings = "\"tnum\"",
                        textAlign = TextAlign.End
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
                )
            }
        }
        Divider(color = if (isError) FinColors.Danger else FinChrome.CardBorder)
    }
}

/** 可点击的设置行，和 [FinInlineField] 共享 52dp 行高与分割线规格。 */
@Composable
fun FinSettingRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    valueColor: Color = FinColors.TextPrimary,
    showDivider: Boolean = true,
    valueContent: (@Composable () -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                label,
                color = FinColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(88.dp)
            )
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                if (valueContent != null) valueContent() else Text(
                    value,
                    color = valueColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End
                )
                if (onClick != null) {
                    Text("›", color = FinColors.TextSecondary, fontSize = 20.sp, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        if (showDivider) Divider(color = FinChrome.CardBorder)
    }
}

@Composable
fun FinBucketTag(label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = CircleShape, color = color.copy(alpha = 0.12f)) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp))
    }
}

/**
 * 总资产卡外壳，对齐 Web 端 `.portfolio-hero`：渐变背景 + 28dp 圆角、零描边零阴影
 * （纯靠背景色块和更大圆角区分层级，和普通 [FinCard] 的"描边为主"策略刻意不同）。
 * 只提供背景/形状这一层，具体内容（金额、涨跌徽标、按钮、meta）由调用方组装。
 * 藏青主题会额外叠一层品牌色径向光晕，极简灰蓝主题没有这层（heroGlow 为 null）。
 */
@Composable
fun FinHeroCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val glow = FinChrome.HeroGlow
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(FinShapes.xl)
            .background(FinChrome.HeroBrush)
            .then(
                if (glow != null) {
                    Modifier.drawWithCache {
                        val brush = Brush.radialGradient(
                            colors = listOf(glow, Color.Transparent),
                            center = Offset(size.width, 0f),
                            radius = size.maxDimension * 1.1f
                        )
                        onDrawBehind { drawRect(brush) }
                    }
                } else Modifier
            )
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/** 涨跌徽标 pill，用在总资产卡里；正/负分别走 hero 专属的 delta 配色。 */
@Composable
fun FinHeroDeltaBadge(text: String, positive: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (positive) FinChrome.HeroDeltaBg else FinChrome.HeroDeltaNegativeBg
    ) {
        Text(
            text,
            color = if (positive) FinChrome.HeroDeltaColor else FinChrome.HeroDeltaNegative,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
        )
    }
}

/** Hero 卡内的操作按钮：ghost（半透明，默认）或 solid（实心，主操作）两种，对齐 Web 端 hero-buttons。 */
@Composable
fun FinHeroButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    solid: Boolean = false,
    icon: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = modifier.height(40.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = if (solid) FinChrome.HeroBtnSolidBg else FinChrome.HeroBtnGhostBg,
        contentColor = if (solid) FinChrome.HeroBtnSolidColor else FinChrome.HeroBtnGhostColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp).fillMaxHeight(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            icon?.invoke()
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}
