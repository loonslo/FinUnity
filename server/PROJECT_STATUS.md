---
project_id: FinUnityWorkspace-FinUnity-server
updated: 2026-10-24
status: active
overview: FinUnity Android 与 Web 的业务接口，统一行情、汇率、OCR 和账本数据。
progress: >-
  2026-10-24 纳入公开 FinUnity monorepo 的 server/ 模块；全量 pytest 140 项通过（一个 Starlette/httpx 弃用警告），Ruff 通过；构建 Web 后，Server 集成入口主页与 SPA 路由均返回 200，CSP 响应头存在。Windows 默认 pytest 临时目录权限不足，成功验收使用模块内忽略目录。未部署。
  2026-10-23 完成本地私有初始状态生成工具、来源/观察/计划分离记录和跨端投资纪律整改方案；13项新增合成数据测试、Ruff及实际初始批次核验通过，重复生成无重复写入。未修改现有账本接口、未导入真实账号、未部署。
  前次摘要（维护日期2026-10-21，本轮未复测其历史验收）：2026-10-21 本地新增当前用户显示名称接口与账本金额差字段，支持 Web 注册两字段后设置名称，以及服务端统一分仓差额；相关 29 项 pytest
  与 5 文件 Ruff 检查通过。未提交、未推送、未部署；未跑全部 Python 测试。历史：2026-10-15 后端 55cf875 已部署并完成当时的 126 项测试与 24 项生产检查，本次未复测该历史验收。
next: 按投资纪律方案优先实施基线切换时点、原子成交与现金/持仓/额度联动，再实现今日任务及提醒；部署仍需按单模块边界切换到 monorepo 后另行验收。Vercel Root Directory 待切换到 web/，服务端运行配置尚未调整。保留前次待发布事项及历史运维待办。
evidence:
- docs/PROFILE_ACCEPTANCE_2026-10-21.md
- docs/WORKSPACE_RELEASE_2026-10-15.md
- README.md
- deploy/IMPLEMENTATION_2026-09-29.md
- deploy/README.md
- tests/test_public_launch.py
- deploy/PRODUCTION_2026-09-29.md
- docs/DOMAIN_MIGRATION_2026-10-03.md
- docs/VERCEL_FRONTEND_MIGRATION_2026-10-03.md
- docs/RELIABILITY_2026-10-05.md
- ops/README.md
- docs/DUAL_DOMAIN_DEPLOYMENT_2026-10-07.md
- docs/dual-domain-http-2026-10-07.json
- docs/INVESTMENT_DISCIPLINE_PLAN_2026-10-23.md
- docs/INVESTMENT_DISCIPLINE_ACCEPTANCE_2026-10-23.md
- tools/bootstrap_portfolio.py
- tests/test_portfolio_bootstrap.py
- docs/MONOREPO_INTEGRATION_2026-10-24.md
---

# FinUnity Server · 项目概览与进度

本页是本项目的概览与进度摘要。更新进度时先核对证据文件及实际验收；任务细节仍以项目原有的 TASKS、ROADMAP 或验收记录为准。只根据有证据的变化修改状态与日期；未验证事项保留“待核实”，不把文件存在或 Git 改动当作完成。


## 2026-10-03 域名迁移


finunity-api.halfopen.dev 已接入有效 HTTPS，新旧 API readiness 均 200；匿名受保护配置接口 401，docs/redoc/openapi 404。原服务镜像/数据/可信代理边界保持。源码默认隐私 URL 已改新域名，36 项 public_launch 测试通过；实际运行 PRIVACY_POLICY_URL 仍是旧地址并经 301 到新页面，未读取凭据环境文件或重启 Server。 详见 docs/DOMAIN_MIGRATION_2026-10-03.md。

## 2026-10-05 可靠性改造

本机改动，未提交、未部署。OCR 上传的像素检查改为先读文件头；匿名会话接口新增限频、索引与会话上限；新增 live、ready、version 探针；新增 CI、异地加密备份与恢复演练。本机验证与未验证项见 [验收记录](docs/RELIABILITY_2026-10-05.md)；异地备份操作说明见 [ops/README.md](ops/README.md)。


## 2026-10-21 本地实施

2026-10-21 本地新增当前用户显示名称接口与账本金额差字段，支持 Web 注册两字段后设置名称，以及服务端统一分仓差额；相关 29 项 pytest 与 5 文件 Ruff 检查通过。未提交、未推送、未部署；未跑全部 Python 测试。历史：2026-10-15 后端 55cf875 已部署并完成当时的 126 项测试与 24 项生产检查，本次未复测该历史验收。 验收记录：[docs/PROFILE_ACCEPTANCE_2026-10-21.md](docs/PROFILE_ACCEPTANCE_2026-10-21.md)。


## 2026-10-23 投资纪律方向与本地记录

2026-10-23 完成本地私有初始状态生成工具、来源/观察/计划分离记录和跨端投资纪律整改方案；13项新增合成数据测试、Ruff及实际初始批次核验通过，重复生成无重复写入。未修改现有账本接口、未导入真实账号、未部署。 证据：[docs/INVESTMENT_DISCIPLINE_PLAN_2026-10-23.md](docs/INVESTMENT_DISCIPLINE_PLAN_2026-10-23.md)。
