package com.finunity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 再平衡提醒组件（简化版）
 * 已集成到 MainScreen，此组件保留备用
 */
@Composable
fun RebalanceAlert(
    currentRatio: Double,
    threshold: Double,
    modifier: Modifier = Modifier
) {
    val ratioPercent = (currentRatio * 100).toInt()
    val thresholdPercent = (threshold * 100).toInt()

    if (currentRatio > threshold) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFFF3E0)  // 浅橙色
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚠️",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "股票占比 ${ratioPercent}%，超过阈值 ${thresholdPercent}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFE65100)
                    )
                    Text(
                        text = "建议：考虑卖出部分股票，转购债券",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFEF6C00).copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
