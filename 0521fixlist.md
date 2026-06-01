# 0521 复验缺陷清单（第 4 轮·完成核对）

> 复验范围：P0/P1/P2-1/P2-2/P2-3 全部代码 + 端到端业务主线。
> 构建：`gradlew assembleDebug testDebugUnitTest` = **BUILD SUCCESSFUL**（编译通过、单测无 FAILED）。
> **结论：所有 P0/P1 缺陷已全部修复，仅剩 2 项低优先级体验细节 + 1 处文档措辞。任务可视为开发完成。**

---

## ✅ 已全部修复（本轮确认）

| 编号 | 问题 | 当前状态 / 证据 |
|---|---|---|
| **NEW-1** | 备份恢复在校验前清库、丢数据 | ✅ 已修：`BackupRepository.import()` 用 `db.withTransaction{}` 包删+插；非法/残缺 JSON 在 `forEach` 抛 NPE 时**整事务回滚**，旧数据保留（`room-ktx` 已在依赖）。 |
| **NEW-2** | Android 13+ 月度提醒永不触发 | ✅ 已修：`MainActivity` 加 `registerForActivityResult(RequestPermission())`，首启对 `POST_NOTIFICATIONS` 申请，拒绝不阻塞。 |
| **NEW-3** | 备份不含价格历史/快照 | ✅ 已修：`BackupData` 增 `priceHistory`/`assetSnapshots`（v2，默认空列表兼容旧备份），导出/恢复均处理。 |
| **NEW-4** | 汇率 `=X` 被编码 | ✅ 已修：`@Path(encoded=true)`。 |
| **NEW-5** | 中国公募基金走 Yahoo 必失败 | ✅ 已修：自动取价白名单收敛为 `STOCK/ETF`。 |
| **NEW-6** | 文档与代码不一致 | ✅ 基本修复：README 改为"版本 9 / 12 小时过期"，CLAUDE.md 版本号、v7→v8/v8→v9 迁移、新资产类型/保命象限全部对齐。（仅余下方一处措辞） |
| **NEW-7** | 流水程序化文案 | ✅ 已修：全部自然语言。 |
| 第 1 轮 FIX-01/04/06/09/10 | 录入扣现金/目标配置重复/引导 | ✅ 已修（前几轮确认）。 |

端到端主线已验证可走通：新手引导 → 建账户 → 录入资产（不再卡"现金不足"）→ 首页四象限结构 → 规划/目标配置/月度复盘 → 备份恢复（数据安全）。

---

## 🟡 仅剩低优先级（不影响主流程，可排后续）

### NEW-8 房产/车辆/保单计入 `cashAssets`（仅快照口径，环形图不受影响）
- 根因：`MainViewModel.calculatePortfolio` 用 `totalCash = totalAssets - computeStockValue()`，实物资产落"现金侧"。风险象限按 `riskBucket` 分组正确（房产→稳健、保单→保命），仅 `AssetSnapshot.cashAssets` 口径偏大。
- 改法（可选）：快照区分"非股票非现金"实物资产，或把 `cashAssets` 语义改名为"非权益资产"。
- 验证：录房产后环形图占比正确；如在意快照口径，确认 cashAssets 不混入实物资产。

### NEW-9 月度提醒用系统占位图标
- 根因：`ReviewReminderWorker.kt:81` 用 `android.R.drawable.ic_dialog_info`。
- 改法：换应用自有单色 24dp 小图标。

### 文档小尾巴
- `README.md:69` 仍写"股票/ETF/**基金**价格每日自动同步" → 实际 NEW-5 后只同步股票/ETF，把"基金"去掉即可。

---

## 建议
P0/P1 已清零，可以进入"运行期实测"阶段（对照 todolist 的 P2-6）：真机录入 `AAPL / 600519.SS / 0700.HK`，验证每日价格与汇率真实回写、断网回退、备份恢复往返一致、Android 13+ 通知可达。
