package com.finunity.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finunity.data.local.AppDatabase
import com.finunity.data.repository.CsvImportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ImportType {
    ACCOUNTS, POSITIONS, ASSET_RECORDS, TRANSACTIONS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportCsvScreen(
    database: AppDatabase,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedImportType by remember { mutableStateOf<ImportType?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                isImporting = true
                importResult = null

                try {
                    val result = withContext(Dispatchers.IO) {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        if (inputStream == null) {
                            "无法读取文件"
                        } else {
                            val fileName = "temp_import.csv"
                            val cacheFile = java.io.File(context.cacheDir, fileName)
                            cacheFile.outputStream().use { output ->
                                inputStream.copyTo(output)
                            }
                            inputStream.close()

                            val csvRepo = CsvImportRepository(database)
                            val importResult = when (selectedImportType) {
                                ImportType.ACCOUNTS -> csvRepo.importAccounts(context, fileName)
                                ImportType.POSITIONS -> csvRepo.importPositions(context, fileName)
                                ImportType.ASSET_RECORDS -> csvRepo.importAssetRecords(context, fileName)
                                ImportType.TRANSACTIONS -> csvRepo.importTransactions(context, fileName)
                                null -> return@withContext "未选择导入类型"
                            }
                            val errors = importResult.errors
                            when (selectedImportType) {
                                ImportType.ACCOUNTS -> {
                                    if (errors.isEmpty()) {
                                        "导入成功：${importResult.accountsImported} 个账户"
                                    } else {
                                        "导入完成：${importResult.accountsImported} 个账户，${errors.size} 个错误\n\n${errors.take(5).joinToString("\n")}"
                                    }
                                }
                                ImportType.POSITIONS -> {
                                    if (errors.isEmpty()) {
                                        "导入成功：${importResult.positionsImported} 条持仓"
                                    } else {
                                        "导入完成：${importResult.positionsImported} 条持仓，${errors.size} 个错误\n\n${errors.take(5).joinToString("\n")}"
                                    }
                                }
                                ImportType.ASSET_RECORDS -> {
                                    if (errors.isEmpty()) {
                                        "导入成功：${importResult.assetRecordsImported} 条资产记录"
                                    } else {
                                        "导入完成：${importResult.assetRecordsImported} 条资产记录，${errors.size} 个错误\n\n${errors.take(5).joinToString("\n")}"
                                    }
                                }
                                ImportType.TRANSACTIONS -> {
                                    if (errors.isEmpty()) {
                                        "导入成功：${importResult.transactionsImported} 条交易流水"
                                    } else {
                                        "导入完成：${importResult.transactionsImported} 条交易流水，${errors.size} 个错误\n\n${errors.take(5).joinToString("\n")}"
                                    }
                                }
                                null -> "未选择导入类型"
                            }
                        }
                    }
                    importResult = result
                } catch (e: Exception) {
                    importResult = "导入失败: ${e.message}"
                } finally {
                    isImporting = false
                }
            }
        }
    }

    fun launchPicker(importType: ImportType) {
        selectedImportType = importType
        filePickerLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("导入 CSV", fontWeight = FontWeight.Medium)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 账户导入
            ImportCard(
                title = "导入账户",
                description = "格式：name,type,currency,balance\n例如：我的券商,BROKER,USD,10000",
                icon = Icons.Default.List,
                isLoading = isImporting && selectedImportType == ImportType.ACCOUNTS,
                onClick = { launchPicker(ImportType.ACCOUNTS) }
            )

            // 持仓导入
            ImportCard(
                title = "导入持仓",
                description = "格式：accountName,symbol,shares,totalCost,currency\n例如：我的券商,AAPL,100,15000,USD",
                icon = Icons.Default.DateRange,
                isLoading = isImporting && selectedImportType == ImportType.POSITIONS,
                onClick = { launchPicker(ImportType.POSITIONS) }
            )

            // 资产记录导入
            ImportCard(
                title = "导入资产记录",
                description = "格式：accountName,assetType,riskBucket,name,quantity,cost,currentPrice,currency",
                icon = Icons.Default.Edit,
                isLoading = isImporting && selectedImportType == ImportType.ASSET_RECORDS,
                onClick = { launchPicker(ImportType.ASSET_RECORDS) }
            )

            // 交易流水导入
            ImportCard(
                title = "导入交易流水",
                description = "格式：accountName,symbol,type,shares,price,amount,currency,note",
                icon = Icons.Default.Add,
                isLoading = isImporting && selectedImportType == ImportType.TRANSACTIONS,
                onClick = { launchPicker(ImportType.TRANSACTIONS) }
            )

            // 显示导入结果
            importResult?.let { result ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "导入结果",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = result,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onClick,
                enabled = !isLoading,
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("选择文件")
            }
        }
    }
}
