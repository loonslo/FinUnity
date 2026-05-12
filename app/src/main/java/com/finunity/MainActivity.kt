package com.finunity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.finunity.data.local.AppDatabase
import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AccountType
import com.finunity.data.local.entity.Position
import com.finunity.data.model.AccountSummary
import com.finunity.data.model.AssetRecordSummary
import com.finunity.data.repository.CsvImportRepository
import com.finunity.data.repository.HistoryRepository
import com.finunity.data.local.entity.AssetRecord
import com.finunity.ui.screens.AccountScreen
import com.finunity.ui.screens.AccountDetailScreen
import com.finunity.ui.screens.AccountHubScreen
import com.finunity.ui.screens.AssetRecordScreen
import com.finunity.ui.screens.HistoryScreen
import com.finunity.ui.screens.MainScreen
import com.finunity.ui.screens.PositionScreen
import com.finunity.ui.screens.PriceHistoryScreen
import com.finunity.ui.screens.SettingsScreen
import com.finunity.ui.screens.RiskBucketDetailScreen
import com.finunity.ui.screens.TransactionHistoryScreen
import com.finunity.ui.screens.ImportCsvScreen
import com.finunity.data.repository.MonthlyChange
import com.finunity.ui.theme.FinUnityTheme
import com.finunity.viewmodel.MainViewModel
import com.finunity.worker.PriceSyncWorker
import com.finunity.worker.SnapshotWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = AppDatabase.getDatabase(applicationContext)

        // 启动后台价格同步
        PriceSyncWorker.schedule(this)

        // 启动后台资产快照
        SnapshotWorker.scheduleDaily(this)

        setContent {
            FinUnityTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FinUnityApp(database)
                }
            }
        }
    }
}

sealed class Screen {
    data object Main : Screen()
    data object Settings : Screen()
    data object ImportCsv : Screen()
    data object AccountHub : Screen()
    data class AddAccount(val account: Account? = null) : Screen()
    data class AddPosition(val position: Position? = null, val accountId: String) : Screen()
    data class AddAssetRecord(val record: AssetRecord? = null, val accountId: String) : Screen()
    data object History : Screen()
    data class AccountDetail(val accountId: String) : Screen()
    data class RiskBucketDetail(val bucketIndex: Int) : Screen()
    data class TransactionHistory(val accountId: String) : Screen()
    data class AssetTransactionHistory(val recordId: String, val assetName: String) : Screen()
    data class PriceHistory(val recordId: String, val assetName: String) : Screen()
}

@Composable
fun FinUnityApp(database: AppDatabase) {
    val viewModel: MainViewModel = viewModel(
        factory = MainViewModel.Factory(database)
    )
    val historyRepository = remember { HistoryRepository(database) }

    val portfolioSummary by viewModel.portfolioSummary.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var showQuickAddAccount by remember { mutableStateOf(false) }

    // 历史数据
    val snapshots by historyRepository.getRecentSnapshots(30).collectAsState(initial = emptyList())
    var monthlyChange by remember { mutableStateOf<MonthlyChange?>(null) }

    // 加载月度变化
    LaunchedEffect(snapshots) {
        monthlyChange = historyRepository.getMonthlyChange()
    }

    // 账户选择对话框
    if (showAccountPicker) {
        AccountPickerDialog(
            accounts = portfolioSummary?.accounts ?: emptyList(),
            baseCurrency = portfolioSummary?.baseCurrency ?: "CNY",
            onSelect = { accountId ->
                showAccountPicker = false
                currentScreen = Screen.AddAssetRecord(record = null, accountId = accountId)
            },
            onDismiss = { showAccountPicker = false }
        )
    }

    // 快速添加账户对话框（添加持仓前）
    if (showQuickAddAccount) {
        QuickAddAccountDialog(
            onConfirm = { account ->
                viewModel.addAccount(account)
                showQuickAddAccount = false
                currentScreen = Screen.AddAssetRecord(record = null, accountId = account.id)
            },
            onDismiss = { showQuickAddAccount = false }
        )
    }

    when (val screen = currentScreen) {
        is Screen.Main -> {
            MainScreen(
                portfolioSummary = portfolioSummary,
                isLoading = isLoading,
                error = error,
                onRefresh = {
                    viewModel.viewModelScope.launch {
                        viewModel.refreshPrices()
                    }
                },
                onAddAccount = { currentScreen = Screen.AddAccount() },
                onAddPosition = {
                    val accounts = portfolioSummary?.accounts
                    if (accounts.isNullOrEmpty()) {
                        // 没有账户，先快速添加账户
                        showQuickAddAccount = true
                    } else if (accounts.size == 1) {
                        // 只有一个账户，直接使用
                        currentScreen = Screen.AddAssetRecord(record = null, accountId = accounts.first().account.id)
                    } else {
                        // 多个账户，显示选择对话框
                        showAccountPicker = true
                    }
                },
                onEditPosition = { holding ->
                    currentScreen = Screen.AddPosition(
                        position = holding.position,
                        accountId = holding.position.accountId
                    )
                },
                onEditAccount = { account ->
                    currentScreen = Screen.AccountDetail(account.id)
                },
                onAddAssetRecord = { accountId ->
                    currentScreen = Screen.AddAssetRecord(record = null, accountId = accountId)
                },
                onEditAssetRecord = { recordSummary ->
                    currentScreen = Screen.AddAssetRecord(
                        record = recordSummary.record,
                        accountId = recordSummary.record.accountId
                    )
                },
                onViewHistory = { currentScreen = Screen.History },
                onViewRiskBucketDetail = { bucketIndex ->
                    currentScreen = Screen.RiskBucketDetail(bucketIndex)
                },
                onOpenSettings = { currentScreen = Screen.Settings },
                onOpenImportCsv = { currentScreen = Screen.ImportCsv },
                onOpenAccountHub = { currentScreen = Screen.AccountHub }
            )
        }

        is Screen.Settings -> {
            SettingsScreen(
                settings = viewModel.settings.value,
                onSave = { newSettings ->
                    viewModel.updateSettings(newSettings)
                    currentScreen = Screen.Main
                },
                onBack = { currentScreen = Screen.Main }
            )
        }

        is Screen.ImportCsv -> {
            ImportCsvScreen(
                database = database,
                onBack = { currentScreen = Screen.Main }
            )
        }

        is Screen.AccountHub -> {
            AccountHubScreen(
                database = database,
                accounts = portfolioSummary?.accounts ?: emptyList(),
                baseCurrency = portfolioSummary?.baseCurrency ?: "CNY",
                onBack = { currentScreen = Screen.Main },
                onViewAccount = { currentScreen = Screen.AccountDetail(it) },
                onEditAccount = { currentScreen = Screen.AddAccount(it) },
                onAddAccount = { currentScreen = Screen.AddAccount(null) },
                onViewTransactions = { currentScreen = Screen.TransactionHistory(it) }
            )
        }

        is Screen.AddAccount -> {
            AccountScreen(
                account = screen.account,
                onSave = { account ->
                    if (screen.account == null) {
                        viewModel.addAccount(account)
                    } else {
                        viewModel.updateAccount(account)
                    }
                    currentScreen = Screen.Main
                },
                onDelete = { id ->
                    viewModel.deleteAccount(id)
                    currentScreen = Screen.Main
                },
                onBack = { currentScreen = Screen.Main }
            )
        }

        is Screen.AddPosition -> {
            PositionScreen(
                position = screen.position,
                accountId = screen.accountId,
                accounts = portfolioSummary?.accounts ?: emptyList(),
                onSave = { position ->
                    if (screen.position == null) {
                        viewModel.addPosition(position)
                    } else {
                        viewModel.updatePosition(position)
                    }
                    currentScreen = Screen.Main
                },
                onDelete = { id ->
                    viewModel.deletePosition(id)
                    currentScreen = Screen.Main
                },
                onSell = { id, shares ->
                    viewModel.sellPosition(id, shares)
                    currentScreen = Screen.Main
                },
                onBack = { currentScreen = Screen.Main }
            )
        }

        is Screen.AddAssetRecord -> {
            val account = portfolioSummary?.accounts?.find { it.account.id == screen.accountId }?.account
            AssetRecordScreen(
                record = screen.record,
                account = account,
                onSave = { record ->
                    if (screen.record == null) {
                        viewModel.addAssetRecord(record)
                    } else {
                        viewModel.updateAssetRecord(record)
                    }
                    currentScreen = Screen.Main
                },
                onDelete = { id ->
                    viewModel.deleteAssetRecord(id)
                    currentScreen = Screen.Main
                },
                onSell = { id, quantity ->
                    viewModel.sellAssetRecord(id, quantity)
                    currentScreen = Screen.Main
                },
                onViewHistory = { recordId ->
                    val recordName = screen.record?.name ?: "资产记录"
                    currentScreen = Screen.AssetTransactionHistory(recordId, recordName)
                },
                onBack = { currentScreen = Screen.Main }
            )
        }

        is Screen.History -> {
            HistoryScreen(
                snapshots = snapshots,
                monthlyChange = monthlyChange,
                baseCurrency = portfolioSummary?.baseCurrency ?: "CNY",
                onBack = { currentScreen = Screen.Main },
                onRefresh = {
                    // 先刷新价格/资产数据，然后保存快照
                    viewModel.viewModelScope.launch {
                        viewModel.refreshPrices()
                        // refreshPrices 内部已等待加载完成，直接重新获取最新汇总
                        val latestSummary = viewModel.portfolioSummary.value
                        latestSummary?.let { historyRepository.saveSnapshot(it) }
                    }
                }
            )
        }

        is Screen.AccountDetail -> {
            val accountSummary = portfolioSummary?.accounts?.find { it.account.id == screen.accountId }
            AccountDetailScreen(
                account = accountSummary?.account ?: Account(name = "", type = AccountType.BANK, currency = "CNY", balance = 0.0),
                accountSummary = accountSummary,
                assetRecords = portfolioSummary?.assetRecords ?: emptyList(),
                baseCurrency = portfolioSummary?.baseCurrency ?: "CNY",
                onBack = { currentScreen = Screen.Main },
                onEditAccount = { currentScreen = Screen.AddAccount(accountSummary?.account) },
                onAddRecord = { currentScreen = Screen.AddAssetRecord(record = null, accountId = screen.accountId) },
                onEditRecord = { record -> currentScreen = Screen.AddAssetRecord(record = record, accountId = screen.accountId) },
                onViewTransactions = { currentScreen = Screen.TransactionHistory(screen.accountId) }
            )
        }

        is Screen.RiskBucketDetail -> {
            val bucket = portfolioSummary?.riskBuckets?.getOrNull(screen.bucketIndex)
            if (bucket != null) {
                RiskBucketDetailScreen(
                    riskBucketSummary = bucket,
                    accounts = portfolioSummary?.accounts ?: emptyList(),
                    assetRecords = portfolioSummary?.assetRecords ?: emptyList(),
                    holdings = portfolioSummary?.holdings ?: emptyList(),
                    baseCurrency = portfolioSummary?.baseCurrency ?: "CNY",
                    onBack = { currentScreen = Screen.Main },
                    onViewAccountTransactions = { accountId -> currentScreen = Screen.TransactionHistory(accountId) },
                    onViewAssetHistory = { recordId ->
                        val record = portfolioSummary?.assetRecords?.find { it.record.id == recordId }
                        currentScreen = Screen.PriceHistory(recordId, record?.record?.name ?: "")
                    },
                    onViewAssetTransactions = { recordId ->
                        val record = portfolioSummary?.assetRecords?.find { it.record.id == recordId }
                        currentScreen = Screen.AssetTransactionHistory(recordId, record?.record?.name ?: "")
                    },
                    onEditAssetRecord = { recordId ->
                        val record = portfolioSummary?.assetRecords?.find { it.record.id == recordId }
                        currentScreen = Screen.AddAssetRecord(record?.record, record?.record?.accountId ?: "")
                    }
                )
            } else {
                currentScreen = Screen.Main
            }
        }

        is Screen.TransactionHistory -> {
            val accountSummary = portfolioSummary?.accounts?.find { it.account.id == screen.accountId }
            val transactions by database.transactionDao().getTransactionsByAccount(screen.accountId).collectAsState(initial = emptyList())
            TransactionHistoryScreen(
                transactions = transactions,
                accountName = accountSummary?.account?.name,
                baseCurrency = portfolioSummary?.baseCurrency ?: "CNY",
                onBack = { currentScreen = Screen.Main }
            )
        }

        is Screen.PriceHistory -> {
            val priceHistory by database.priceHistoryDao().getHistoryByRecord(screen.recordId).collectAsState(initial = emptyList())
            val assetCurrency = portfolioSummary?.assetRecords?.find { it.record.id == screen.recordId }?.record?.currency ?: "USD"
            PriceHistoryScreen(
                priceHistory = priceHistory,
                assetName = screen.assetName,
                assetCurrency = assetCurrency,
                onBack = { currentScreen = Screen.Main }
            )
        }

        is Screen.AssetTransactionHistory -> {
            val transactions by database.transactionDao().getTransactionsByRecordId(screen.recordId).collectAsState(initial = emptyList())
            TransactionHistoryScreen(
                transactions = transactions,
                accountName = screen.assetName,
                baseCurrency = portfolioSummary?.baseCurrency ?: "CNY",
                onBack = { currentScreen = Screen.Main }
            )
        }
    }
}

@Composable
fun AccountPickerDialog(
    accounts: List<AccountSummary>,
    baseCurrency: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择账户") },
        text = {
            LazyColumn {
                items(accounts) { summary ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSelect(summary.account.id) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = summary.account.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${summary.account.type.name} · ${summary.account.currency}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Text(
                                text = formatCurrency(summary.balanceInBaseCurrency, baseCurrency),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddAccountDialog(
    onConfirm: (Account) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(AccountType.BROKER) }
    var selectedCurrency by remember { mutableStateOf("CNY") }
    var balance by remember { mutableStateOf("") }
    var expandedType by remember { mutableStateOf(false) }
    var expandedCurrency by remember { mutableStateOf(false) }

    val currencies = listOf("CNY", "USD", "HKD")
    val currencyLabels = mapOf("CNY" to "人民币", "USD" to "美元", "HKD" to "港币")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加账户") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("账户名称") },
                    placeholder = { Text("如：富途-港股") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 账户类型下拉
                ExposedDropdownMenuBox(
                    expanded = expandedType,
                    onExpandedChange = { expandedType = it }
                ) {
                    OutlinedTextField(
                        value = selectedType.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("账户类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedType,
                        onDismissRequest = { expandedType = false }
                    ) {
                        AccountType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    selectedType = type
                                    expandedType = false
                                }
                            )
                        }
                    }
                }

                // 币种下拉
                ExposedDropdownMenuBox(
                    expanded = expandedCurrency,
                    onExpandedChange = { expandedCurrency = it }
                ) {
                    OutlinedTextField(
                        value = "${selectedCurrency} (${currencyLabels[selectedCurrency]})",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("币种") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCurrency) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedCurrency,
                        onDismissRequest = { expandedCurrency = false }
                    ) {
                        currencies.forEach { currency ->
                            DropdownMenuItem(
                                text = { Text("$currency (${currencyLabels[currency]})") },
                                onClick = {
                                    selectedCurrency = currency
                                    expandedCurrency = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("初始余额") },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        val account = Account(
                            name = name.trim(),
                            type = selectedType,
                            currency = selectedCurrency,
                            balance = balance.toDoubleOrNull() ?: 0.0
                        )
                        onConfirm(account)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("继续添加资产")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

fun formatCurrency(amount: Double, currency: String): String {
    return when (currency) {
        "CNY" -> "¥${String.format("%.2f", amount)}"
        "USD" -> "$${String.format("%.2f", amount)}"
        "HKD" -> "HK$${String.format("%.2f", amount)}"
        else -> "${currency}${String.format("%.2f", amount)}"
    }
}
