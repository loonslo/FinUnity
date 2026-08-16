package com.finunity

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.finunity.data.local.AppDatabase
import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AccountType
import com.finunity.data.local.entity.Position
import com.finunity.data.repository.CsvImportRepository
import com.finunity.data.repository.HistoryRepository
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.RiskBucket
import com.finunity.ui.screens.AccountScreen
import com.finunity.ui.screens.AccountAssetsByAccountScreen
import com.finunity.ui.screens.AccountDetailScreen
import com.finunity.ui.screens.AmountVisibility
import com.finunity.ui.screens.AssetRecordScreen
import com.finunity.ui.screens.AssetDetailScreen
import com.finunity.ui.screens.CashFlowScreen
import com.finunity.ui.screens.HistoryScreen
import com.finunity.ui.screens.MainScreen
import com.finunity.ui.screens.PositionScreen
import com.finunity.ui.screens.PriceChangeScreen
import com.finunity.ui.screens.PriceHistoryScreen
import com.finunity.ui.screens.PrototypeAccountsScreen
import com.finunity.ui.screens.PrototypeAddSourceScreen
import com.finunity.ui.screens.PrototypeAllocationScreen
import com.finunity.ui.screens.PrototypeBottomBar
import com.finunity.ui.screens.PrototypeBucket
import com.finunity.ui.screens.PrototypeHoldingsScreen
import com.finunity.ui.screens.PrototypeFlowsScreen
import com.finunity.ui.screens.PrototypeManualEntryScreen
import com.finunity.ui.screens.PrototypeOcrImportScreen
import com.finunity.ui.screens.PrototypeOverviewScreen
import com.finunity.ui.screens.PrototypeTab
import com.finunity.ui.screens.PrototypeTradeEntryScreen
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
import com.finunity.data.local.entity.parseTargetAllocation
import com.finunity.data.model.normalizeSecurityCode

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
    data object Holdings : Screen()
    data object Flows : Screen()
    data object Allocation : Screen()
    data object AddSource : Screen()
    data object OcrImport : Screen()
    data object ManualImport : Screen()
    data object TradeEntry : Screen()
    data object Settings : Screen()
    data object Planning : Screen()
    data object MonthlyReview : Screen()
    data object ExpenseSimulation : Screen()
    data object StressTest : Screen()
    data object LandingPoints : Screen()
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
    data class Trade(val recordId: String, val isBuy: Boolean) : Screen()
    data object Backup : Screen()
}

private enum class TopLevelTab {
    Overview,
    Holdings,
    Flows,
    Allocation,
    Accounts
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
    val settings by viewModel.settings.collectAsState()
    AmountVisibility.visible = settings.amountsVisible

    val initialScreen = remember(openScreen) {
        if (openScreen == "review") Screen.MonthlyReview else Screen.Main
    }
    var currentScreen by remember { mutableStateOf<Screen>(initialScreen) }
    var navStack by remember { mutableStateOf(listOf<Screen>()) }
    var pendingNewAccount by remember { mutableStateOf<Account?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 历史数据
    val snapshots by historyRepository.getRecentSnapshots(30).collectAsState(initial = emptyList())
    val allPriceHistory by database.priceHistoryDao().getAllHistory().collectAsState(initial = emptyList())
    val allTransactions by database.transactionDao().getAllTransactions().collectAsState(initial = emptyList())
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

    fun startAddFlow() {
        // 原型要求所有新增数据先经过统一的三种接入方式入口。
        navigateTo(Screen.AddSource)
    }

    val bottomBar: @Composable (TopLevelTab) -> Unit = { selected ->
        PrototypeBottomBar(
            selected = when (selected) {
                TopLevelTab.Overview -> PrototypeTab.Overview
                TopLevelTab.Holdings -> PrototypeTab.Holdings
                TopLevelTab.Flows -> PrototypeTab.Flows
                TopLevelTab.Allocation -> PrototypeTab.Allocation
                TopLevelTab.Accounts -> PrototypeTab.Accounts
            },
            onSelect = { tab ->
                switchTopLevel(when (tab) {
                    PrototypeTab.Overview -> Screen.Main
                    PrototypeTab.Holdings -> Screen.Holdings
                    PrototypeTab.Flows -> Screen.Flows
                    PrototypeTab.Allocation -> Screen.Allocation
                    PrototypeTab.Accounts -> Screen.AccountHub
                })
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val screen = currentScreen) {
            is Screen.Main -> {
                PrototypeOverviewScreen(
                    portfolioSummary = portfolioSummary,
                    isLoading = isLoading,
                    lastPriceUpdated = lastPriceUpdated,
                    onStartAddFlow = { startAddFlow() },
                    onRefreshPrices = { scope.launch { viewModel.refreshPrices() } },
                    bottomBar = { bottomBar(TopLevelTab.Overview) }
                )
            }

            is Screen.Holdings -> {
                PrototypeHoldingsScreen(
                    portfolioSummary = portfolioSummary,
                    onOpenAsset = { key ->
                        val asset = portfolioSummary?.assetRecords?.firstOrNull {
                            normalizeSecurityCode(it.record.securityCode.ifBlank { it.record.name }) == key ||
                                it.record.name.equals(key, ignoreCase = true)
                        }
                        if (asset != null) navigateTo(Screen.AssetDetail(asset.record.id))
                    },
                    bottomBar = { bottomBar(TopLevelTab.Holdings) }
                )
            }

            is Screen.Flows -> {
                PrototypeFlowsScreen(
                    transactions = allTransactions,
                    accounts = portfolioSummary?.accounts ?: emptyList(),
                    baseCurrency = portfolioSummary?.baseCurrency ?: "CNY",
                    onRecordTrade = {
                        if (portfolioSummary?.accounts.isNullOrEmpty()) {
                            showMessage("请先添加成交账户")
                            startAddFlow()
                        } else {
                            navigateTo(Screen.TradeEntry)
                        }
                    },
                    bottomBar = { bottomBar(TopLevelTab.Flows) }
                )
            }

            is Screen.Allocation -> {
                PrototypeAllocationScreen(
                    portfolioSummary = portfolioSummary,
                    onSave = { values ->
                        val old = parseTargetAllocation(settings.targetAllocation)
                        val oldStable = (old["CONSERVATIVE"] ?: 0.0) + (old["INSURANCE"] ?: 0.0)
                        val insuranceShare = if (oldStable > 0) (old["INSURANCE"] ?: 0.0) / oldStable else 0.0
                        val stable = values[PrototypeBucket.BALANCED] ?: 0f
                        val defensive = values[PrototypeBucket.DEFENSIVE] ?: 0f
                        val aggressive = values[PrototypeBucket.AGGRESSIVE] ?: 0f
                        val conservative = stable * (1f - insuranceShare.toFloat())
                        val insurance = stable * insuranceShare.toFloat()
                        viewModel.updateSettings(settings.copy(targetAllocation = "CASH:$defensive,CONSERVATIVE:$conservative,AGGRESSIVE:$aggressive,INSURANCE:$insurance"))
                    },
                    onSaved = { showMessage("目标配置已保存") },
                    bottomBar = { bottomBar(TopLevelTab.Allocation) }
                )
            }

            is Screen.AddSource -> {
                PrototypeAddSourceScreen(
                    accounts = portfolioSummary?.accounts ?: emptyList(),
                    onBack = { navigateBack() },
                    onBrokerConnect = { account ->
                        viewModel.addAccount(account)
                        showMessage("${account.name} 已接入")
                        navigateBack()
                    },
                    onScreenshot = {
                        if (portfolioSummary?.accounts.isNullOrEmpty()) {
                            showMessage("请先创建一个归属账户")
                            navigateTo(Screen.AddAccount(continueToAsset = true))
                        } else {
                            navigateTo(Screen.OcrImport)
                        }
                    },
                    onManual = {
                        if (portfolioSummary?.accounts.isNullOrEmpty()) {
                            showMessage("请先创建一个归属账户")
                            navigateTo(Screen.AddAccount(continueToAsset = true))
                        } else {
                            navigateTo(Screen.ManualImport)
                        }
                    }
                )
            }

            is Screen.OcrImport -> {
                PrototypeOcrImportScreen(
                    accounts = portfolioSummary?.accounts ?: emptyList(),
                    onBack = { navigateBack() },
                    onImport = { accountId, rows ->
                        rows.forEach { row ->
                            val currentPrice = if (row.quantity > 0) row.marketValue / row.quantity else 0.0
                            viewModel.addAssetRecord(
                                AssetRecord(
                                    accountId = accountId,
                                    assetType = AssetType.ETF,
                                    riskBucket = RiskBucket.AGGRESSIVE,
                                    name = row.name,
                                    securityCode = row.securityCode,
                                    quantity = row.quantity,
                                    cost = row.marketValue,
                                    currentPrice = currentPrice,
                                    currency = "CNY"
                                )
                            )
                        }
                        showMessage("已导入 ${rows.size} 项持仓")
                        navigateBack()
                    }
                )
            }

            is Screen.ManualImport -> {
                PrototypeManualEntryScreen(
                    accounts = portfolioSummary?.accounts ?: emptyList(),
                    onBack = { navigateBack() },
                    onSave = { record ->
                        viewModel.addAssetRecord(record)
                        showMessage("持仓已加入汇总")
                        navigateBack()
                    }
                )
            }

            is Screen.TradeEntry -> {
                PrototypeTradeEntryScreen(
                    accounts = portfolioSummary?.accounts ?: emptyList(),
                    holdings = portfolioSummary?.mergedHoldings ?: emptyList(),
                    onBack = { navigateBack() },
                    onSave = { isBuy, accountId, name, securityCode, quantity, price, bucket, timestamp ->
                        scope.launch {
                            val errorMessage = viewModel.recordTradeBySecurityCode(
                                accountId = accountId,
                                securityCode = securityCode,
                                name = name,
                                isBuy = isBuy,
                                quantity = quantity,
                                price = price,
                                riskBucket = when (bucket) {
                                    PrototypeBucket.DEFENSIVE -> RiskBucket.CASH
                                    PrototypeBucket.BALANCED -> RiskBucket.CONSERVATIVE
                                    PrototypeBucket.AGGRESSIVE -> RiskBucket.AGGRESSIVE
                                },
                                timestamp = timestamp
                            )
                            if (errorMessage == null) {
                                showMessage(if (isBuy) "买入流水已记录" else "卖出流水已记录")
                                navigateBack()
                            } else {
                                showMessage(errorMessage)
                            }
                        }
                    }
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
                bottomBar = { bottomBar(TopLevelTab.Accounts) }
            )
        }

        is Screen.Settings -> {
            SettingsScreen(
                settings = settings,
                onSave = { newSettings ->
                    viewModel.updateSettings(newSettings)
                    showMessage("设置已保存")
                    navigateBack()
                },
                onBack = { navigateBack() }
            )
        }

        is Screen.Planning -> {
            com.finunity.ui.screens.PlanningScreen(
                portfolioSummary = portfolioSummary,
                onBack = { navigateBack() },
                onEditTarget = { navigateTo(Screen.TargetAllocation) },
                onReview = { navigateTo(Screen.MonthlyReview) },
                onOpenHistory = { navigateTo(Screen.History) },
                onSimulateExpense = { navigateTo(Screen.ExpenseSimulation) },
                onOpenLandingPoints = { navigateTo(Screen.LandingPoints) },
                onOpenStressTest = { navigateTo(Screen.StressTest) }
            )
        }

        is Screen.LandingPoints -> {
            com.finunity.ui.screens.LandingPointScreen(
                portfolioSummary = portfolioSummary,
                onBack = { navigateBack() },
                onSaveTarget = { target ->
                    viewModel.saveAllocationTarget(target)
                    showMessage("落点目标已保存")
                },
                onDeleteTarget = { subCategory ->
                    viewModel.deleteAllocationTarget(subCategory)
                    showMessage("落点目标已删除")
                }
            )
        }

        is Screen.ExpenseSimulation -> {
            com.finunity.ui.screens.ExpenseSimulationScreen(
                portfolioSummary = portfolioSummary,
                onBack = { navigateBack() }
            )
        }

        is Screen.StressTest -> {
            com.finunity.ui.screens.StressTestScreen(
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
                settings = settings,
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
            PrototypeAccountsScreen(
                portfolioSummary = portfolioSummary,
                onViewAccount = { navigateTo(Screen.AccountDetail(it)) },
                onAddSource = { navigateTo(Screen.AddSource) },
                bottomBar = { bottomBar(TopLevelTab.Accounts) }
            )
        }

        is Screen.AccountAssetsByAccount -> {
            AccountAssetsByAccountScreen(
                accountCount = portfolioSummary?.accounts?.size ?: 0,
                onAddAccount = { navigateTo(Screen.AddAccount(null, continueToAsset = false)) },
                onOpenImportCsv = { navigateTo(Screen.ImportCsv) },
                onOpenSettings = { navigateTo(Screen.Settings) },
                onOpenBackup = { navigateTo(Screen.Backup) },
                bottomBar = { bottomBar(TopLevelTab.Accounts) }
            )
        }

        is Screen.Backup -> {
            BackupScreen(
                database = database,
                onBack = { navigateBack() }
            )
        }

        is Screen.AddAccount -> {
            val deleteRecords = portfolioSummary?.assetRecords.orEmpty().filter { it.record.accountId == screen.account?.id }
            val deleteRecordIds = deleteRecords.map { it.record.id }.toSet()
            AccountScreen(
                account = screen.account,
                allowDelete = screen.allowDelete,
                deleteAssetCount = deleteRecords.size,
                deleteTransactionCount = allTransactions.count { it.accountId == screen.account?.id },
                deletePriceHistoryCount = allPriceHistory.count { it.recordId in deleteRecordIds },
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
                onAddAsset = { navigateTo(Screen.AddAssetRecord(record = null, accountId = screen.accountId)) },
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
                accountNames = portfolioSummary?.accounts.orEmpty().associate { it.account.id to it.account.name },
                onBack = { navigateBack() }
            )
        }

        is Screen.AllTransactions -> {
            val transactions by database.transactionDao().getAllTransactions().collectAsState(initial = emptyList())
            TransactionHistoryScreen(
                transactions = transactions,
                accountName = "交易流水",
                baseCurrency = portfolioSummary?.baseCurrency ?: "CNY",
                accountNames = portfolioSummary?.accounts.orEmpty().associate { it.account.id to it.account.name },
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
                    },
                    onBuy = { navigateTo(Screen.Trade(summary.record.id, isBuy = true)) },
                    onSell = { navigateTo(Screen.Trade(summary.record.id, isBuy = false)) }
                )
            } else {
                navigateBack()
            }
        }

        is Screen.Trade -> {
            val tradeSummary = portfolioSummary?.assetRecords?.find { it.record.id == screen.recordId }
            if (tradeSummary != null) {
                com.finunity.ui.screens.TradeScreen(
                    summary = tradeSummary,
                    isBuy = screen.isBuy,
                    onBack = { navigateBack() },
                    onConfirmBuy = { qty, price ->
                        viewModel.buyMoreAssetRecord(screen.recordId, qty, price)
                        showMessage("买入已记录")
                        navigateBack()
                    },
                    onConfirmSell = { qty, price, fee, timestamp, note ->
                        viewModel.sellAssetRecord(screen.recordId, qty, price, fee, timestamp, note)
                        showMessage("卖出已记录")
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
                accountNames = portfolioSummary?.accounts.orEmpty().associate { it.account.id to it.account.name },
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
