package com.finunity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.finunity.ui.theme.FinColors
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

// FinCard - 无边框，使用背景色区分
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
        shape = FinShapes.sm,
        color = if (selected) FinColors.Primary.copy(alpha = 0.1f) else Color.Transparent
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) FinColors.Primary else MaterialTheme.colorScheme.onSurfaceVariant
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
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
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
        shape = FinShapes.md,
        color = if (enabled) FinColors.Primary else MaterialTheme.colorScheme.surfaceVariant,
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
    value > 0 -> "+${String.format("%.2f", value)}"
    else -> String.format("%.2f", value)
}

@Composable
fun profitPercent(value: Double): String = when {
    value > 0 -> "+${String.format("%.2f", value * 100)}%"
    else -> "${String.format("%.2f", value * 100)}%"
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