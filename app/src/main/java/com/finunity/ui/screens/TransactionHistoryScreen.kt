package com.finunity.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finunity.data.local.entity.Transaction
import com.finunity.data.local.entity.TransactionType
import com.finunity.data.model.displayName
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    transactions: List<Transaction>,
    accountName: String?,
    baseCurrency: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    var typeFilter by remember { mutableStateOf<TransactionType?>(null) }
    val filteredTransactions = remember(transactions, typeFilter) {
        if (typeFilter == null) transactions
        else transactions.filter { it.type == typeFilter }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // 筛选栏
            item {
                val filterOptions = listOf(
                    null to "全部",
                    TransactionType.BUY to "买入",
                    TransactionType.SELL to "卖出",
                    TransactionType.DEPOSIT to "入金",
                    TransactionType.WITHDRAW to "出金",
                    TransactionType.TRANSFER_IN to "转入",
                    TransactionType.TRANSFER_OUT to "转出",
                    TransactionType.DIVIDEND to "分红",
                    TransactionType.FEE to "手续费"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filterOptions.forEach { (type, label) ->
                        FilterChip(
                            selected = typeFilter == type,
                            onClick = { typeFilter = type },
                            label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            if (filteredTransactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (typeFilter != null) "无此类交易记录" else "暂无交易记录",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                items(filteredTransactions) { transaction ->
                    TransactionItem(
                        transaction = transaction,
                        baseCurrency = baseCurrency,
                        dateFormat = dateFormat
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun TransactionItem(
    transaction: Transaction,
    baseCurrency: String,
    dateFormat: SimpleDateFormat
) {
    val typeLabel = when (transaction.type) {
        TransactionType.BUY -> "买入"
        TransactionType.SELL -> "卖出"
        TransactionType.DIVIDEND -> "分红"
        TransactionType.FEE -> "手续费"
        TransactionType.TRANSFER_IN -> "转入"
        TransactionType.TRANSFER_OUT -> "转出"
        TransactionType.DEPOSIT -> "入金"
        TransactionType.WITHDRAW -> "出金"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (transaction.symbol != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = transaction.symbol,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
                Text(
                    text = formatCurrency(transaction.amount, transaction.currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (transaction.shares != null && transaction.price != null) {
                    Text(
                        text = "${String.format("%.4f", transaction.shares)} 份 × ${formatCurrency(transaction.price, transaction.currency)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                } else if (transaction.shares != null) {
                    Text(
                        text = "${String.format("%.4f", transaction.shares)} 份",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                } else {
                    Text(
                        text = transaction.currency,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                Text(
                    text = dateFormat.format(Date(transaction.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            if (!transaction.note.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = transaction.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}
