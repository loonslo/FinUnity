package com.finunity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
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
import com.finunity.data.local.entity.displayName
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
import com.finunity.ui.screens.AssetDetailScreen
import com.finunity.ui.screens.HistoryScreen
import com.finunity.ui.screens.MainScreen
import com.finunity.ui.screens.PositionScreen
import com.finunity.ui.screens.PriceChangeScreen
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
    data object PriceChanges : Screen()
    data class AddAccount(val account: Account? = null, val continueToAsset: Boolean = false) : Screen()
    data class AddPosition(val position: Position? = null, val accountId: String) : Screen()
    data class AddAssetRecord(val record: AssetRecord? = null, val accountId: String) : Screen()
    data object History : Screen()
    data class AccountDetail(val accountId: String) : Screen()
    data class RiskBucketDetail(val bucketIndex: Int) : Screen()
    data class TransactionHistory(val accountId: String) : Screen()
    data class AssetTransactionHistory(val recordId: String, val assetName: String) : Screen()
    data class PriceHistory(val recordId: String, val assetName: String) : Screen()
    data class AssetDetail(val recordId: String) : Screen()
}

private enum class TopLevelTab {
    Main,
    History,
    Accounts
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
    var navStack by remember { mutableStateOf(listOf<Screen>()) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var pendingNewAccount by remember { mutableStateOf<Account?>(null) }

    // 历史数据
    val snapshots by historyRepository.getRecentSnapshots(30).collectAsState(initial = emptyList())
    val allPriceHistory by database.priceHistoryDao().getAllHistory().collectAsState(initial = emptyList())
    var monthlyChange by remember { mutableStateOf<MonthlyChange?>(null) }

    // 加载月度变化
    LaunchedEffect(snapshots) {
        monthlyChange = historyRepository.getMonthlyChange()
    }

    // 导航到新页面时保存当前页面到栈
    fun navigateTo(screen: Screen) {
        navStack = navStack + currentScreen
        currentScreen = screen
    }

    // 返回上一页
    fun navigateBack() {
        if (navStack.isNotEmpty()) {
            currentScreen = navStack.last()
            navStack = navStack.dropLast(1)
        } else {
            // 如果没有历史记录，返回到主页
            currentScreen = Screen.Main
        }
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
            onCreateAccount = {
                showAccountPicker = false
                currentScreen = Screen.AddAccount(continueToAsset = true)
            },
            onDismiss = { showAccountPicker = false }
        )
    }

    fun startAddFlow() {
        val accounts = portfolioSummary?.accounts.orEmpty()
        if (accounts.isEmpty()) {
            navigateTo(Screen.AccountHub)
        } else {
            showAccountPicker = true
        }
    }

    val bottomBar: @Composable (TopLevelTab) -> Unit = { selected ->
        FinUnityBottomBar(
            selected = selected,
            onSelect = { tab ->
                navigateTo(when (tab) {
                    TopLevelTab.Main -> Screen.Main
                    TopLevelTab.History -> Screen.PriceChanges
                    TopLevelTab.Accounts -> Screen.AccountHub
                })
            }
        )
    }

    when (val screen = currentScreen) {
        is Screen.Main -> {
            MainScreen(
                portfolioSummary = portfolioSummary,
                isLoading = isLoading,
                error = error,
                onStartAddFlow = { startAddFlow() },
                onEditAccount = { account ->
                    navigateTo(Screen.AccountDetail(account.id))
                },
                onViewHistory = { navigateTo(Screen.PriceChanges) },
                onViewRiskBucketDetail = { bucketIndex ->
                    navigateTo(Screen.RiskBucketDetail(bucketIndex))
                },
                onOpenSettings = { navigateTo(Screen.Settings) },
                onOpenImportCsv = { navigateTo(Screen.ImportCsv) },
                bottomBar = { bottomBar(TopLevelTab.Main) }
            )
        }

        is Screen.PriceChanges -> {
            PriceChangeScreen(
                records = portfolioSummary?.assetRecords ?: emptyList(),
                priceHistory = allPriceHistory,
                baseCurrency = portfolioSummary?.baseCurrency ?: "CNY",
                onViewAssetHistory = { recordId ->
                    navigateTo(Screen.AssetDetail(recordId))
                },
                bottomBar = { bottomBar(TopLevelTab.History) }
            )
        }

        is Screen.Settings -> {
            SettingsScreen(
                settings = viewModel.settings.value,
                onSave = { newSettings ->
                    viewModel.updateSettings(newSettings)
                    navigateBack()
                },
                onBack = { navigateBack() }
            )
        }

        is Screen.ImportCsv -> {
            ImportCsvScreen(
                database = database,
                onBack = { navigateBack() }
            )
        }

        is Screen.AccountHub -> {
            AccountHubScreen(
                accounts = portfolioSummary?.accounts ?: emptyList(),
                baseCurrency = portfolioSummary?.baseCurrency ?: "CNY",
                onViewAccount = { navigateTo(Screen.AccountDetail(it)) },
                onAddAccount = { navigateTo(Screen.AddAccount(null, continueToAsset = true)) },
                onOpenImportData = { navigateTo(Screen.ImportCsv) },
                bottomBar = { bottomBar(TopLevelTab.Accounts) }
            )
        }

        is Screen.AddAccount -> {
            AccountScreen(
                account = screen.account,
                onSave = { account ->
                    if (screen.account == null) {
                        viewModel.addAccount(account)
                        if (screen.continueToAsset) {
                            pendingNewAccount = account
                            navigateTo(Screen.AddAssetRecord(record = null, accountId = account.id))
                        } else {
                            navigateTo(Screen.AccountHub)
                        }
                    } else {
                        viewModel.updateAccount(account)
                        navigateTo(Screen.AccountHub)
                    }
                },
                onDelete = { id ->
                    viewModel.deleteAccount(id)
                    navigateTo(Screen.AccountHub)
                },
                onBack = { navigateBack() }
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
                    navigateBack()
                },
                onDelete = { id ->
                    viewModel.deletePosition(id)
                    navigateBack()
                },
                onSell = { id, shares ->
                    viewModel.sellPosition(id, shares)
                    navigateBack()
                },
                onBack = { navigateBack() }
            )
        }

        is Screen.AddAssetRecord -> {
            val account = portfolioSummary?.accounts?.find { it.account.id == screen.accountId }?.account
                ?: pendingNewAccount?.takeIf { it.id == screen.accountId }
            AssetRecordScreen(
                record = screen.record,
                account = account,
                onSave = { record ->
                    if (screen.record == null) {
                        viewModel.addAssetRecord(record)
                    } else {
                        viewModel.updateAssetRecord(record)
                    }
                    pendingNewAccount = null
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
                onBack = { navigateBack() }
            )
        }

        is Screen.History -> {
            HistoryScreen(
                snapshots = snapshots,
                monthlyChange = monthlyChange,
                baseCurrency = portfolioSummary?.baseCurrency ?: "CNY",
                onBack = { navigateBack() },
                bottomBar = {}
            )
        }

        is Screen.AccountDetail -> {
            val accountSummary = portfolioSummary?.accounts?.find { it.account.id == screen.accountId }
            AccountDetailScreen(
                account = accountSummary?.account ?: Account(name = "", type = AccountType.BANK, currency = "CNY", balance = 0.0),
                accountSummary = accountSummary,
                assetRecords = portfolioSummary?.assetRecords ?: emptyList(),
                baseCurrency = portfolioSummary?.baseCurrency ?: "CNY",
                onBack = { navigateBack() },
                onEditAccount = { navigateTo(Screen.AddAccount(accountSummary?.account)) },
                onAddRecord = { navigateTo(Screen.AddAssetRecord(record = null, accountId = screen.accountId)) },
                onEditRecord = { record -> navigateTo(Screen.AddAssetRecord(record = record, accountId = screen.accountId)) },
                onViewTransactions = { navigateTo(Screen.TransactionHistory(screen.accountId)) }
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
                    onBack = { navigateBack() },
                    onViewAccountTransactions = { accountId -> navigateTo(Screen.TransactionHistory(accountId)) },
                    onEditAssetRecord = { recordId ->
                        val record = portfolioSummary?.assetRecords?.find { it.record.id == recordId }
                        navigateTo(Screen.AddAssetRecord(record?.record, record?.record?.accountId ?: ""))
                    }
                )
            } else {
                navigateBack()
            }
        }

        is Screen.TransactionHistory -> {
            val accountSummary = portfolioSummary?.accounts?.find { it.account.id == screen.accountId }
            val transactions by database.transactionDao().getTransactionsByAccount(screen.accountId).collectAsState(initial = emptyList())
            TransactionHistoryScreen(
                transactions = transactions,
                accountName = accountSummary?.account?.name,
                baseCurrency = portfolioSummary?.baseCurrency ?: "CNY",
                onBack = { navigateBack() }
            )
        }

        is Screen.PriceHistory -> {
            val priceHistory by database.priceHistoryDao().getHistoryByRecord(screen.recordId).collectAsState(initial = emptyList())
            val assetCurrency = portfolioSummary?.assetRecords?.find { it.record.id == screen.recordId }?.record?.currency ?: "USD"
            PriceHistoryScreen(
                priceHistory = priceHistory,
                assetName = screen.assetName,
                assetCurrency = assetCurrency,
                onBack = { navigateBack() }
            )
        }

        is Screen.AssetDetail -> {
            val summary = portfolioSummary?.assetRecords?.find { it.record.id == screen.recordId }
            val priceHistory by database.priceHistoryDao().getHistoryByRecord(screen.recordId).collectAsState(initial = emptyList())
            val transactions by database.transactionDao().getTransactionsByRecordId(screen.recordId).collectAsState(initial = emptyList())
            if (summary != null) {
                AssetDetailScreen(
                    summary = summary,
                    priceHistory = priceHistory,
                    transactions = transactions,
                    baseCurrency = portfolioSummary?.baseCurrency ?: "CNY",
                    onBack = { navigateBack() },
                    onEdit = {
                        navigateTo(Screen.AddAssetRecord(
                            record = summary.record,
                            accountId = summary.record.accountId
                        ))
                    },
                    onSell = {
                        navigateTo(Screen.AddAssetRecord(
                            record = summary.record,
                            accountId = summary.record.accountId
                        ))
                    }
                )
            } else {
                navigateBack()
            }
        }

        is Screen.AssetTransactionHistory -> {
            val transactions by database.transactionDao().getTransactionsByRecordId(screen.recordId).collectAsState(initial = emptyList())
            TransactionHistoryScreen(
                transactions = transactions,
                accountName = screen.assetName,
                baseCurrency = portfolioSummary?.baseCurrency ?: "CNY",
                onBack = { navigateBack() }
            )
        }
    }
}

@Composable
private fun FinUnityBottomBar(
    selected: TopLevelTab,
    onSelect: (TopLevelTab) -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = selected == TopLevelTab.Main,
            onClick = { onSelect(TopLevelTab.Main) },
            icon = { Icon(Icons.Default.Home, contentDescription = "总览") },
            label = { Text("总览") }
        )
        NavigationBarItem(
            selected = selected == TopLevelTab.History,
            onClick = { onSelect(TopLevelTab.History) },
            icon = { Icon(Icons.Default.DateRange, contentDescription = "持仓") },
            label = { Text("持仓") }
        )
        NavigationBarItem(
            selected = selected == TopLevelTab.Accounts,
            onClick = { onSelect(TopLevelTab.Accounts) },
            icon = { Icon(Icons.Default.Person, contentDescription = "账户") },
            label = { Text("账户") }
        )
    }
}

@Composable
fun AccountPickerDialog(
    accounts: List<AccountSummary>,
    baseCurrency: String,
    onSelect: (String) -> Unit,
    onCreateAccount: () -> Unit,
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
                                    text = "${summary.account.type.displayName()} · ${summary.account.currency}",
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
        confirmButton = {
            TextButton(onClick = onCreateAccount) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("新增账户")
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
    if (amount.isNaN() || amount.isInfinite()) return "--"
    val safeAmount = amount
    return when (currency) {
        "CNY" -> "¥${String.format("%.2f", safeAmount)}"
        "USD" -> "$${String.format("%.2f", safeAmount)}"
        "HKD" -> "HK$${String.format("%.2f", safeAmount)}"
        else -> "${currency}${String.format("%.2f", safeAmount)}"
    }
}
