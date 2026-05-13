package com.finunity.data.repository

import android.content.Context
import com.finunity.data.local.AppDatabase
import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AccountType
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.PriceHistory
import com.finunity.data.local.entity.Position
import com.finunity.data.local.entity.RiskBucket
import com.finunity.data.local.entity.Transaction
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
    "防守", "CASH", "" -> if (assetType == AssetType.CASH) RiskBucket.CASH else null
    else -> null
}

class CsvImportRepository(private val database: AppDatabase) {

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
                                    balance = balance
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
                                    // 检查是否已存在同名持仓（防止重复导入）
                                    val existingPositions = database.positionDao().getAllPositions().first()
                                    val isDuplicate = existingPositions.any {
                                        it.accountId == accountId && it.symbol == symbol &&
                                            kotlin.math.abs(it.shares - shares) < 0.0001
                                    }
                                    if (isDuplicate) {
                                        errors.add("行 ${index + 2}: 持仓 '$symbol' 在账户 '$accountName' 中已存在，跳过重复导入")
                                        return@forEachIndexed
                                    }

                                    val position = Position(
                                        accountId = accountId,
                                        symbol = symbol,
                                        shares = shares,
                                        totalCost = totalCost,
                                        currency = currency
                                    )
                                    database.positionDao().insert(position)

                                    // 补录初始 BUY 流水
                                    val averageCost = if (shares > 0) totalCost / shares else 0.0
                                    val buyTransaction = Transaction(
                                        accountId = accountId,
                                        symbol = symbol,
                                        type = TransactionType.BUY,
                                        shares = shares,
                                        price = averageCost,
                                        amount = totalCost,
                                        currency = currency,
                                        note = "CSV 导入初始化"
                                    )
                                    database.transactionDao().insert(buyTransaction)
                                    positionsImported++
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
                                    // 检查是否已存在同名资产记录（防止重复导入）
                                    val existingRecords = database.assetRecordDao().getRecordsByAccount(accountId).first()
                                    val isDuplicate = existingRecords.any {
                                        it.name == name && it.assetType == assetType &&
                                            kotlin.math.abs(it.quantity - quantity) < 0.0001
                                    }
                                    if (isDuplicate) {
                                        errors.add("行 ${index + 2}: 资产 '$name' 已存在，跳过重复导入")
                                        return@forEachIndexed
                                    }

                                    val record = AssetRecord(
                                        accountId = accountId,
                                        assetType = assetType,
                                        riskBucket = riskBucket,
                                        name = name,
                                        quantity = quantity,
                                        cost = cost,
                                        currentPrice = currentPrice,
                                        currency = currency
                                    )
                                    database.assetRecordDao().insert(record)

                                    if (isTradable) {
                                        val averageCost = cost / quantity

                                        // 补录初始 BUY 流水，与 MainViewModel.addAssetRecord 保持一致
                                        val buyTransaction = Transaction(
                                            accountId = accountId,
                                            symbol = name,
                                            type = TransactionType.BUY,
                                            shares = quantity,
                                            price = averageCost,
                                            amount = cost,
                                            currency = currency,
                                            note = "CSV 导入初始化",
                                            recordId = record.id
                                        )
                                        database.transactionDao().insert(buyTransaction)

                                        // 补录初始价格历史：price/cost 都是单位价格口径
                                        val priceHistory = PriceHistory(
                                            recordId = record.id,
                                            price = currentPrice,
                                            cost = averageCost
                                        )
                                        database.priceHistoryDao().insert(priceHistory)
                                    }
                                    recordsImported++
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
                                    // 检查是否已存在相同的交易流水（防止重复导入）
                                    val existingTransactions = database.transactionDao().getTransactionsByAccount(accountId).first()
                                    val isDuplicate = existingTransactions.any {
                                        it.symbol == symbol && it.type == type &&
                                            it.shares != null && shares != null && kotlin.math.abs(it.shares - shares) < 0.0001 &&
                                            it.price != null && price != null && kotlin.math.abs(it.price - price) < 0.0001 &&
                                            kotlin.math.abs(it.amount - amount) < 0.0001
                                    }
                                    if (isDuplicate) {
                                        errors.add("行 ${index + 2}: 交易流水已存在，跳过重复导入")
                                        return@forEachIndexed
                                    }

                                    val transaction = Transaction(
                                        accountId = accountId,
                                        symbol = symbol,
                                        type = type,
                                        shares = shares,
                                        price = price,
                                        amount = amount,
                                        currency = currency,
                                        note = note
                                    )
                                    database.transactionDao().insert(transaction)
                                    transactionsImported++
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
