package com.finunity.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
    var pendingTemplate by remember { mutableStateOf<ImportType?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<String?>(null) }

    val templateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        val importType = pendingTemplate
        if (uri != null && importType != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(templateCsv(importType).toByteArray(Charsets.UTF_8))
                }
                importResult = "模板已保存"
            } catch (e: Exception) {
                importResult = "模板保存失败: ${e.message}"
            } finally {
                pendingTemplate = null
            }
        } else {
            pendingTemplate = null
        }
    }

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
                            val csvImportResult = when (selectedImportType) {
                                ImportType.ACCOUNTS -> csvRepo.importAccounts(context, fileName)
                                ImportType.POSITIONS -> csvRepo.importPositions(context, fileName)
                                ImportType.ASSET_RECORDS -> csvRepo.importAssetRecords(context, fileName)
                                ImportType.TRANSACTIONS -> csvRepo.importTransactions(context, fileName)
                                null -> return@withContext "未选择导入类型"
                            }
                            val errors = csvImportResult.errors
                            when (selectedImportType) {
                                ImportType.ACCOUNTS -> {
                                    if (errors.isEmpty()) {
                                        "导入成功：${csvImportResult.accountsImported} 个账户"
                                    } else {
                                        "导入完成：${csvImportResult.accountsImported} 个账户，${errors.size} 个错误\n\n${errors.take(5).joinToString("\n")}"
                                    }
                                }
                                ImportType.POSITIONS -> {
                                    if (errors.isEmpty()) {
                                        "导入成功：${csvImportResult.positionsImported} 条持仓"
                                    } else {
                                        "导入完成：${csvImportResult.positionsImported} 条持仓，${errors.size} 个错误\n\n${errors.take(5).joinToString("\n")}"
                                    }
                                }
                                ImportType.ASSET_RECORDS -> {
                                    if (errors.isEmpty()) {
                                        "导入成功：${csvImportResult.assetRecordsImported} 条持仓"
                                    } else {
                                        "导入完成：${csvImportResult.assetRecordsImported} 条持仓，${errors.size} 个错误\n\n${errors.take(5).joinToString("\n")}"
                                    }
                                }
                                ImportType.TRANSACTIONS -> {
                                    if (errors.isEmpty()) {
                                        "导入成功：${csvImportResult.transactionsImported} 条交易流水"
                                    } else {
                                        "导入完成：${csvImportResult.transactionsImported} 条交易流水，${errors.size} 个错误\n\n${errors.take(5).joinToString("\n")}"
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

    fun downloadTemplate(importType: ImportType) {
        pendingTemplate = importType
        templateLauncher.launch(templateFileName(importType))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedImportType != null) {
                            selectedImportType = null
                            importResult = null
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (selectedImportType == null) {
                // 首页：展示导入类型列表
                ImportTypeItem(
                    title = "导入账户",
                    description = "支持 CSV / Excel",
                    onClick = { selectedImportType = ImportType.ACCOUNTS }
                )
                ImportTypeItem(
                    title = "导入持仓",
                    description = "支持 CSV / Excel",
                    onClick = { selectedImportType = ImportType.ASSET_RECORDS }
                )
                ImportTypeItem(
                    title = "导入交易流水",
                    description = "支持 CSV / Excel",
                    onClick = { selectedImportType = ImportType.TRANSACTIONS }
                )
            } else {
                // 二级页：下载模板和选择文件
                ImportDetailCard(
                    importType = selectedImportType!!,
                    isLoading = isImporting,
                    onDownloadTemplate = { downloadTemplate(selectedImportType!!) },
                    onSelectFile = { launchPicker(selectedImportType!!) }
                )
            }

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
private fun ImportTypeItem(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
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
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun ImportDetailCard(
    importType: ImportType,
    isLoading: Boolean,
    onDownloadTemplate: () -> Unit,
    onSelectFile: () -> Unit
) {
    val fieldDescription = when (importType) {
        ImportType.ACCOUNTS -> "字段顺序：账户名、账户类型、币种"
        ImportType.POSITIONS -> "字段顺序：账户名、代码、数量、总成本、币种"
        ImportType.ASSET_RECORDS -> "字段顺序：账户名、资产类型、风险维度、名称、数量、成本、当前价、币种"
        ImportType.TRANSACTIONS -> "字段顺序：账户名、代码、交易类型、数量、价格、金额、币种、备注"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = fieldDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDownloadTemplate,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("下载模板")
                }
                Button(
                    onClick = onSelectFile,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("选择文件")
                    }
                }
            }
        }
    }
}

private fun importTypeTitle(importType: ImportType): String = when (importType) {
    ImportType.ACCOUNTS -> "导入账户"
    ImportType.POSITIONS -> "导入持仓"
    ImportType.ASSET_RECORDS -> "导入持仓"
    ImportType.TRANSACTIONS -> "导入交易流水"
}

private fun templateFileName(importType: ImportType): String = when (importType) {
    ImportType.ACCOUNTS -> "finunity_accounts_template.csv"
    ImportType.POSITIONS -> "finunity_positions_template.csv"
    ImportType.ASSET_RECORDS -> "finunity_holdings_template.csv"
    ImportType.TRANSACTIONS -> "finunity_transactions_template.csv"
}

private fun templateCsv(importType: ImportType): String = when (importType) {
    ImportType.ACCOUNTS -> """
        账户名,账户类型,币种
        我的券商,券商,美元
        招商银行,银行,人民币
    """.trimIndent()
    ImportType.POSITIONS -> """
        账户名,代码,数量,总成本,币种
        我的券商,AAPL,100,15000,美元
    """.trimIndent()
    ImportType.ASSET_RECORDS -> """
        账户名,资产类型,风险维度,名称,数量,成本,当前价,币种
        我的券商,股票,进取,AAPL,100,15000,180,美元
        招商银行,定期存款,稳健,一年定期,10000,10000,1.03,人民币
    """.trimIndent()
    ImportType.TRANSACTIONS -> """
        账户名,代码,交易类型,数量,价格,金额,币种,备注
        我的券商,AAPL,BUY,100,150,15000,美元,首次买入
    """.trimIndent()
}
