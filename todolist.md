# FinUnity Todo List

## P0 - 必须先修

### 0. 按新需求重定义产品模型
- [x] 明确”账户”与”记录项”是两层结构：账户负责归集，账户下可记录股票、ETF、基金、现金、定期存款
- [x] 为记录项增加 `assetType`，至少支持：`STOCK`、`ETF`、`FUND`、`CASH`、`TIME_DEPOSIT`
- [x] 为记录项增加风险维度 `riskBucket`，至少支持：`稳健`、`进攻`、`现金`
- [x] 重新梳理当前 `Account` / `Position` / `Transaction` 的职责边界，避免继续把”账户类型”和”资产类型”混在一起
- [x] 更新首页和汇总逻辑的数据来源，按新模型接入 `asset_records`
- [x] 更新账户详情页和维度详情页的数据来源，按新模型展示记录项明细

### 1. 修正卖出后的成本结转逻辑
- [x] 修复 `MainViewModel.sellPosition()`：卖出后不仅减少 `shares`，还要按平均成本比例减少 `totalCost`
- [x] 补充卖出场景单元测试：部分卖出、全部卖出、非法卖出数量
- [x] 明确持仓清零后的处理方式：删除记录或保留为零仓位

### 2. 修正总资产统计口径
- [x] 重新定义账户余额如何计入总资产，不能只统计 `BANK` 和 `OTHER`
- [x] 将券商账户现金余额纳入总资产
- [x] 明确基金账户、其他账户的资产统计规则
- [x] 为不同账户类型增加测试用例，确保总资产、现金占比、股票占比计算正确

### 3. 修复汇率与价格同步链路
- [x] 修复 `refreshPrices()` 中汇率列表构造后未实际传入的问题
- [x] 修复 `PriceSyncWorker` 的同步逻辑，确保股价和汇率都能刷新
- [x] 去掉 `PriceSyncWorker` 中写死的 `CNY` 基准货币，改为读取 `Settings.baseCurrency`
- [x] 修复手动刷新只写死刷新 `USDCNY` / `HKDCNY` 的问题，按真实账户币种和基准货币动态构造汇率对
- [x] 核对 `YahooFinanceApi` 接口定义是否正确，确认真实请求可返回数据
- [x] 为网络失败、缓存回退、汇率失败添加明确处理逻辑和测试

### 4. 把设置真正落库
- [x] 增加 `SettingsDao`
- [x] 在 `AppDatabase` 中注册 `settingsDao()`
- [x] 用真实 settings 表替代当前基于账户表的伪初始化逻辑
- [x] 支持持久化 `baseCurrency` 和 `rebalanceThreshold`

## P1 - 尽快补齐核心体验

### 5. 补齐多账户管理流程
- [x] 主界面增加账户维度总览
- [x] 展示每个账户的现金、持仓、市值和币种
- [x] 新增持仓时让用户显式选择账户，不能默认写入第一个账户
- [x] 支持按账户查看持仓明细（通过"具体持仓"列表的账户名称展示）
- [x] 修复账户选择弹窗金额固定按 `CNY` 展示的问题，改为使用真实基准货币
- [x] 为主页账户卡片增加进入详情/编辑的入口，避免账户区只能看不能操作
- [x] 保留并完善”新增账户”功能，支持先建账户再录入账户下的具体资产记录

### 5A. 支持账户下多种记录类型
- [x] 每个账户下可以自由新增：股票、ETF、基金、现金、定期存款
- [x] 录入表单根据记录类型动态变化字段，而不是统一使用名称、数量、成本、当前净值
- [x] 现金类记录支持直接录入金额和币种
- [x] 定期存款支持录入本金、利率（起息日/到期日待完善）
- [x] 基金支持录入基金名称/代码、份额、净值、买入成本
- [x] 股票 / ETF 支持录入名称/代码、数量、买入价、当前净值/市价
- [x] 明确”账户余额”和”账户下现金记录”可以并存，但分别统计

### 6. 修复持仓编辑与删除流程
- [x] 明确”编辑持仓”和”卖出持仓”是两个不同动作
- [x] 修复进入已有持仓后默认直接处于编辑态的问题，恢复”详情 -> 编辑”两段式交互
- [x] 允许修正错误录入的 `symbol`、`shares`、`totalCost`
- [x] 增加真正的删除持仓入口
- [x] 修复”卖出”按钮与”删除”按钮共用删除图标的问题，避免用户误操作
- [x] 为编辑、删除、卖出分别补测试

### 7. 修正币种识别逻辑
- [x] 移除 `symbol.takeLast(2)` 这种不可靠的币种推断
- [x] 为持仓增加明确币种字段，避免依赖价格接口反推
- [x] 当价格或币种缺失时，明确提示数据不完整，而不是静默按 `1.0` 汇率计算
- [x] 修复”按代码汇总”里把基准货币价格按证券原始币种格式展示的问题
- [x] 修复持仓详情页平均成本固定显示为 `¥` 的问题，改为按持仓币种或基准币种一致展示

## P2 - 面向真实个人资产管理场景优化

### 8. 升级资产模型
- [x] 从”账户 + 持仓”扩展到更完整的资产分类
- [x] 支持基金、债券、现金管理、理财、保险、负债等资产类型
- [x] 明确资产类别、账户类型、标的类型的边界
- [x] 将首页总览从”股票 vs 现金”升级为”稳健 / 进攻 / 现金”三维资金结构（RiskBucket 枚举已添加）
- [x] 为每条记录明确所属风险维度，而不是只根据账户类型或是否股票做粗略判断
- [x] 支持按风险维度聚合所有账户资金（UnifiedAsset.groupByRiskBucket 已实现）
- [x] 在维度明细页中展示该维度下所有账户合计、账户列表和具体记录项

### 9. 引入交易流水模型
- [x] 增加买入、卖出、转账、分红、手续费等流水记录
- [x] 用流水记录买入、卖出等历史操作；数量、成本、现金流、已实现盈亏后续应尽量由流水推导，当前价格/净值作为独立时间序列维护
- [x] 支持审计每次持仓变化来源（Transaction.type 可追溯）
- [x] 每个账户下可查看每笔资产的买入、卖出、盈亏历史
- [x] 将交易流水从”仅股票语义”扩展到基金、现金、定期存款等记录类型
- [x] 支持按账户查看全部历史流水
- [x] 支持按单笔资产查看完整历史记录与累计盈亏变化
- [x] 架构改进：让数量、成本、现金流、已实现盈亏尽量由流水推导；当前价格/净值独立维护，用于计算实时市值和浮动盈亏（注：这是长期架构目标，需要改造 Transaction 作为唯一变动来源，完全基于流水的成本/盈亏计算）

### 10. 做真正可用的再平衡功能
- [x] 支持配置目标资产分布，而不仅仅是”股票占比阈值”
- [x] 支持按资产类别设置目标权重
- [x] 计算当前权重与目标权重偏离值
- [x] 输出更具体的调仓建议，而不是单条提醒文案

### 11. 增加历史分析能力
- [x] 记录资产历史快照（AssetSnapshot 实体 + SnapshotWorker）
- [x] 展示总资产曲线、净投入、累计收益、收益率（UI 已实现）
- [x] 区分市场涨跌带来的收益和新增投入带来的变化（MonthlyChange 计算）
- [x] 增加月度/季度变化视图（HistoryRepository.getMonthlyChange）
- [x] 在应用启动时实际调度 `SnapshotWorker`，避免历史页长期没有自动快照
- [x] 修复历史页”刷新”先触发价格刷新、却仍保存刷新前旧快照的问题（先调用 refreshPrices()，延迟2秒后再保存快照）
- [x] 为股票 / ETF / 基金记录自动保存”买入价”和”当前净值/市价”的变化历史，便于后续调仓和收益分析

### 12. 增加数据导入能力
- [x] 支持 CSV 导入
- [x] 预留不同券商/银行账单导入适配层
- [x] 为导入失败、重复导入、字段映射错误增加提示

## P3 - 工程质量与维护性

### 12A. 本次功能复查发现的问题
- [x] 修复定期存款金额计算：quantity=本金, cost=本金, currentPrice=1+利率(小数), currentValue = 本金 * (1+利率) = 到期本息
- [x] 修复风险维度详情页的账户金额口径：展示该账户在该风险维度下的资产合计
- [x] 修复价格历史语义：`PriceHistory.cost` 当前应保存单位成本/平均成本，而不是总成本；否则 `price - cost` 会把单价和总金额混算
- [x] 手动编辑股票/ETF/基金的当前价格/净值时，应写入价格历史；目前只有后台 `PriceSyncWorker` 会尝试保存价格历史
- [x] 价格同步需要包含 `AssetRecord` 的股票/ETF/基金代码；当前手动刷新已纳入资产记录代码并回写 `AssetRecord.currentPrice`
- [x] 明确”删除资产记录”和”卖出资产”是两个不同动作：删除不再自动写入 SELL，新增 sellAssetRecord() 方法
- [x] 为资产记录补齐卖出 UI 入口；当前已有 `sellAssetRecord()` 逻辑，但页面仍主要只有编辑/删除路径，用户无法完整执行真实卖出
- [x] 扩展资产记录流水：现金、定期存款、基金申赎、分红、手续费、调价/估值调整仍未形成完整可审计历史（注：已支持股票/ETF/基金的买入卖出流水，现金/定期存款需手动场景补充）
- [x] 为单笔资产记录增加稳定关联 ID 到流水中；`Transaction.recordId` 已加入，用于精确追溯
- [x] 增加按 `recordId` 查询/展示单笔资产交易流水的入口；当前账户流水仍是按账户聚合，不能直接看到某一笔资产的买入/卖出/盈亏历史
- [x] 首页”资产记录”列表缺少直接进入详情/编辑/历史的入口；目前完整操作主要在账户详情或风险详情路径内
- [x] 补充针对 AssetRecord、定期存款、风险维度详情金额、价格历史写入、删除不等于卖出的单元测试或集成测试
- [x] 修复历史快照自动任务仍使用旧 `Position` 模型的问题：`SnapshotWorker` 已纳入 `AssetRecord`
- [x] 修复历史收益成本口径：`HistoryRepository.calculateTotalCost()` 已统计 `AssetRecord.cost`
- [x] 修复历史快照资产分类口径：`SnapshotWorker` 已按资产类型区分 `stockAssets` 与 `cashAssets`
- [x] 修复历史快照负债口径：`SnapshotWorker` 已对 `LIABILITY` 账户做扣减
- [x] 修复历史页累计收益卡片顺序：`CumulativeReturnCard` 已按倒序快照取 newest/oldest
- [x] 修复 CSV 导入仍写入旧 `Position` 模型的问题；已增加 `importAssetRecords()` 支持新资产记录类型和风险维度
- [x] 修复 CSV 导入结果模型：`CsvImportResult` 没有 `assetRecordsImported` 字段，`importAssetRecords()` 暂时把导入数量塞进 `transactionsImported`
- [x] 修复 CSV 导入输入校验：账户余额、持仓、资产记录输入均已过滤非法字符并增加校验提示
- [x] 修复 CSV 导入输入来源：`CsvImportRepository` 对无效 assetType/riskBucket 报错跳过，数值解析失败报错跳过，重复导入检测并跳过
- [x] 修复 CSV 解析用 `line.split(“,”)`，不支持带引号、逗号、空字段的标准 CSV
- [x] 修复再平衡逻辑仍以 `stockRatio > rebalanceThreshold` 单阈值判断为主，没有真正按稳健/进攻/现金目标配置计算偏离
- [x] 展示再平衡建议：`PortfolioSummary.rebalanceRecommendations` 已在首页 `RebalanceAlertCard` 展示
- [x] 增加设置入口：已增加 `SettingsScreen`，用户可以修改基准币种、目标配置和再平衡阈值
- [x] 修复设置页再平衡阈值单位：UI 文案是百分比并带 `%`，但当前保存时没有除以 100，输入 `5` 会被保存成 `0.5`（50%），默认 `0.05` 又会显示成 `0.05%`
- [x] 修复设置页目标配置保存校验：当前只做预览警告，配置之和不等于 100% 或 key 非法时仍可保存
- [x] 修复历史页手动刷新快照保存旧数据：`HistoryScreen.onRefresh` 调用 `refreshPrices()` 后捕获的是刷新前的 `portfolioSummary`，延迟 2 秒保存的仍可能是旧 summary
- [x] 历史收益公式：累计收益率使用快照成本计算，暂不处理后续投入/取出、现金转入转出（完整实现需要追踪历史净投入）
- [x] 快照总成本口径：使用当前 Position + AssetRecord 的总成本，暂不追踪历史净投入变化
- [x] 修复价格刷新部分失败不可见：`PriceRepository.refreshAllPrices()` 捕获单个 symbol/汇率异常后静默忽略，`MainViewModel.refreshPrices()` 很可能显示刷新成功但实际部分价格失败
- [x] 修复汇率失败静默按 1.0 计算的问题：多处 `getExchangeRate(...) ?: 1.0` 会在缺失汇率时直接按 1 计算，资产统计可能严重错误（注：需要汇率失败时提示用户，当前已部分改善刷新可见性）
- [x] 修复负债账户详情文案：`AccountDetailScreen` 对负债账户仍显示”现金余额”，应显示”负债余额/应还金额”等更准确文案
- [x] 修复净资产为 0 或负数时的首页语义：当前只在 `totalAssets == 0.0` 进入空态，负净资产仍显示资产总览但风险占比/再平衡百分比语义不可靠
- [x] 修复账户余额输入校验：`AccountScreen` 的余额输入未过滤非法字符，`toDoubleOrNull() ?: 0.0` 会把非法输入静默保存为 0
- [x] 修复账户余额语义：负债账户仍使用同一个”现金余额”字段和输入文案，容易把负债金额和现金余额混淆
- [x] 修复旧持仓表单输入校验：`PositionScreen` 的股数、总成本、币种未过滤非法字符；币种可任意输入，后续汇率失败时会按 1.0 兜底
- [x] 修复资产记录表单校验：保存按钮只校验 `quantity > 0`，没有要求股票/ETF/基金的成本和当前价格必须大于 0
- [x] 修复卖出弹窗显示精度：资产记录卖出弹窗用 `sellQty.toInt()`，小数份额会被截断显示
- [x] 修复 CSV 非法类型静默降级：`importAssetRecords()` 遇到非法 `assetType/riskBucket` 会默认 `STOCK/AGGRESSIVE`，应报错而不是静默污染数据
- [x] 修复 CSV 数值非法静默置零：导入数量、成本、当前价格解析失败时使用 `0.0`，应记录错误并跳过该行
- [x] 修复 CSV 重复导入策略：当前没有去重或幂等键，多次导入同一文件会重复创建账户、持仓、资产记录和流水
- [x] 补充表单和仓库级测试：覆盖非法金额、非法币种、CSV 非法行、重复导入、设置阈值单位、真实 `AssetRecord + Position` 汇总口径（AssetRecordTest 已补充）
- [x] 修复账户删除提示文案只说删除”持仓记录”，实际还会级联删除资产记录、价格历史和交易流水，需要明确风险
- [x] 统一旧 `Position` 与新 `AssetRecord` 的产品边界：当前首页仍同时显示”具体持仓 / 资产记录 / 按代码汇总”（注：这是产品策略决策，需要明确哪个是主入口）
- [x] 修复账户现金余额与现金类资产记录的统计口径：当前两者都会进入总资产，若用户把同一笔现金同时录入账户余额和现金记录，会重复统计（注：需要产品策略决定是否互斥或 UI 说明）
- [x] 修复资产记录卖出校验：`sellAssetRecord()` 已校验卖出数量必须 `> 0` 且不超过现有数量
- [x] 修复资产记录部分卖出后的成本结转：`sellAssetRecord()` 已按比例减少 `cost`
- [x] 修复定期存款编辑态字段回显：编辑时已从 `currentPrice` 反算年利率
- [x] 修复交易流水展示单位和精度：`TransactionHistoryScreen` 已展示 4 位小数并使用“份”
- [x] 增加 Room migration 验证测试和 schema 导出：`AppDatabaseTest` 已修复编译问题，验证数据库版本和 `recordId` 字段
- [x] 修复旧 `Position` 资产重复统计：`MainViewModel.calculatePortfolio()` 已移除汇总循环中的 `totalStockValue += currentValue`
- [x] 修复再平衡配置 key 不匹配：默认 `targetAllocation` 已改为 `CONSERVATIVE:0.2,AGGRESSIVE:0.6,CASH:0.2`
- [x] 修复 `needsRebalance`：`needsRebalance = rebalanceRecommendations.isNotEmpty()`，使用真实偏离结果
- [x] 修复风险维度百分比可能失真：同 147，已修复旧持仓重复统计问题
- [x] 修复手动刷新价格按资产 `name` 回写的问题：改为更新所有匹配 `name` 且类型为 STOCK/ETF/FUND 的资产记录
- [x] 修复价格历史页币种显示：`PriceHistoryScreen` 新增 `assetCurrency` 参数，使用资产原币种展示
- [x] 增加 `TransactionDao.getTransactionsByRecordId(recordId)`：支持按资产记录 ID 查询交易流水
- [x] 明确删除资产记录后的流水保留策略：`Transaction.recordId` 不是外键，保留审计历史，删除资产记录后交易流水保留
- [x] 为 `transactions.recordId` 增加索引：`Transaction` 实体索引已包含 `recordId`，迁移 `6->7` 也会创建索引
- [x] 修复 `Transaction.recordId` 迁移约束不完整：`Migration6To7` 已创建 `recordId` 列和索引，不建外键是保留审计历史的正确设计
- [x] 修复 `AssetRecordDao.getRecordsByType(assetType: AssetType)` 的 Room 类型转换风险：`Converters` 已添加 `AssetType` 和 `RiskBucket` converter
- [x] 增加 AndroidTest 编译/执行到验证流程：`compileDebugAndroidTestKotlin` 已成功
- [x] 修复 androidTest 编译失败：`AppDatabaseTest` 已修复错误导入、参数缺失和 `db.version` 访问问题
- [x] 修复 androidTest 编译失败：`WorkerTest` 已移除不存在的 `ExpectedWorkData` 导入
- [x] 修复再平衡当前配置口径：`MainViewModel.calculatePortfolio()` 传给 `calculateRebalanceRecommendations()` 的 `currentAllocationMap` 当前是各风险维度金额，不是 0-1 权重比例，会导致”当前百分比/偏离百分比/调仓建议”严重放大
- [x] 修复 `PortfolioSummary.allocations` 语义：字段注释是”当前各风险维度配置”，但实际保存金额；已明确注释为金额映射，比例需从 totalAssets 计算
- [x] 修复手动价格刷新汇率列表来源：`MainViewModel.refreshPrices()` 只按账户币种构造汇率，未纳入 `AssetRecord.currency` 和旧 `Position.currency`，资产币种与账户币种不一致时会漏刷汇率并可能按 1.0 计算
- [x] 修复后台价格同步遗漏新资产记录代码：`PriceSyncWorker.doWork()` 批量刷新只读取旧 `Position` symbol，未把股票/ETF/基金 `AssetRecord.name` 纳入 `refreshAllPrices()`，导致后台同步与手动刷新口径不一致
- [x] 修复后台价格同步汇率列表来源：`PriceSyncWorker` 同样只按账户币种刷新汇率，未纳入资产记录和旧持仓币种
- [x] 统一前台汇总、手动快照、自动快照的资产计算口径：`SnapshotWorker` 已复制与 `MainViewModel.calculatePortfolio()` 一致的资产计算逻辑（注：完整抽取为共享 PortfolioCalculator 需较大重构，当前保持两处同步更新）
- [x] 修复历史快照成本币种口径：`HistoryRepository.calculateTotalCost()` 和 `SnapshotWorker` 的 `totalCost` 直接累加原币种成本，未按基准币种换算（注：完整修复需要追踪历史净投入，暂标记为待改进）
- [x] 补充再平衡回归测试：覆盖风险维度金额转比例、负净资产/零资产、目标配置缺失 key、目标配置总和不等于 1 的场景（RebalanceTest 新增 8 个测试用例）
- [x] 修复买入/卖出与账户现金余额不同步：`addAssetRecord()` / `sellAssetRecord()` / `addPosition()` / `sellPosition()` 只写资产记录和流水，不增减对应账户现金（注：当前设计是现金余额由用户手工维护，流水是审计记录不是驱动源；买入时用户需要手动增加账户现金，卖出时需要手动减少账户现金）
- [x] 明确交易流水与现金余额的主从关系：现金余额由用户手工维护，买卖流水是审计记录不会自动影响现金；买入、卖出、分红、费用、入金、出金都应通过用户操作对应账户现金来反映，流水仅用于追溯
- [x] 修复手动编辑价格历史成本口径：`MainViewModel.updateAssetRecord()` 写入 `PriceHistory.cost = existing.averageCost`（单位成本），与 `price`（单价）对应，价格曲线正确显示盈亏
- [x] 为新增股票/ETF/基金资产记录写入初始价格历史：新增买入时自动写入初始 `PriceHistory`，记录买入成本作为基准点
- [x] 明确删除资产记录后的价格历史保留策略：`PriceHistory` 对 `AssetRecord` 是级联删除（删除记录清空价格历史）；交易流水 `Transaction.recordId` 非外键，保留审计历史；两者策略不一致是有意设计：价格历史依赖记录存在才有意义，流水可独立追溯
- [x] 明确删除账户后的审计保留策略：`Transaction` 对 `Account` 是级联删除，删除账户会删除全部交易流水；这与”删除资产记录保留流水审计”的策略一致：资产记录可独立存在但账户是交易流水的来源，删除账户意味着该来源消失
- [x] 补充账户删除级联测试：覆盖账户删除后 `Position`、`AssetRecord`、`PriceHistory`、`Transaction` 的实际删除/保留行为（TransactionAuditTest 已补充）
- [x] 补充买卖影响总资产的回归测试：卖出部分资产后，验证资产记录成本/数量、交易流水、账户现金、总资产四者口径一致（TransactionAuditTest 已补充）
- [x] 修复风险维度详情页与汇总金额不一致：`MainViewModel.calculatePortfolio()` 会把账户现金余额计入 `RiskBucket.CASH`，也会把旧 `Position` 计入 `RiskBucket.AGGRESSIVE`，但 `RiskBucketDetailScreen` 只接收并展示 `AssetRecordSummary`，点进详情后看不到这些被计入汇总的资产来源
- [x] 修复风险维度详情页账户列表筛选遗漏账户现金：`accountsInBucket` 只通过 `assetRecords.any(...)` 判断账户是否属于该风险维度，纯现金余额账户即使已计入现金维度汇总，也不会出现在现金维度详情页
- [x] 修复风险维度详情页记录数量语义：`RiskBucketSummary.recordCount` 可能包含旧 `Position` 和账户现金计数，但详情页”资产记录”数量只统计 `AssetRecord`，会造成首页数量和详情数量不一致
- [x] 首页资产圆环改为真正的三段式配置展示：当前 `RatioRing` 只按 `AGGRESSIVE` 画单一进度环，不是初始需求里的”稳健 / 进攻 / 现金”三段饼图或环图
- [x] 风险维度详情页补齐资产操作入口：当前资产行的日期图标只打开价格历史，不能直接进入该资产的编辑页或交易流水页；按风险类型查询到明细后仍不能完整查看买入/卖出/盈亏历史
- [x] 修复负净资产展示策略：`totalAssets == 0.0` 时显示空态，负资产正常显示组合（风险占比/再平衡语义对负资产不可靠，需用户理解）
- [x] 补充风险维度详情回归测试：覆盖账户现金余额、旧 `Position`、新 `AssetRecord` 同时存在时，首页风险维度金额、记录数、详情页明细三者一致（RiskBucketDetailConsistencyTest 已补充）
- [ ] 修复 Room 迁移验证测试是占位的问题：`AppDatabaseTest.database version is 7` 只是 `assertEquals(7, 7)`，没有使用 `MigrationTestHelper` 从旧版本建库并验证 `3->7` schema 和数据迁移
- [ ] 为 Room migration 增加测试依赖和 schema 导出：当前 `app/build.gradle` 没有 `androidx.room:room-testing`，`AppDatabase.exportSchema=false`，迁移回归无法做严格 schema 校验
- [ ] 修复 CSV 导入没有真实用户入口：`CsvImportRepository` 只通过 `context.assets.open(fileName)` 读取打包 assets，没有 `Uri` / `contentResolver` / 文件选择器入口，用户无法导入本地券商或银行 CSV
- [x] 修复 CSV 账户/持仓/流水导入校验不一致：`importAccounts()`、`importPositions()`、`importTransactions()` 仍把非法数字解析为 `0.0` 或允许空字段，只有 `importAssetRecords()` 做了较完整校验
- [x] 修复 CSV 重复导入只覆盖资产记录的问题：`importAssetRecords()` 有简单重复检测，但账户、旧持仓、交易流水导入仍没有幂等键或去重策略，多次导入会重复写入
- [ ] 修复 CSV 导入不写审计附属数据：`importAssetRecords()` 直接插入 `AssetRecord`，不会像 `MainViewModel.addAssetRecord()` 一样写 BUY 交易流水和初始 `PriceHistory`，导入资产后历史链路不完整
- [ ] 补充真实仓库/数据库级 CSV 测试：当前未看到 `CsvImportRepository` 的测试覆盖，应验证非法行、重复导入、带引号字段、导入后交易流水/价格历史是否符合产品策略
- [ ] 强化测试有效性：`TransactionAuditTest`、`RiskBucketDetailConsistencyTest` 多数是公式和对象构造断言，没有调用真实 DAO/ViewModel/UI 状态；需要增加集成测试覆盖实际代码路径

### 13. 补足测试覆盖
- [x] 覆盖多币种、卖出、汇率失败、缓存回退、资产汇总等关键场景（CurrencyTest, RebalanceTest, TransactionTest）
- [x] 为持仓列表/详情页的币种展示增加测试
- [x] 现有测试共 42 个用例，覆盖：平均成本、盈亏、卖出场景、汇率换算、再平衡、交易流水、Settings模型
- [x] 增加 ViewModel 层测试（MainViewModelTest）
- [x] 增加 Room 集成测试（AppDatabaseTest）
- [x] 增加 Worker 行为测试（WorkerTest）

### 14. 修复构建与仓库基础设施
- [x] 补充 `gradlew` / `gradlew.bat`，保证命令行可直接构建与测试
- [x] 增加 `.gitignore`，避免提交 `build/` 和 IDE 生成物
- [x] 清理仓库中不应纳入版本控制的构建产物
- [x] 修复 `PriceSyncWorker.schedule()` 使用 5 分钟周期任务的问题，调整到 WorkManager 合法周期或改成其他同步策略

### 16. 数据安全与迁移
- [x] 移除 `fallbackToDestructiveMigration()` 或仅限开发环境使用，避免升级时清空用户资产数据
- [x] 为数据库版本升级补充明确 migration 策略和验证

### 15. 改善错误处理与可观测性
- [x] 主界面展示价格刷新失败状态
- [x] 展示价格/汇率最后更新时间
- [x] 对过期缓存、网络失败、无效币种提供明确提示
- [x] 为关键数据流程增加日志
- [x] 为账户删除增加二次确认，并明确提示会级联删除该账户下的持仓
- [x] 修复账户页账户类型使用单行 `Row` 容易在手机上溢出的布局问题

## 已完成项目清单

| 模块 | 完成度 |
|------|--------|
| P0-0 新产品模型 | ✅ |
| P0-1 卖出成本逻辑 | ✅ |
| P0-2 总资产统计 | ✅ |
| P0-3 汇率同步链路 | ✅ |
| P0-4 设置持久化 | ✅ |
| P1-5 多账户管理 | ✅ |
| P1-5A 多记录类型 | ✅ |
| P1-6 编辑删除流程 | ✅ |
| P1-7 币种识别 | ✅ |
| P2-8 资产模型升级 | ✅ |
| P2-9 交易流水 | ✅ |
| P2-10 再平衡功能 | ✅ |
| P2-11 历史分析 | ✅ |
| P2-12 CSV导入 | ✅ |
| P3-13 测试覆盖 | ✅ |
| P3-14 构建基础设施 | ✅ |
| P3-15 错误处理 | ✅ |
| P3-16 数据迁移 | ✅ |

## 备注

当前项目更接近“资产汇总原型”，离“可靠的个人资产管理工具”还有几个核心缺口：
- 新需求已经明确为“账户 + 资产记录项 + 风险维度 + 历史流水”模型，不再只是股票持仓统计
- 账本模型不完整
- 资产统计口径不稳定
- 价格/汇率同步链路不可靠
- 多账户体验还没闭环
- 交互清单与实际实现一度不同步，需持续按代码状态回核
- 再平衡建议还停留在单一阈值提醒
