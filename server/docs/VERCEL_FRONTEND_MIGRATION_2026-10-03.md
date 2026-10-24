> 历史记录：2026-10-07 已撤销域名迁站，当前以 [双域名部署记录](DUAL_DOMAIN_DEPLOYMENT_2026-10-07.md) 为准；以下原日期及验收不作本次复测。

# 2026-10-03 Web 前端迁移后的 API 接入

衡仓 Web 已由 Vercel `finunity-web` 服务 `https://finunity.halfopen.dev`。后端仍使用原 Ubuntu 上的 FinUnityServer，服务镜像、数据、账号和既有 Android API 未重启或迁移。

Web 的 `/api/*` 和 `/health/*` 由 Vercel 同域代理到受保护的 `finunity-web-api.halfopen.dev`。新代理入口验证托管专用请求头后才接受 Vercel 写入的真实客户端 IP；该请求头不传入应用。实际 Web readiness 200、未登录 `/api/v1/me` 401、API no-store，直接请求代理源站或伪造请求头 403。`finunity-api.halfopen.dev`、旧 API 域名和旧 Web `/health/*` 的兼容检查均 200。

实际证书续期脚本已加入三站代理证书；语法检查通过，原 timer enabled。原证书与精确 nginx 备份保留。没有读取私钥内容、凭据环境文件或真实账号资料。

## 实际验收与边界

2026-10-03，Windows / Python 3.14 / Node 22.22.3：171 项正式 HTTPS 检查通过，包括 45 个公开页面的 GET/HEAD、canonical、robots/sitemap、7 个 noindex 应用壳、未知路由和私有路径 404、旧域名 301 保留路径与查询、同域 API、未登录 401、CSRF 403、源站入口 403 和 API no-store。

角色站 8 项 SEO pytest、Knowledge 42 项隔离 public_tests、看板 37 项 unittest 与 15 项 Node、衡仓 Web build/lint 与 17 项 Node 通过。Knowledge 两项依赖弃用警告保留；首次测试路径被项目门禁拒绝，改用项目指定的新隔离 test-runs 目录后完成。没有读取外部知识资料库、执行真实账号操作、OCR/模型请求、行情刷新、交易或通知。

线上业务 JS/CSS 使用原生产版本的公开文件；本地此前未发布的业务改动保留，没有借托管迁移一起部署。前端路由只允许已声明的页面和 API，静态应用路由只接收 GET/HEAD；没有全路径 SPA 回退或公开本地管理界面。

Vercel 外部 API 代理最长 120 秒，超过此时长的业务需使用后台任务或单独设计直连 API；本轮未实测耗时 OCR/模型操作。官方限制：[Vercel proxied request timeout](https://vercel.com/docs/limits#proxied-request-timeout)。公网完整注册/登录、文件存储与业务验收仍沿用原项目门禁。

本次只迁移托管，公开 URL、canonical 与 sitemap 未新增变更，因此不重复提交 Google 地址变更；此前 Search Console 迁移结果与后续收录状态仍以域名迁移记录为准。
