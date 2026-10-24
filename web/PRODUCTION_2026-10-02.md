# FinUnity Web 生产部署记录（2026-10-02）

## 发布内容

- Web：从代码提交 `fc8c598` 构建；应用代码与 `c9f7af8` 相同，本次最新提交为项目规则和部署状态文档。
- Server：未改动或重启；继续运行 `finunity-server:20260929-8d1836f`。
- Android：未构建或发布正式 APK/AAB。
- 新 Web 静态版本：`/home/ubuntu/finunity-web/releases/20261002-fc8c598`。
- 上一 Web 版本：`/home/ubuntu/finunity-web/releases/20260929-d34c01c`，保留用于回滚。
- 活动挂载：`finunity-web` 容器将新版本只读挂载到 `/usr/share/nginx/html`；CSP/nginx 配置保持原样，端口仍为 `127.0.0.1:8001`。
- 部署配置备份：`/home/ubuntu/finunity-web/public-launch.override.yml.backup-deploy-20261002-fc8c598`。
- 上传归档 SHA-256：`475403bfd9da7777e355f137cefa35f56ff28c5ffd8adde2792f593d63687fce`；本地与服务器校验值一致。

## 验收结果

2026-10-02，Windows 工作区与正式 HTTPS 域名验收：

| 检查 | 结果 |
|---|---|
| `npm run build` | 通过，Vite 生产构建成功 |
| `npm run lint` | 通过，无 lint 错误 |
| `npm test` | 12 项通过，0 失败 |
| `/privacy-en.html` | HTTP 200，正文包含 `FinUnity Web Privacy Policy` |
| `/terms-en.html` | HTTP 200，正文包含 `FinUnity Terms of Use` |
| `/android-privacy-en.html` | HTTP 200，正文包含 `Hengcang Privacy Policy` |
| `/app/holdings` | HTTP 200 |
| API `/health/ready` | HTTP 200 |
| Web 首页 CSP | 返回同源 CSP 响应头 |
| HTTP 首页 | HTTP 308 跳转至 HTTPS |
| 容器挂载与状态 | `finunity-web` 为 Up，挂载新版本目录；仅重建 Web 容器 |

尚未执行登录账号、有密码输入的浏览器端到端流程、语言偏好跨会话同步和真实用户截图导入验收。此记录不代表上述流程已验收。

## 回滚

旧版本静态目录仍保留。回滚时将 `public-launch.override.yml` 恢复为部署配置备份，再以同一组 Compose 文件执行 `docker compose up -d finunity-web`；只会切回旧 Web 静态目录，不需回退 API 容器或数据库。
