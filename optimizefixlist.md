# FinUnity 优化与新功能改法清单（2026-06-29）

> 来源：对照两个产品初衷逐项排优先级——
> ①**做财富管理**；②**把所有账户归并成一个总览，知道自己"今天的营收"**。
> 每条格式：现象 / 根因(文件·行) / 改法(含代码) / 验证。状态：`[ ]` 待做。
> 通用前置：改完 `gradlew assembleDebug testDebugUnitTest` 通过。

优先级口径：
- **P0**：直接卡住核心初衷（看不到今日营收）。
- **P1**：明显改善录入效率 / 数据正确性（含拍照录入新功能）。
- **P2**：体验增强，不影响主流程。

---

## 🔴 P0

### OPT-01 看不到"今日营收"——你最明确的初衷没实现
**现象**：总览只有"累计收益"（相对买入成本的总盈亏）和月度变化，**没有"今天涨跌了多少"**。每天打开 App 看不到当日营收。
**根因（链路缺一环：没存昨收价）**：
- `YahooFinanceApi.kt > StockMeta` 只取了 `regularMarketPrice`，没取 `regularMarketPreviousClose`（同一个接口的 meta 里就有这个字段）。
- `Price.kt` 实体没有 `previousClose` 字段。
- `PriceRepository.kt:108 / :215 / :246` 三处 `Price(...)` 构造都没带昨收。
- `MainScreen.kt:352 AssetOverviewCard` 只算 `cumulativeProfit = totalAssets - totalCost`，没有今日维度。

**改法**（meta 取昨收 → 存进 Price → 汇总算今日盈亏 → 总览顶部加一行）：

1. `YahooFinanceApi.kt`，`StockMeta` 加字段：
   ```kotlin
   data class StockMeta(
       val symbol: String?,
       val regularMarketPrice: Double?,
       val regularMarketPreviousClose: Double?,   // 新增：昨收
       val chartPreviousClose: Double?,           // 兜底：部分标的只有这个
       val currency: String?
   )
   ```
2. `Price.kt` 加 `previousClose`，并升级 DB 版本 `v11→v12`：
   ```kotlin
   val previousClose: Double = 0.0   // 昨收价，0 表示未知
   ```
   `DatabaseMigrations.kt` 加 `MIGRATION_11_12`：
   ```kotlin
   db.execSQL("ALTER TABLE prices ADD COLUMN previousClose REAL NOT NULL DEFAULT 0")
   ```
   `AppDatabase.kt` version 改 12、注册迁移。
3. `PriceRepository.kt` 三处构造 `Price(...)` 带上昨收（取 `regularMarketPreviousClose ?: chartPreviousClose ?: 0.0`）。
4. `PortfolioCalculator.kt` 新增（按本位币折算）：
   ```kotlin
   fun computeTodayChange(): Double = unifiedAssets()
       .filter { it.isPriceTracked }                  // 股票/ETF/有现价的标的
       .sumOf { a ->
           val prev = priceOf(a.symbol)?.previousClose ?: 0.0
           if (prev <= 0.0) 0.0
           else (a.currentPrice - prev) * a.quantity * fxRate(a.currency)
       }
   ```
   `PortfolioSummary` 加 `val todayChange: Double` 和 `val todayChangeRatio: Double`（分母用"昨日市值 = 今日市值 − todayChange"）。
5. `MainScreen.kt > AssetOverviewCard`：在"资产结构"标题下、累计收益之上加一行醒目的今日营收：
   ```kotlin
   val todayColor = if (summary.todayChange >= 0) FinColors.Profit else FinColors.Loss
   Text("今日 ${if (summary.todayChange >= 0) "+" else ""}" +
        "${formatCurrency(summary.todayChange, baseCurrency)} ${formatSignedPercent(summary.todayChangeRatio)}",
        style = MaterialTheme.typography.titleLarge, color = todayColor)
   ```
**验证**：单测 `computeTodayChange` 用 price=110/prevClose=100/qty=10 → +100；总览顶部出现"今日 +X (+x%)"，颜色随正负变化；昨收未知的标的不计入、不报错。

---

## 🟠 P1

### OPT-02【新功能】拍照/截图识别录入——方便录入多平台数据
**现象/需求**：资产分散在多个券商/银行/基金 App，手工逐条录入累、易错。希望**截图持仓或余额页 → 大模型识别 → 自动预填录入表**，确认即入库。

**两种方案对比**（你提的两个做法）：

| 维度 | 方案A：客户端直连大模型 | 方案B：经后端代理大模型 |
|---|---|---|
| 开发量 | 最小，App 内直接调 API | 需搭一个薄后端 |
| **API Key 安全** | ❌ Key 打进 APK，可被反编译盗刷 | ✅ Key 只在后端，App 不持有 |
| 换模型/限流/重试 | 改 App 要发版 | 后端改即可，App 不动 |
| 成本控制 | 难（Key 泄露=失控扣费） | 可加配额/用量监控 |
| 截图隐私 | 直发第三方模型 | 可在后端先脱敏（打码账号/余额）再转发 |
| 离线/弱网 | 直连失败即不可用 | 后端可缓存/排队 |

**推荐（结合你"能接受云端模型、后端还没定"）**：
> **走方案B，但做成"极薄代理后端"**——不是传统业务后端，只有一个无状态函数：收图 → 调多模态大模型（带固定抽取 Prompt）→ 返回结构化 JSON。
>
> 落地选 **Serverless（Cloudflare Workers / 阿里云函数计算 / Vercel）**，几十行代码、近乎零运维、按调用计费。理由：你能接受云端模型，所以"隐私"不是阻力；真正的阻力是 **API Key 塞进 Android APK 必被盗刷**——这一条就足以否掉纯客户端直连（除非永远只你自己一台手机用 debug 包）。
>
> 过渡期想先验证效果：可以先用方案A 做一版本地原型（Key 写在本地、不上架），跑通识别质量后再把那段调用搬到后端。识别 Prompt 和确认页 UI 两边完全复用。

**数据流（方案B）**：
```
App 选图/截图
  → POST 到代理后端(multipart 图片)
  → 后端调多模态模型，system prompt 要求"只输出 JSON，字段对齐 AssetRecord"
  → 返回 List<识别结果>
  → App 进"确认录入页"逐条预填（可改）
  → 用户确认 → 走现有 AssetRecord 写入路径
```
**约定的抽取 JSON Schema**（让模型按这个吐，字段对齐 `AssetRecord.kt`）：
```json
[{
  "name": "纳指100ETF",        // 标的名
  "assetType": "ETF",          // STOCK/ETF/FUND/CASH/TIME_DEPOSIT/...
  "quantity": 1200,            // 份额/股数
  "price": 1.836,             // 当前价/净值（截图里的现价）
  "cost": null,                // 成本，截图常没有→留空，进确认页手填
  "currency": "CNY",
  "confidence": 0.92           // 模型自评置信度，低的标黄让用户重点核对
}]
```
**改法（分阶段）**：
1. **后端**（独立于 App 仓库，新建一个 worker）：单个 `/extract` 端点，环境变量存 Key，固定抽取 Prompt，返回上面的 JSON 数组。加最简单的鉴权（App 端 token）+ 每日调用上限。
2. **App 数据层**：新增 `ImageImportRepository.kt`（Retrofit 调后端 `/extract`，返回 `List<ExtractedAsset>`）。`riskBucket` 不让模型猜，落库前用现有 `defaultRiskBucketFor(assetType, accountType)`（`AssetRecordScreen.kt:770`）自动归类，跟 OPT 之外的 P1-01 一致。
3. **入口**：`AccountHubScreen` / `AssetRecordScreen` 顶部加"📷 拍照录入"按钮 → `navigateTo(Screen.ImportByPhoto(accountId))`（`MainActivity.kt` 加 sealed `Screen.ImportByPhoto` + 渲染分支）。
4. **确认页** `ImportConfirmScreen.kt`：每条一张可编辑卡（预填 name/quantity/price/currency，cost 高亮待填，低 confidence 标黄），底部"全部保存"复用现有 AssetRecord 写入与 `adjustCashAsset()`。
5. **隐私小优化（可选）**：后端转发前对明显的账号串/手机号做正则打码；不改变识别质量。
**验证**：截图一张券商持仓页 → 识别出 N 条预填卡 → 改掉一条成本 → 保存后总览市值/环形图正确更新；后端 Key 不出现在 APK 里（`apktool` 反编译搜不到）；超日调用上限时 App 有友好提示。
**工作量估**：后端 0.5 天；App 数据层+确认页 1.5～2 天；识别 Prompt 调优 0.5 天。

---

### OPT-03 快照"现金"口径混入房产/车/保单
**现象**：历史快照里的"现金资产"虚高——房产、车辆、保单被算进了现金侧。环形图按风险象限分组是对的，仅快照口径偏大。
**根因**：`MainViewModel.kt:124` `val totalCash = totalAssets - totalStockValue`，凡"非股票"全被当现金；:180 `cashAssets = totalCash` 写进快照。
**改法**：快照按 `riskBucket` 真实分桶，别用"总额减股票"：
```kotlin
val cashAssets = calculator.valueOfBucket(RiskBucket.CASH)            // 只统计防守/现金桶
// 或：把 AssetSnapshot.cashAssets 语义改名为"非权益资产"，文档同步
```
**验证**：录一笔房产后，快照 `cashAssets` 不再包含房产；环形图占比不变。

---

### OPT-04 价格新鲜度在总览不可见（含节假日/停牌）
**现象**：A 股/港股休市或停牌时拉不到新价，会回落到 `isFallback` 旧价，但总览不提示"这个数是几天前的"，"今日营收"可能基于陈旧价误导。
**根因**：`Price.confidence()`（`Price.kt`）已经能判 REAL_TIME/DELAYED/STALE，但 `MainScreen` 总览没展示。
**改法**：总览"今日营收"行旁加一个小标签，取所有被跟踪标的里最旧的置信度：
```kotlin
when (summary.worstPriceConfidence) {
    PriceConfidence.STALE -> Text("价格偏旧，仅供参考", color = FinColors.TextSecondary, style = ...labelSmall)
    PriceConfidence.DELAYED -> Text("价格有延迟", color = FinColors.TextSecondary, ...)
    else -> {}
}
```
**验证**：断网/休市时总览显示"价格偏旧"；正常交易日不显示。

---

## 🟡 P2

### OPT-05 首屏不回答"现在该不该动"——决策信息散在子页
**现象**：风险体检、落点超限、再平衡建议这些"做决策"的信息（`evaluateRiskAlerts` / `evaluateHoldingRedlines`，写得不错）都埋在规划页/落点页，第一屏看不到。对"财富管理"来说，总览缺一句"现在要不要动"的结论。
**根因**：`summary.riskAlerts` 已聚合好，但 `MainScreen` 总览未消费。
**改法**：总览顶部（今日营收下方）加一张轻量提示条，只显示最高优先级的一条 WARNING；无告警时显示"配置健康，无需调整"：
```kotlin
summary.riskAlerts.firstOrNull { it.level == RiskAlertLevel.WARNING }?.let {
    FinCard { Text("⚠ ${it.title}", color = FinColors.Loss); Text(it.detail, style = ...bodySmall) }
} ?: Text("配置健康，暂无需调整", color = FinColors.TextSecondary)
```
**验证**：进取超上限时总览顶部出现预警并可点进规划页；正常时显示健康。

---

## 建议执行顺序
1. **OPT-01 今日营收**（最贴初衷、改动集中，先做）。
2. **OPT-02 拍照录入**（先方案A 本地原型验证识别质量 → 再搬到极薄代理后端）。
3. OPT-03 / OPT-04（数据正确性与可信度）。
4. OPT-05（决策前置到首屏）。
