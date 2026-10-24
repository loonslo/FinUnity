> 2026-10-07 当前部署：baikai.site / halfopen.dev 相同内容分别部署并开放搜索。迁站策略已撤销；详见 [双域名部署与验收](docs/DUAL_DOMAIN_DEPLOYMENT_2026-10-07.md)。旧记录保留原验收日期。

# FinUnity Web

本模块位于 FinUnity monorepo 的 `web/`。从仓库根目录运行前端命令时先执行 `cd web`；Vercel 项目根目录也应指向 `web/`。API 服务源码位于同仓库 `server/`，运行与部署说明见其 README。

FinUnity 的 Web 客户端：用户名与密码注册/登录、一次性显示的恢复码、账本、三桶配置和持仓截图识别。业务数据以 FinUnityServer 为唯一来源。短期访问令牌保存在 sessionStorage，refresh token 保存在 localStorage。注册与找回成功后，必须确认已保存恢复码才进入应用。旧邮箱密码账号登录后先设置用户名；邮箱验证码入口只在 EMAIL_AUTH_ENABLED=true 时显示。

设置页支持旧密码修改密码、密码确认后重新生成恢复码、JSON数据导出，以及密码和二次确认后注销账号。完整隐私政策、用户协议和 Android 政策以静态页面提供，未登录时也可访问 `/privacy`、`/terms`、`/android-privacy`。联系邮箱为 chongqing115@126.com。

界面支持简体中文与英文，可在登录页或应用顶部切换；语言会保存在当前浏览器，登录后读取账号已保存的语言偏好。设置页的“保存偏好”会把当前语言同步到账号。公开政策页也提供英文版本：`/privacy-en.html`、`/terms-en.html`、`/android-privacy-en.html`，并可在页面顶部切回中文。

账本金额由服务端统一计算：期初现金余额加减流水得到现金余额，再与持仓市值合并为账户资产。不同币种不会直接相加；未配置汇率时只展示分币种结果，并明确标出未计入主币种总额的币种。截图原图只在识别请求期间使用，识别出的结构化记录会在服务端留档，用户确认后才写入持仓。


## 2026-09-05 数据一致性修复

Web 各功能使用 `/api/v1/ledger/summary` 的同一份后端快照；市值、盈亏、现金、配置比例和流水分类由 FinUnityServer 计算。需要与本次服务端变更配套运行。每日行情刷新需在服务端配置真实行情适配器，未配置时会显示提示并保留已录入价格。

详细修复、测试边界及未完成接入见 [一致性修复记录](docs/CONSISTENCY_AUDIT_2026-09-05.md)。

## 本地运行

1. 默认无需 `.env`：开发使用 `http://127.0.0.1:8000`，生产使用 `window.location.origin`。`VITE_API_BASE_URL` 仅用于覆盖，生产构建前须清除遗留覆盖值。
2. 在 monorepo 根目录下先启动 `server/` 中的 FinUnityServer，再进入 `web/` 运行 `npm install` 和 `npm run dev`。
3. `npm run build` 执行 TypeScript 检查和 Vite 静态构建；Vercel 仅发布 `dist/`，不生成 Cloudflare Worker。

大模型 API key 只通过服务端环境变量注入，不要添加任何 `VITE_*` 形式的密钥。前端部署配置为 `vercel.json`，Vercel 通过同域 `/api/*` 代理到后端；后端部署见 `../server/deploy/README.md`。跨域覆盖需要单独修改 CSP 和 CORS，不把密钥放进前端。

## 技术栈

React 19、React Router、TanStack Query、Recharts、TypeScript 与 Vite。

<!-- Original Vite template notes -->

This template provides a minimal setup to get React working in Vite with HMR and some Oxlint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the Oxlint configuration

If you are developing a production application, we recommend enabling type-aware lint rules by installing `oxlint-tsgolint` and editing `.oxlintrc.json`:

```json
{
  "$schema": "./node_modules/oxlint/configuration_schema.json",
  "plugins": ["react", "typescript", "oxc"],
  "options": {
    "typeAware": true
  },
  "rules": {
    "react/rules-of-hooks": "error",
    "react/only-export-components": ["warn", { "allowConstantExport": true }]
  }
}
```

See the [Oxlint rules documentation](https://oxc.rs/docs/guide/usage/linter/rules) for the full list of rules and categories.

## 公网上线验证

运行 `npm run build`、`npm run lint`、`npm test`。公开政策随 dist 一起部署；Android 政策改动后在这个工作区运行 `npm run sync:android-privacy`，从 Android 的 PRIVACY_POLICY.md 同步网页，再构建。普通 Web 构建不依赖 Android 仓库。配置密钥、预算、配额，发布政策并完成实机验收后，维护者才开启 OCR_ENABLED。验收状态见 PROJECT_STATUS.md。

## SEO 公开页面

公开页面与索引/错误路由约定见 [docs/SEO.md](docs/SEO.md)。本次第一批验收见 [docs/SEO_PHASE1_ACCEPTANCE.md](docs/SEO_PHASE1_ACCEPTANCE.md)；本地改动未发布。

## SEO 第三批

中英产品、多币种和持仓导入共 6 个静态内容页及原创合成账本图完成。生产构建、lint、17 项 Node 测试与实际 nginx 64 项 GET/HEAD/308 检查通过；根部登录和业务深链保留。

详见 [docs/SEO_PHASE3_ACCEPTANCE.md](docs/SEO_PHASE3_ACCEPTANCE.md)；未发布。

## 2026-10-03 halfopen.dev 域名迁移


finunity.halfopen.dev 生产 HTTPS、12 公开页 canonical 与 sitemap 已切换；从线上原版本复制并仅替换公开 HTML/XML/TXT 域名，全部业务 JS/CSS 字节保持一致。旧公开地址 301，旧 /api/* 和 /health/* 继续代理；根部/注册/持仓壳 noindex 通过。Google 独立地址变更已确认，sitemap 报表 Success，发现 12 页；后续页面收录待 Google。 详见 [迁移记录](docs/DOMAIN_MIGRATION_2026-10-03.md)。


## 2026-10-03 当前前端托管

2026-10-03 前端统一 Vercel：主站与四个子站正式 HTTPS 页面均由 Vercel 提供，45 公开页及应用入口、旧域名跳转、API 权限/缓存共 171 项线上检查通过。Cloudflare 看板公开域名与 workers.dev/预览入口均关闭，定时发布改为 Vercel；后端留在原 Ubuntu，未改账号数据或模型权限。 详见 [验收记录](docs/VERCEL_FRONTEND_MIGRATION_2026-10-03.md)。

## 2026-10-03 访客入口

用户无需先登录即可浏览公开说明与合成示例，在保存自己的资料、上传或导入时再登录。此调整已发布并完成匿名入口复验；实际验收与精确发布材料见 [访客入口验收](docs/GUEST_ENTRY_ACCEPTANCE_2026-10-03.md)。

## 2026-10-14 无账号示例模式

未登录访问 `/app` 会进入浏览器内存里的示例账本（真实工作台，数字虚构，不向服务端发请求）；已登录用户不受影响。设计、验证与重拍首页截图的方法见 [docs/DEMO_MODE_2026-10-14.md](docs/DEMO_MODE_2026-10-14.md)；示例数据在 `src/demo/seed.ts`。未提交、未部署。


## 2026-10-21 产品即首页（本地实现，未部署）

`/` 与 `/en/` 直接展示示例/本人工作台；登录注册与恢复码改为覆盖面板；静态正文迁到 `/about/` 和 `/en/about/`。带入语言、主币种、改过的目标比例；访客本人新增的持仓/流水逐条确认后带入，种子及示例截图数据排除。`/demo` 可供已登录用户回看。显示名称移至个人中心，需配套发布 FinUnityServer 的 `POST /api/v1/me/profile` 及账本统一 `gap_minor` 金额差字段。

验证与边界见 [入口改版验收](docs/ENTRY_ONBOARDING_ACCEPTANCE_2026-10-21.md)。本地构建预览可运行 `node scripts/serve-reviewed-build.mjs 4173`（只模拟静态路由，不代理 API）；截图重拍脚本继续使用 `scripts/capture-landing-screenshots.mjs`。


## 2026-10-23 投资纪律整改对接

新增[Web对接任务](docs/INVESTMENT_DISCIPLINE_HANDOFF_2026-10-23.md)：以服务端计划与成交正本生成今日安排，记录一次成交后统一刷新持仓、现金及额度。本轮仅完成源码核对与方向文档，今日页和成交联动尚未实现；没有把个人记录加入公开示例。
