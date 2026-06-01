# FinUnity 任务清单

> 本文件是**唯一权威**的任务清单。历史的早期产品任务板已删除（其中 P0/P1 大部分已实现），
> 避免与现状冲突造成困惑。已完成项详情见 `ROADMAP.md`。

产品原则：

```text
首页看结构
规划做决策
账户管数据
明细看变化
```

---

## 全局设计约束（所有任务执行前先遵守）

### 产品气质
- 做成个人资产账本和家庭资产决策辅助工具，不做传统资产后台。
- 页面要安静、干净、轻量、柔和，整体参考 iOS-like 轻量理财风格。
- 低信息密度，大留白，大圆角，弱阴影；少按钮，少说明文字，少工具栏。
- 避免复杂数据大屏、后台管理表格、密集筛选、堆满功能入口。

### 视觉规范
- 页面背景统一浅灰 `#F7F8FA`（`FinColors.PageBg`）。
- 卡片白色 `#FFFFFF`，圆角优先 `24dp`（`FinShapes.xl`），阴影 `elevation = 0.dp`。
- 页面左右边距 `20dp`，卡片内边距 `20dp`，卡片间距 `16dp`。
- 主文字 `#1F2933`/`FinColors.TextPrimary`，辅助文字 `FinColors.TextSecondary`，重点数字 `FinColors.Number`。
- 正收益 `FinColors.Profit`，负收益低饱和红 `FinColors.Loss`，持平用灰不用黑。
- 大金额字号突出，指标字号小；同时适配 Android 和 iOS 审美。

### 组件与信息层级
- 首页和主流程用白色大圆角卡片，每个卡片只承载一个主题，不在卡片里堆按钮。
- 次级操作放更多菜单/详情页/低强调入口，不要把所有功能并列成按钮。
- 第一屏只回答最重要的问题：总览突出结构、账户突出管理、明细突出变化、规划突出决策。

### 图表约束
- 总览页只允许一个资产结构环形图；规划/复盘用当前 vs 目标对比条、调整前/后结构条。
- 不做 K 线、复杂折线、雷达、Sankey、Treemap；不在每个账户/持仓/卡片放图；不在一页放多个环形图。
- 例外：资产详情页"价格"Tab 允许一条轻量价格折线（见 P2-4）。

---

## 一、已完成（验收已过，详见 ROADMAP.md）

- **P0** Yahoo 接口修复(路径参数+UA) · 价格每日更新(过期阈值12h) · 首页「立即刷新」 · A股/港股代码后缀提示 · 补 `gradlew.bat`
- **P1** 标普四象限(新增 INSURANCE 保命象限，全链路打通) · 互联网平台账户补「定期」 · 新手引导 `OnboardingScreen` · 目标配置模板页 `TargetAllocationScreen` · 规划页 `PlanningScreen` · 月度复盘页 `MonthlyReviewScreen`
- **基础页面**（早期已实现，无需重做）：账户增删改 `AccountScreen`(8 种账户类型) · 资产录入 `AssetRecordScreen`(动态表单) · 账户详情 `AccountDetailScreen` · 资产详情 `AssetDetailScreen`(概览/流水/价格 Tab) · 记一笔/收支转账 `CashFlowScreen` · 交易流水 `TransactionHistoryScreen` · CSV 导入 · 每日快照 `SnapshotWorker`
- **P2-1** 大额支出模拟页 `ExpenseSimulationScreen`

---

## 二、待执行任务（工程级，可直接照做）

> 约定：①每个任务列出**确切要改/新建的文件**；②给出**函数签名与代码骨架**；③导航类改动统一在
> `MainActivity.kt` 的 `sealed class Screen` 和 `Box{ when(currentScreen) }` 两处；
> ④新页面背景 `FinColors.PageBg`、白卡 `FinShapes.xl`、`elevation = 0.dp`、左右 `20dp`；
> ⑤完成后 `./gradlew assembleDebug` 和 `./gradlew testDebugUnitTest` 必须绿。

---

### P2-3 更多资产类型（房产 / 车辆 / 保险产品）—— 建议先做，改动最聚焦

**目标**：让用户能录入房产、车辆、保单这类"非交易、按估值计"的资产，并正确进入四象限。

**新建文件**：无。**修改文件**（6 个）：
1. `data/local/entity/AssetRecord.kt`
2. `data/model/UnifiedAsset.kt`
3. `data/model/AccountAssetRules.kt`
4. `data/model/PortfolioCalculator.kt`
5. `ui/screens/AssetRecordScreen.kt`
6. （确认项）`worker/PriceSyncWorker.kt` + `viewmodel/MainViewModel.kt`

**实现步骤**：
1. `AssetRecord.kt` 的 `enum class AssetType` 末尾加三个值：
   ```kotlin
   REAL_ESTATE,       // 房产
   VEHICLE,           // 车辆
   INSURANCE_POLICY   // 保单/年金
   ```
2. 编译，编译器会在所有 `when(AssetType)` 缺分支处报错，逐个补齐（已知 4 处）：
   - `UnifiedAsset.kt > AssetType.displayName()`：`REAL_ESTATE->"房产"`、`VEHICLE->"车辆"`、`INSURANCE_POLICY->"保险"`。
   - `AssetRecordScreen.kt > getNameLabel()` / `getNamePlaceholder()`：房产→"房产名称"/"如：自住房"；车辆→"车辆名称"；保单→"保单名称"。
   - `AssetRecordScreen.kt > DynamicAssetFields()` 的 `when`：这三类走"估值录入"分支——只显示一个 `FinTextField`（标签"当前估值"），并在 `LaunchedEffect` 里 `onQuantityChange("1")`、`onCostChange(估值)`、`onCurrentPriceChange(估值)`，使 `currentValue = 1 * 估值 = 估值`。
   - `PortfolioCalculator.kt > computeTotalAssets()` 的 `when(record.assetType)`：把三者归入"非股票"累加分支（与 CASH/TIME_DEPOSIT 同侧），保证只加一次。
3. `AssetRecordScreen.kt > defaultRiskBucketFor(assetType, accountType)` 增规则：
   ```kotlin
   assetType == AssetType.INSURANCE_POLICY -> RiskBucket.INSURANCE
   assetType == AssetType.REAL_ESTATE || assetType == AssetType.VEHICLE -> RiskBucket.CONSERVATIVE
   ```
4. `AccountAssetRules.kt`：`INSURANCE` 账户的 `allowedAssetTypes` 加入 `INSURANCE_POLICY`；`OTHER` 已是全部类型；同步更新 `allowedAssetText()` 文案。
5. **确认项（通常无需改代码）**：`PriceSyncWorker` 与 `MainViewModel.refreshPrices()` 的 `tradableTypes` 是 `STOCK/ETF/FUND` 白名单，新类型不在其中即不会去 Yahoo 拉价——在 PR 描述里写明已确认。

**边界**：估值为 0 时禁用保存（沿用现有 `isFormValid`）；删除/编辑复用现有路径，不触发现金自动管理（仅 STOCK/ETF/FUND 触发）。

**验收标准**（Given/When/Then）：
- Given 新增了三个枚举值，When `./gradlew assembleDebug`，Then 编译通过且无 `when` 非穷尽错误。
- Given 在 OTHER 账户录入"自住房 估值 200万"，When 回总览，Then 总资产增加 200万，且计入预期象限（房产→稳健）。
- Given 录入"重疾保单 现金价值 5万"，When 看资产结构环形图，Then 该 5 万计入"保命"象限。
- Given 三类资产存在，When 触发"立即刷新"，Then 日志中无对它们 name 的 Yahoo 请求，市值始终等于录入估值。
- Given 列表/详情/风险桶详情页，Then 显示正确中文名（房产/车辆/保险）与对应图例颜色。

---

### P2-4 资产详情价格趋势轻量折线图

**目标**：在资产详情页"价格"Tab 顶部，用已有 `price_history` 画一条无依赖的折线。
**修改文件**：仅 `ui/screens/AssetDetailScreen.kt`。

**实现步骤**：
1. 新增私有 Composable：
   ```kotlin
   @Composable
   private fun PriceTrendChart(history: List<PriceHistory>, currency: String) { /* Canvas 画线 */ }
   ```
   - 用**升序**点集：`history.sortedBy { it.timestamp }`（详情页传入是 DESC）。
   - `androidx.compose.foundation.Canvas`：`price` 映射 [min,max]→[height,0]，x 按索引等距；线色 `FinColors.Accent`，线宽 2dp，`StrokeCap.Round`；高度 160dp。
   - 顶部小字显示区间最高/最低价。
2. 在 `when (selectedTab) { 2 -> ... }` 分支，于现有 `items(priceHistory)` 之前插入：
   ```kotlin
   2 -> {
       val tradable = summary.record.assetType in listOf(AssetType.STOCK, AssetType.ETF, AssetType.FUND)
       if (tradable && priceHistory.size >= 2) {
           item { /* 白卡包 PriceTrendChart(priceHistory, summary.record.currency) */ }
       } else if (priceHistory.size < 2) {
           item { EmptyDetailText("积累几天价格后展示趋势") }
       }
       items(priceHistory, key = { it.id }) { DetailPriceItem(it, dateFormat) }
   }
   ```
3. 不改任何其它页面——保证折线只在此处出现。

**边界**：所有点价格相等（max==min）时画居中水平线，禁止除 0；点数<2 走占位分支。

**验收标准**：
- Given 一只有 ≥2 条 price_history 的股票，When 进入"价格"Tab，Then 顶部出现折线，方向与数据一致。
- Given price_history 只有 0~1 条，When 进入"价格"Tab，Then 显示占位文案，不画空图、不崩溃。
- Given 现金/定期/房产资产，When 进入"价格"Tab，Then 不出现折线。
- Given 首页/账户/风险桶/各列表项，Then 均无折线图（截图核查）。
- Given 未新增 build.gradle 依赖，When `assembleDebug`，Then 通过。

---

### P2-2 数据备份 / 恢复（JSON）+ 月度复盘提醒

**目标**：把全部本地数据导出为一个 JSON 并能从中恢复；每月本地通知提醒复盘。
**前置**：Gson 已在依赖中（NetworkModule 用了 GsonConverterFactory）。

**A. 备份/恢复**

**新建文件**：`data/repository/BackupRepository.kt`、`ui/screens/BackupScreen.kt`。
**修改文件**：`MainActivity.kt`（导航+入口）、`ui/screens/AccountHubScreen.kt`（"我的"工具区加入口）。

**实现步骤**：
1. `BackupRepository.kt`：
   ```kotlin
   data class BackupData(
       val version: Int = 1,
       val exportedAt: Long = System.currentTimeMillis(),
       val accounts: List<Account>,
       val assetRecords: List<AssetRecord>,
       val positions: List<Position>,
       val transactions: List<Transaction>,
       val settings: Settings
   )
   class BackupRepository(private val db: AppDatabase) {
       suspend fun export(): String           // 各 DAO getAllX().first() → Gson().toJson(BackupData)
       suspend fun import(json: String): Result<Unit>  // 解析失败返回 Result.failure，不动数据库
   }
   ```
   - `export()` 用 `kotlinx.coroutines.flow.first()` 取快照；枚举 Gson 默认按名字序列化（与 Room TEXT 一致）。
   - `import()`：先 `Gson().fromJson` 到 `BackupData`，校验 `accounts != null`；再在 `withContext(Dispatchers.IO)` 内 清空四表 → 按 账户→资产/持仓→交易 顺序 insert（外键依赖）→ `settingsDao().update`。解析异常 catch → `Result.failure`。
   - 各 DAO 若缺 `deleteAll()`，加 `@Query("DELETE FROM 表名") suspend fun deleteAll()`。
2. `BackupScreen.kt`（参数 `database`, `onBack`）：
   - 顶部返回栏（仿 `SettingsScreen`）。
   - "导出备份" → `rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"))`，回调 `contentResolver.openOutputStream(uri)` 写 `repo.export()`，成功弹 Snackbar。
   - "从文件恢复" → `ActivityResultContracts.OpenDocument(arrayOf("application/json"))`，选中后**先弹 AlertDialog**"恢复将覆盖当前所有数据，确定？"，确认后读 `openInputStream` → `repo.import()`，成功提示"恢复成功"、失败提示"文件无法识别"。
   - 用 `rememberCoroutineScope().launch` 包 suspend 调用。
3. `MainActivity.kt`：`Screen` 加 `data object Backup`；`when` 渲染 `BackupScreen(database, onBack={navigateBack()})`。
4. `AccountHubScreen.kt > AccountToolsCard`：加 `SecondaryToolChip(text="备份恢复", onClick=onOpenBackup)`；把 `onOpenBackup` 形参一路加到 `AccountAssetsByAccountScreen`，`MainActivity` 渲染处传 `onOpenBackup = { navigateTo(Screen.Backup) }`。

**B. 月度复盘提醒**

**新建文件**：`worker/ReviewReminderWorker.kt`。**修改文件**：`AndroidManifest.xml`、`MainActivity.onCreate`。

**实现步骤**：
1. `AndroidManifest.xml` 权限区加 `<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>`。
2. `ReviewReminderWorker`（仿 `SnapshotWorker`）：`doWork()` 建通知渠道（id `review`）→ 发通知"该做月度复盘了"，`contentIntent` 指向 `MainActivity`（带 extra `open=review`，由 `MainActivity` 解析后 `navigateTo(Screen.MonthlyReview)`）。`companion fun scheduleMonthly(context)`：`PeriodicWorkRequestBuilder<ReviewReminderWorker>(30, TimeUnit.DAYS)` + `enqueueUniquePeriodicWork("review_reminder", KEEP, req)`。
3. `MainActivity.onCreate` 加 `ReviewReminderWorker.scheduleMonthly(this)`；Android 13+ 用 `ActivityResultContracts.RequestPermission` 首启申请 `POST_NOTIFICATIONS`，拒绝不阻塞。

**验收标准**：
- Given 有账户/资产/交易，When "导出备份"，Then 生成非空 JSON，含 accounts/assetRecords/positions/transactions/settings 五段，字段名与实体一致。
- Given 用该 JSON，When 清数据后"从文件恢复"，Then 账户/资产/交易/设置条数与总资产与导出前**完全一致**。
- Given 选了非备份的随机文件，When 恢复，Then 弹"文件无法识别"，现有数据**未被破坏**。
- Given 备份入口，Then 仅在"我的"页工具区，首页/总览无此入口。
- Given 触发 `ReviewReminderWorker`，Then 收到通知，点击进入月度复盘页。
- Given Android 13+ 首启，Then 申请通知权限；拒绝后不崩溃，仅不发提醒。

---

### P2-5 全局视觉统一

**目标**：收敛多套灰度/颜色定义，删死代码，统一卡片与盈亏色。
**修改文件**：`ui/theme/Theme.kt`、`ui/components/FinUi.kt`、各 screen 散落硬编码色；删 `ui/components/RebalanceAlert.kt`、`MainScreen.kt > EmptyState`/`RatioRing`（确认无引用后）。

**实现步骤**：
1. `Theme.kt > FinColors` 确立唯一灰度 4 级（Primary `#111827`/Secondary `#6B7280`/Tertiary `#9CA3AF`/Disabled `#D1D5DB`），现有 `TextPrimary/Secondary/Tertiary` 指向它们。
2. 全局搜索硬编码灰（如 `Color(0xFF6B7280)`、正文用的 `onSurface.copy(alpha=…)`）替换为 `FinColors.TextSecondary` 等，逐文件改、改完编译。
3. 盈亏色：`AssetDetailScreen.kt:52` 的 `Color(0xFF0F9D58)/Color(0xFFD93025)` 改用 `FinColors.Profit/Loss`。
4. 删死代码：`grep` 确认 `EmptyState(`/`RatioRing(`/`RebalanceAlert(` 无调用后删除；`formatCurrency` 在 `MainActivity.kt` 与 `MainScreen.kt` 各一份，保留 `MainScreen` 版（带千分位），删 `MainActivity` 版并让 `AccountPickerDialog` 复用。
5. **封装 `FinTopBar(title, onBack, actions)`**（放 `ui/components/FinUi.kt`）：现有各返回页 `TopAppBar` 有的带标题、有的空、背景不一；统一替换为它（背景 `FinColors.PageBg`、返回箭头同色）。
6. **统一按钮语义**：主操作用 `FinSoftButton`、危险操作统一红色 `TextButton`、次操作 `OutlinedButton`；清理页面里零散 `Button(...)` 直拼，权重保持一致。
7. **统一卡片圆角**：主信息卡 `FinShapes.xl(24)`、列表/表单卡 `FinShapes.md(16)`；消除 `RoundedCornerShape(10/12/18/28)` 混用。
8. 每改一类跑一次 `assembleDebug`，便于定位。

**保持不变（避免破坏 iOS-like 风格，明确不采纳旧 UX 文档的两条建议）**：
- **不**把主绿 `#166B45` 改成更亮的 `#10B981/#059669`——保持低饱和绿只做点缀。
- **不**给页面背景/卡片加渐变或分层——保持浅灰底 + 纯白扁平卡 + 弱阴影。

**验收标准**：
- Given 全局搜索，Then 灰度文字色仅来自 `FinColors`，主要页面无散落正文灰硬编码。
- Given 抽查总览/规划/账户/资产详情/目标配置/月度复盘 6 页，Then 背景/圆角/边距/盈亏色符合规范（截图核对）。
- Given 所有返回页，Then 顶部栏均为 `FinTopBar`，标题/返回/背景表现一致。
- Given 删死代码后，When `assembleDebug` + `testDebugUnitTest`，Then 均通过。
- Given `formatCurrency`，Then 仅一处实现并被复用。

---

### P2-6 运行期验证价格管线（真机/模拟器实测，非纯编码）

**目标**：确认 P0 的接口修复在真实网络下生效。**前置**：装 debug APK 到模拟器/真机（联网）。

**步骤**：
1. 新建证券账户，录入 `AAPL`(USD)、`600519.SS`(CNY)、`0700.HK`(HKD) 各一笔，currentPrice 先随便填。
2. 首页"立即刷新"，看三只 currentPrice 是否刷新为接近当日行情、"数据时间"是否更新。
3. Logcat 看 `PriceRepository`/`PriceSyncWorker` 标签确认请求与回写。
4. 断网再刷新，确认回退缓存、UI 不崩溃；恢复网络再刷新成功。

**验收标准**：
- Given 三只标的，When 刷新，Then currentPrice ≈ 当日 Yahoo 行情（非录入值），"数据时间"更新。
- Given 多币种，Then `USDCNY=X`/`HKDCNY=X` 汇率成功，总资产折算正确。
- Given 断网刷新，Then 提示缓存/回退、不崩溃；恢复网络可再次成功。
- Given 连续失败 5 次，Then 熔断打开，5 分钟内不再发请求（Logcat 见 "Circuit breaker opened"）。

---

## 三、收尾 / 技术债（穿插完成）

- [ ] **交易流水自然语言文案**：`MainViewModel.kt` 中 `note = "资产记录买入: X"` 等改为自然语言（如"买入 X · 1000 元"）。
      验收：流水页无"资产记录买入"这类程序化文案，类型/金额/数量单价/时间/备注齐全可读。
- [ ] **Onboarding 持久化**：`Settings` 加 `val onboarded: Boolean = false`（DB 版本 7→8 迁移：`ALTER TABLE settings ADD COLUMN onboarded INTEGER NOT NULL DEFAULT 0`，登记到 `DatabaseMigrations.ALL_MIGRATIONS`）；完成首个账户后置 true；`MainScreen` 用 `onboarded` 而非"无账户"判断是否展示引导。
      验收：完成首个账户后不再展示引导；清空账户也不强制重走。
- [ ] **README 更新**：补四象限说明、每日更新、新增页面、`gradlew.bat`/JAVA_HOME 构建说明。
      验收：README 与实际功能一致，新人按文档可在 Windows `./gradlew assembleDebug` 成功。
- [ ] **（可选）首页最近变化**：总览页底部可加一条"最近变化"摘要（数据用 `HistoryRepository.getMonthlyChange()`）。低优先，做不做取决于首页是否过空。

---

## 四、体验与信任细节（自 UX 审查筛选并入，均与现有 iOS-like 风格一致）

> 这些是"小而真实"的打磨项，按主题分组。每条标注：涉及文件 → 做什么 → 验收一句话。

**信任与资金安全**
- [ ] 删除前展示影响范围：`AssetRecordScreen`/账户删除确认框列出将删除的"资产记录数 / 流水数 / 价格历史数"。
      验收：删除对话框显示具体受影响条数，而非仅一句"确定删除吗"。
- [ ] 金额隐藏全局化：把 `AccountHubScreen` 的 `amountsVisible` 提升为全局状态（`MainViewModel` 或 `Settings`），覆盖首页/详情/流水，并记住选择。
      验收：任一页切换"隐藏金额"后，所有页同步隐藏并在重启后保持。
- [ ] 卖出流程补齐：`AssetRecordScreen.showSellDialog` 增加成交价、费用、日期、备注（当前只填数量、用现价估算）。
      验收：卖出流水含真实成交价/费用/日期，不再仅按当前价估算。
- [ ] 转账余额预览：`CashFlowScreen` 转账时显示"转出后余额/转入后余额"，跨币种时解释为何禁用。
      验收：转账前可见双方余额变化预览。

**文案与解释（少刺激、多解释，对照设计约束的文案原则）**
- [ ] 去掉"实时价格"绝对表述：扫描 UI/`README.md`/`CLAUDE.md`，将"实时/每5分钟"改为"自动同步 / 每日更新 / 最近同步"。
      验收：全仓无"实时价格"字样，表述与"每日更新一次"一致。
- [ ] 风险桶"旧持仓"改名：`RiskBucketDetailScreen` 的"旧持仓"改为"证券持仓（迁移数据）"。
      验收：界面不再出现"旧持仓"这种内部术语。
- [ ] 设置项影响说明：`SettingsScreen`/`TargetAllocationScreen` 注明"本位币/目标配置/阈值改动会影响哪些页面"。
      验收：每个设置项下有一句影响说明。
- [ ] 错误提示给行动建议：价格同步失败等提示补"稍后重试 / 手动输入价格 / 查看上次价格"。
      验收：错误提示是可操作建议而非技术报错。

**录入与流水体验**
- [ ] 表单离开前未保存提示：`AccountScreen`/`AssetRecordScreen` 返回时若有改动，弹"保存草稿 / 放弃修改"。
      验收：有未保存输入时返回会二次确认。
- [ ] 大额输入辅助：金额输入框下显示"约 X 万 CNY"。
      验收：输入 100000 时显示"约 10.00 万"。
- [ ] 交易流水筛选：`TransactionHistoryScreen` 增加按账户/资产/类型/时间范围筛选。
      验收：可按四个维度过滤流水。

**交互去重**
- [ ] 资产详情主操作收敛：`AssetDetailScreen` 顶部"买入/卖出" 与底部"调整持仓"重复，统一为主操作"记一笔"+二级"编辑资料/删除"。
      验收：详情页无重复的买卖入口。
- [ ] `CashFlowScreen` 的"投资买卖统一接入这里"半成品提示：要么接入真实买卖，要么先隐藏该入口。
      验收：无"功能像坏了"的占位入口。
- [ ] `AccountHubScreen` 的"加权收益率/年化收益率"若仍显示 `--`：补齐计算或隐藏。
      验收：界面无长期 `--` 占位指标。

---

## 五、暂不做 / 记录备查（不进当前迭代，避免遗忘）

> 来自 UX 审查，属于"新功能或更大改造"，与当前"标普四象限账本"主线不冲突但非当前优先。
> 列在此处仅为备查，开工前需单独立项与产品确认。

- 隐私锁（生物识别 / PIN 进入校验）
- 资产目标（买房/教育/养老/应急金，与配置和大额支出模拟打通）
- 家庭成员 / 标签 / 多账本（本人/配偶/孩子/共同）
- 账本页"账户视角 / 资产视角"切换
- 空状态场景化入口（我有银行卡 / 基金 / 股票 / 先导入 CSV）
- 资产详情"收益贡献"分解（价格变化 / 现金流 / 汇率影响）
- 提醒策略扩展（配置偏离、同步失败、长期未更新、现金低于应急目标）

**已评估并明确不采纳**（会破坏既定 UI 风格）：
- 提亮主色为 `#10B981` 类亮绿 → 维持 `#166B45` 低饱和点缀。
- 背景/卡片加层次、渐变 → 维持浅灰底 + 纯白扁平卡。

---

## 建议执行顺序

```text
P2-3 更多资产类型      （改动聚焦、纯本地，适合起步）
P2-4 价格趋势折线      （单文件、低风险）
P2-6 运行期验证价格    （确认核心数据真能用，可随时插入）
P2-2 数据备份 + 复盘提醒（涉及 SAF/通知/权限，复杂度最高）
P2-5 全局视觉统一      （收口式清理，放最后避免反复冲突）
收尾：流水文案 / Onboarding 持久化 / README
```

> 第四节"体验与信任细节"建议**穿插**在相关功能任务里顺手完成（例如做 P2-4 时一并处理资产详情主操作去重、改 `RiskBucketDetailScreen` 时顺手把"旧持仓"改名），不必单独排期。
> 第五节为备查，不进当前迭代。
