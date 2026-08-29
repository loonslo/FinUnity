package com.finunity.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.finunity.ui.theme.FinColors
import com.finunity.ui.theme.FinShapes

@Composable
fun AccountAssetsByAccountScreen(
    accountCount: Int,
    onAddAccount: () -> Unit,
    onOpenImportCsv: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBackup: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        bottomBar = bottomBar,
        containerColor = FinColors.PageBg,
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(FinColors.PageBg)
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(16.dp)) }
            item { AccountProfileHeader() }
            item {
                AccountToolsCard(
                    accountCount = accountCount,
                    onAddAccount = onAddAccount,
                    onOpenImportCsv = onOpenImportCsv,
                    onOpenSettings = onOpenSettings,
                    onOpenBackup = onOpenBackup
                )
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun AccountProfileHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = FinShapes.xl,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = FinShapes.xl,
                color = FinColors.Accent.copy(alpha = 0.09f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "账",
                        style = MaterialTheme.typography.headlineSmall,
                        color = FinColors.Accent
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "账户与数据",
                    style = MaterialTheme.typography.headlineSmall,
                    color = FinColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "本地账本、统一查看、按用途管理",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FinColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun AccountToolsCard(
    accountCount: Int,
    onAddAccount: () -> Unit,
    onOpenImportCsv: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBackup: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "数据工具",
            style = MaterialTheme.typography.labelMedium,
            color = FinColors.TextSecondary.copy(alpha = 0.72f)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SecondaryToolChip("添加账户", onAddAccount, Modifier.weight(1f))
            SecondaryToolChip(
                text = if (accountCount == 0) "导入表格" else "导入数据",
                onClick = onOpenImportCsv,
                modifier = Modifier.weight(1f)
            )
            SecondaryToolChip("备份恢复", onOpenBackup, Modifier.weight(1f))
            SecondaryToolChip("偏好设置", onOpenSettings, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SecondaryToolChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = FinShapes.sm,
        color = Color.White.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, FinColors.Outline.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = FinColors.TextSecondary
            )
        }
    }
}
