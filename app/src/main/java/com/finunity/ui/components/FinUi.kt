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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.finunity.ui.theme.FinColors
import java.util.Locale
import com.finunity.ui.theme.FinShapes
import com.finunity.ui.theme.FinSizes

// 兼容旧代码（保留别名过渡）
val FinGreen = FinColors.Primary
val FinLine = FinColors.Outline
val FinBlue = FinColors.Conservative
val FinGold = FinColors.Cash
val FinPage = FinColors.PageBg
val FinMuted = FinColors.Muted
val FinAccent = FinColors.Accent

// 间距系统（基于 8dp）
val FinSpacing = object {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

// 统一面板：14dp 圆角、细边、无阴影。
@Composable
fun FinCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = FinShapes.md,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
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

// FinPill - 无边框，改用背景色
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
        color = if (selected) Color.White else Color.Transparent
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) FinColors.PageBg else MaterialTheme.colorScheme.onSurfaceVariant
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
        shape = FinShapes.sm,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = FinColors.Primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = FinColors.Primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

// FinSoftButton - 绿色按钮
@Composable
fun FinSoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        modifier = modifier
            .height(FinSizes.buttonHeight)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        // 禁用态用半透明主色而非浅灰，保证白色文字始终清晰可读
        color = if (enabled) FinColors.Primary else FinColors.Primary.copy(alpha = 0.45f),
        contentColor = Color.White
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
        Divider(color = if (isError) FinColors.Danger else Color.White.copy(alpha = 0.06f))
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
        if (showDivider) Divider(color = Color.White.copy(alpha = 0.06f))
    }
}

@Composable
fun FinBucketTag(label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = CircleShape, color = color.copy(alpha = 0.12f)) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp))
    }
}
