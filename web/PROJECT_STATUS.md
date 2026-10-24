---
project_id: FinUnityWorkspace-FinUnity-web
updated: 2026-10-24
status: active
overview: 衡仓（FinUnity）Web：打开即示例的家庭多币种资产账本，注册后原地切换本人数据。
progress: >-
  2026-10-24 纳入公开 FinUnity monorepo 的 web/ 模块；npm test 44 项通过、lint 与生产构建通过。服务端集成入口的主页及 SPA fallback 均返回 200，CSP 头存在。npm audit 报告 vite 与 source-map-js 有 2 个高危项，未自动升级。未部署。
  前次状态所引用的部分本机忽略验收资料未随源码迁入公开仓库；相关历史结论未作为本轮复测证据。
  2026-10-23 核对持仓导入、服务端快照和现有页面入口，完成投资纪律Web对接任务：今日安排、计划预览、成交一次联动、增量对账。仅新增方向文档，未修改业务界面，未重新构建或浏览器验收；保留此前未提交修改。
  前次摘要（维护日期2026-10-21，本轮未复测其历史验收）：2026-10-21 本地实施产品即首页 P0–P2：两入口、覆盖式认证与恢复码、设置和本人行确认带入、导览与分仓条、品牌与截图、独立示例回看。44 项 Node
  测试、类型检查/构建、lint，最终生产构建两组各 12 步浏览器检查及 59 项本地 HTTP 检查通过。个人中心改名及统一金额差字段依赖本次服务端接口。未提交、未推送、未部署，真实账号、真人与生产环境未验收。此前 2026-10-14/15
  的发布结果属于历史记录。
next: 先配合服务端P0成交一致性与计划契约，再实现今日安排及成交/跳过/延后流程；部署切换时将 Vercel Root Directory 调整为 web/ 并重新验收线上路由。npm audit 中两个高危依赖待评估。前次入口改版配套发布与真实账号验收仍未完成。前次后续事项：复核并同批发布 Web 与服务端显示名称接口后，验收线上路由、真实账号与 OCR；完成真机和真人
  5 秒/30 秒测试。P3 统计及隐私政策仍待决定，带入来源关系仍未扩展。
evidence:
- docs/ENTRY_ONBOARDING_PLAN_2026-10-21.md
- docs/WORKSPACE_RELEASE_2026-10-15.md
- README.md
- docs/PUBLIC_LAUNCH_2026-09-29.md
- PRODUCTION_2026-10-02.md
- PRODUCTION_2026-10-07.md
- src/i18n.ts
- src/holdingImport.ts
- public/privacy-en.html
- public/terms-en.html
- public/android-privacy-en.html
- docs/SEO.md
- MONOREPO_INTEGRATION_2026-10-24.md
- docs/SEO_PHASE3_ACCEPTANCE.md
- docs/SEO_RELEASE_CHECKLIST.md
- docs/SEO_RELEASE_CANDIDATE_ACCEPTANCE.md
- docs/SEO_PRODUCTION_ACCEPTANCE_2026-10-03.md
- reports/acceptance/GUEST_ENTRY_PRODUCTION_HTTP_2026-10-03.json
- docs/RELIABILITY_2026-10-05.md
- docs/DUAL_DOMAIN_DEPLOYMENT_2026-10-07.md
- docs/dual-domain-http-2026-10-07.json
- docs/ASSET_CACHING_2026-10-08.md
- docs/DEMO_MODE_2026-10-14.md
- scripts/demo.test.mjs
- scripts/browser-demo.mjs
- docs/RELEASE_ONBOARDING_2026-10-14.md
- scripts/url-language.test.mjs
- docs/RELEASE_CACHE_NAVIGATION_2026-10-08.md
---

# FinUnity Web · 项目概览与进度

本页是本项目的概览与进度摘要。更新进度时先核对证据文件及实际验收；任务细节仍以项目原有的 TASKS、ROADMAP 或验收记录为准。只根据有证据的变化修改状态与日期；未验证事项保留“待核实”，不把文件存在或 Git 改动当作完成。


## 2026-10-03 域名迁移


finunity.halfopen.dev 生产 HTTPS、12 公开页 canonical 与 sitemap 已切换；从线上原版本复制并仅替换公开 HTML/XML/TXT 域名，全部业务 JS/CSS 字节保持一致。旧公开地址 301，旧 /api/* 和 /health/* 继续代理；根部/注册/持仓壳 noindex 通过。Google 独立地址变更已确认，sitemap 报表 Success，发现 12 页；后续页面收录待 Google。 详见 docs/DOMAIN_MIGRATION_2026-10-03.md。


## 2026-10-03 访客入口发布

统一为先浏览公开内容/示例、正式操作再登录。相关本地验证及生产匿名入口复验通过，精确候选已发布；以 docs/GUEST_ENTRY_ACCEPTANCE_2026-10-03.md 为本次证据。

## 2026-10-05 可靠性改造

只新增了 CI 配置，未改业务代码，未提交。本机验证与未验证项见 [验收记录](docs/RELIABILITY_2026-10-05.md)。

## 2026-10-08 静态资源缓存

本机改动，未提交、未部署。`/assets/<名称>-<哈希>.js|css` 等带哈希的构建产物改为 `immutable`，应用壳与未带哈希的文件仍每次校验，兜底 404 不会被缓存成 immutable。验证与未验证项见 [docs/ASSET_CACHING_2026-10-08.md](docs/ASSET_CACHING_2026-10-08.md)；线上响应头未验证。

## 2026-10-08 本次代码发布结果

本批从最新远程分支的独立发布副本提交与推送，两个域名生产部署均返回 READY。代码提交 b961058d37a776f99bd4a0e58d7e5b4898e8e192。按用户指令跳过测试与线上页面/缓存头验收；原工作树及父工作区的其他改动保留。详见 docs/RELEASE_CACHE_NAVIGATION_2026-10-08.md。

## 2026-10-08 本次代码发布结果

本批从最新远程分支的独立发布副本提交与推送，两个域名生产部署均返回 READY。代码提交 b961058d37a776f99bd4a0e58d7e5b4898e8e192。按用户指令跳过测试与线上页面/缓存头验收；原工作树及父工作区的其他改动保留。详见 docs/RELEASE_CACHE_NAVIGATION_2026-10-08.md。

## 2026-10-14 无账号示例模式与首页引导

依据日常库《三站首屏引导与交互调整方案》的衡仓部分，本机完成：未登录访问 `/app` 进入真实工作台的浏览器内存示例（带“示例模式”常驻提示、示例截图导入、重置），静态首页 `/`、`/en/` 重排首屏并把数据去向与首次使用下移，注册后的空账本显示三步清单，首页与指南加“由 半開 制作”页脚。（该批已于 2026-10-14 发布，见下节。）验证（2026-10-14，Windows、Chrome）：`npm test` 41 项（含 `/app?lang=` 语言深链 2 项）、类型检查、lint 通过；真实浏览器 12 步在开发服务器与生产构建预览上各通过一遍，对 `/api` 和非 GET 的请求均为 0；示例账本与服务端 `public_holding`、`ledger_summary` 源码交叉核对一致；首页截图因侧栏文案过期已重拍，英文示例截图的排版已修。未验证：线上部署与响应头、Safari/Firefox/真机、真人 5 秒与 30 秒测试。与方案的差异（现有 Web 端没有账户概念、服务端不折算币种、不做访问计数）和重拍截图的方法见 [docs/DEMO_MODE_2026-10-14.md](docs/DEMO_MODE_2026-10-14.md)。

## 2026-10-14 示例模式与首页改版发布

用户明确要求提交、推送、部署。代码提交 `a5a9fc68eabbc2c87a5931fbd7ac6860a688071f` 已推送到 main，两个域名生产部署均 READY，线上 12 步浏览器验收通过；服务端没有改动。变更说明见 [docs/DEMO_MODE_2026-10-14.md](docs/DEMO_MODE_2026-10-14.md)，发布结果、线上验收与未验证项见 [docs/RELEASE_ONBOARDING_2026-10-14.md](docs/RELEASE_ONBOARDING_2026-10-14.md)。

## 2026-10-21 产品即首页调整方案

用户指出静态介绍页和独立注册页违背“打开即是带示例数据的产品、用完再登录、减少跳转”的要求。线上走查（Windows、Claude 内置浏览器，桌面 1366×860 与手机 375×812，只浏览、未提交表单、未创建账号）后写成方案：首页即示例账本，登录注册改为叠在示例上的面板，注册后原地切换到自己的账本，并把 2026-09-24 任务清单的 U06、U07、U17–U20 提到同批。用户已确认四项决定（中文主名衡仓、唯一定义句、静态首页迁到 `/about/`、P2 只带入访客自己录入的数据）。本次只新增方案文档并更新本摘要，未改业务代码；两份文件已在本地提交，未推送、未部署。详见 [docs/ENTRY_ONBOARDING_PLAN_2026-10-21.md](docs/ENTRY_ONBOARDING_PLAN_2026-10-21.md)。


## 2026-10-21 本地实施

2026-10-21 本地实施产品即首页 P0–P2：两入口、覆盖式认证与恢复码、设置和本人行确认带入、导览与分仓条、品牌与截图、独立示例回看。44 项 Node 测试、类型检查/构建、lint，最终生产构建两组各 12 步浏览器检查及 59 项本地 HTTP 检查通过。个人中心改名及统一金额差字段依赖本次服务端接口。未提交、未推送、未部署，真实账号、真人与生产环境未验收。此前 2026-10-14/15 的发布结果属于历史记录。 验收记录：[docs/ENTRY_ONBOARDING_ACCEPTANCE_2026-10-21.md](docs/ENTRY_ONBOARDING_ACCEPTANCE_2026-10-21.md)。


## 2026-10-23 投资纪律方向与本地记录

2026-10-23 核对持仓导入、服务端快照和现有页面入口，完成投资纪律Web对接任务：今日安排、计划预览、成交一次联动、增量对账。仅新增方向文档，未修改业务界面，未重新构建或浏览器验收；保留此前未提交修改。 证据：[docs/INVESTMENT_DISCIPLINE_HANDOFF_2026-10-23.md](docs/INVESTMENT_DISCIPLINE_HANDOFF_2026-10-23.md)。
