package com.finunity

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finunity.data.local.AppDatabase
import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AccountType
import com.finunity.data.repository.CsvImportRepository
import com.finunity.data.repository.HistoryRepository
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.HoldingSourceType
import com.finunity.data.local.entity.RiskBucket
import com.finunity.data.local.entity.defaultRiskBucket
import com.finunity.data.local.entity.CashFlowCategory
import com.finunity.ui.screens.AccountScreen
import com.finunity.ui.screens.AccountAssetsByAccountScreen
import com.finunity.ui.screens.AccountDetailScreen
import com.finunity.ui.screens.AmountVisibility
import com.finunity.ui.screens.AssetRecordScreen
import com.finunity.ui.screens.AssetDetailScreen
import com.finunity.ui.screens.CashFlowScreen
import com.finunity.ui.screens.HistoryScreen
import com.finunity.ui.screens.PriceChangeScreen
import com.finunity.ui.screens.PriceHistoryScreen
import com.finunity.ui.screens.PrivacyScreen
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
import com.finunity.ui.screens.MergedHoldingDetailScreen
import com.finunity.ui.screens.SettingsScreen
import com.finunity.ui.screens.RiskBucketDetailScreen
import com.finunity.ui.screens.TransactionHistoryScreen
import com.finunity.ui.screens.BackupScreen
import com.finunity.ui.screens.ImportCsvScreen
import com.finunity.ui.screens.FinancialReportScreen
import com.finunity.ui.screens.RecurringRulesScreen
import com.finunity.ui.screens.ReconciliationScreen
import com.finunity.ui.screens.ExportScreen
import com.finunity.data.repository.MonthlyChange
import com.finunity.data.repository.LedgerResult
import com.finunity.ui.theme.FinUnityTheme
import com.finunity.viewmodel.MainViewModel
import com.finunity.worker.PriceSyncWorker
import com.finunity.worker.ReviewReminderWorker
import com.finunity.worker.SnapshotWorker
import com.finunity.worker.RecurringRuleWorker
import com.finunity.data.repository.FinancialReportRepository
import com.finunity.viewmodel.ReconciliationResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.finunity.data.local.entity.formatTargetAllocation
import com.finunity.data.model.normalizeSecurityCode

class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase
    private var notificationsAllowed by mutableStateOf(false)

    // Android 13+ 通知权限请求
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) notificationsAllowed = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = AppDatabase.getDatabase(applicationContext)
        notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

        // 启动后台价格同步
        PriceSyncWorker.schedule(this)

        // 启动后台资产快照
        SnapshotWorker.scheduleDaily(this)

        // 启动月度复盘提醒
        ReviewReminderWorker.scheduleMonthly(this)
        RecurringRuleWorker.scheduleDaily(this)

        // 处理通知点击跳转
        val openScreen = intent.getStringExtra("open")

        setContent {
            FinUnityTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FinUnityApp(
                        database = database,
                        openScreen = openScreen,
                        notificationsAllowed = notificationsAllowed,
                        onRequestNotificationPermission = ::requestNotificationPermission
                    )
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

sealed class Screen {
    data object Main : Screen()
    data object Holdings : Screen()
    data class MergedHoldingDetail(val code: String) : Screen()
    data object Flows : Screen()
    data object Allocation : Screen()
    data object AddSource : Screen()
    data object OcrImport : Screen()
    data object ManualImport : Screen()
    data object TradeEntry : Screen()
    data object Settings : Screen()
    data object Privacy : Screen()
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
        val accountId: String? = null,
        val continueToAsset: Boolean = false,
        val allowDelete: Boolean = false
    ) : Screen()
    data class CashFlow(val accountId: String) : Screen()
    data class AddAssetRecord(val recordId: String? = null, val accountId: String) : Screen()
    data object History : Screen()
    data class AccountDetail(val accountId: String) : Screen()
    data class RiskBucketDetail(val bucketIndex: Int) : Screen()
    data class TransactionHistory(val accountId: String) : Screen()
    data class AssetTransactionHistory(val recordId: String, val assetName: String) : Screen()
    data class PriceHistory(val recordId: String, val assetName: String) : Screen()
    data class AssetDetail(val recordId: String, val initialTab: Int = 0) : Screen()
    data class Trade(val recordId: String, val isBuy: Boolean) : Screen()
    data object Backup : Screen()
    data object FinancialReport : Screen()
    data object RecurringRules : Screen()
    data object Reconciliation : Screen()
    data object Export : Screen()
}

private fun encodeScreen(screen: Screen): String = when (screen) {
    Screen.Main -> "Main"
    Screen.Holdings -> "Holdings"
    is Screen.MergedHoldingDetail -> "MergedHoldingDetail:${screen.code}"
    Screen.Flows -> "Flows"
    Screen.Allocation -> "Allocation"
    Screen.AddSource -> "AddSource"
    Screen.OcrImport -> "OcrImport"
    Screen.ManualImport -> "ManualImport"
    Screen.TradeEntry -> "TradeEntry"
    Screen.Settings -> "Settings"
    Screen.Privacy -> "Privacy"
    Screen.Planning -> "Planning"
    Screen.MonthlyReview -> "MonthlyReview"
    Screen.ExpenseSimulation -> "ExpenseSimulation"
    Screen.StressTest -> "StressTest"
    Screen.LandingPoints -> "LandingPoints"
    Screen.TargetAllocation -> "TargetAllocation"
    Screen.ImportCsv -> "ImportCsv"
    Screen.AccountHub -> "AccountHub"
    Screen.AccountAssetsByAccount -> "AccountAssetsByAccount"
    Screen.PriceChanges -> "PriceChanges"
    Screen.AllTransactions -> "AllTransactions"
    Screen.History -> "History"
    Screen.Backup -> "Backup"
    Screen.FinancialReport -> "FinancialReport"
    Screen.RecurringRules -> "RecurringRules"
    Screen.Reconciliation -> "Reconciliation"
    Screen.Export -> "Export"
    is Screen.AccountDetail -> "AccountDetail:${screen.accountId}"
    is Screen.TransactionHistory -> "TransactionHistory:${screen.accountId}"
    is Screen.AssetTransactionHistory -> "AssetTransactionHistory:${screen.recordId}:${screen.assetName}"
    is Screen.PriceHistory -> "PriceHistory:${screen.recordId}:${screen.assetName}"
    is Screen.AssetDetail -> "AssetDetail:${screen.recordId}:${screen.initialTab}"
    is Screen.Trade -> "Trade:${screen.recordId}:${screen.isBuy}"
    is Screen.CashFlow -> "CashFlow:${screen.accountId}"
    is Screen.AddAssetRecord -> "AddAssetRecord:${screen.recordId.orEmpty()}:${screen.accountId}"
    is Screen.AddAccount -> "AddAccount:${screen.accountId.orEmpty()}:${screen.continueToAsset}:${screen.allowDelete}"
    is Screen.RiskBucketDetail -> "RiskBucketDetail:${screen.bucketIndex}"
}

private val screenSaver = Saver<Screen, String>(
    save = { screen -> encodeScreen(screen) },
    restore = { key ->
        val parts = key.split(":", limit = 4)
        when (parts[0]) {
            "Main" -> Screen.Main
            "Holdings" -> Screen.Holdings
            "MergedHoldingDetail" -> parts.getOrNull(1)?.let { Screen.MergedHoldingDetail(it) }
            "Flows" -> Screen.Flows
            "Allocation" -> Screen.Allocation
            "AddSource" -> Screen.AddSource
            "OcrImport" -> Screen.OcrImport
            "ManualImport" -> Screen.ManualImport
            "AddAccount" -> Screen.AddAccount(
                accountId = parts.getOrNull(1)?.takeIf { it.isNotBlank() },
                continueToAsset = parts.getOrNull(2).toBoolean(),
                allowDelete = parts.getOrNull(3).toBoolean()
            )
            "TradeEntry" -> Screen.TradeEntry
            "Settings" -> Screen.Settings
            "Privacy" -> Screen.Privacy
            "Planning" -> Screen.Planning
            "MonthlyReview" -> Screen.MonthlyReview
            "ExpenseSimulation" -> Screen.ExpenseSimulation
            "StressTest" -> Screen.StressTest
            "LandingPoints" -> Screen.LandingPoints
            "TargetAllocation" -> Screen.TargetAllocation
            "ImportCsv" -> Screen.ImportCsv
            "AccountHub" -> Screen.AccountHub
            "AccountAssetsByAccount" -> Screen.AccountAssetsByAccount
            "PriceChanges" -> Screen.PriceChanges
            "AllTransactions" -> Screen.AllTransactions
            "History" -> Screen.History
            "Backup" -> Screen.Backup
            "FinancialReport" -> Screen.FinancialReport
            "RecurringRules" -> Screen.RecurringRules
            "Reconciliation" -> Screen.Reconciliation
            "Export" -> Screen.Export
            "AccountDetail" -> parts.getOrNull(1)?.let(Screen::AccountDetail)
            "TransactionHistory" -> parts.getOrNull(1)?.let(Screen::TransactionHistory)
            "AssetTransactionHistory" -> parts.getOrNull(1)?.let { id -> Screen.AssetTransactionHistory(id, parts.getOrNull(2).orEmpty()) }
            "PriceHistory" -> parts.getOrNull(1)?.let { id -> Screen.PriceHistory(id, parts.getOrNull(2).orEmpty()) }
            "AssetDetail" -> parts.getOrNull(1)?.let { id -> Screen.AssetDetail(id, parts.getOrNull(2)?.toIntOrNull() ?: 0) }
            "Trade" -> parts.getOrNull(1)?.let { id -> Screen.Trade(id, parts.getOrNull(2).toBoolean()) }
            "CashFlow" -> parts.getOrNull(1)?.let(Screen::CashFlow)
            "AddAssetRecord" -> Screen.AddAssetRecord(
                recordId = parts.getOrNull(1)?.takeIf { it.isNotBlank() },
                accountId = parts.getOrNull(2).orEmpty()
            )
            "RiskBucketDetail" -> parts.getOrNull(1)?.toIntOrNull()?.let(Screen::RiskBucketDetail)
            else -> null
        }
    }
)

private val screenListSaver = listSaver<List<Screen>, String>(
    save = { screens -> screens.map(::encodeScreen) },
    restore = { values -> values.mapNotNull { screenSaver.restore(it) } }
)

private enum class TopLevelTab {
    Overview,
    Assets,
    Mine
}

@Composable
fun FinUnityApp(
    database: AppDatabase,
    openScreen: String? = null,
    notificationsAllowed: Boolean = false,
    onRequestNotificationPermission: () -> Unit = {}
) {
    val viewModel: MainViewModel = viewModel(
        factory = MainViewModel.Factory(database)
    )
    val historyRepository = remember { HistoryRepository(database) }

    val portfolioSummary by viewModel.portfolioSummary.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val priceHealth by viewModel.priceHealth.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    AmountVisibility.visible = settings.amountsVisible

    val initialScreen = remember(openScreen) {
        if (openScreen == "review") Screen.MonthlyReview else Screen.Main
    }
    var currentScreen by rememberSaveable(stateSaver = screenSaver) { mutableStateOf<Screen>(initialScreen) }
    var navStack by rememberSaveable(stateSaver = screenListSaver) { mutableStateOf(emptyList()) }
    var pendingNewAccount by remember { mutableStateOf<Account?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 历史数据
    val snapshots by historyRepository.getRecentSnapshots(30).collectAsStateWithLifecycle(initialValue = emptyList())
    val allPriceHistory by database.priceHistoryDao().getAllHistory().collectAsStateWithLifecycle(initialValue = emptyList())
    val allPrices by database.priceDao().observeAllPrices().collectAsStateWithLifecycle(initialValue = emptyList())
    val allTransactions by database.transactionDao().getAllTransactions().collectAsStateWithLifecycle(initialValue = emptyList())
    val recurringRules by database.recurringRuleDao().getAll().collectAsStateWithLifecycle(initialValue = emptyList())
    var financialReport by remember { mutableStateOf<com.finunity.data.repository.FinancialReport?>(null) }
    val reconciliationResults = remember { mutableStateMapOf<String, ReconciliationResult>() }
    var monthlyChange by remember { mutableStateOf<MonthlyChange?>(null) }
    val lastPriceUpdated = remember(allPrices) {
        allPrices.maxOfOrNull { it.updatedAt }
    }

    fun showMessage(message: String) {
        scope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(error) {
        error?.takeIf { it.isNotBlank() }?.let(::showMessage)
    }

    // 加载月度变化
    LaunchedEffect(snapshots) {
        monthlyChange = historyRepository.getMonthlyChange()
    }

    LaunchedEffect(allTransactions, settings.baseCurrency) {
        financialReport = FinancialReportRepository(database).build(allTransactions, settings.baseCurrency)
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

    // 系统返回键与页面顶部返回按钮共用同一套手动导航栈。
    BackHandler(enabled = navStack.isNotEmpty()) {
        navigateBack()
    }

    fun startAddFlow() {
        // 所有新增、交易和资金事件统一从“添加记录”入口开始。
        if (portfolioSummary?.accounts.isNullOrEmpty()) {
            navigateTo(Screen.AddAccount(continueToAsset = true))
        } else {
            navigateTo(Screen.AddSource)
        }
    }

    val bottomBar: @Composable (TopLevelTab) -> Unit = { selected ->
        PrototypeBottomBar(
            selected = when (selected) {
                TopLevelTab.Overview -> PrototypeTab.Overview
                TopLevelTab.Assets -> PrototypeTab.Assets
                TopLevelTab.Mine -> PrototypeTab.Mine
            },
            onSelect = { tab ->
                switchTopLevel(when (tab) {
                    PrototypeTab.Overview -> Screen.Main
                    PrototypeTab.Assets -> Screen.Holdings
                    PrototypeTab.Mine -> Screen.AccountHub
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
                    priceStatus = priceHealth.status,
                    priceHistory = allPriceHistory,
                    missingCurrencies = portfolioSummary?.missingExchangeRateCurrencies.orEmpty(),
                    monthlyChange = monthlyChange,
                    onStartAddFlow = { startAddFlow() },
                    onRefreshPrices = { scope.launch { viewModel.refreshPrices() } },
                    onOpenAllocation = { navigateTo(Screen.Allocation) },
                    onOpenBucket = { bucketIndex -> navigateTo(Screen.RiskBucketDetail(bucketIndex)) },
                    onOpenDataQuality = { recordId -> navigateTo(Screen.AssetDetail(recordId)) },
                    onOpenAsset = { code -> navigateTo(Screen.MergedHoldingDetail(code)) },
                    onOpenMonthlyReview = { navigateTo(Screen.MonthlyReview) },
                    bottomBar = { bottomBar(TopLevelTab.Overview) }
                )
            }

            is Screen.Holdings -> {
                PrototypeHoldingsScreen(
                    portfolioSummary = portfolioSummary,
                    onRecordTrade = {
                        startAddFlow()
                    },
                    onOpenAsset = { key -> navigateTo(Screen.MergedHoldingDetail(key)) },
                    onOpenFlows = { navigateTo(Screen.Flows) },
                    bottomBar = { bottomBar(TopLevelTab.Assets) }
                )
            }

            is Screen.MergedHoldingDetail -> {
                val holding = portfolioSummary?.mergedHoldings?.firstOrNull { it.code == screen.code }
                val sources = portfolioSummary?.assetRecords.orEmpty().filter {
                    normalizeSecurityCode(it.record.securityCode.ifBlank { it.record.name }) == screen.code
                }
                if (holding != null) {
                    MergedHoldingDetailScreen(
                        holding = holding,
                        sources = sources,
                        baseCurrency = portfolioSummary?.baseCurrency ?: "CNY",
                        onOpenTrade = { recordId, isBuy -> navigateTo(Screen.Trade(recordId, isBuy)) },
                        onBack = { navigateBack() }
                    )
                } else {
                    navigateBack()
                }
            }

            is Screen.Flows -> {
                PrototypeFlowsScreen(
                    transactions = allTransactions,
                    accounts = portfolioSummary?.accounts ?: emptyList(),
                    baseCurrency = portfolioSummary?.baseCurrency ?: "CNY",
                    onRecordTrade = { startAddFlow() },
                    onBack = { navigateBack() },
                    bottomBar = {}
                )
            }

            is Screen.Allocation -> {
                PrototypeAllocationScreen(
                    portfolioSummary = portfolioSummary,
                    onSave = { values ->
                        val target = mapOf(
                            "DEFENSIVE" to (values[PrototypeBucket.DEFENSIVE] ?: 0f).toDouble(),
                            "BALANCED" to (values[PrototypeBucket.BALANCED] ?: 0f).toDouble(),
                            "AGGRESSIVE" to (values[PrototypeBucket.AGGRESSIVE] ?: 0f).toDouble()
                        )
                        viewModel.updateSettings(settings.copy(targetAllocation = formatTargetAllocation(target)))
                    },
                    onSaved = { showMessage("目标配置已保存") },
                    onOpenPlanning = { navigateTo(Screen.Planning) },
                    onBack = { navigateBack() },
                    bottomBar = {}
                )
            }

            is Screen.AddSource -> {
                PrototypeAddSourceScreen(
                    onBack = { navigateBack() },
                    onAddAccount = { navigateTo(Screen.AddAccount(continueToAsset = false)) },
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
                    },
                    onRecordTrade = {
                        if (portfolioSummary?.accounts.isNullOrEmpty()) {
                            showMessage("请先创建一个归属账户")
                            navigateTo(Screen.AddAccount(continueToAsset = true))
                        } else {
                            navigateTo(Screen.TradeEntry)
                        }
                    },
                    onRecordCashFlow = {
                        val accountId = portfolioSummary?.accounts?.firstOrNull()?.account?.id
                        if (accountId == null) {
                            showMessage("请先创建一个归属账户")
                            navigateTo(Screen.AddAccount(continueToAsset = false))
                        } else {
                            navigateTo(Screen.CashFlow(accountId))
                        }
                    }
                )
            }

            is Screen.OcrImport -> {
                PrototypeOcrImportScreen(
                    accounts = portfolioSummary?.accounts ?: emptyList(),
                    onBack = { navigateBack() },
                    onImport = { accountId, rows ->
                        scope.launch {
                            val records = rows.map { row ->
                                val code = normalizeSecurityCode(row.securityCode)
                                val assetType = assetTypeForScreenshot(row.name, code)
                                AssetRecord(
                                    accountId = accountId,
                                    assetType = assetType,
                                    riskBucket = assetType.defaultRiskBucket(),
                                    name = row.name.trim(),
                                    securityCode = code,
                                    instrumentId = row.instrumentId,
                                    quantity = row.quantityValue!!,
                                    cost = row.costValue!!,
                                    currentPrice = row.currentPriceValue!!,
                                    currency = row.currency,
                                    sourceType = HoldingSourceType.OCR,
                                    sourceAccountId = accountId,
                                    sourceRecordId = row.rawSecurityCode,
                                    importBatchId = "OCR:$accountId",
                                    sourceFingerprint = "OCR:$accountId:${row.currency.uppercase()}:$code"
                                )
                            }
                            val result = viewModel.importAssetRecordsBatch(records)
                            if (result.committed) {
                                showMessage("已导入 ${records.size} 项资产")
                                navigateBack()
                            } else {
                                val failed = result.rows.firstOrNull { it.error != null }
                                showMessage("导入已回滚：第 ${(failed?.rowIndex ?: 0) + 1} 行 ${failed?.error ?: "数据无效"}")
                            }
                        }
                    }
                )
            }

            is Screen.ManualImport -> {
                PrototypeManualEntryScreen(
                    accounts = portfolioSummary?.accounts ?: emptyList(),
                    onBack = { navigateBack() },
                    onImportWithScreenshot = { navigateTo(Screen.OcrImport) },
                    onSave = { record ->
                        scope.launch {
                            when (val result = viewModel.addAssetRecordAndWait(record)) {
                                is LedgerResult.Success -> {
                                    showMessage("资产已加入总览")
                                    navigateBack()
                                }
                                is LedgerResult.Error -> showMessage("保存失败：${result.message}")
                            }
                        }
                    }
                )
            }

            is Screen.TradeEntry -> {
                PrototypeTradeEntryScreen(
                    accounts = portfolioSummary?.accounts ?: emptyList(),
                    holdings = portfolioSummary?.mergedHoldings ?: emptyList(),
                    onBack = { navigateBack() },
                    onSave = { isBuy, accountId, name, securityCode, quantity, price, bucket, timestamp, currency, fee, note ->
                        scope.launch {
                            val errorMessage = viewModel.recordTradeBySecurityCode(
                                accountId = accountId,
                                securityCode = securityCode,
                                name = name,
                                isBuy = isBuy,
                                quantity = quantity,
                                price = price,
                                currency = currency,
                                riskBucket = when (bucket) {
                                    PrototypeBucket.DEFENSIVE -> RiskBucket.DEFENSIVE
                                    PrototypeBucket.BALANCED -> RiskBucket.BALANCED
                                    PrototypeBucket.AGGRESSIVE -> RiskBucket.AGGRESSIVE
                                },
                                timestamp = timestamp,
                                fee = fee,
                                note = note
                            )
                            if (errorMessage == null) {
                                showMessage(if (isBuy) "买入交易已记录" else "卖出交易已记录")
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
                    navigateTo(Screen.AssetDetail(recordId, initialTab = 2))
                },
                bottomBar = { bottomBar(TopLevelTab.Mine) }
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
                onOpenPrivacy = { navigateTo(Screen.Privacy) },
                onOpenReport = { navigateTo(Screen.FinancialReport) },
                onOpenRecurringRules = { navigateTo(Screen.RecurringRules) },
                onOpenReconciliation = { navigateTo(Screen.Reconciliation) },
                onOpenExport = { navigateTo(Screen.Export) },
                onOpenHoldingImport = { navigateTo(Screen.AddSource) },
                onOpenCsvImport = { navigateTo(Screen.ImportCsv) },
                onOpenBackup = { navigateTo(Screen.Backup) },
                onOpenPlanning = { navigateTo(Screen.Planning) },
                onOpenMonthlyReview = { navigateTo(Screen.MonthlyReview) },
                onOpenHistory = { navigateTo(Screen.History) },
                onOpenExpenseSimulation = { navigateTo(Screen.ExpenseSimulation) },
                onOpenStressTest = { navigateTo(Screen.StressTest) },
                onOpenLandingPoints = { navigateTo(Screen.LandingPoints) },
                onBack = { navigateBack() },
                notificationsAllowed = notificationsAllowed,
                onRequestNotificationPermission = onRequestNotificationPermission
            )
        }

        is Screen.Privacy -> {
            PrivacyScreen(onBack = { navigateBack() })
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
                transactions = allTransactions,
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

        is Screen.FinancialReport -> {
            FinancialReportScreen(report = financialReport, snapshots = snapshots, onBack = { navigateBack() })
        }

        is Screen.RecurringRules -> {
            RecurringRulesScreen(
                accounts = portfolioSummary?.accounts?.map { it.account } ?: emptyList(),
                rules = recurringRules,
                onSave = { rule -> scope.launch { database.recurringRuleDao().insert(rule) } },
                onDelete = { rule -> scope.launch { database.recurringRuleDao().delete(rule) } },
                onBack = { navigateBack() }
            )
        }

        is Screen.Reconciliation -> {
            ReconciliationScreen(
                accounts = portfolioSummary?.accounts?.map { it.account } ?: emptyList(),
                results = reconciliationResults,
                onCheck = { accountId -> scope.launch { reconciliationResults[accountId] = viewModel.reconcileAccountBalance(accountId) } },
                onBack = { navigateBack() }
            )
        }

        is Screen.Export -> {
            ExportScreen(database = database, baseCurrency = settings.baseCurrency, onBack = { navigateBack() })
        }

            is Screen.AccountHub -> {
                PrototypeAccountsScreen(
                    portfolioSummary = portfolioSummary,
                    onViewAccount = { navigateTo(Screen.AccountDetail(it)) },
                    onAddAccount = { navigateTo(Screen.AddAccount(continueToAsset = false)) },
                    onOpenSettings = { navigateTo(Screen.Settings) },
                    bottomBar = { bottomBar(TopLevelTab.Mine) }
                )
            }

        is Screen.AccountAssetsByAccount -> {
            AccountAssetsByAccountScreen(
                accountCount = portfolioSummary?.accounts?.size ?: 0,
                onAddAccount = { navigateTo(Screen.AddAccount(accountId = null, continueToAsset = false)) },
                onOpenImportCsv = { navigateTo(Screen.ImportCsv) },
                onOpenSettings = { navigateTo(Screen.Settings) },
                onOpenBackup = { navigateTo(Screen.Backup) },
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
            val editingAccount = screen.accountId?.let { id ->
                portfolioSummary?.accounts?.firstOrNull { it.account.id == id }?.account
            }
            val deleteRecords = portfolioSummary?.assetRecords.orEmpty().filter { it.record.accountId == editingAccount?.id }
            val deleteRecordIds = deleteRecords.map { it.record.id }.toSet()
            AccountScreen(
                account = editingAccount,
                allowDelete = screen.allowDelete,
                deleteAssetCount = deleteRecords.size,
                deleteTransactionCount = allTransactions.count { it.accountId == editingAccount?.id },
                deletePriceHistoryCount = allPriceHistory.count { it.recordId in deleteRecordIds },
                onSave = { account ->
                    if (screen.accountId == null) {
                        viewModel.addAccount(account)
                        showMessage("账户已添加")
                        if (screen.continueToAsset) {
                            pendingNewAccount = account
                            navigateTo(Screen.AddAssetRecord(recordId = null, accountId = account.id))
                        } else {
                            currentScreen = Screen.AccountDetail(account.id)
                        }
                    } else {
                        viewModel.updateAccount(account)
                        showMessage("账户已更新")
                        currentScreen = Screen.AccountDetail(account.id)
                    }
                },
                onDelete = { id ->
                    viewModel.deleteAccount(id)
                    showMessage("账户已删除")
                    currentScreen = Screen.AccountHub
                },
                onBack = { navigateBack() }
            )
        }

        is Screen.AddAssetRecord -> {
            val account = portfolioSummary?.accounts?.find { it.account.id == screen.accountId }?.account
                ?: pendingNewAccount?.takeIf { it.id == screen.accountId }
            val editingRecord = screen.recordId?.let { id ->
                portfolioSummary?.assetRecords?.firstOrNull { it.record.id == id }?.record
            }
            AssetRecordScreen(
                record = editingRecord,
                account = account,
                onSave = { record ->
                    if (screen.recordId == null) {
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
                onEditAccount = { navigateTo(Screen.AddAccount(accountId = screen.accountId, allowDelete = true)) },
                onRecordCashFlow = { navigateTo(Screen.CashFlow(screen.accountId)) },
                onAddRecord = { navigateTo(Screen.AddAssetRecord(recordId = null, accountId = screen.accountId)) },
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
                onAddAsset = { navigateTo(Screen.AddAssetRecord(recordId = null, accountId = screen.accountId)) },
                onSaveCashIn = { amount, category, note ->
                    scope.launch {
                        when (val result = viewModel.recordIncomeAndWait(screen.accountId, amount, category, note)) {
                            is LedgerResult.Success -> {
                                showMessage("${category.displayName}已记录")
                                currentScreen = Screen.AccountDetail(screen.accountId)
                            }
                            is LedgerResult.Error -> showMessage(result.message)
                        }
                    }
                },
                onSaveCashOut = { amount, category, note ->
                    scope.launch {
                        when (val result = viewModel.recordExpenseAndWait(screen.accountId, amount, category, note)) {
                            is LedgerResult.Success -> {
                                showMessage("${category.displayName}已记录")
                                currentScreen = Screen.AccountDetail(screen.accountId)
                            }
                            is LedgerResult.Error -> showMessage(result.message)
                        }
                    }
                },
                onSaveTransfer = { targetAccountId, amount, note ->
                    scope.launch {
                        when (val result = viewModel.transferCashAndWait(screen.accountId, targetAccountId, amount, note)) {
                            is LedgerResult.Success -> {
                                showMessage("转账已记录")
                                currentScreen = Screen.AccountDetail(screen.accountId)
                            }
                            is LedgerResult.Error -> showMessage(result.message)
                        }
                    }
                },
                onSaveLiabilityPayment = { amount, note ->
                    scope.launch {
                        when (val result = viewModel.recordLiabilityPaymentAndWait(screen.accountId, amount, note)) {
                            is LedgerResult.Success -> {
                                showMessage("还款已记录")
                                currentScreen = Screen.AccountDetail(screen.accountId)
                            }
                            is LedgerResult.Error -> showMessage(result.message)
                        }
                    }
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
            val transactions by database.transactionDao().getTransactionsByAccount(screen.accountId).collectAsStateWithLifecycle(initialValue = emptyList())
            TransactionHistoryScreen(
                transactions = transactions,
                accountName = accountSummary?.account?.name,
                accountNames = portfolioSummary?.accounts.orEmpty().associate { it.account.id to it.account.name },
                onBack = { navigateBack() }
            )
        }

        is Screen.AllTransactions -> {
            val transactions by database.transactionDao().getAllTransactions().collectAsStateWithLifecycle(initialValue = emptyList())
            TransactionHistoryScreen(
                transactions = transactions,
                accountName = "交易记录",
                accountNames = portfolioSummary?.accounts.orEmpty().associate { it.account.id to it.account.name },
                onBack = { navigateBack() }
            )
        }

        is Screen.PriceHistory -> {
            val priceHistory by database.priceHistoryDao().getHistoryByRecord(screen.recordId).collectAsStateWithLifecycle(initialValue = emptyList())
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
            val priceHistory by database.priceHistoryDao().getHistoryByRecord(screen.recordId).collectAsStateWithLifecycle(initialValue = emptyList())
            val transactions by database.transactionDao().getTransactionsByRecordId(screen.recordId).collectAsStateWithLifecycle(initialValue = emptyList())
            if (summary != null) {
                AssetDetailScreen(
                    summary = summary,
                    priceHistory = priceHistory,
                    transactions = transactions,
                    baseCurrency = portfolioSummary?.baseCurrency ?: "CNY",
                    initialTab = screen.initialTab,
                    onBack = { navigateBack() },
                    onEdit = {
                        navigateTo(Screen.AddAssetRecord(
                            recordId = summary.record.id,
                            accountId = summary.record.accountId
                        ))
                    },
                    onDelete = {
                        viewModel.deleteAssetRecord(summary.record.id)
                        showMessage("资产已删除")
                        navigateBack()
                    },
                    onRecordTrade = { navigateTo(Screen.Trade(summary.record.id, isBuy = true)) },
                    onViewTransactions = { navigateTo(Screen.AssetTransactionHistory(summary.record.id, summary.record.name)) },
                    onViewPrices = { navigateTo(Screen.PriceHistory(summary.record.id, summary.record.name)) }
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
                    onConfirmBuy = { qty, price, fee, timestamp, note ->
                        scope.launch {
                            when (val result = viewModel.buyMoreAssetRecordAndWait(screen.recordId, qty, price, fee, timestamp, note)) {
                                is LedgerResult.Success -> {
                                    showMessage("买入已记录")
                                    navigateBack()
                                }
                                is LedgerResult.Error -> showMessage(result.message)
                            }
                        }
                    },
                    onConfirmSell = { qty, price, fee, timestamp, note ->
                        scope.launch {
                            when (val result = viewModel.sellAssetRecordAndWait(screen.recordId, qty, price, fee, timestamp, note)) {
                                is LedgerResult.Success -> {
                                    showMessage("卖出已记录")
                                    navigateBack()
                                }
                                is LedgerResult.Error -> showMessage(result.message)
                            }
                        }
                    }
                )
            } else {
                navigateBack()
            }
        }

        is Screen.AssetTransactionHistory -> {
            val transactions by database.transactionDao().getTransactionsByRecordId(screen.recordId).collectAsStateWithLifecycle(initialValue = emptyList())
            TransactionHistoryScreen(
                transactions = transactions,
                accountName = screen.assetName,
                accountNames = portfolioSummary?.accounts.orEmpty().associate { it.account.id to it.account.name },
                onBack = { navigateBack() }
            )
        }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // 根页面的 Snackbar 不能覆盖各页面 Scaffold 中的 66dp 底部导航栏。
                // 同时避开手势导航区域，确保提示显示期间底部 Tab 仍可点击。
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 82.dp)
        )
    }
}

private fun assetTypeForScreenshot(name: String, securityCode: String): AssetType =
    if (name.contains("ETF", ignoreCase = true) || securityCode.matches(Regex("5\\d{5}"))) AssetType.ETF else AssetType.STOCK
