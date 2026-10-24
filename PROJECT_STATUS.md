---
project_id: FinUnity
updated: 2026-10-25
status: active
overview: 多币种家庭资产账本与三桶规划 Android 应用。
progress: >-
  2026-10-24 将 FinUnityPrototype 的目标规划第一阶段并入 FinUnity Android，新增 Compose 目标规划页、BigDecimal 本地测算、Room v27、目标关联实际转账、方案版本、月度复盘及 JSON 备份 v10；暖白橄榄为默认视觉。Windows 工作区 :app:assembleDebug 成功；:app:testDebugUnitTest 154 项通过；:app:compileDebugAndroidTestKotlin 成功。环境没有 adb，未运行设备迁移/备份测试或页面验收。
  2026-10-24 将 FinUnityServer、FinUintyWeb 与 FinUnityPrototype 工作树快照归并为本公开 monorepo 的 server/、web/ 和 planner-prototype-reference/；Server 140 项 pytest 与 Ruff 通过，Web 44 项 Node 测试、lint、build 通过，Server 集成 SPA 冒烟返回 200 并带 CSP。Prototype 标为历史参考，未重跑其测试。来源 Git 历史未导入，旧目录和远程未修改；npm audit 的 Vite/source-map-js 两个高危项未自动升级。本分支未修改线上部署配置。
  2026-10-24 从历史原型归档快照移除 frontend/vite.config.ts，并同步归档 README、TypeScript include 与项目记录；原型外部工作目录和线上部署配置未修改。既有公开 Git 历史仍包含旧文件；本次未运行原型构建或测试。
  2026-10-25 合并原型 Vite 配置清理 PR；将单仓库位置从 FinUnityWorkspace/FinUnity 调整到 FinUnity/，同步各模块 project_id 和工作区规则。旧 sibling 工作副本与包装目录附属资料移入 Windows 回收站；未运行构建/测试或修改线上部署配置。
  Android 环境没有 adb，未运行设备迁移/备份测试或页面验收；Prototype 本机资料未导入。
  前次进展（2026-10-23）：核对本机账本、备份、定期规则及复盘Worker，完成投资纪律Android对接任务：计划执行单、今日页、来源指纹去重、通知及迁移兼容。仅新增方向文档，没有修改Room、业务代码或Worker，未构建APK或进行真机验收。
  前次摘要（维护日期2026-10-15，本轮未复测其历史验收）：2026-10-15 双域 Android API 健康，后端升级至 55cf875；Android 工作区与远程一致，本次没有构建或分发 APK。 历史进展：Android
  新增两域构建参数，旧域生成 URL 核对成功，默认域 Kotlin 编译通过；两域 API 保持在线，未发行新版 APK。
next: 具备 adb/模拟器后运行 Room 迁移、备份导入导出测试并验收 Android 页面。部署迁移时把 Vercel Root Directory 切到 web/ 并单独验收；服务端部署配置需按模块核对。投资纪律方向待对齐服务端成交/现金/持仓契约。前次后续事项：审阅并发布双域构建配置，完成同版本双项目发布自动化；后续观察 Google 重新抓取与收录。
evidence:
- docs/WORKSPACE_RELEASE_2026-10-15.md
- README.md
- ROADMAP.md
- docs/PUBLIC_LAUNCH_2026-09-29.md
- docs/DOMAIN_MIGRATION_2026-10-03.md
- docs/DUAL_DOMAIN_DEPLOYMENT_2026-10-07.md
- docs/dual-domain-http-2026-10-07.json
- docs/INVESTMENT_DISCIPLINE_HANDOFF_2026-10-23.md
- docs/FINANCIAL_PLANNER_INTEGRATION_2026-10-24.md
- app/schemas/com.finunity.data.local.AppDatabase/27.json
- app/src/test/java/com/finunity/data/model/FinancialPlannerEngineTest.kt
- app/src/androidTest/java/com/finunity/data/local/Migration26To27Test.kt
- docs/MONOREPO_MIGRATION_2026-10-24.md
- planner-prototype-reference/docs/MONOREPO_ARCHIVE_2026-10-24.md
---

# FinUnity Android · 项目概览与进度

本页是本项目的概览与进度摘要。更新进度时先核对证据文件及实际验收；任务细节仍以项目原有的 TASKS、ROADMAP 或验收记录为准。只根据有证据的变化修改状态与日期；未验证事项保留“待核实”，不把文件存在或 Git 改动当作完成。


## 2026-10-03 域名迁移


Android 默认 API 与隐私说明源码已更新为新域名；generateDebugBuildConfig 成功，生成 HTTPS API 地址正确，新旧 API readiness 已验收 200。已安装客户端继续使用旧 API 兼容入口；本次未签名/打包/分发、未做真机与完整业务网络验收。 详见 docs/DOMAIN_MIGRATION_2026-10-03.md。


## 2026-10-23 投资纪律方向与本地记录

2026-10-23 核对本机账本、备份、定期规则及复盘Worker，完成投资纪律Android对接任务：计划执行单、今日页、来源指纹去重、通知及迁移兼容。仅新增方向文档，没有修改Room、业务代码或Worker，未构建APK或进行真机验收。 证据：[docs/INVESTMENT_DISCIPLINE_HANDOFF_2026-10-23.md](docs/INVESTMENT_DISCIPLINE_HANDOFF_2026-10-23.md)。
