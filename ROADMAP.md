# FinUnity 现状分析与路线图

> 目标：帮助用户做家庭财务规划。按"标普四象限"思路划分资产（要花的钱 / 保命的钱 / 生钱的钱 / 保本的钱），
> 一步步引导用户录入真实账户与资产（券商 / 互联网平台 / 银行 / 现金），
> 券商股票/ETF 每日更新一次价格，按月提醒复盘调整。

---

## 一、现状评估（对照 README 与产品目标）

### ✅ 已实现
| 能力 | 说明 | 位置 |
|---|---|---|
| 多账户模型 | BROKER/BANK/FUND/CASH_MANAGEMENT/BOND/INSURANCE/LIABILITY/OTHER | `Account.kt` |
| 账户-资产类型约束 | 不同账户类型可添加不同资产（券商可买股票，银行只能基金/定期等） | `AccountAssetRules.kt` |
| 资产记录模型 | AssetType(STOCK/ETF/FUND/CASH/TIME_DEPOSIT) + RiskBucket | `AssetRecord.kt` |
| 资产结构环形图 | 三段式（进取/稳健/防守），点击进入分类详情 | `MainScreen.kt` |
| 再平衡提醒 | 偏离目标 > 阈值时给出"增加/减少"建议 | `MainScreen.kt`, `RebalanceAlert.kt` |
| 多币种换算 | CNY/USD/HKD，按汇率统一到本位币 | `PortfolioCalculator.kt` |
| 每日资产快照 | SnapshotWorker 每天 9 点记录总资产 | `SnapshotWorker.kt` |
| 交易流水 | 买卖/收支/转账记录 | `TransactionHistoryScreen.kt` |
| 现金自动管理 | 买卖时自动增减 CASH 记录 | `MainViewModel.adjustCashAsset()` |
| CSV 导入 | 账户/持仓/交易导入 | `CsvImportRepository.kt` |

### ⚠️ 部分实现 / 与目标有偏差
| 项 | 现状 | 目标差距 |
|---|---|---|
| 标普四象限 | 只有 3 个 RiskBucket（进取/稳健/防守现金），**缺"保命的钱"（保险）象限** | 标普四象限是 4 个：要花/保命/生钱/保本。需决策是否补齐保险象限 |
| 价格更新频率 | PriceSyncWorker 每 **15 分钟**跑一次 | 目标是**每天一次**。频率过高、易触发限流 |
| 月度复盘 | 仅有 snapshots + monthlyChange 数据，无独立复盘流程/页面 | 缺"按月引导复盘 + 调整建议"的完整体验 |
| 规划页 | 再平衡建议挂在首页卡片里，无独立"规划"Tab | 目标信息架构是 `总览/规划/账户`，规划应独立 |
| 互联网平台资产类型 | CASH_MANAGEMENT 只允许 基金/现金 | 用户要求互联网平台支持 定期+现金+基金，缺定期 |

### ❌ 未实现 / 存在严重缺陷
| 项 | 严重度 | 说明 |
|---|---|---|
| **Yahoo 价格接口失效** | 🔴 P0 致命 | `YahooFinanceApi` 用 `?symbol=AAPL` 查询参数，而 Yahoo 真实接口是路径 `/v8/finance/chart/AAPL`。当前**价格永远拉不到网络数据**，股票/ETF 市值不会更新——直接破坏"券商股票每日更新"核心目标 |
| 新手引导（Onboarding） | 🟠 P1 | 仅有空状态提示"添加账户"，没有"一步步引导录入"的向导流程 |
| 目标配置模板页 | 🟠 P1 | settings 里有 targetAllocation 字段，但无"保守/稳健/平衡/进取"模板选择页 |
| 月度复盘页 | 🟠 P1 | 无 |
| 大额支出模拟 | 🟡 P2 | 无 |
| 数据备份 / 导出 | 🟡 P2 | 仅导入，无导出/备份 |
| 房产/车辆/保险/负债细分资产 | 🟡 P2 | AssetType 仅 5 种 |

---

## 二、路线图（按优先级拆分子任务）

### P0 修复核心数据闭环（先让"每日更新"真正能用）
- [x] **P0-1 修复 Yahoo 价格接口**：symbol 改为路径参数 + 浏览器 User-Agent 头
- [x] **P0-2 价格更新改为每日一次**：PriceSyncWorker 15min → 24h，Price 过期阈值 10min → 12h
- [x] **P0-3 手动刷新入口**：首页"安心检查"卡片加"立即刷新"，带 loading 反馈
- [x] **P0-4 价格拉取健壮性**：A股/港股代码后缀（.SS/.SZ/.HK）录入提示与占位示例
- [x] **P0-附 补 gradlew.bat**：仓库缺失 Windows 构建脚本，已补齐

### P1 标普四象限 + 规划闭环
- [x] **P1-1 四象限模型落地**：新增 INSURANCE（保命的钱）桶，贯通图表/详情/配置/再平衡/导入/配色
- [x] **P1-2 互联网平台资产类型补齐**：CASH_MANAGEMENT 增加 TIME_DEPOSIT
- [x] **P1-3 新手引导向导**：欢迎 → 标普四象限 → 三步开始（OnboardingScreen，首启空状态展示）
- [x] **P1-4 目标配置模板页**：保守/稳健/平衡/进取模板 + 四象限微调 + 预览（TargetAllocationScreen）
- [x] **P1-5 独立规划页**：当前 vs 目标对比条 + 调整建议 + 目标配置/复盘入口（PlanningScreen，首页入口）
- [x] **P1-6 月度复盘流程**：本月变化 + 四象限偏离 + 建议动作（MonthlyReviewScreen，规划页进入）
      · 待补：每月主动提醒（通知）尚未做，列入 P2

### P2 体验增强
- [x] P2-1 大额支出模拟页（ExpenseSimulationScreen）：金额+用途+资金来源瀑布扣减，支出前后总资产与四象限结构对比，温和风险提示，保命的钱不动用；规划页进入
- [ ] P2-2 数据备份/导出
- [ ] P2-3 更多资产类型（房产/车辆/保险/负债细分）
- [ ] P2-4 资产详情价格趋势轻量折线图
- [ ] P2-5 全局视觉统一（浅灰底 #F7F8FA、白卡大圆角、灰度层级，详见 todolist.md）

---

## 三、已确认的关键决策
1. **标普四象限：补齐为完整 4 象限** ✅
   - 新增 `RiskBucket.INSURANCE`（保命的钱 / 保险）
   - 环形图改为四段、目标配置 4 项、再平衡逻辑覆盖 4 桶
   - 保险账户(INSURANCE)下资产默认归入 INSURANCE 桶
   - 四象限语义：要花的钱(CASH/防守) · 保命的钱(INSURANCE/保险) · 生钱的钱(AGGRESSIVE/进取) · 保本的钱(CONSERVATIVE/稳健)
2. **执行顺序：按 ROADMAP P0→P1→P2 连续执行** ✅，每个子任务完成后汇报
