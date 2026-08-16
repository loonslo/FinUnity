package com.finunity.data.repository

import android.content.Context
import com.finunity.data.local.AppDatabase
import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AccountSourceType
import com.finunity.data.local.entity.AccountType
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.HoldingSourceType
import com.finunity.data.local.entity.RiskBucket
import com.finunity.data.local.entity.TransactionOrigin
import com.finunity.data.local.entity.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * CSV 导入结果
 */
data class CsvImportResult(
    val accountsImported: Int,
    val positionsImported: Int,
    val assetRecordsImported: Int,
    val transactionsImported: Int,
    val errors: List<String>
)

/**
 * CSV 导入仓库
 * 支持从 CSV 文件导入账户、持仓和交易流水
 */
/**
 * 解析 CSV 行
 * 支持带引号的字段、字段内逗号、空字段
 */
private fun parseCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    var current = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            c == '"' -> {
                if (inQuotes) {
                    // 转义引号或结束引号
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    inQuotes = true
                }
            }
            c == ',' && !inQuotes -> {
                result.add(current.toString().trim())
                current = StringBuilder()
            }
            else -> {
                current.append(c)
            }
        }
        i++
    }
    result.add(current.toString().trim())
    return result
}

private fun parseAccountType(value: String): AccountType? = when (value.trim().uppercase()) {
    "券商", "券商账户", "BROKER" -> AccountType.BROKER
    "银行", "银行账户", "BANK" -> AccountType.BANK
    "基金", "基金账户", "FUND" -> AccountType.FUND
    "现金管理", "CASH_MANAGEMENT" -> AccountType.CASH_MANAGEMENT
    "债券", "BOND" -> AccountType.BOND
    "保险", "INSURANCE" -> AccountType.INSURANCE
    "负债", "LIABILITY" -> AccountType.LIABILITY
    "其他", "OTHER" -> AccountType.OTHER
    else -> null
}

private fun parseCurrency(value: String): String = when (value.trim().uppercase()) {
    "人民币", "元", "CNY" -> "CNY"
    "美元", "USD" -> "USD"
    "港币", "HKD" -> "HKD"
    else -> value.trim().uppercase()
}

private fun parseAssetType(value: String): AssetType? = when (value.trim().uppercase()) {
    "股票", "STOCK" -> AssetType.STOCK
    "ETF" -> AssetType.ETF
    "基金", "FUND" -> AssetType.FUND
    "现金", "CASH" -> AssetType.CASH
    "定期", "定期存款", "TIME_DEPOSIT" -> AssetType.TIME_DEPOSIT
    else -> null
}

private fun parseRiskBucket(value: String, assetType: AssetType): RiskBucket? = when (value.trim().uppercase()) {
    "稳健", "CONSERVATIVE" -> RiskBucket.CONSERVATIVE
    "进取", "AGGRESSIVE" -> RiskBucket.AGGRESSIVE
    "保命", "INSURANCE" -> RiskBucket.INSURANCE
    "防守", "CASH", "" -> if (assetType == AssetType.CASH) RiskBucket.CASH else null
    else -> null
}

class CsvImportRepository(private val database: AppDatabase) {
    private val holdingLedger = HoldingLedgerRepository(database)

    /**
     * 从 CSV 导入账户
     * 格式：name,type,currency[,liabilityAmount]
     * 非负债账户只作为容器，金额应导入为账户下的资产记录。
     */
    suspend fun importAccounts(context: Context, fileName: String): CsvImportResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        var accountsImported = 0

        try {
            File(context.cacheDir, fileName).inputStream().use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                    lines.drop(1).forEachIndexed { index, line ->
                        try {
                            val parts = parseCsvLine(line)
                            if (parts.size >= 3) {
                                val name = parts[0]
                                if (name.isEmpty()) {
                                    errors.add("行 ${index + 2}: 账户名称为空，跳过此行")
                                    return@forEachIndexed
                                }

                                val typeStr = parts[1]
                                val type = parseAccountType(typeStr)
                                if (type == null) {
                                    errors.add("行 ${index + 2}: 无效的账户类型 '$typeStr'，跳过此行")
                                    return@forEachIndexed
                                }

                                val currency = parseCurrency(parts[2])
                                if (currency.isEmpty()) {
                                    errors.add("行 ${index + 2}: 币种为空，跳过此行")
                                    return@forEachIndexed
                                }

                                val balance = if (type == AccountType.LIABILITY) {
                                    val amountText = parts.getOrNull(3).orEmpty()
                                    val amount = amountText.toDoubleOrNull()
                                    if (amount == null) {
                                        errors.add("行 ${index + 2}: 负债账户需要有效金额，跳过此行")
                                        return@forEachIndexed
                                    }
                                    amount
                                } else {
                                    0.0
                                }

                                val account = Account(
                                    name = name,
                                    type = type,
                                    currency = currency,
                                    balance = balance,
                                    sourceType = AccountSourceType.CSV,
                                    externalSourceId = "CSV:$name:$currency"
                                )

                                // 检查是否已存在同名账户（防止重复导入）
                                val existingAccounts = database.accountDao().getAllAccounts().first()
                                val isDuplicate = existingAccounts.any { it.name == name }
                                if (isDuplicate) {
                                    errors.add("行 ${index + 2}: 账户 '$name' 已存在，跳过重复导入")
                                    return@forEachIndexed
                                }

                                database.accountDao().insert(account)
                                accountsImported++
                            }
                        } catch (e: Exception) {
                            errors.add("行 ${index + 2}: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("文件读取失败: ${e.message}")
        }

        CsvImportResult(
            accountsImported = accountsImported,
            positionsImported = 0,
            assetRecordsImported = 0,
            transactionsImported = 0,
            errors = errors
        )
    }

    /**
     * 从 CSV 导入持仓
     * 格式：accountName,symbol,shares,totalCost,currency
     * 例如：我的券商,AAPL,100,15000,USD
     */
    suspend fun importPositions(context: Context, fileName: String): CsvImportResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        var positionsImported = 0

        try {
            File(context.cacheDir, fileName).inputStream().use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                    lines.drop(1).forEachIndexed { index, line ->
                        try {
                            val parts = parseCsvLine(line)
                            if (parts.size >= 5) {
                                val accountName = parts[0]
                                if (accountName.isEmpty()) {
                                    errors.add("行 ${index + 2}: 账户名称为空，跳过此行")
                                    return@forEachIndexed
                                }

                                val symbol = parts[1].uppercase()
                                if (symbol.isEmpty()) {
                                    errors.add("行 ${index + 2}: 股票代码为空，跳过此行")
                                    return@forEachIndexed
                                }

                                val shares = parts[2].toDoubleOrNull()
                                if (shares == null || shares <= 0) {
                                    errors.add("行 ${index + 2}: 无效的股数 '${parts[2]}'，跳过此行")
                                    return@forEachIndexed
                                }

                                val totalCost = parts[3].toDoubleOrNull()
                                if (totalCost == null || totalCost < 0) {
                                    errors.add("行 ${index + 2}: 无效的总成本 '${parts[3]}'，跳过此行")
                                    return@forEachIndexed
                                }

                                val currency = parseCurrency(parts[4])
                                if (currency.isEmpty()) {
                                    errors.add("行 ${index + 2}: 币种为空，跳过此行")
                                    return@forEachIndexed
                                }

                                // 查找对应账户
                                val accounts = database.accountDao().getAllAccounts().first()
                                val accountId = accounts.firstOrNull { it.name == accountName }?.id
                                if (accountId != null) {
                                    val result = holdingLedger.upsertSnapshot(
                                        HoldingSnapshotCommand(
                                            record = AssetRecord(
                                                accountId = accountId,
                                                assetType = AssetType.STOCK,
                                                riskBucket = RiskBucket.AGGRESSIVE,
                                                name = symbol,
                                                securityCode = symbol,
                                                quantity = shares,
                                                cost = totalCost,
                                                currentPrice = totalCost / shares,
                                                currency = currency
                                            ),
                                            sourceType = HoldingSourceType.CSV,
                                            sourceAccountId = accountId,
                                            sourceRecordId = "$fileName:${index + 2}",
                                            importBatchId = fileName,
                                            sourceFingerprint = "CSV:$accountId:STOCK:$symbol"
                                        )
                                    )
                                    if (result is LedgerResult.Error) {
                                        errors.add("行 ${index + 2}: ${result.message}")
                                    } else {
                                        positionsImported++
                                    }
                                } else {
                                    errors.add("行 ${index + 2}: 找不到账户 '$accountName'")
                                }
                            }
                        } catch (e: Exception) {
                            errors.add("行 ${index + 2}: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("文件读取失败: ${e.message}")
        }

        CsvImportResult(
            accountsImported = 0,
            positionsImported = positionsImported,
            assetRecordsImported = 0,
            transactionsImported = 0,
            errors = errors
        )
    }

    /**
     * 从 CSV 导入资产记录（新模型）
     * 格式：accountName,assetType,riskBucket,name,quantity,cost,currentPrice,currency
     * 例如：我的券商,STOCK,AGGRESSIVE,AAPL,100,15000,18000,USD
     * assetType: STOCK, ETF, FUND, CASH, TIME_DEPOSIT
     * riskBucket: CONSERVATIVE, AGGRESSIVE, CASH
     */
    suspend fun importAssetRecords(context: Context, fileName: String): CsvImportResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        var recordsImported = 0

        try {
            File(context.cacheDir, fileName).inputStream().use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                    lines.drop(1).forEachIndexed { index, line ->
                        try {
                            val parts = parseCsvLine(line)
                            if (parts.size >= 8) {
                                val accountName = parts[0]

                                // 校验 assetType
                                val assetTypeStr = parts[1]
                                val assetType = parseAssetType(assetTypeStr)
                                if (assetType == null) {
                                    errors.add("行 ${index + 2}: 无效的资产类型 '$assetTypeStr'，跳过此行")
                                    return@forEachIndexed
                                }

                                // 校验 riskBucket
                                val riskBucketStr = parts[2]
                                val riskBucket = parseRiskBucket(riskBucketStr, assetType)
                                if (riskBucket == null) {
                                    errors.add("行 ${index + 2}: 无效的风险维度 '$riskBucketStr'，跳过此行")
                                    return@forEachIndexed
                                }

                                val name = if (assetType == AssetType.CASH) "现金" else parts[3]

                                // 校验数值字段
                                val quantity = parts[4].toDoubleOrNull()
                                if (quantity == null || quantity <= 0) {
                                    errors.add("行 ${index + 2}: 无效的数量 '${parts[4]}'，跳过此行")
                                    return@forEachIndexed
                                }

                                val cost = parts[5].toDoubleOrNull()
                                if (cost == null || cost < 0) {
                                    errors.add("行 ${index + 2}: 无效的成本 '${parts[5]}'，跳过此行")
                                    return@forEachIndexed
                                }

                                val currentPrice = parts[6].toDoubleOrNull()
                                if (currentPrice == null || currentPrice <= 0) {
                                    errors.add("行 ${index + 2}: 无效的价格 '${parts[6]}'，跳过此行")
                                    return@forEachIndexed
                                }

                                val isTradable = assetType in listOf(AssetType.STOCK, AssetType.ETF, AssetType.FUND)
                                if (isTradable && cost <= 0) {
                                    errors.add("行 ${index + 2}: 股票/ETF/基金成本必须大于 0，跳过此行")
                                    return@forEachIndexed
                                }

                                val currency = parseCurrency(parts[7])

                                // 查找对应账户
                                val accounts = database.accountDao().getAllAccounts().first()
                                val accountId = accounts.firstOrNull { it.name == accountName }?.id
                                if (accountId != null) {
                                    val code = if (assetType == AssetType.CASH) "" else name
                                    val result = holdingLedger.upsertSnapshot(
                                        HoldingSnapshotCommand(
                                            record = AssetRecord(
                                                accountId = accountId,
                                                assetType = assetType,
                                                riskBucket = riskBucket,
                                                name = name,
                                                securityCode = code,
                                                quantity = quantity,
                                                cost = cost,
                                                currentPrice = currentPrice,
                                                currency = currency
                                            ),
                                            sourceType = HoldingSourceType.CSV,
                                            sourceAccountId = accountId,
                                            sourceRecordId = "$fileName:${index + 2}",
                                            importBatchId = fileName,
                                            sourceFingerprint = "CSV:$accountId:${assetType.name}:$code"
                                        )
                                    )
                                    if (result is LedgerResult.Error) {
                                        errors.add("行 ${index + 2}: ${result.message}")
                                    } else {
                                        recordsImported++
                                    }
                                } else {
                                    errors.add("行 ${index + 2}: 找不到账户 '$accountName'")
                                }
                            }
                        } catch (e: Exception) {
                            errors.add("行 ${index + 2}: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("文件读取失败: ${e.message}")
        }

        CsvImportResult(
            accountsImported = 0,
            positionsImported = 0,
            assetRecordsImported = recordsImported,
            transactionsImported = 0,
            errors = errors
        )
    }

    /**
     * 从 CSV 导入交易流水
     * 格式：accountName,symbol,type,shares,price,amount,currency,note
     * type: BUY, SELL, DIVIDEND, FEE, TRANSFER_IN, TRANSFER_OUT, DEPOSIT, WITHDRAW
     */
    suspend fun importTransactions(context: Context, fileName: String): CsvImportResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        var transactionsImported = 0

        try {
            File(context.cacheDir, fileName).inputStream().use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                    lines.drop(1).forEachIndexed { index, line ->
                        try {
                            val parts = parseCsvLine(line)
                            if (parts.size >= 7) {
                                val accountName = parts[0]
                                if (accountName.isEmpty()) {
                                    errors.add("行 ${index + 2}: 账户名称为空，跳过此行")
                                    return@forEachIndexed
                                }

                                val symbol = parts[1].ifEmpty { null }?.uppercase()

                                val typeStr = parts[2].uppercase()
                                val type = try { TransactionType.valueOf(typeStr) } catch (e: Exception) {
                                    errors.add("行 ${index + 2}: 无效的交易类型 '$typeStr'，跳过此行")
                                    return@forEachIndexed
                                }

                                val shares = parts[3].toDoubleOrNull()
                                val price = parts[4].toDoubleOrNull()

                                val amount = parts[5].toDoubleOrNull()
                                if (amount == null || amount < 0) {
                                    errors.add("行 ${index + 2}: 无效的金额 '${parts[5]}'，跳过此行")
                                    return@forEachIndexed
                                }

                                val currency = parseCurrency(parts[6])
                                if (currency.isEmpty()) {
                                    errors.add("行 ${index + 2}: 币种为空，跳过此行")
                                    return@forEachIndexed
                                }

                                val note = if (parts.size > 7 && parts[7].isNotEmpty()) parts[7] else null

                                // 查找对应账户
                                val accounts = database.accountDao().getAllAccounts().first()
                                val accountId = accounts.firstOrNull { it.name == accountName }?.id
                                if (accountId != null) {
                                    val fingerprint = "CSV_TX:$fileName:${index + 2}"
                                    val result = when (type) {
                                        TransactionType.BUY, TransactionType.SELL -> {
                                            if (symbol.isNullOrBlank() || shares == null || shares <= 0 || price == null || price <= 0) {
                                                LedgerResult.Error("买卖流水需要证券编码、数量和成交价")
                                            } else if (kotlin.math.abs(shares * price - amount) > 0.01) {
                                                LedgerResult.Error("成交金额必须等于数量 × 成交价")
                                            } else {
                                                val existing = database.assetRecordDao().getRecordsByAccount(accountId).first()
                                                    .firstOrNull { it.securityCode.equals(symbol, ignoreCase = true) || it.name.equals(symbol, ignoreCase = true) }
                                                holdingLedger.recordTrade(
                                                    HoldingTradeCommand(
                                                        accountId = accountId,
                                                        securityCode = symbol,
                                                        name = existing?.name ?: symbol,
                                                        assetType = existing?.assetType ?: AssetType.ETF,
                                                        riskBucket = existing?.riskBucket ?: RiskBucket.AGGRESSIVE,
                                                        isBuy = type == TransactionType.BUY,
                                                        quantity = shares,
                                                        price = price,
                                                        note = note ?: "CSV 导入交易",
                                                        origin = TransactionOrigin.CSV_IMPORT,
                                                        sourceFingerprint = fingerprint
                                                    )
                                                )
                                            }
                                        }
                                        TransactionType.DEPOSIT,
                                        TransactionType.WITHDRAW,
                                        TransactionType.DIVIDEND,
                                        TransactionType.FEE,
                                        TransactionType.TRANSFER_IN,
                                        TransactionType.TRANSFER_OUT -> holdingLedger.recordCashMovement(
                                            accountId = accountId,
                                            amount = amount,
                                            type = type,
                                            note = note ?: "CSV 导入${type.name}",
                                            origin = TransactionOrigin.CSV_IMPORT,
                                            sourceFingerprint = fingerprint
                                        )
                                    }
                                    if (result is LedgerResult.Error) {
                                        errors.add("行 ${index + 2}: ${result.message}")
                                    } else {
                                        transactionsImported++
                                    }
                                } else {
                                    errors.add("行 ${index + 2}: 找不到账户 '$accountName'")
                                }
                            }
                        } catch (e: Exception) {
                            errors.add("行 ${index + 2}: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("文件读取失败: ${e.message}")
        }

        CsvImportResult(
            accountsImported = 0,
            positionsImported = 0,
            assetRecordsImported = 0,
            transactionsImported = transactionsImported,
            errors = errors
        )
    }
}
