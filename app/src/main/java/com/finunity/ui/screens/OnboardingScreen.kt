package com.finunity.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finunity.ui.theme.FinColors
import com.finunity.ui.theme.FinShapes

private data class BucketInfo(val name: String, val desc: String, val color: Color)

// 桶颜色随主题变化，不能放进顶层 val（在 Composable 上下文之外求值），改成按需构建的函数。
@Composable
private fun buckets(): List<BucketInfo> = listOf(
    BucketInfo("防守", "现金、活期和随时要用的备用金", FinColors.Cash),
    BucketInfo("稳健", "定期、债券、保险、房产、车辆和低波动资产", FinColors.Conservative),
    BucketInfo("进攻", "股票、ETF、权益基金和可承受波动的长期资金", FinColors.Aggressive)
)

/**
 * 新手引导：欢迎 → 三桶理念 → 怎么用 → 开始添加账户。
 * 内部维护步骤状态，最后一步触发 onStart（进入添加账户流程）。
 */
@Composable
fun OnboardingScreen(
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableStateOf(0) }
    val lastStep = 2

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FinColors.PageBg)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (step) {
                0 -> WelcomeStep()
        1 -> BucketStep()
                else -> HowToStep()
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 步骤指示点
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(lastStep + 1) { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == step) 22.dp else 8.dp, 8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (i == step) FinColors.Accent else FinColors.Outline)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = { if (step < lastStep) step++ else onStart() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = FinShapes.md,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FinColors.SoftGreen, contentColor = FinColors.Number
                )
            ) {
                Text(
                    text = if (step < lastStep) "下一步" else "开始添加账户",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FinColors.Number
                )
            }

            if (step < lastStep) {
                TextButton(onClick = onStart) {
                    Text(
                        text = "跳过",
                        color = FinColors.TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(72.dp).background(FinColors.SoftGreen, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("¥", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = FinColors.Accent)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text("把分散的钱，看成一盘", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, color = FinColors.TextPrimary)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "券商、银行、互联网平台……把分散在各处的资产记到一起，按用途看清结构，帮你做家庭财务规划。",
            style = MaterialTheme.typography.bodyMedium,
            color = FinColors.TextSecondary
        )
    }
}

@Composable
private fun BucketStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("家庭资产三桶", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, color = FinColors.TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text("把钱按用途分成三桶，比只看涨跌更安心", style = MaterialTheme.typography.bodyMedium,
            color = FinColors.TextSecondary)
        Spacer(modifier = Modifier.height(20.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = FinShapes.xl,
            colors = CardDefaults.cardColors(containerColor = FinColors.Surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                buckets().forEach { q ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(q.color))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(q.name, style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold, color = FinColors.TextPrimary)
                            Text(q.desc, style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HowToStep() {
    val steps = listOf(
        "1" to "添加你的账户：券商、银行、互联网平台、现金等",
        "2" to "在账户下记录资产：股票/ETF 填代码可每日自动更新价格",
        "3" to "设定三桶目标，每月复盘一次，按建议慢慢调整"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("三步开始", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, color = FinColors.TextPrimary)
        Spacer(modifier = Modifier.height(20.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = FinShapes.xl,
            colors = CardDefaults.cardColors(containerColor = FinColors.Surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                steps.forEach { (num, text) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(28.dp).clip(CircleShape).background(FinColors.SoftGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(num, style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold, color = FinColors.Accent)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text, style = MaterialTheme.typography.bodyMedium, color = FinColors.TextPrimary)
                    }
                }
            }
        }
    }
}
