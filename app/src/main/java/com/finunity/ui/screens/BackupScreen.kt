package com.finunity.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finunity.data.local.AppDatabase
import com.finunity.data.repository.BackupRepository
import com.finunity.ui.theme.FinColors
import com.finunity.ui.components.FinTopBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    database: AppDatabase,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { BackupRepository(database) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingSummary by remember { mutableStateOf<com.finunity.data.repository.BackupSummary?>(null) }

    // 导出：创建文件
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = repo.export()
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(json.toByteArray(Charsets.UTF_8))
                    }
                    snackbarHostState.showSnackbar("备份已导出")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("导出失败: ${e.message}")
                }
            }
        }
    }

    // 导入：选择文件
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val summary = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }.orEmpty()
                }.getOrElse { "" }
                repo.summarize(summary).fold(
                    onSuccess = {
                        pendingImportUri = uri
                        pendingSummary = it
                        showRestoreConfirm = true
                    },
                    onFailure = { snackbarHostState.showSnackbar("备份文件无法识别：${it.message.orEmpty()}") }
                )
            }
        }
    }

    // 恢复确认对话框
    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = {
                showRestoreConfirm = false
                pendingImportUri = null
                pendingSummary = null
            },
            title = { Text("恢复数据") },
            text = {
                val summary = pendingSummary
                Text(
                    if (summary == null) "正在读取备份摘要…"
                    else "这是覆盖恢复，将替换当前本地数据。\n\n" +
                        "备份时间：${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(summary.exportedAt))}\n" +
                        "账户 ${summary.accountCount} · 资产 ${summary.assetCount} · 流水 ${summary.transactionCount}\n" +
                        "快照 ${summary.snapshotCount} · 周期规则 ${summary.recurringRuleCount}\n\n确定继续吗？"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreConfirm = false
                        val uri = pendingImportUri
                        pendingImportUri = null
                        pendingSummary = null
                        if (uri != null) {
                            scope.launch {
                                try {
                                    val inputStream = context.contentResolver.openInputStream(uri)
                                    val json = inputStream?.bufferedReader()?.readText() ?: ""
                                    inputStream?.close()
                                    val result = repo.import(json)
                                    result.fold(
                                        onSuccess = { snackbarHostState.showSnackbar("恢复成功") },
                                        onFailure = { snackbarHostState.showSnackbar("文件无法识别") }
                                    )
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("文件无法识别")
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("确定恢复")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    pendingImportUri = null
                    pendingSummary = null
                }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = { FinTopBar("备份恢复", onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 导出卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "导出备份",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = FinColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "将所有账户、资产、持仓、交易记录、价格缓存和设置导出为一个 JSON 文件。导出文件是明文财务数据，请妥善保管。",
                        style = MaterialTheme.typography.bodySmall,
                        color = FinColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val timestamp = java.text.SimpleDateFormat(
                                "yyyyMMdd_HHmmss",
                                java.util.Locale.getDefault()
                            ).format(java.util.Date())
                        exportLauncher.launch("衡仓_backup_$timestamp.json")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FinColors.SoftGreen,
                            contentColor = FinColors.Number
                        )
                    ) {
                        Text("导出备份", color = FinColors.Number)
                    }
                }
            }

            // 恢复卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "从文件恢复",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = FinColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "选择一个之前导出的 JSON 备份文件来恢复数据。恢复会覆盖当前所有数据，并会自动兼容旧版 Position 持仓，请谨慎操作。",
                        style = MaterialTheme.typography.bodySmall,
                        color = FinColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = FinColors.TextSecondary
                        )
                    ) {
                        Text("从文件恢复")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
