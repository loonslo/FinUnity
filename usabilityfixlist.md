# FinUnity 可用性 / 易用性 修复清单

> 来源：以"做理财规划的真实用户"视角，逐页走查全部 21 个页面与按钮（基于源码追踪每个入口的跳转与后果）。
> 每条：现象 / 根因(文件·行) / 改法(含代码骨架) / 验证。状态：`[ ]` 待修　`[x]` 已修
> 通用前置：改完 `gradlew assembleDebug` 通过。

---

## 🔴 P0 阻断（页面进不去 / 控件点不到，必须先修）

### UX-01 「价格变化」整页用户无法进入（死页面）
**现象**：`PriceChangeScreen`（按类型筛选 + 按市值/变化/名称排序 + 迷你折线）功能完整，但**没有任何入口**，用户永远打不开。
**根因**：`MainActivity.kt` 中 `is Screen.PriceChanges ->` 有渲染（约 273 行），但全工程无 `navigateTo(Screen.PriceChanges)` / `switchTopLevel(Screen.PriceChanges)`。
**改法**（在"账本"页总资产卡加入口）：
1. `AccountHubScreen.kt > AccountHubScreen(...)` 形参加 `onOpenPriceChanges: () -> Unit = {}`，并传入 `TotalAssetOverviewCard`。
2. `TotalAssetOverviewCard` 的"交易记录"`TextButton` 旁加一个：
   ```kotlin
   TextButton(onClick = onOpenPriceChanges, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
       Text("价格变化", style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
   }
   ```
3. `MainActivity.kt` 渲染 `AccountHubScreen` 处加 `onOpenPriceChanges = { navigateTo(Screen.PriceChanges) }`。
**验证**：账本页能看到"价格变化"入口 → 点击进入 `PriceChangeScreen` → 返回正常；筛选/排序/折线可用。

### UX-02 「历史 / 资产走势」页无法进入（死页面）
**现象**：`HistoryScreen`（资产快照趋势、月度变化）只有渲染、无入口；规划页"复盘"已改指向 `MonthlyReview`，把它彻底孤立。理财 App 看不到资产历史曲线是真实缺失。
**根因**：`MainActivity.kt` `is Screen.History ->`（约 474 行）有渲染，但无 `navigateTo(Screen.History)`。
**改法**（在"规划"页加入口卡，复用现有 `EntryRowCard`）：
1. `PlanningScreen.kt` 形参加 `onOpenHistory: () -> Unit = {}`。
2. 在"月度复盘""大额支出模拟"两张 `EntryRowCard` 旁再加一张：
   ```kotlin
   item { EntryRowCard(title = "资产历史走势", subtitle = "看总资产这段时间怎么变", onClick = onOpenHistory) }
   ```
3. `MainActivity.kt` 渲染 `PlanningScreen` 处加 `onOpenHistory = { navigateTo(Screen.History) }`。
**验证**：规划页出现"资产历史走势"入口 → 进入 `HistoryScreen` 看到快照/月度变化 → 返回正常。

### UX-03 交易流水筛选栏窄屏溢出，后几个筛选点不到
**现象**：流水页筛选有 9 个 `FilterChip`（全部/买入/卖出/入金/出金/转入/转出/分红/手续费），手机一行放不下，"分红/手续费"被挤出屏幕、点不到。
**根因**：`TransactionHistoryScreen.kt:80-92` 用**不可滚动**的 `Row` 承载 9 个 chip。
**改法**：改为横向滚动（最小改动）：
```kotlin
Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(6.dp)
) { filterOptions.forEach { (type, label) -> FilterChip(...) } }
```
（需 `import androidx.compose.foundation.horizontalScroll` 与 `rememberScrollState`。或改用 `FlowRow`。）
**验证**：9 个筛选项可横向滑动并全部可点；选"分红/手续费"能正确过滤。

---

## 🟠 P1 交互合理性 / 易用性

### UX-04 「账本」「我的」两个 Tab 的账户卡点击行为不一致 + 删除入口隐蔽
**现象**：账本页点账户 → 账户详情；我的页点账户 → 编辑账户（且只有这里能删账户）。两张几乎一样的卡，行为不同，用户困惑；想删账户也找不到。
**根因**：
- `MainActivity.kt` 中"我的"页 `AccountAssetsByAccountScreen.onEditAccount` 被接到 `Screen.AddAccount(account, allowDelete=true)`；
- "账户详情" `onEditAccount` 接到 `Screen.AddAccount(account)`（`allowDelete` 默认 false，故详情里编辑账户无法删除）。
**改法**（统一为：两处都进详情，删除收到详情的"更多→编辑账户"里）：
1. `MainActivity.kt`：把"我的"页账户点击改为进详情：
   `onEditAccount = { account -> navigateTo(Screen.AccountDetail(account.id)) }`
2. `MainActivity.kt`：账户详情的编辑允许删除：
   `onEditAccount = { navigateTo(Screen.AddAccount(accountSummary?.account, allowDelete = true)) }`
**验证**：两个 Tab 点账户都进同一个账户详情；详情 → 更多 → 编辑账户 → 能看到并执行删除。

### UX-05 「记一笔 → 资产调整」文案过度承诺 + 死枚举
**现象**：记一笔只渲染"日常收支""资产调整"两张卡；"资产调整"副标题写"账户转账、分类调整、数据修正"，但实际只做了**转账**，用户点进去发现没有分类调整/数据修正。
**根因**：`CashFlowScreen.kt:142` 副标题；`RecordEntry.INVEST`(63-67 行)为未渲染的死枚举。
**改法**：副标题改为与能力一致，如 `"在账户之间转账"`；删除未使用的 `RecordEntry.INVEST`（或保留但注明 TODO）。
**验证**：副标题描述与页面实际能做的一致，无"功能缺斤少两"观感。

### UX-06 转账「余额预览」在非本位币账户算错
**现象**：美元账户（本位币 CNY）转账时，预览的"转出后余额"是用 **CNY 折算余额 − USD 金额** 算的，数字错误，会误导。
**根因**：`CashFlowScreen.kt:220-262` 用 `balanceInBaseCurrency`（本位币）减 `amount`（账户原币）。
**改法**（最小正确化）：仅当账户币种==本位币时显示"后余额"，否则只显示转出/转入金额、不算后余额：
```kotlin
val sameAsBase = account?.currency == baseCurrency   // baseCurrency 需作为参数传入本页
if (selectedTarget != null && amount != null && amount > 0 && sameAsBase) {
    // 现有的后余额预览
} else if (selectedTarget != null && amount != null && amount > 0) {
    Text("转出 ${formatCurrency(amount, account?.currency ?: "CNY")} 到 ${selectedTarget.name}",
        style = MaterialTheme.typography.bodySmall, color = FinColors.TextSecondary)
}
```
（`CashFlowScreen` 目前没有 `baseCurrency` 参数，需从 `MainActivity` 传入。）
**验证**：USD 账户转账不再出现错误的"CNY 后余额"；本位币账户预览仍正常。

### UX-07 账本页"总资产"卡的两个假指标
**现象**：指标行显示"账户数 = 可追溯""记录方式 = 本地账本"——标着"账户数"却显示文字"可追溯"，像没渲染好的占位。
**根因**：`AccountHubScreen.kt:216-225` 三个 `AssetMetricItem` 里有两个写死的非数据文案。
**改法**：用真实账户数替换，并删掉"记录方式"占位（或换成真实信息）：
```kotlin
// AccountHubScreen 形参已有 accounts；把 accounts.size 传进 TotalAssetOverviewCard
AssetMetricItem(label = "账户数", value = "$accountCount 个", valueColor = FinColors.TextPrimary)
// 去掉 "记录方式 / 本地账本" 这一项，或改为真实的"最近更新"时间
```
**验证**：指标显示真实账户数；不再有"账户数=可追溯"这种文字。

---

## 🟡 P2 观感 / 措辞

### UX-08 "持仓"措辞对银行/现金账户不贴切
**现象**：账户详情、账本卡把现金/定期/房产都叫"持仓 (N 项)"。
**根因**：`AccountDetailScreen.kt:116`（"持仓 (${size})"）、`AccountHubScreen.kt:309`（"持仓数量"）。
**改法**：统一改"资产"（"资产 (N)"/"资产数量"）。
**验证**：纯存款账户不再显示"持仓"。

### UX-09 房产/车辆/保单详情显示"盈亏 +0.00 / 收益率 +0.0%"
**现象**：实物资产按估值录入，本无盈亏，详情却显示 0 盈亏，略怪。
**根因**：`AssetDetailScreen.kt > DetailOverview`（176-180 行）只对 `CASH` 隐藏盈亏。
**改法**：把隐藏条件扩展到非交易型：
```kotlin
val hideProfit = summary.record.assetType in listOf(
    AssetType.CASH, AssetType.REAL_ESTATE, AssetType.VEHICLE, AssetType.INSURANCE_POLICY)
if (!hideProfit) { /* 买入成本 / 累计盈亏 / 收益率三行 */ }
```
（头部卡 99 行的收益率同理可加判断。）
**验证**：房产/车辆/保单详情不再显示盈亏与收益率；股票/ETF/基金不受影响。

### UX-10 「我的」页头像/用户名写死（已知限制）
**现象**：顶部永远是"F / FinUnity / 本地记录…"，非用户自己的头像和名字，且不可编辑。
**根因**：`AccountHubScreen.kt > AccountProfileHeader` 为静态占位。
**改法**：属"用户资料"功能，需新增可编辑昵称（可存 `Settings`）。本轮可先去掉误导性的"个人感"，或排入后续迭代。
**验证**：要么支持编辑昵称，要么文案不再暗示是用户个人资料。

---

## 说明：CSV 模板小问题
- `ImportCsvScreen.kt > templateCsv(ASSET_RECORDS)` 的"风险维度"示例只给了进取/稳健，建议补一行保命/防守示例，避免用户漏填四象限两类。

---

## 建议修复顺序
```text
UX-01 价格变化页入口   ┐
UX-02 历史页入口        ├ 改动都很小，但解决"整页打不开/点不到"，价值最高
UX-03 流水筛选可滚动   ┘
UX-04 账户点击/删除统一  ·  UX-06 转账预览币种  ·  UX-05 记一笔文案
UX-07 假指标  ·  UX-08 措辞  ·  UX-09 实物盈亏  ·  UX-10 用户资料(后续)
```
