# FinUnity Web 服务器发布记录（2026-10-07）

## 发布信息

- Web 当前分支提交：`f64eb59059dfac0f2e65fbb4d1206092ed0f202b`；当前工作树无应用代码未提交改动。
- `npm run build`：通过。
- Web 容器静态目录已切换至 `/home/ubuntu/finunity-web/releases/20261007-f64eb59`。
- 原静态目录 `/home/ubuntu/finunity-web/releases/halfopen-20261003/site` 与 nginx 配置 `/home/ubuntu/finunity-web/releases/seo-28bb272/source/deploy/nginx.conf` 保留。
- 部署前 Compose 覆盖文件备份：`/home/ubuntu/finunity-web/public-launch.override.yml.backup-deploy-20261007-f64eb59`。
- 新容器 Web 端口仍为 `127.0.0.1:8001`；Server 容器未重启。
- 上传归档 SHA-256：`ae6dbbabacccce981614354346f96423162856566f0cc127e9a5a133ba60e7ed`。

## 验收结果

2026-10-07 直接从服务器本机访问 Web 上游 `http://127.0.0.1:8001`：首页、`/privacy-en.html`、`/terms-en.html`、`/android-privacy-en.html`、`/app/holdings` 均 HTTP 200；首页响应包含 CSP。服务器本机 API `http://127.0.0.1:8000/health/ready` 返回 HTTP 200。`finunity-web` 容器状态为 Up，静态目录挂载指向本次版本。

公开域名 `https://finunity.baikai.site/privacy-en.html` 当前返回 HTTP 301，跳转到 `https://finunity.halfopen.dev/privacy-en.html`；跳转目标返回 HTTP 200（由 Vercel 响应）。因此本次确认服务器本机 Web 上游已更新，但没有据此声称公开域名正在提供该服务器版本。公开入口现指向 Vercel 的事实以线上响应为准。

本次按要求直接构建和部署，未运行 `npm run lint`、`npm test`、登录浏览器端到端、语言偏好同步或真实截图导入验收。

## 回滚

将 `public-launch.override.yml` 恢复为上述备份，再以 `/home/ubuntu/finunity-web/docker-compose-public-20260929.yml` 与 `/home/ubuntu/finunity-web/public-launch.override.yml` 执行 `docker compose up -d finunity-web`，可恢复先前的静态目录和 nginx 配置。
