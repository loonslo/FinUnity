package com.finunity

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import com.finunity.ui.screens.AccountAssetsByAccountScreen
import com.finunity.ui.screens.AccountDetailScreen
import com.finunity.ui.screens.AccountHubScreen
import com.finunity.ui.screens.AssetRecordScreen
import com.finunity.ui.screens.AssetDetailScreen
import com.finunity.ui.screens.CashFlowScreen
import com.finunity.ui.screens.HistoryScreen
import com.finunity.ui.screens.MainScreen
import com.finunity.ui.screens.formatCurrency
import com.finunity.ui.screens.PositionScreen
import com.finunity.ui.screens.PriceChangeScreen
import com.finunity.ui.screens.PriceHistoryScreen
import com.finunity.ui.screens.SettingsScreen
import com.finunity.ui.screens.RiskBucketDetailScreen
import com.finunity.ui.screens.TransactionHistoryScreen
import com.finunity.ui.screens.BackupScreen
import com.finunity.ui.screens.ImportCsvScreen
import com.finunity.data.repository.MonthlyChange
import com.finunity.ui.theme.FinUnityTheme
import com.finunity.viewmodel.MainViewModel
import com.finunity.worker.PriceSyncWorker
import com.finunity.worker.ReviewReminderWorker
import com.finunity.worker.SnapshotWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase

    // Android 13+ 通知权限请求
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> /* 拒绝不阻塞功能，仅不发提醒 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = AppDatabase.getDatabase(applicationContext)

        // 申请 Android 13+ 通知权限（首启弹窗，拒绝不崩溃）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 启动后台价格同步
        PriceSyncWorker.schedule(this)

        // 启动后台资产快照
        SnapshotWorker.scheduleDaily(this)

        // 启动月度复盘提醒
        ReviewReminderWorker.scheduleMonthly(this)

        // 处理通知点击跳转
        val openScreen = intent.getStringExtra("open")

        setContent {
            FinUnityTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FinUnityApp(database, openScreen)
                }
            }
        }
    }
}

sealed class Screen {
    data object Main : Screen()
    data object Settings : Screen()
    data object Planning : Screen()
    data object MonthlyReview : Screen()
    data object ExpenseSimulation : Screen()
    data object TargetAllocation : Screen()
    data object ImportCsv : Screen()
    data object AccountHub : Screen()
    data object AccountAssetsByAccount : Screen()
    data object PriceChanges : Screen()
    data object AllTransactions : Screen()
    data class AddAccount(
        val account: Account? = null,
        val continueToAsset: Boolean = false,
        val allowDelete: Boolean = false
    ) : Screen()
    data class CashFlow(val accountId: String) : Screen()
    data class AddPosition(val position: Position? = null, val accountId: String) : Screen()
    data class AddAssetRecord(val record: AssetRecord? = null, val accountId: String) : Screen()
    data object History : Screen()
    data class AccountDetail(val accountId: String) : Screen()
    data class RiskBucketDetail(val bucketIndex: Int) : Screen()
    data class TransactionHistory(val accountId: String) : Screen()
    data class AssetTransactionHistory(val recordId: String, val assetName: String) : Screen()
    data class PriceHistory(val recordId: String, val assetName: String) : Screen()
    data class AssetDetail(val recordId: String) : Screen()
    data object Backup : Screen()
}

private enum class TopLevelTab {
    Main,
    Ledger,
    Mine
}

@Composable
fun FinUnityApp(database: AppDatabase, openScreen: String? = null) {
    val viewModel: MainViewModel = viewModel(
        factory = MainViewModel.Factory(database)
    )
    val historyRepository = remember { HistoryRepository(database) }

    val portfolioSummary by viewModel.portfolioSummary.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    val initialScreen = remember(openScreen) {
        if (openScreen == "review") Screen.MonthlyReview else Screen.Main
    }
    var currentScreen by remember { mutableStateOf<Screen>(initialScreen) }
    var navStack by remember { mutableStateOf(listOf<Screen>()) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var pendingNewAccount by remember { mutableStateOf<Account?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 历史数据
    val snapshots by historyRepository.getRecentSnapshots(30).collectAsState(initial = emptyList())
    val allPriceHistory by database.priceHistoryDao().getAllHistory().collectAsState(initial = emptyList())
    var monthlyChange by remember { mutableStateOf<MonthlyChange?>(null) }
    val lastPriceUpdated = remember(allPriceHistory) {
        allPriceHistory.maxOfOrNull { it.timestamp }
    }

    fun showMessage(message: String) {
        scope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    // 加载月度变化
    LaunchedEffect(snapshots) {
        monthlyChange = historyRepository.getMonthlyChange()
    }

    // 导航到新页面时保存当前页面到栈
    fun navigateTo(screen: Screen) {
        navStack = navStack + currentScreen
        currentScreen = screen
    }

    fun switchTopLevel(screen: Screen) {
        navStack = emptyList()
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
            navigateTo(Screen.AddAccount(null, continueToAsset = true))
        } else {
            showAccountPicker = true
        }
    }

    val bottomBar: @Composable (TopLevelTab) -> Unit = { selected ->
        FinUnityBottomBar(
            selected = selected,
            onSelect = { tab ->
                switchTopLevel(when (tab) {
                    TopLevelTab.Main -> Screen.Main
                    TopLevelTab.Ledger -> Screen.AccountHub
                    TopLevelTab.Mine -> Screen.AccountAssetsByAccount
                })
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val screen = currentScreen) {
            is Screen.Main -> {
                MainScreen(
                    portfolioSummary = portfolioSummary,
                    isLoading = isLoading,
                    error = error,
                    lastPriceUpdated = lastPriceUpdated,
                    onboarded = viewModel.settings.value.onboarded,
                    onStartAddFlow = { startAddFlow() },
                    onEditAccount = { account ->
                        navigateTo(Screen.AccountDetail(account.id))
                    },
                    onViewRiskBucketDetail = { bucketIndex ->
                        navigateTo(Screen.RiskBucketDetail(bucketIndex))
                    },
                    onViewAccounts = { switchTopLevel(Screen.AccountHub) },
                    onRefreshPrices = { scope.launch { viewModel.refreshPrices() } },
                    onOpenPlanning = { navigateTo(Screen.Planning) },
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
                bottomBar = { bottomBar(TopLevelTab.Ledger) }
            )
        }

        is Screen.Settings -> {
            SettingsScreen(
                settings = viewModel.settings.value,
                onSave = { newSettings ->
                    viewModel.updateSettings(newSettings)
                    showMessage("设置已保存")
                    navigateBack()
                },
                onBack = { navigateBack() },
                onOpenTargetAllocation = { navigateTo(Screen.TargetAllocation) }
            )
        }

        is Screen.Planning -> {
            com.finunity.ui.screens.PlanningScreen(
                portfolioSummary = portfolioSummary,
                onBack = { navigateBack() },
                onEditTarget = { navigateTo(Screen.TargetAllocation) },
                onReview = { navigateTo(Screen.MonthlyReview) },
                onOpenHistory = { navigateTo(Screen.History) },
                onSimulateExpense = { navigateTo(Screen.ExpenseSimulation) }
            )
        }

        is Screen.ExpenseSimulation -> {
            com.finunity.ui.screens.ExpenseSimulationScreen(
                portfolioSummary = portfolioSummary,
                onBack = { navigateBack() }
            )
        }

        is Screen.MonthlyReview -> {
            com.finunity.ui.screens.MonthlyReviewScreen(
                portfolioSummary = portfolioSummary,
                monthlyChange = monthlyChange,
                onBack = { navigateBack() },
                onEditTarget = { navigateTo(Screen.TargetAllocation) }
            )
        }

        is Screen.TargetAllocation -> {
            com.finunity.ui.screens.TargetAllocationScreen(
                settings = viewModel.settings.value,
                onSave = { newSettings ->
                    viewModel.updateSettings(newSettings)
                    showMessage("目标配置已保存")
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
            val amountsVisible = viewModel.settings.value.amountsVisible
            AccountHubScreen(
                accounts = portfolioSummary?.accounts ?: emptyList(),
                assetRecords = portfolioSummary?.assetRecords ?: emptyList(),
                holdings = portfolioSummary?.holdings ?: emptyList(),
                baseCurrency = portfolioSummary?.baseCurrency ?: "CNY",
                onViewAccount = { navigateTo(Screen.AccountDetail(it)) },
                onAddAccount = { navigateTo(Screen.AddAccount(null, continueToAsset = false)) },
                onOpenTransactions = { navigateTo(Screen.AllTransactions) },
                onOpenPriceChanges = { navigateTo(Screen.PriceChanges) },
                amountsVisible = amountsVisible,
                onToggleAmounts = { viewModel.toggleAmountsVisible() },
                bottomBar = { bottomBar(TopLevelTab.Ledger) }
            )
        }

        is Screen.AccountAssetsByAccount -> {
            val amountsVisible = viewModel.settings.value.amountsVisible
            AccountAssetsByAccountScreen(
                accounts = portfolioSummary?.accounts ?: emptyList(),
                assetRecords = portfolioSummary?.assetRecords ?: emptyList(),
                holdings = portfolioSummary?.holdings ?: emptyList(),
                baseCurrency = portfolioSummary?.baseCurrency ?: "CNY",
                onAddAccount = { navigateTo(Screen.AddAccount(null, continueToAsset = false)) },
                onEditAccount = { account -> navigateTo(Screen.AccountDetail(account.id)) },
                onOpenImportCsv = { navigateTo(Screen.ImportCsv) },
                onOpenSettings = { navigateTo(Screen.Settings) },
                onOpenBackup = { navigateTo(Screen.Backup) },
                amountsVisible = amountsVisible,
                onToggleAmounts = { viewModel.toggleAmountsVisible() },
                bottomBar = { bottomBar(TopLevelTab.Mine) }
            )
        }

        is Screen.Backup -> {
            BackupScreen(
                database = database,
                onBack = { navigateBack() }
            )
        }

        is Screen.AddAccount -> {
            AccountScreen(
                account = screen.account,
                allowDelete = screen.allowDelete,
                onSave = { account ->
                    if (screen.account == null) {
                        viewModel.addAccount(account)
                        showMessage("账户已添加")
                        if (screen.continueToAsset) {
                            pendingNewAccount = account
                            navigateTo(Screen.AddAssetRecord(record = null, accountId = account.id))
                        } else {
                            navigateTo(Screen.AccountAssetsByAccount)
                        }
                    } else {
                        viewModel.updateAccount(account)
                        showMessage("账户已更新")
                        navigateTo(Screen.AccountAssetsByAccount)
                    }
                },
                onDelete = { id ->
                    viewModel.deleteAccount(id)
                    showMessage("账户已删除")
                    navigateTo(Screen.AccountAssetsByAccount)
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
                        showMessage("持仓已添加")
                    } else {
                        viewModel.updatePosition(position)
                        showMessage("持仓已更新")
                    }
                    navigateBack()
                },
                onDelete = { id ->
                    viewModel.deletePosition(id)
                    showMessage("持仓已删除")
                    navigateBack()
                },
                onSell = { id, shares ->
                    viewModel.sellPosition(id, shares)
                    showMessage("卖出已记录")
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
                        showMessage("资产已记录")
                    } else {
                        viewModel.updateAssetRecord(record)
                        showMessage("资产已更新")
                    }
                    val wasFirstAsset = pendingNewAccount != null
                    pendingNewAccount = null
                    currentScreen = if (wasFirstAsset) Screen.Main else Screen.AccountDetail(record.accountId)
                },
                onDelete = { id ->
                    viewModel.deleteAssetRecord(id)
                    showMessage("资产记录已删除")
                    currentScreen = Screen.AccountDetail(screen.accountId)
                },
                onSell = { id, quantity ->
                    viewModel.sellAssetRecord(id, quantity)
                    showMessage("卖出已记录")
                    currentScreen = Screen.AccountDetail(screen.accountId)
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
                assetRecords = portfolioSummary?.assetRecords ?: emptyList(),
                baseCurrency = portfolioSummary?.baseCurrency ?: "CNY",
                onBack = { navigateBack() },
                onEditAccount = { navigateTo(Screen.AddAccount(accountSummary?.account, allowDelete = true)) },
                onRecordCashFlow = { navigateTo(Screen.CashFlow(screen.accountId)) },
                onAddRecord = { navigateTo(Screen.AddAssetRecord(record = null, accountId = screen.accountId)) },
                onEditRecord = { record -> navigateTo(Screen.AssetDetail(record.id)) },
                onViewTransactions = { navigateTo(Screen.TransactionHistory(screen.accountId)) }
            )
        }

        is Screen.CashFlow -> {
            CashFlowScreen(
                accountId = screen.accountId,
                accounts = portfolioSummary?.accounts ?: emptyList(),
                baseCurrency = portfolioSummary?.baseCurrency ?: "CNY",
                onBack = { navigateBack() },
                onSaveCashIn = { amount, note ->
                    viewModel.recordCashIn(screen.accountId, amount, note)
                    showMessage("收入已记录")
                    currentScreen = Screen.AccountDetail(screen.accountId)
                },
                onSaveCashOut = { amount, note ->
                    viewModel.recordCashOut(screen.accountId, amount, note)
                    showMessage("支出已记录")
                    currentScreen = Screen.AccountDetail(screen.accountId)
                },
                onSaveTransfer = { targetAccountId, amount, note ->
                    viewModel.transferCash(screen.accountId, targetAccountId, amount, note)
                    showMessage("转账已记录")
                    currentScreen = Screen.AccountDetail(screen.accountId)
                }
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
                    onViewAccount = { accountId -> navigateTo(Screen.AccountDetail(accountId)) },
                    onViewAssetRecord = { recordId -> navigateTo(Screen.AssetDetail(recordId)) }
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

        is Screen.AllTransactions -> {
            val transactions by database.transactionDao().getAllTransactions().collectAsState(initial = emptyList())
            TransactionHistoryScreen(
                transactions = transactions,
                accountName = "交易流水",
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
                    onDelete = {
                        viewModel.deleteAssetRecord(summary.record.id)
                        showMessage("资产已删除")
                        navigateBack()
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
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
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
            icon = { Icon(Icons.Default.Home, contentDescription = "首页") },
            label = { Text("首页") }
        )
        NavigationBarItem(
            selected = selected == TopLevelTab.Ledger,
            onClick = { onSelect(TopLevelTab.Ledger) },
            icon = { Icon(Icons.Default.DateRange, contentDescription = "账本") },
            label = { Text("账本") }
        )
        NavigationBarItem(
            selected = selected == TopLevelTab.Mine,
            onClick = { onSelect(TopLevelTab.Mine) },
            icon = { Icon(Icons.Default.Person, contentDescription = "我的") },
            label = { Text("我的") }
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
