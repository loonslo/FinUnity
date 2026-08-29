# FinUnity 三桶正式化与发布前修复清单

> 适用范围：当前工作树（Room v22、深色三桶原型 UI、本地 OCR、备份/报表/周期规则等新增功能）。  
> 产品决策：正式采用三桶，不再以“四象限”为产品口径。  
> 执行方式：严格按 Task 顺序推进；每个 Task 完成后先通过该 Task 的验证点，再进入下一项。  
> 状态约定：`[ ]` 待做，`[~]` 进行中，`[x]` 已完成，`[!]` 阻塞。

## 本轮执行记录（2026-08-28，模拟器重跑）

- Task 00 `[~]`：已复核工作区、敏感文件忽略和基线；已纳入 Room 22/23/24 schema，v9/v14/v16 脱敏 fixture 与提交拆分仍未完成。
- Task 01 `[~]`：三桶模型、兼容映射和纯函数测试已复核；完整边界验收仍未覆盖。
- Task 02 `[~]`：修复 AndroidTest schema assets 配置，v22→v23 真实迁移测试已在模拟器通过；v3/v9/v14/v16 全链路 fixture 仍缺失。
- Task 03 `[~]`：核心三桶/负债/今日涨跌测试已复核；SnapshotWorker 对照和全部数据质量场景仍需补齐。
- Task 04 `[~]`：规划导航与可保存表单代码已复核；七个子功能、通知冷启动和旋转验收尚未完整执行。
- Task 05 `[~]`：覆盖恢复实现已复核；逐表 round-trip、非法外键回滚和旧备份矩阵测试仍需补齐。
- Task 06 `[~]`：价格健康状态、缓存回退和 Worker 实现已复核；完整 Mock 网络矩阵和四类证券设备验收仍需补齐。
- Task 07 `[~]`：OCR 解析、统一代码和 HoldingLedger 已复核；批量失败回滚、重复导入和多币种 UI 验收仍需补齐。
- Task 08 `[~]`：报表、周期规则和对账实现已复核；仓储级自动化覆盖仍不完整。
- Task 09 `[~]`：合并持仓模型和详情页已复核；双账户买卖、多币种限制的完整 UI 验收仍需补齐。
- Task 10 `[~]`：旧页面入口清理、生命周期采集和 Locale 格式化已复核；`PrototypeScreens.kt` 仍为 1689 行，尚未按页面拆分。
- Task 11 `[~]`：本轮未纳入执行；隐私政策真实支持邮箱/公开 HTTPS URL 等发布资料仍待补齐。
- Task 12 `[~]`：clean、JVM `test`（Debug 142/142；Release 142/142）、Debug、AndroidTest 编译、Lint、Release APK/AAB 全部通过；模拟器 connected tests 25/25 通过，debug 安装启动无崩溃；完整产品流程仍未全部手工验收。

## 继续修复记录（2026-08-28）

- Task 02：v22→v23 迁移 fixture 增加 positions、prices、price_history、transactions、asset_snapshots、recurring_rules 行数校验，并增加未知风险桶失败测试，迁移类 2/2 通过。
- Task 05：新增备份导出/覆盖恢复、非法外键保护、未来版本拒绝测试，备份类 3/3 通过。
- Task 06：`PriceRepository` 支持注入 API，新增成功、失败、缓存回退和熔断测试，行情类 4/4 通过。
- Task 08：新增周期规则幂等、报表缺汇率/有效汇率、对账一致/不一致/数据不足测试，共 6/6 通过。
- Task 10：删除未引用的重复 `AccountHubScreen`，将仍使用的账户工具页独立为 `AccountAssetsByAccountScreen.kt`；正式 UI 不再读取 `summary.positions`。
- Task 12：完整 instrumentation suite 从 11 项扩展并通过至 25 项；最终 clean 全链路和模拟器启动冒烟均通过。
- UI 实机复核：按当前导航逐页截图检查总览、持仓、流水、配置、账户、账户详情、添加/编辑账户、资产录入、导入、规划、报表、周期收支、备份、隐私、对账、导出及交易录入等页面；确认可滚动页面的底部内容可达。修复财务报表指标卡横向截断、周期收入类别换行异常、交易录入底部按钮文字被导航栏 inset 裁切。

---

## 一、最终产品口径

### 1. 三桶定义

| 稳定键 | 中文名称 | 目标 | 典型资产 |
|---|---|---|---|
| `DEFENSIVE` | 防守 | 保证近期流动性、随时可用 | 现金、活期、余额宝、短期备用金 |
| `BALANCED` | 稳健 | 保障、保值、低波动 | 定期、债券、保险、年金、房产、车辆、低波动基金 |
| `AGGRESSIVE` | 进攻 | 长期增值、承担波动 | 股票、ETF、权益基金、训练仓 |

### 2. 默认目标比例

```text
DEFENSIVE:0.1,BALANCED:0.6,AGGRESSIVE:0.3
```

该比例由原默认四象限合并得出：防守 10%，稳健 40% + 保命 20% = 稳健 60%，进攻 30%。

### 3. 旧数据映射

| 旧值 | 新值 |
|---|---|
| `CASH` | `DEFENSIVE` |
| `CONSERVATIVE` | `BALANCED` |
| `INSURANCE` | `BALANCED` |
| `AGGRESSIVE` | `AGGRESSIVE` |

### 4. 统一统计口径

- `grossAssets`：所有正资产总额，不减负债。
- `liabilities`：负债余额的正数合计。
- `netWorth`：`grossAssets - liabilities`。
- 三桶金额之和必须等于 `grossAssets`，负债不进入任何桶。
- 三桶占比、目标偏离和再平衡均以“可配置资产”为分母，不以净资产为分母。
- 锁定专款仍展示在对应桶中，但不进入可投资策略盘和再平衡金额。
- “今日涨跌比例”的分母只使用有昨收价的可跟踪证券昨日市值，不能用房产、现金和净资产稀释。

### 5. 全局完成标准

- [ ] 运行代码、数据库、备份、CSV、OCR、页面、文案和文档只出现正式三桶口径。
- [ ] 旧四象限数据库和旧备份可无损迁移到三桶。
- [ ] 核心写操作具备事务性，UI 不提前报告成功。
- [ ] 所有自动化测试、Debug/Release 构建、Lint 门禁和真机核心流程通过。
- [ ] 不再存在用户可见的死入口、不可达正式页面或与能力不一致的承诺。

---

## 二、按顺序执行的 Task

## Task 00 `[~]`：冻结基线并建立可回退验证样本

**优先级**：P0  
**依赖**：无  
**目标**：先保护当前大量未提交工作，保存能验证迁移和核心计算的真实样本。

### 修改/产出

- [ ] 盘点当前 tracked/untracked 文件，确认截图、演示数据库、签名文件是否应进入版本库。
- [ ] 确保 `signing.properties`、`keys/`、`local.properties`、数据库样本中的敏感数据继续被忽略。
- [ ] 将本轮功能改动整理为可审查提交；不要把所有功能和三桶迁移压成一个不可回滚提交。
- [ ] 准备以下脱敏测试 fixture：
  - v9 四象限数据库；
  - v14 含估值字段数据库；
  - v16 含 Position + AssetRecord 数据库；
  - v22 当前数据库；
  - 旧备份 JSON（至少 v1、v6、v8）；
  - 含现金、保险、房产、股票、USD/HKD、负债的综合样本。
- [ ] 记录当前 142 个 Debug JVM 单测（Debug/Release 各 142，共 284 次执行）、Debug/Release 构建和 Lint 数量作为回归基线。

### 验证点

- [ ] `git status --short` 中无意外密钥、真实财务数据或 APK/AAB。
- [ ] 每个 fixture 都能说明来源版本、预期账户数、资产数、流水数和总额。
- [ ] fixture 只使用虚构名称和金额。
- [ ] 当前基线执行：

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug compileDebugAndroidTestKotlin
.\gradlew.bat lintDebug
```

---

## Task 01 `[~]`：建立三桶领域模型和纯函数测试

**优先级**：P0  
**依赖**：Task 00  
**目标**：先在纯 Kotlin 层定义唯一三桶语义，再改数据库和 UI。

### 修改范围

- `data/local/entity/AssetRecord.kt`
- `data/local/entity/Settings.kt`
- `data/local/entity/AllocationTarget.kt`
- `data/model/UnifiedAsset.kt`
- `data/model/PortfolioSummary.kt`
- `data/model/PortfolioCalculator.kt`
- 再平衡、风险告警、压力测试、落点相关纯函数与测试

### 任务项

- [ ] 将正式枚举收口为 `DEFENSIVE / BALANCED / AGGRESSIVE`。
- [ ] 删除业务层对 `CASH / CONSERVATIVE / INSURANCE` 的新增写入能力。
- [ ] 建立单一兼容映射函数，例如 `legacyBucketToThreeBucket()`；迁移、备份和导入统一复用。
- [ ] 将目标配置稳定键改为三桶，默认值改为 `0.1 / 0.6 / 0.3`。
- [ ] `parseTargetAllocation()` 同时兼容旧四键和新三键；旧 `CONSERVATIVE + INSURANCE` 自动合并。
- [ ] 明确资产类型默认桶：
  - `CASH` → `DEFENSIVE`
  - `TIME_DEPOSIT / REAL_ESTATE / VEHICLE / INSURANCE_POLICY` → `BALANCED`
  - `STOCK / ETF` → `AGGRESSIVE`
  - `FUND` 默认 `BALANCED` 或根据基金子类型决定；当前没有子类型时不得无提示地固定为进攻。
- [ ] 将再平衡、风险上限、落点和锁定专款逻辑改成三桶键。
- [ ] 保留 `maxAggressiveRatio`，文案统一为“进攻仓位上限”。
- [ ] 删除所有“四象限/保命桶/标普四象限”代码注释和用户文案。

### 验证点

- [ ] 旧目标 `CONSERVATIVE:0.4,AGGRESSIVE:0.3,INSURANCE:0.2,CASH:0.1` 解析结果为三桶 `0.1/0.6/0.3`。
- [ ] 新三桶目标解析后总和严格为 1；允许小数误差不超过 `1e-6`。
- [ ] 目标缺项、重复项、非法数字、负数、总和不为 1 时有明确校验结果。
- [ ] 保险、房产、车辆全部进入 `BALANCED`，不进入 `DEFENSIVE`。
- [ ] 进攻上限和再平衡只使用三桶键。
- [ ] 所有三桶纯函数均有边界单测：空资产、零总额、负债大于资产、锁定资产、缺汇率。

---

## Task 02 `[~]`：数据库 v22→v23 三桶数据迁移

**优先级**：P0  
**依赖**：Task 01  
**目标**：把现有数据库中的旧四象限值真实迁移成三桶，避免只在 UI 临时合并。

### 修改范围

- `data/local/AppDatabase.kt`
- `data/local/Converters.kt`
- `data/local/migration/DatabaseMigrations.kt`
- Room schema 输出目录
- `androidTest/.../AppDatabaseTest.kt` 或新的迁移测试

### 任务项

- [ ] 数据库版本升级到 v23。
- [ ] 新增 `MIGRATION_22_23`，更新：
  - `asset_records.riskBucket`
  - `allocation_targets.riskBucket`
  - `settings.targetAllocation`
- [ ] 自定义目标配置迁移必须保留用户比例，不能一律覆盖成默认值。
- [ ] 迁移中把 `CONSERVATIVE + INSURANCE` 比例求和为 `BALANCED`。
- [ ] `Converters.toRiskBucket()` 对未知值给出可诊断错误；不能静默写错桶。
- [ ] 开启 `exportSchema = true`，将 v23 schema 纳入版本控制。
- [ ] 使用 `MigrationTestHelper` 建立真实迁移测试，而不是只创建当前版本内存数据库。
- [ ] 迁移后执行 Room schema validation。

### 验证点

- [ ] v22 fixture 升级后不存在 `CASH / CONSERVATIVE / INSURANCE` 风险桶值。
- [ ] 迁移前后账户数、资产数、交易数、价格历史数、快照数完全一致。
- [ ] 迁移前四桶金额合并后与迁移后三桶金额一致，误差小于 0.01。
- [ ] 用户自定义比例迁移前后数学等价。
- [ ] v3→v23、v9→v23、v14→v23、v16→v23、v22→v23 全链路测试通过。
- [ ] 迁移失败时不会触发 v3+ 的破坏性重建。

---

## Task 03 `[~]`：重构总资产、负债、三桶和今日涨跌口径

**优先级**：P0  
**依赖**：Task 02  
**目标**：修正 `cashAssets = totalAssets - stockAssets` 等错误口径，建立可解释的家庭资产总览。

### 修改范围

- `PortfolioCalculator.kt`
- `PortfolioSummary.kt`
- `MainViewModel.kt`
- `SnapshotWorker.kt`
- `AssetSnapshot.kt`
- `HistoryRepository.kt`
- 历史、报表、总览、压力测试页面

### 任务项

- [ ] `PortfolioSummary` 明确输出：
  - `grossAssets`
  - `liabilities`
  - `netWorth`
  - `defensiveAssets`
  - `balancedAssets`
  - `aggressiveAssets`
  - `strategyAssets`
- [ ] 删除或弃用含糊的 `cashAssets / stockAssets / totalAssets` 业务含义，防止旧口径继续传播。
- [ ] 三桶金额只由 AssetRecord 风险桶汇总；负债账户单独计算。
- [ ] 三桶占比分母使用可配置资产，不使用净资产。
- [ ] 锁定专款从策略盘中扣除，但仍计入总资产和桶金额。
- [ ] “今日涨跌额”只计算有有效昨收的证券。
- [ ] “今日涨跌率”分母改为上述证券的昨日市值。
- [ ] 汇率缺失时返回带原因的数据质量状态，不得用 1:1 静默折算。
- [ ] 数据库升级到 v24，为快照增加三桶、总正资产、负债和口径版本字段。
- [ ] 旧快照标记为 legacy；不能伪造无法从旧字段恢复的三桶历史。
- [ ] 新快照满足：`三桶合计 = grossAssets`、`grossAssets - liabilities = netWorth`。

### 验证点

- [ ] 仅有房产时，`balancedAssets` 等于房产市值，`defensiveAssets` 为 0。
- [ ] 仅有保单时不会显示为现金。
- [ ] 100 万资产 + 20 万负债：`grossAssets=100万`、`liabilities=20万`、`netWorth=80万`，三桶占比仍合计 100%。
- [ ] 有锁定专款时，总资产不变，策略盘正确减少。
- [ ] 有房产和现金时，证券今日涨跌率不被非证券资产稀释。
- [ ] 缺 USD/HKD 汇率时界面明确显示“未计入/汇率缺失”，不产生 1:1 假总额。
- [ ] SnapshotWorker 与 MainViewModel 对同一 fixture 的结果逐字段一致。

---

## Task 04 `[~]`：让三桶规划闭环重新可达

**优先级**：P0  
**依赖**：Task 03  
**目标**：消除已经实现但用户无法进入的规划、历史和风险页面。

### 修改范围

- `MainActivity.kt`
- `PrototypeScreens.kt`
- `PlanningScreen.kt`
- `TargetAllocationScreen.kt`
- `MonthlyReviewScreen.kt`
- `ExpenseSimulationScreen.kt`
- `LandingPointScreen.kt`
- `StressTestScreen.kt`
- `HistoryScreen.kt`
- `SettingsScreen.kt`

### 任务项

- [ ] 明确一级信息架构：总览 / 持仓 / 流水 / 配置 / 账户。
- [ ] “配置”页保留三桶目标调节，并增加“进入完整规划”入口。
- [ ] 完整规划页提供：目标配置、偏离建议、落点、月度复盘、历史、支出模拟、压力测试。
- [ ] 所有旧四象限 UI 改成三桶。
- [ ] 删除“稳健+保命按旧比例再拆分”的保存逻辑。
- [ ] 月度提醒点击后能直接进入月度复盘，并能正常返回原路径。
- [ ] 设置页对规划入口的说明与真实导航一致。
- [ ] 给正式页面建立导航可达性清单；不可达页面要么接入，要么删除。
- [ ] 系统返回、顶部返回、底部 Tab 切换遵循同一导航规则。

### 验证点

- [ ] 从首页最多两次点击可进入完整规划页。
- [ ] 从规划页可进入并返回七个子功能，返回栈无跳页和死循环。
- [ ] 三桶目标保存后，首页、配置、规划、复盘显示完全一致。
- [ ] 旋转屏幕后仍停留在当前页面，已填写表单不丢失。
- [ ] 通知冷启动和应用已打开两种场景都能进入月度复盘。
- [ ] 静态扫描不存在只有 `when` 分支、没有任何入口的正式 Screen。

---

## Task 05 `[~]`：修复备份恢复和迁移安全

**优先级**：P0  
**依赖**：Task 02、Task 03  
**目标**：确保真实家庭财务数据可完整恢复且不会混入旧数据。

### 修改范围

- `BackupRepository.kt`
- 全部 DAO
- `BackupScreen.kt`
- 备份兼容测试

### 任务项

- [ ] `AssetSnapshotDao` 增加 `deleteAll()`。
- [ ] 恢复事务中清空所有会被备份恢复的表，包括 `asset_snapshots`。
- [ ] 备份版本升级到 v9，正式保存三桶键和 v24 快照字段。
- [ ] `normalizeLegacyJson()` 在 Gson 反序列化前将旧风险桶和旧目标配置改成三桶。
- [ ] 校验重复 ID、悬空 accountId/recordId、非法金额、NaN/Infinity、未知枚举和未来版本。
- [ ] 恢复前展示摘要：账户、资产、流水、快照、规则数量及备份时间。
- [ ] 恢复失败必须完整回滚，原数据库逐表不变。
- [ ] 恢复成功后重新调度周期任务并刷新 UI 状态。
- [ ] 明确“覆盖恢复”，不使用容易误解的“导入合并”文案。

### 验证点

- [ ] 在已有 10 条本地快照时恢复仅含 3 条快照的备份，恢复后必须恰好 3 条。
- [ ] v1/v6/v8 旧备份恢复为三桶后金额等价。
- [ ] v9 备份导出→清库→恢复后，逐表内容一致。
- [ ] 中途制造一条非法外键，恢复失败且原库 hash/计数不变。
- [ ] 未来备份版本给出“请升级应用”，不得尝试部分恢复。

---

## Task 06 `[~]`：修复行情同步、重试和价格可信度

**优先级**：P0  
**依赖**：Task 03  
**目标**：让“自动同步成功”与真实价格结果一致，并让用户知道数据是否过期。

### 修改范围

- `PriceRepository.kt`
- `PriceSyncWorker.kt`
- `MainViewModel.kt`
- `Price.kt`
- `PriceDao.kt`
- 总览和账户同步状态 UI
- Worker/Repository 测试

### 任务项

- [ ] `PriceSyncWorker` 必须消费 `RefreshResult`：
  - 全部成功 → `Result.success()`
  - 部分失败 → 保存成功项并记录失败详情；根据策略决定 retry
  - 全部失败且仍有重试次数 → `Result.retry()`
  - 达到上限 → `Result.failure()` 并保留旧缓存
- [ ] 删除未使用的 `BACKOFF_DELAYS`，或实现与注释一致的统一退避策略。
- [ ] 批量刷新前检查熔断器；半开状态只允许有限探测请求。
- [ ] 手动刷新和后台刷新复用同一服务，不重复请求同一 symbol。
- [ ] 手动刷新成功后写入价格历史或从 `prices.updatedAt` 读取真实同步时间。
- [ ] 首页展示整体最差价格状态：正常 / 延迟 / 过期 / 缓存回退 / 部分失败。
- [ ] 不支持 Yahoo 的基金代码保留手动价格，不应每天制造错误噪音。
- [ ] 统一证券代码：上海 `.SS`、深圳 `.SZ`、港股 `.HK`、美股 ticker。
- [ ] 网络失败时不得把旧价格标成“已同步”。

### 验证点

- [ ] Mock 全成功、部分失败、全部失败、超限、429、超时、断网、缓存回退。
- [ ] 全部失败会进入 WorkManager 重试，而不是成功结束。
- [ ] 手动刷新后首页同步时间立即更新。
- [ ] 断网时总额使用旧缓存并显示“价格偏旧”，恢复网络后可再次成功。
- [ ] 真机/模拟器验证 `AAPL`、`600519.SS`、`159919.SZ`、`0700.HK`。
- [ ] 验证 `USDCNY=X`、`HKDCNY=X` 汇率以及多币种总额。
- [ ] 连续失败和熔断恢复行为可从测试或日志明确确认。

---

## Task 07 `[~]`：修复 OCR、手动录入和批量导入事务

**优先级**：P1  
**依赖**：Task 01、Task 05  
**目标**：确保导入的数据类型、代码、币种和成功状态可信。

### 修改范围

- `HoldingScreenshotParser.kt`
- `ScreenshotImportRepository.kt`
- `PrototypeScreens.kt`
- `MainActivity.kt`
- `HoldingLedgerRepository.kt`
- `CsvImportRepository.kt`
- 导入测试

### 任务项

- [ ] OCR 正则支持 `.SS/.SH/.SZ/.HK`，并将 `.SH` 规范化为 `.SS`。
- [ ] 裸六位 A 股代码必须推断交易所或标记“需确认”，不能静默生成无法同步的代码。
- [ ] 港股统一处理 4/5 位代码和前导零。
- [ ] OCR 解析保留原始文本、标准化代码和置信/需确认原因。
- [ ] OCR 批量保存改为单个 suspend API + Room 事务，返回逐行结果。
- [ ] UI 仅在事务成功后提示“已导入 N 项”；失败显示具体行，不提前返回。
- [ ] 手动快速录入增加：资产名称、资产类型、币种、数量、成本价、当前价。
- [ ] 风险桶由资产类型自动映射，不让用户直接选择三桶；基金类型不明确时提示确认。
- [ ] 同码、同账户、同币种的更新/合并规则写进 UI 说明和单测。
- [ ] CSV、OCR、手动录入统一走 HoldingLedger，不各自直接写 DAO。
- [ ] 回滚导入批次时只删除该批新增项，不删除此前已存在并被更新的持仓。

### 验证点

- [ ] OCR 单测覆盖：`510300.SS`、`600519.SH`、`159919.SZ`、`00700.HK`、`AAPL`。
- [ ] 市值绝不被当作成本；成本缺失必须人工填写。
- [ ] 10 行批量导入第 6 行失败时，根据产品策略做到全回滚或明确部分成功，不能假报 10 行成功。
- [ ] 重复导入同一截图不会生成重复持仓和重复流水。
- [ ] 同一券商账户可录入 CNY/USD/HKD 资产。
- [ ] 股票、ETF、现金、定期、保单默认桶正确。

---

## Task 08 `[~]`：修复报表、周期规则和对账正确性

**优先级**：P1  
**依赖**：Task 03、Task 05  
**目标**：消除看起来完整但金额可能错误的辅助功能。

### 修改范围

- `FinancialReportRepository.kt`
- `FinancialReportScreen.kt`
- `RecurringRuleRepository.kt`
- `RecurringRuleWorker.kt`
- `ReconciliationScreen.kt`
- `MainViewModel.reconcileAccountBalance()`

### 任务项

- [ ] 报表缺汇率时不得使用 `1.0`；使用最后有效汇率或将该币种明确排除出合计。
- [ ] 报表显示汇率时间和未计入币种。
- [ ] 周期规则的 29/30/31 日按“当月最后一天”规则处理。
- [ ] 周期规则以 `ruleId + yyyy-MM` 保证幂等。
- [ ] 处理闰年、时区变化、Worker 延迟跨月和应用长期未打开场景。
- [ ] 周期规则删除、禁用、账户删除后的行为明确。
- [ ] 对账建立“期初余额/起始快照”概念；没有期初数据时不得把 0 当成可靠起点。
- [ ] 对账结果区分：一致、不一致、数据不足。
- [ ] `getComputedBalance()` 名称和实现一致；不要仅返回最近 `balanceAfter` 却声称完成计算。
- [ ] 普通现金账户和负债账户分别测试，不用一套错误口径强行统一。

### 验证点

- [ ] USD 汇率缺失时，CNY 总报表不会把 100 USD 算成 100 CNY。
- [ ] 2 月的 31 日规则会在 2 月最后一天执行一次。
- [ ] Worker 同月重复运行不会重复生成流水。
- [ ] 账户没有期初余额时显示“数据不足”，不显示虚假的差额。
- [ ] 有完整期初余额和流水时，对账结果可复算且误差小于 0.01。

---

## Task 09 `[~]`：补齐跨账户合并持仓详情

**优先级**：P1  
**依赖**：Task 03、Task 04  
**目标**：让多账户聚合不只停留在列表总数，点击后仍能看到完整来源。

### 修改范围

- `MergedHolding.kt`
- 新建 `MergedHoldingDetailScreen.kt`
- `PrototypeHoldingsScreen`
- `MainActivity.kt`
- 交易入口和交易历史查询

### 任务项

- [ ] 新增以标准化证券代码为键的合并详情页。
- [ ] 展示总数量、总成本、总市值、总盈亏、加权成本和币种状态。
- [ ] 展示每个账户/来源的数量、成本、现价、同步状态。
- [ ] 同码多币种不得强行合并成一个价格；需要分组或明确 `MIXED` 限制。
- [ ] 买入/卖出必须明确选择账户和币种。
- [ ] 多来源卖出流水能在合并详情和对应来源中追溯。
- [ ] 删除“点击合并持仓后打开 firstOrNull 资产记录”的逻辑。

### 验证点

- [ ] 两个账户持有同一 ETF，列表和详情的数量、成本、市值一致。
- [ ] 点击详情可看到两个来源，不会随机打开第一条。
- [ ] 从账户 A 卖出后只减少 A，合并总额同步变化。
- [ ] 同码不同币种时禁止未经确认的跨币种卖出。

---

## Task 10 `[~]`：导航、状态和遗留代码收口

**优先级**：P2  
**依赖**：Task 04、Task 09  
**目标**：降低超大文件和新旧页面并存造成的回归概率。

### 任务项

- [ ] 使用 Navigation Compose 或等价可保存导航状态替换手工 `Screen + List` 栈。
- [ ] 页面参数只传稳定 ID，不把完整 Entity 放进可恢复导航状态。
- [ ] ViewModel 按 feature 拆分：总览、持仓交易、导入、规划、设置/工具。
- [ ] 将 1580 行 `PrototypeScreens.kt` 按页面拆文件。
- [ ] 删除未使用的旧 `MainScreen`、不可达 `PositionScreen` 和重复 UI，或明确迁移期限。
- [ ] Position 表完成退役：主计算、Worker、Flow 不再读取；评估后续数据库版本删除表。
- [ ] Flow 改用 `collectAsStateWithLifecycle()`。
- [ ] 表单状态使用 `rememberSaveable` 或 ViewModel，支持旋转和进程重建。
- [ ] 清理未使用参数、未使用资源、过时注释和死枚举。
- [ ] 所有金额格式化显式使用 Locale，处理 Lint `DefaultLocale`。

### 验证点

- [ ] 旋转屏幕、切后台、系统回收后恢复当前页面和未提交表单。
- [ ] 底部 Tab 不产生无限返回栈。
- [ ] 全局静态扫描无旧四象限枚举和值。
- [ ] 主代码不再查询活跃 Position；旧 fixture 迁移后仍可读取 AssetRecord。
- [ ] Lint 的未使用参数、默认 Locale、弃用 API告警显著收敛，并记录剩余豁免原因。

---

## Task 11 `[~]`：发布工程、隐私和体积收尾

**优先级**：P2  
**依赖**：Task 05～Task 10  
**目标**：达到可以交付真实用户测试的发布状态。

### 任务项

- [ ] Release 开启 R8 和资源收缩，维护必要 keep rules。
- [ ] 使用 AAB 评估本地中文 OCR 的实际下载体积；记录 APK/AAB 前后变化。
- [ ] 检查 ML Kit、Room、Compose、Lifecycle、WorkManager、Retrofit 等依赖兼容升级，不一次性盲升全部。
- [ ] 修复 `CreateDocument()` 弃用调用并指定明确 MIME 类型。
- [ ] 完成隐私政策真实支持邮箱和公开 HTTPS URL。
- [ ] 商店说明明确：数据本地保存、OCR 本地处理、行情会向 Yahoo 请求证券代码、备份为明文。
- [ ] 补数据删除说明、免责声明和行情数据来源说明。
- [ ] 版本号、签名、图标、应用名“衡仓/FinUnity”统一。
- [ ] README、AGENTS、ROADMAP、任务清单全部更新为三桶、Room 当前版本和真实功能状态。
- [ ] 券商授权同步在首批发布中若未实现，所有 UI 和文档不得暗示已经支持。

### 验证点

- [ ] Release APK/AAB 可签名安装、冷启动、升级安装。
- [ ] R8 后 Room、Gson 备份、Retrofit、WorkManager、ML Kit 正常。
- [ ] 反编译 APK 搜不到签名密码、API Key、真实邮箱以外的敏感配置。
- [ ] 隐私政策 URL 无登录即可访问，应用内文案与网页一致。
- [ ] Lint 0 error；新增 warning 必须说明原因或修复。
- [ ] Release 体积有记录并达到团队设定上限。

---

## Task 12 `[~]`：最终自动化与真机验收

**优先级**：P0 发布门禁  
**依赖**：全部前置 Task  
**目标**：用完整数据闭环证明三桶版本可用，而不只证明能编译。

### 自动化命令

```powershell
.\gradlew.bat clean
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat compileDebugAndroidTestKotlin
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleRelease
```

### 必测场景

- [ ] 全新安装：Onboarding → 建账户 → 录现金/定期/保单/股票 → 三桶总览正确。
- [ ] 旧版升级：v9/v14/v16/v22 数据升级到三桶，金额和流水不丢失。
- [ ] 多币种：CNY/USD/HKD 汇率正常、缺失、过期三种状态。
- [ ] 交易：买入、加仓、部分卖出、全部卖出、手续费、现金增减和流水一致。
- [ ] 多账户同码：合并展示、来源详情、指定账户卖出。
- [ ] 导入：CSV、OCR、手动录入、重复导入、批次回滚。
- [ ] 备份：导出、覆盖恢复、非法文件、未来版本、恢复中断。
- [ ] 后台任务：价格同步、快照、月度提醒、周期收支。
- [ ] 规划：目标、偏离、落点、复盘、支出模拟、压力测试、历史。
- [ ] 状态恢复：旋转、切后台、进程重建、通知冷启动。
- [ ] 隐私：截图不上传、自动云备份关闭、明文备份风险提示。

### 数学不变量

- [ ] `grossAssets - liabilities = netWorth`。
- [ ] `defensiveAssets + balancedAssets + aggressiveAssets = grossAssets`。
- [ ] 有资产时三桶占比之和为 100%，误差小于 `1e-6`。
- [ ] 部分卖出后单位成本不变，剩余成本按数量比例下降。
- [ ] 交易前后现金、持仓和流水可复算。
- [ ] 备份恢复前后逐表记录数和关键金额一致。
- [ ] 缺价格/汇率不会生成 NaN、Infinity 或 1:1 假结果。

### 发布判定

- [ ] 所有 P0/P1 Task 完成。
- [ ] 自动化命令全部通过。
- [ ] 真机验收无阻断问题。
- [ ] 无未说明的数据迁移风险。
- [ ] 无用户可见旧四象限文案或不可达正式页面。
- [ ] 产品负责人确认三桶默认比例、基金默认桶和负债展示口径。

---

## 三、建议迭代拆分

### 迭代 A：三桶和数据安全

```text
Task 00 → Task 01 → Task 02 → Task 03 → Task 05
```

交付标准：旧数据可迁移、三桶金额正确、备份可恢复。

### 迭代 B：核心闭环可用

```text
Task 04 → Task 06 → Task 07
```

交付标准：规划可达、行情可信、导入不假报成功。

### 迭代 C：辅助能力和多账户体验

```text
Task 08 → Task 09
```

交付标准：报表/周期/对账可信，合并持仓可追溯。

### 迭代 D：工程与发布

```text
Task 10 → Task 11 → Task 12
```

交付标准：架构收口、发布资料完备、完整验收通过。

---

## 四、暂不纳入首版阻断的后续项目

以下需求不应混入三桶修复主线；若未实现，产品入口必须明确隐藏或标注范围：

- [ ] 券商 OAuth/只读授权和定时持仓同步。
- [ ] 家庭成员、多账本和共同资产归属。
- [ ] 生物识别/PIN 隐私锁。
- [ ] 买房、教育、养老等目标账户体系。
- [ ] 自动基金分类、估值数据、场内溢价数据源。
- [ ] 收益贡献分解：价格、现金流、汇率影响。
- [ ] 云端加密同步与跨设备自动同步。

这些项目应单独立项，先明确接口合规、安全存储、隐私政策和成本预算。
