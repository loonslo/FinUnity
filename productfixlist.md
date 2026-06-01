# FinUnity 产品体验修复清单（来自真实使用反馈）

> 来源：用户实际使用中发现的 6 个问题。每条：现象 / 根因(文件·行) / 改法(含代码) / 验证。
> 通用前置：改完 `gradlew assembleDebug` 通过。状态：`[ ]` 待修

---

## P1-01 风险维度应由资产类型自动归类，不该让用户手选
**现象**：录入资产时有"风险维度（稳健/进取/保命/防守）"一排可点的 pill，用户能随意选——但风险象限本应由"产品/标的本身"决定，手选会导致归类错误。
**根因**：`AssetRecordScreen.kt:366-385` 渲染了 `风险维度` 的 `FinPill` 选择器；其实已有 `defaultRiskBucketFor(assetType, accountType)`（:770）能自动推导。
**改法**：删除手选 UI，改为**只读展示**自动归类结果（仍保留 `selectedRiskBucket` 用于保存，它已在类型切换时自动同步）：
1. 删除 `AssetRecordScreen.kt:366-385` 的整段 `if (hasRiskBucket) { ... 风险维度 pill ... }`。
2. 替换为只读说明（让用户知道归到哪一象限、为什么）：
   ```kotlin
   if (hasRiskBucket) {
       Text("风险维度", style = MaterialTheme.typography.labelLarge,
           color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
       Spacer(Modifier.height(8.dp))
       Surface(shape = FinShapes.sm, color = FinColors.Primary.copy(alpha = 0.1f)) {
           Text("${selectedRiskBucket.displayName()}（按资产类型自动归类）",
               modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
               style = MaterialTheme.typography.bodySmall, color = FinColors.Primary)
       }
   }
   ```
3. `selectedRiskBucket` 不再被 `onClick` 改写；保存逻辑（:568）不变。
**说明（需产品确认）**：`FUND` 现统一归"进取"。若要区分货币基金(防守/稳健)与股票基金(进取)，需后续按"基金子类型"细分，本次先按资产类型自动归类。
**验证**：录入页不再有可点的风险维度 pill；选不同资产类型时只读标签随之变化（股票→进取、定期→稳健、保单→保命、现金→防守）。

---

## P1-02 买入成本录入方式不直观（应"数量 + 买入单价"自动算总成本）
**现象**：股票/ETF/基金录入要同时填"数量""买入总成本""当前价格"，关系不清楚，容易填错；用户期望：填**数量 + 买入单价(净值)**，自动算总成本，再用当前价算盈亏。
**根因**：`AssetRecordScreen.kt > DynamicAssetFields` 的 `else` 分支（约 722-744）让用户直接填"买入总成本"，没有单价概念；校验文案 :549 也是"买入总成本"。
**改法**（改成 数量 + 买入单价 + 当前价；`cost = 数量 × 买入单价`）：
1. `else` 分支替换"买入总成本"输入为"买入单价/净值"，并按数量算总成本回填 `cost`：
   ```kotlin
   else -> {
       FinTextField(value = quantity, onValueChange = { onQuantityChange(it.filter { c -> c.isDigit() || c == '.' }) },
           label = "数量/份额", placeholder = "0", keyboardType = KeyboardType.Decimal)
       // 买入单价：编辑态用 cost/quantity 反推
       var buyPrice by remember(selectedAssetType) {
           mutableStateOf(run {
               val q = quantity.toDoubleOrNull() ?: 0.0; val c = cost.toDoubleOrNull() ?: 0.0
               if (q > 0 && c > 0) (c / q).toString() else ""
           })
       }
       FinTextField(value = buyPrice, onValueChange = { buyPrice = it.filter { c -> c.isDigit() || c == '.' } },
           label = "买入单价/净值", placeholder = "0.00", keyboardType = KeyboardType.Decimal)
       FinTextField(value = currentPrice, onValueChange = { onCurrentPriceChange(it.filter { c -> c.isDigit() || c == '.' }) },
           label = "当前价格/净值", placeholder = "0.00", keyboardType = KeyboardType.Decimal)
       LaunchedEffect(quantity, buyPrice) {
           val q = quantity.toDoubleOrNull() ?: 0.0; val bp = buyPrice.toDoubleOrNull() ?: 0.0
           onCostChange(if (q > 0 && bp > 0) (q * bp).toString() else "")
       }
   }
   ```
2. 校验文案 :549 `add("买入总成本")` → `add("买入单价")`。
3. 预览区（约 330-410）已用 `cost` 计算总成本/盈亏，可加一行"总成本：数量×单价"展示，逻辑不变。
**验证**：填 数量100 + 买入单价150 → 自动总成本 15000；当前价180 → 盈亏 +3000(+20%)；编辑已有资产时单价能正确反推显示。

---

## P1-03 币种重复选择 + 保存按钮文字对比度差
**现象 A（币种重复）**：账户创建要选币种，录入资产又要选币种，重复。用户希望币种在录入资产时选即可（同一券商可持有美股/港股），账户不必再选。
**现象 B（按钮灰）**：录入页"保存"按钮在未填全时是浅灰底+白字，几乎看不清。
**根因 A**：`AccountScreen.kt` 账户表单里有币种选择（`currencies` + `AccountFormCard` 的币种 pill）；`AssetRecordScreen` 也有币种选择，二者重复。
**根因 B**：`FinUi.kt > FinSoftButton`（:152）禁用态背景用 `surfaceVariant`（浅灰）但 `contentColor` 仍是白色 → 白字浅灰底，对比极差。
**改法 A（账户去掉币种，默认本位币；资产级币种为准）**：
- `AccountScreen.kt`：移除币种选择 UI，保存时 `currency = settings.baseCurrency`（或常量 "CNY"）。需把 baseCurrency 传入或用默认。
- `AssetRecordScreen` 的币种选择保留（已存在）。
- 注意下游：转账同币种过滤（`CashFlowScreen`）、现金币种（`adjustCashAsset` 用 `record.currency`）仍按资产币种走，逻辑不受影响。
**改法 B（禁用态也保证白字可读）**：`FinSoftButton` 禁用态背景改为半透明主色，而非浅灰：
```kotlin
color = if (enabled) FinColors.Primary else FinColors.Primary.copy(alpha = 0.45f),
contentColor = Color.White
```
**验证**：A — 新建账户无币种选项；录入资产仍可选币种，多币种总资产折算正确。B — 保存按钮无论可否点击，文字都是清晰白色。

---

## P1-04 银行等账户在"新增资产"时看不到"现金"（被错误过滤）
**现象**：给银行账户加资产时，类型里没有"现金"（只有基金/定期）。
**根因**：`AssetRecordScreen.kt > allowedAssetTypesFor`（约 780-786）对**新建记录**做了 `filterNot { it == AssetType.CASH }`，把现金从所有账户的新增选项里去掉了。这是早期"现金自动管理"遗留逻辑，FIX-01 之后录入已不再扣现金，应允许直接录现金。
**账户↔资产类型映射核对**（`AccountAssetRules.kt`，结论：基本合理，仅"新增时过滤现金"是 bug）：
| 账户 | 当前可选 | 评估 |
|---|---|---|
| 券商 BROKER | 股票/ETF/基金/现金 | ✅ |
| 银行 BANK | 基金/定期/现金 | ✅（现金被过滤才看不到）|
| 基金平台 FUND | 基金/现金 | ✅ |
| 现金管理/互联网 CASH_MANAGEMENT | 基金/定期/现金 | ✅ |
| 债券 BOND | 基金/定期/现金 | ✅ |
| 保险 INSURANCE | 基金/定期/保险 | ✅（可考虑去掉基金）|
| 其他 OTHER | 全部 | ✅ |
**改法**：去掉对现金的过滤（保留"现金不作为默认首选"即可——把现金排到列表末尾，而不是删除）：
```kotlin
private fun allowedAssetTypesFor(accountType: AccountType?, currentType: AssetType?): List<AssetType> {
    val baseAllowed = AccountAssetRules.allowedAssetTypes(accountType)
    // 新建时不再过滤现金；只是让现金排在最后，默认选中第一个非现金类型
    val allowed = if (currentType == null) {
        baseAllowed.sortedBy { it == AssetType.CASH }   // 现金排末尾
    } else baseAllowed
    return if (currentType != null && currentType !in allowed) listOf(currentType) + allowed else allowed
}
```
（同时确认 `selectedAssetType` 初值仍取 `allowedAssetTypes.firstOrNull()`，即默认非现金。）
**验证**：给银行账户新增资产，类型里能看到"现金"并可录入；默认仍选中"基金"。

---

## P1-05 卖出不可用：卖出对话框存在但没有任何按钮触发
**现象**：只能录入(买入)资产，找不到"卖出"。
**根因**：`AssetRecordScreen.kt` 里**有完整的卖出对话框**（`showSellDialog`，:151-245，确认时调用 `onSell→sellAssetRecord`），但**全页没有任何地方把 `showSellDialog` 置为 true**；编辑态本应放操作按钮的卡片（:584-599）是个**空 Column `{}`**，是没写完的占位。资产详情页的"卖出"按钮(onSell)只是跳到这个编辑页，于是死路。
**改法**：在编辑态、可交易资产上补"卖出"按钮，触发已有对话框：
```kotlin
if (!isNewRecord && selectedAssetType in listOf(AssetType.STOCK, AssetType.ETF, AssetType.FUND)) {
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = { showSellDialog = true },
        modifier = Modifier.fillMaxWidth(),
        shape = FinShapes.md,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
    ) { Text("卖出") }
}
```
（把它替换 :584-599 那个空占位卡片。卖出对话框与 `viewModel.sellAssetRecord` 已就绪，无需再改。）
**可选增强**：资产详情页"卖出"按钮可直接弹该对话框，少跳一层。
**验证**：进入已有股票 → 编辑/详情 → 出现"卖出"按钮 → 输入数量 → 确认后数量与成本按比例减少、生成卖出流水、现金增加。

---

## P1-06 只有 1 个账户时仍显示"资产调整(转账)"，点了无法用
**现象**：只有 1 个账户时，"记一笔"里仍有"资产调整"入口，但转账没有可选的转入账户，无法保存。
**根因**：`CashFlowScreen.kt` 始终渲染"资产调整"`RecordEntryCard`（约 140-149），而它只做转账；转账目标 `transferTargets`（:87-89，需同币种的其它账户）此时为空。
**改法**：无可转入目标时隐藏"资产调整"入口：
```kotlin
// transferTargets 已计算
if (transferTargets.isNotEmpty()) {
    RecordEntryCard(title = "资产调整", subtitle = "在账户之间转账", icon = Icons.Default.Person,
        selected = selectedEntry == RecordEntry.ADJUST,
        onClick = { selectedEntry = RecordEntry.ADJUST; mode = CashFlowMode.TRANSFER })
}
```
（同时：若当前已选中 ADJUST 但目标为空，回退到 CASH。）
**验证**：只有 1 个账户时，记一笔只显示"日常收支"；新增第二个同币种账户后，"资产调整"出现并可正常转账。

---

## 建议修复顺序
```text
P1-04 现金被过滤（影响银行/现金录入，最常见）
P1-05 卖出不可用（核心功能缺失）
P1-02 成本录入方式
P1-01 风险维度自动归类
P1-06 单账户隐藏资产调整  ·  P1-03 币种去重 + 按钮白字
```
