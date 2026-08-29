package com.finunity.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.finunity.data.local.entity.*
import com.finunity.ui.components.FinTopBar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecurringRulesScreen(
    accounts: List<Account>,
    rules: List<RecurringRule>,
    onSave: (RecurringRule) -> Unit,
    onDelete: (RecurringRule) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showForm by remember { mutableStateOf(false) }
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id.orEmpty()) }
    var type by remember { mutableStateOf(RecurringRuleType.INCOME) }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CashFlowCategory.SALARY) }
    var day by remember { mutableStateOf("1") }
    var note by remember { mutableStateOf("") }
    val account = accounts.firstOrNull { it.id == selectedAccountId }
    val categories = if (type == RecurringRuleType.INCOME) {
        listOf(CashFlowCategory.SALARY, CashFlowCategory.BONUS, CashFlowCategory.RENTAL_INCOME, CashFlowCategory.INTEREST, CashFlowCategory.OTHER_INCOME)
    } else {
        listOf(CashFlowCategory.FOOD, CashFlowCategory.HOUSING, CashFlowCategory.INSURANCE, CashFlowCategory.LOAN_REPAYMENT, CashFlowCategory.OTHER_EXPENSE)
    }

    Scaffold(topBar = { FinTopBar("周期收支", onBack) }, modifier = modifier) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Spacer(Modifier.height(8.dp))
                Text("周期规则只会在本机生成记录，不会自动扣款；每月 29/30/31 日按当月最后一天执行。删除或停用后不再补发。", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { showForm = !showForm }, modifier = Modifier.fillMaxWidth()) { Text(if (showForm) "收起新增" else "新增周期规则") }
            }
            if (showForm) item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("新增规则", fontWeight = FontWeight.SemiBold)
                        if (accounts.isEmpty()) Text("请先添加账户", color = MaterialTheme.colorScheme.error)
                        else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(type == RecurringRuleType.INCOME, { type = RecurringRuleType.INCOME; category = CashFlowCategory.SALARY }, label = { Text("收入") })
                                FilterChip(type == RecurringRuleType.EXPENSE, { type = RecurringRuleType.EXPENSE; category = CashFlowCategory.OTHER_EXPENSE }, label = { Text("支出") })
                            }
                            Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                accounts.forEach { item ->
                                    FilterChip(selected = item.id == selectedAccountId, onClick = { selectedAccountId = item.id }, label = { Text(item.name.take(8)) })
                                }
                            }
                            OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("每月金额") }, suffix = { Text(account?.currency ?: "") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(day, { day = it.filter(Char::isDigit).take(2) }, label = { Text("每月几日") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(note, { note = it }, label = { Text("备注") }, singleLine = true, modifier = Modifier.weight(2f))
                            }
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                categories.forEach { item -> FilterChip(category == item, { category = item }, label = { Text(item.displayName, maxLines = 1, softWrap = false) }) }
                            }
                            Button(onClick = {
                                val value = amount.toDoubleOrNull() ?: 0.0
                                if (selectedAccountId.isNotBlank() && value > 0) {
                                    onSave(RecurringRule(accountId = selectedAccountId, type = type, amount = value, currency = account?.currency ?: "CNY", category = category, note = note.ifBlank { category.displayName }, dayOfMonth = day.toIntOrNull()?.coerceIn(1, 31) ?: 1))
                                    amount = ""; note = ""; showForm = false
                                }
                            }, enabled = selectedAccountId.isNotBlank() && amount.toDoubleOrNull()?.let { it > 0 } == true, modifier = Modifier.fillMaxWidth()) { Text("保存规则") }
                        }
                    }
                }
            }
            if (rules.isEmpty()) item { Text("暂无周期规则", modifier = Modifier.padding(vertical = 40.dp)) }
            items(rules) { rule ->
                val accountName = accounts.firstOrNull { it.id == rule.accountId }?.name ?: "未知账户"
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("${if (rule.type == RecurringRuleType.INCOME) "收入" else "支出"} · ${rule.category.displayName}", fontWeight = FontWeight.SemiBold)
                            Text("每月 ${rule.dayOfMonth} 日 · $accountName · ${rule.note}", style = MaterialTheme.typography.bodySmall)
                            Text("${formatCurrency(rule.amount, rule.currency)}", color = if (rule.type == RecurringRuleType.INCOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = { onDelete(rule) }) { Text("删除") }
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
