package com.finunity.data.repository

import android.content.Context
import com.finunity.data.local.AppDatabase
import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AccountType
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.Position
import com.finunity.data.local.entity.RiskBucket
import com.finunity.data.local.entity.Transaction
import com.finunity.data.local.entity.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
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

class CsvImportRepository(private val database: AppDatabase) {

    /**
     * 从 CSV 导入账户
     * 格式：name,type,currency,balance
     * 例如：我的券商,BROKER,USD,10000
     */
    suspend fun importAccounts(context: Context, fileName: String): CsvImportResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        var accountsImported = 0

        try {
            context.assets.open(fileName).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                    lines.drop(1).forEachIndexed { index, line ->
                        try {
                            val parts = parseCsvLine(line)
                            if (parts.size >= 4) {
                                val name = parts[0]
                                val type = AccountType.valueOf(parts[1].uppercase())
                                val currency = parts[2]
                                val balance = parts[3].toDoubleOrNull() ?: 0.0

                                val account = Account(
                                    name = name,
                                    type = type,
                                    currency = currency,
                                    balance = balance
                                )
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
            context.assets.open(fileName).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                    lines.drop(1).forEachIndexed { index, line ->
                        try {
                            val parts = parseCsvLine(line)
                            if (parts.size >= 5) {
                                val accountName = parts[0]
                                val symbol = parts[1].uppercase()
                                val shares = parts[2].toDoubleOrNull() ?: 0.0
                                val totalCost = parts[3].toDoubleOrNull() ?: 0.0
                                val currency = parts[4].uppercase()

                                // 查找对应账户
                                val accounts = database.accountDao().getAllAccounts().first()
                                val accountId = accounts.firstOrNull { it.name == accountName }?.id
                                if (accountId != null) {
                                    val position = Position(
                                        accountId = accountId,
                                        symbol = symbol,
                                        shares = shares,
                                        totalCost = totalCost,
                                        currency = currency
                                    )
                                    database.positionDao().insert(position)
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
            context.assets.open(fileName).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                    lines.drop(1).forEachIndexed { index, line ->
                        try {
                            val parts = parseCsvLine(line)
                            if (parts.size >= 8) {
                                val accountName = parts[0]

                                // 校验 assetType
                                val assetTypeStr = parts[1].uppercase()
                                val assetType = try { AssetType.valueOf(assetTypeStr) } catch (e: Exception) {
                                    errors.add("行 ${index + 2}: 无效的资产类型 '$assetTypeStr'，跳过此行")
                                    return@forEachIndexed
                                }

                                // 校验 riskBucket
                                val riskBucketStr = parts[2].uppercase()
                                val riskBucket = try { RiskBucket.valueOf(riskBucketStr) } catch (e: Exception) {
                                    errors.add("行 ${index + 2}: 无效的风险维度 '$riskBucketStr'，跳过此行")
                                    return@forEachIndexed
                                }

                                val name = parts[3]

                                // 校验数值字段
                                val quantity = parts[4].toDoubleOrNull()
                                if (quantity == null) {
                                    errors.add("行 ${index + 2}: 无效的数量 '${parts[4]}'，跳过此行")
                                    return@forEachIndexed
                                }

                                val cost = parts[5].toDoubleOrNull()
                                if (cost == null) {
                                    errors.add("行 ${index + 2}: 无效的成本 '${parts[5]}'，跳过此行")
                                    return@forEachIndexed
                                }

                                val currentPrice = parts[6].toDoubleOrNull()
                                if (currentPrice == null) {
                                    errors.add("行 ${index + 2}: 无效的价格 '${parts[6]}'，跳过此行")
                                    return@forEachIndexed
                                }

                                val currency = parts[7].uppercase()

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
            context.assets.open(fileName).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                    lines.drop(1).forEachIndexed { index, line ->
                        try {
                            val parts = parseCsvLine(line)
                            if (parts.size >= 7) {
                                val accountName = parts[0]
                                val symbol = parts[1].ifEmpty { null }?.uppercase()
                                val type = TransactionType.valueOf(parts[2].uppercase())
                                val shares = parts[3].toDoubleOrNull()
                                val price = parts[4].toDoubleOrNull()
                                val amount = parts[5].toDoubleOrNull() ?: 0.0
                                val currency = parts[6].uppercase()
                                val note = if (parts.size > 7) parts[7] else null

                                // 查找对应账户
                                val accounts = database.accountDao().getAllAccounts().first()
                                val accountId = accounts.firstOrNull { it.name == accountName }?.id
                                if (accountId != null) {
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
