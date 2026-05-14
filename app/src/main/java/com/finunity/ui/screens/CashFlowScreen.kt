package com.finunity.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.finunity.data.model.AccountSummary
import com.finunity.ui.components.FinPill

enum class CashFlowMode {
    CASH_IN,
    CASH_OUT,
    TRANSFER
}

private enum class RecordEntry {
    CASH,
    INVEST,
    ADJUST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashFlowScreen(
    accountId: String,
    accounts: List<AccountSummary>,
    onBack: () -> Unit,
    onSaveCashIn: (Double, String?) -> Unit,
    onSaveCashOut: (Double, String?) -> Unit,
    onSaveTransfer: (String, Double, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val account = accounts.firstOrNull { it.account.id == accountId }?.account
    var selectedEntry by remember { mutableStateOf<RecordEntry?>(null) }
    var mode by remember { mutableStateOf(CashFlowMode.CASH_IN) }
    var amountInput by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var targetExpanded by remember { mutableStateOf(false) }
    var targetAccountId by remember { mutableStateOf("") }
    val transferTargets = accounts
        .map { it.account }
        .filter { it.id != accountId && it.currency == account?.currency }
    val selectedTarget = transferTargets.firstOrNull { it.id == targetAccountId }
    val amount = amountInput.toDoubleOrNull()
    val canSave = account != null &&
        amount != null &&
        amount > 0.0 &&
        (mode != CashFlowMode.TRANSFER || selectedTarget != null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记一笔") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = Color(0xFFF7F8FA),
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF7F8FA))
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = account?.name ?: "账户不存在",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RecordEntryCard(
                    title = "日常收支",
                    subtitle = "工资、消费、现金增减",
                    icon = Icons.Default.Add,
                    selected = selectedEntry == RecordEntry.CASH,
                    onClick = {
                        selectedEntry = RecordEntry.CASH
                        mode = CashFlowMode.CASH_IN
                    }
                )
                RecordEntryCard(
                    title = "投资买卖",
                    subtitle = "买入、卖出资产",
                    icon = Icons.Default.DateRange,
                    selected = selectedEntry == RecordEntry.INVEST,
                    onClick = {
                        selectedEntry = RecordEntry.INVEST
                    }
                )
                RecordEntryCard(
                    title = "资产调整",
                    subtitle = "账户转账、分类调整、数据修正",
                    icon = Icons.Default.Person,
                    selected = selectedEntry == RecordEntry.ADJUST,
                    onClick = {
                        selectedEntry = RecordEntry.ADJUST
                        mode = CashFlowMode.TRANSFER
                    }
                )
            }

            if (selectedEntry == RecordEntry.INVEST) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {
                    Text(
                        text = "投资买卖会统一接入这里。当前请先在账户详情中通过资产记录完成买入或卖出。",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            if (selectedEntry == RecordEntry.CASH || selectedEntry == RecordEntry.ADJUST) {
                if (selectedEntry == RecordEntry.CASH) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FinPill(
                            text = "收入",
                            selected = mode == CashFlowMode.CASH_IN,
                            onClick = { mode = CashFlowMode.CASH_IN }
                        )
                        FinPill(
                            text = "支出",
                            selected = mode == CashFlowMode.CASH_OUT,
                            onClick = { mode = CashFlowMode.CASH_OUT }
                        )
                    }
                }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it.filter { c -> c.isDigit() || c == '.' } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("金额") },
                    suffix = { Text(account?.currency ?: "") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp)
                )

                if (mode == CashFlowMode.TRANSFER) {
                    ExposedDropdownMenuBox(
                        expanded = targetExpanded,
                        onExpandedChange = { targetExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedTarget?.name ?: "",
                            onValueChange = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            readOnly = true,
                            label = { Text("转入账户") },
                            placeholder = { Text("选择同币种账户") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetExpanded) },
                            shape = RoundedCornerShape(14.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = targetExpanded,
                            onDismissRequest = { targetExpanded = false }
                        ) {
                            transferTargets.forEach { target ->
                                DropdownMenuItem(
                                    text = { Text("${target.name} · ${target.currency}") },
                                    onClick = {
                                        targetAccountId = target.id
                                        targetExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注") },
                    placeholder = { Text(defaultNoteFor(mode)) },
                    minLines = 2,
                    shape = RoundedCornerShape(14.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (selectedEntry == RecordEntry.CASH || selectedEntry == RecordEntry.ADJUST) {
                Button(
                    onClick = {
                        val safeAmount = amount ?: return@Button
                        val safeNote = note.ifBlank { defaultNoteFor(mode) }
                        when (mode) {
                            CashFlowMode.CASH_IN -> onSaveCashIn(safeAmount, safeNote)
                            CashFlowMode.CASH_OUT -> onSaveCashOut(safeAmount, safeNote)
                            CashFlowMode.TRANSFER -> onSaveTransfer(targetAccountId, safeAmount, safeNote)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = canSave,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    Text("保存")
                }
            }
        }
    }
}

@Composable
private fun RecordEntryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFFEAF7EF) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
    }
}

private fun defaultNoteFor(mode: CashFlowMode): String = when (mode) {
    CashFlowMode.CASH_IN -> "收入"
    CashFlowMode.CASH_OUT -> "支出"
    CashFlowMode.TRANSFER -> "账户转账"
}
