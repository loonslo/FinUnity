# FinUnity 公网上线与回滚（2026-09-29）

本文件提供服务器、DNS、Cloudflare、费用预算和正式发布操作步骤。初始本机验收不含生产部署；用户后续授权的实际部署另行记录。真实行情接入另立项目；当前界面必须继续标识参考/手工价格。

2026-09-29 已获用户授权完成实际部署。现场沿用 Dify nginx 提供 HTTPS，具体版本、备份、定时器、指标和验证边界见 [生产部署记录](PRODUCTION_2026-09-29.md)。以下 Caddy 方案保留为其他环境的参考，不代表当前生产已安装 Caddy。

## 2026-10-03 域名切换

当前 Web 为 `https://finunity.halfopen.dev`，Android/API 为 `https://finunity-api.halfopen.dev/api/v1/`；生产继续由既有 Dify nginx 提供 TLS。旧 API 主机及旧 Web 的 `/api/*`、`/health/*` 保持兼容，旧公开页面 301 到新域名。当前 `PRIVACY_POLICY_URL` 旧值通过 301 可用，源码默认值已更新；本次未重启后端或读取凭据环境文件。详细镜像、验证及续期边界见 [域名迁移验收](../docs/DOMAIN_MIGRATION_2026-10-03.md)。下文 2026-09-29 初始操作不是再次迁移账号或轮换密钥的指令。

## 部署边界

沿用一台新加坡服务器、一个 uvicorn 进程和 SQLite `data/` 数据卷。Web nginx 只映射 `127.0.0.1:8001`，API 只映射 `127.0.0.1:8000`。宿主机 Caddy 提供 HTTPS：Web 同源转发 `/api/*`、`/health/*`；Android 使用 `https://finunity-api.baikai.site/api/v1/`。Caddy 示例见 `Caddyfile.example`，需要与公共清单中已有站点配置合并，不能覆盖其他项目配置。

若现场盘点发现 80/443 已由其他应用 nginx 占用，沿用公共清单允许的 nginx + certbot 方案，避免中断现有应用。示例见 `nginx-proxy.conf.example`：只新增两个 FinUnity 主机名，保留已有默认站点；代理容器接入 FinUnity 两个 Docker 网络，API 仅信任其实际来源 IP。网络连接须通过额外 Compose 配置持久化，证书续期后执行 nginx 配置检查与 reload。生产现场路径与验收另记，不能直接替换其他应用的配置。

## 0. 先关闭裸端口【本人】

按公共接入清单关闭云防火墙/安全组的 8000、8001，只保留所需 SSH 和 80/443。旧服务含认证缺陷，不能只给旧服务加域名后开放注册。以下顺序先构建、上传，再备份与切换。

## 1. 构建与上传【本人】

为新旧镜像分别记录日期或提交号，例如 `finunity-server:20260929-<commit>`；下文的标记必须替换为实际值。不要覆盖旧镜像标签。

```sh
docker build --build-arg INSTALL_PADDLEOCR=false -t finunity-server:<new-tag> .
docker save finunity-server:<new-tag> -o finunity-server-<new-tag>.tar
```

Web 执行 `npm run build`，上传 `dist/` 至一个新的版本目录（例如 `/srv/finunity/web/releases/<new-tag>/`），保留上一个站点目录。Compose 的 `deploy/site` 指向对应版本目录。只上传必要源码/镜像/产物，排除 `.env*`、密钥、运行数据库和历史 `.deploy-*.tgz`；备份通过独立安全渠道保管。

## 2. 配置、账号盘点与备份【本人】

在服务器准备权限 600 的环境文件，不提交到 Git：`APP_ENV=production`、`EMAIL_AUTH_ENABLED=false`、`EXPOSE_VERIFICATION_CODE=false`、`CORS_ORIGINS=[]`、`PRIVACY_POLICY_URL=https://finunity.baikai.site/privacy`、`OCR_PROVIDER=qwen`、当前中国站端点及对应 Key。`SESSION_SECRET` 与 `PASSWORD_PEPPER` 每个独立设置至少 32 字符的随机值。已有密码使用原有 `PASSWORD_PEPPER`：更换它会使旧密码无法验证，只有明确接受重置测试账号时才更换；`SESSION_SECRET` 本次必须更换。

OCR 初始保持 `OCR_ENABLED=false`。配额默认：用户 20/日、用户全局 200/日；匿名安装 10/日、IP 30/日、匿名全局 100/日。全局值是本次可调整的保守初始配置，不是实测成本结论。阿里云费用中心预算预警和以下上线验收完成后再开启。国际站需另有对应账号/Key，并核对模型部署范围、可用性与三个隐私页面；不能只改域名就宣称推理全在新加坡。

先停止旧 API 以取得一致快照，在旧数据库上执行（仅 Python 标准库）：

```sh
python3 deploy/database.py audit --database data/finunity.db
python3 deploy/database.py backup --database data/finunity.db --output backups/finunity-<old-tag>.db
```

盘点脚本只输出账号数量和完整性，不打印邮箱或凭据。新版本兼容已有邮箱+密码账号，登录后 Web 强制设置唯一用户名并保存恢复码，保留原业务数据。因此无须凭空为其他用户命名。只有无密码的旧邮箱账号时，先离线制定账号恢复安排；生产关闭邮箱通道后不能用验证码登录。

新版本自动重建 `users` 表（保留 ID、原邮箱、密码与子表），新增用户名/恢复码哈希/凭据版本。迁移前额外生成 SQLite 一致备份；旧 refresh 会话无可信类型，迁移时清空。仍需人工更换 `SESSION_SECRET`；不能恢复旧 token。`PASSWORD_PEPPER` 若仍为不合规开发默认值，生产启动会被拒绝，需要先安排账号重置。

## 3. 代理真实 IP 与启动【本人】

`IMAGE_TAG` 指向刚上传的镜像；`SERVER_ENV_FILE` 指向环境文件；`TRUSTED_PROXY_IPS` 必须是容器实际看到的 Caddy 来源地址。不要填 `*`，也不要凭空固定 Docker 网关。先查看该网络的网关地址，再用容器网络抓包确认通过宿主机回环端口进入请求的来源。必要时使用服务器已有抓包工具；捕获包头即可，不捕获请求体。应用仅使用 uvicorn 处理后的 `request.client.host`，不自行信任任意转发头。

```sh
docker load -i finunity-server-<new-tag>.tar
IMAGE_TAG=<new-tag> SERVER_ENV_FILE=<absolute-env-path> TRUSTED_PROXY_IPS=<confirmed-source-ip> docker compose up -d --no-build
curl --fail http://127.0.0.1:8000/health/live
curl --fail http://127.0.0.1:8000/health/ready
```

部署日志不能出现配置拒绝或 SQLite 迁移失败。就绪检查不能代替真实 OCR 测试。要确认真实 IP，从两台不同出口设备发起认证请求，再在维护窗口检查 `usage_counters` 的认证计数是否按两个客户端 IP 区分；不要把这些 IP/数据库输出提交到 Git。自动化测试覆盖受信任与不受信任代理的转发行为，实际 Docker 来源仍须现场验证。

验证迁移后数据库完整性，执行 `revoke-sessions` 确保所有旧 refresh 清空（必须在新用户登录前执行一次）：

```sh
python3 deploy/database.py audit --database data/finunity.db
python3 deploy/database.py revoke-sessions --database data/finunity.db
```

启用 Web nginx 配置；检查 Caddy 示例与服务器安装版本兼容后，验证配置再 reload。DNS 为 `finunity`、`finunity-api` 添加指向服务器的 A 记录，按公共清单使用 DNS only。

## 4. 上线验收与备份期限【本人】

验收清单：HTTPS/HTTP跳转、外网8000/8001不可达、两域 `/docs` `/redoc` `/openapi.json` 为404、`/app/holdings` 刷新正常、注册/恢复码保存/登录/锁定/密码修改/注销/JSON导出、两账号隔离、浏览器无混合内容/CSP错误。公开 `/privacy`、`/terms`、`/android-privacy` 无须登录；联系邮箱为 `chongqing115@126.com`，如使用 Cloudflare 别名转发，由本人核实转发可达。

开启 OCR 前先设置预算预警，确认真实截图、配额和停用开关。图片不落盘；注册用户结果入库，匿名结果仅有至多24小时内存缓存。配额达到80%会记录 `OCR_QUOTA_ALERT`；全站注册达到阈值记录 `REGISTRATION_DAILY_ALERT`。这些是日志告警，外部通知/预算预警由本人配置。失败的上游识别尝试也消耗当日预算，以限制重试费用。

业务数据在账号存续期间保存，归档仍留数据库，注销会移除当前库中的归档与业务数据。沿用公共接入清单的14天备份期限：维护者每天核对 `backups/` 及 `data/*.pre-public-v3-*` 的精确路径、日期后清理超过14天的备份，需保留的发布镜像与无业务数据源码另行保存。建立此维护安排后才能按本政策发布；当前代码不会自动清理任意运维目录，也不会假称注销自动抹掉离线备份。

## 5. 回滚【本人】

优先在未开放注册的维护窗口演练。先停止 API，保留新库的独立一致备份；同时切回配套的旧 Web 站点目录和旧 API 镜像标签。数据库 schema 有变化：旧镜像不应直接使用升级后的库，需恢复迁移前一致备份（先核对完整性和时间），这会丢失备份之后的新写入，必须先评估并保留新库。

旧版本存在认证缺陷，回滚时保持裸端口关闭、禁止公网注册与 API 访问，仅用于维护恢复；不能把旧认证代码作为公开服务回滚方案。下一次切到修复版本再次更换 `SESSION_SECRET`、清空 refresh，要求重新登录。

完成恢复演练后记录镜像标签、备份时间、完整性检查结果、丢失写入范围和恢复用时，不记录机密或用户数据。

## 镜像体积与内存测量（待服务器实测）

本机没有 Docker，因此本次没有虚构镜像体积和内存下降数字。服务器分别构建 `INSTALL_PADDLEOCR=true/false` 镜像，用 `docker image inspect --format '{{.Size}}' <image>` 记录大小；在相同环境、相同空载观察时间用 `docker stats --no-stream` 记录内存。尚未实测前 D3 的构建开关已实现，体积/内存比较仍待验收。
