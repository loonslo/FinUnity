package com.finunity.ui.screens

import android.net.Uri
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.finunity.data.local.AppDatabase
import com.finunity.data.repository.ExportRepository
import com.finunity.ui.components.FinTopBar
import kotlinx.coroutines.launch

@Composable
fun ExportScreen(database: AppDatabase, baseCurrency: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { ExportRepository(database) }
    var pending by remember { mutableStateOf<ExportKind?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val onDocumentCreated: (Uri?) -> Unit = { uri ->
        val kind = pending
        pending = null
        if (uri != null && kind != null) scope.launch {
            runCatching {
                val content = when (kind) {
                    ExportKind.ASSETS -> repo.exportAssetsCsv()
                    ExportKind.TRANSACTIONS -> repo.exportTransactionsCsv()
                    ExportKind.REPORT -> repo.exportHtmlReport(baseCurrency)
                }
                context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            }.onSuccess { message = "导出完成" }.onFailure { message = "导出失败：${it.message}" }
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv"), onDocumentCreated)
    val htmlLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html"), onDocumentCreated)
    Scaffold(topBar = { FinTopBar("导出数据", onBack) }, modifier = modifier) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("导出的内容来自本机记录，可用 Excel 打开 CSV；HTML 报表可在浏览器中打印为 PDF。", style = MaterialTheme.typography.bodySmall)
            ExportButton("资产 CSV（Excel 可打开）", "账户、资产、成本、当前估值", onClick = { pending = ExportKind.ASSETS; csvLauncher.launch("衡仓_assets.csv") })
            ExportButton("流水 CSV（Excel 可打开）", "收入、支出、分红、投资和备注", onClick = { pending = ExportKind.TRANSACTIONS; csvLauncher.launch("衡仓_transactions.csv") })
            ExportButton("财务报表 HTML / PDF", "月度收入、支出和结余；浏览器打印即可保存 PDF", onClick = { pending = ExportKind.REPORT; htmlLauncher.launch("衡仓_report.html") })
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

private enum class ExportKind { ASSETS, TRANSACTIONS, REPORT }

@Composable
private fun ExportButton(title: String, description: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Text(title)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
