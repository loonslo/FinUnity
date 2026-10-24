> 2026-10-07 当前部署：baikai.site / halfopen.dev 相同内容分别部署并开放搜索。迁站策略已撤销；详见 [双域名部署与验收](docs/DUAL_DOMAIN_DEPLOYMENT_2026-10-07.md)。旧记录保留原验收日期。

# FinUnity Server

本模块位于 FinUnity monorepo 的 `server/`。从仓库根目录运行时先执行 `cd server`；构建依赖、环境变量和 Docker Compose 均以本目录为服务根目录。

Web 端业务接口只使用 GET 与 POST；普通记录通过 `POST .../{id}:archive` 软归档，账号注销使用 `POST /api/v1/me:delete` 物理删除当前库中的用户数据，不暴露 DELETE/PUT/PATCH。

## Web 运行配置

默认只从环境变量读取配置，不自动读取本地 `.env`。开发可显式使用 `uv run --env-file .env uvicorn finunity_server.main:app --reload`，部署由 Compose `env_file` 注入服务器专用文件。正式账号使用用户名+密码和恢复码，不需要邮件服务。`APP_ENV=production` 会验证密钥、隐私政策地址，以及启用邮件时的 SMTP/验证码配置；生产关闭 Swagger/Redoc/OpenAPI。`SESSION_SECRET`、`PASSWORD_PEPPER` 必须是独立的至少32字符机密。`OCR_ENABLED=false` 为安全默认值，完成政策、配额、费用预警验收后再开启。生产仍按已确认方案使用千问 `qwen3.7-flash`，Key 只在服务端注入。

百度 OCR、OpenAI 兼容识别器和本地 PaddleOCR 可通过 `OCR_PROVIDER` 切换。

本地 PaddleOCR 是后端开关，不改变客户端接口。先安装与服务器硬件匹配的 PaddlePaddle 推理引擎，再安装项目可选依赖：`uv sync --extra paddleocr --extra dev`。然后在 `.env` 中设置 `OCR_PROVIDER=paddleocr`；无独立 API Key，默认 `PADDLEOCR_DEVICE=cpu`。CPU 环境默认关闭 `PADDLEOCR_ENABLE_MKLDNN`，用于规避部分 PaddlePaddle PIR/oneDNN 兼容问题；模型首次识别时自动下载并缓存。

启动：`uv sync --extra dev`，然后运行 `uv run --env-file .env uvicorn finunity_server.main:app --reload`。默认数据库位于 `data/finunity.db`。

FinUnity Android App 的专属 FastAPI 服务。它提供稳定的 `/api/v1` 协议，隔离行情、基金、汇率和 OCR 上游差异；所有业务接口均使用统一成功/错误包络和 Bearer 会话。

## 当前实现

- API-00：匿名客户端会话、Refresh Token 轮换、客户端能力配置
- API-01：证券搜索、批量代码解析、稳定 `instrument_id`
- API-02～05：资产聚合同步、股票/ETF 行情、基金净值与资料、汇率
- API-06：持仓截图结构化解析、图片校验、幂等结果、主动删除
- API-07～10：历史数据、市场指标、公司行动、存活/就绪/能力状态

市场数据目前由 `ReferenceDataProvider` 提供固定的联调样本，响应会标记 `STALE` 并返回 `REFERENCE_DATA` 警告。它不是实时行情，必须持续展示参考数据标识；合法商用行情接入另立项目，不阻塞本次参考账本上线。OCR 仅在内存中处理图片；远程 OCR 返回文字和位置后，或本地 PaddleOCR 直接返回文字和位置后，服务端统一在本地解析持仓字段，原图不会落盘。

## 本地启动

```powershell
uv sync --extra dev
Copy-Item .env.example .env
uv run --env-file .env uvicorn finunity_server.main:app --reload
```

可用入口：

- Swagger：`http://127.0.0.1:8000/docs`
- OpenAPI 3.1：`http://127.0.0.1:8000/openapi.json`
- 存活：`GET /health/live`
- 就绪：`GET /health/ready`

## 用户名、密码与账号生命周期

- `POST /api/v1/auth/register`：`username`、`password`、`password_confirmation`、`accept_privacy=true`、`accept_terms=true`，可选 `display_name`。用户名3–32位，ASCII字母、数字、下划线或短横线，大小写不敏感、系统名称保留。服务端验证两次密码一致、8–128位、不能等于用户名。
- `POST /api/v1/auth/password/login`：用户名、密码与 `client_request_id`；迁移期兼容已有邮箱密码账号，返回 `requires_username`，Web 要求先通过 `POST /api/v1/me/username` 设置用户名。
- 注册成功只在响应中发放一次恢复码，数据库仅保存哈希；Web 确认保存后才进入应用。
- `POST /api/v1/auth/recovery/reset`：用户名、恢复码、新密码两次。恢复码原子校验与轮换，吊销所有旧access/refresh，并发放新恢复码。
- `POST /api/v1/me/password/change`：旧密码、新密码两次；吊销旧会话并为当前浏览器签发替代令牌。
- `POST /api/v1/me/recovery/regenerate`：密码确认后轮换恢复码并吊销旧会话。
- `POST /api/v1/me:delete`：再次验证密码，物理删除当前数据库中的全部用户业务数据（含归档），旧access/refresh失效，用户名可以重新注册。
- `GET /api/v1/me/export`：导出自己的账户、持仓和流水JSON，包含自己的归档记录。

密码使用随机盐和 PASSWORD_PEPPER 的 PBKDF2-HMAC-SHA256（60万轮）。注册、登录、找回和安全操作有持久化IP限速；用户名连续5次失败锁定15分钟，IP失败也设单独上限。429响应带 Retry-After。小时/每日预算采用固定UTC窗口，失败的注册请求也占IP防滥用预算；全站注册达到阈值记录日志告警。

`EMAIL_AUTH_ENABLED=false` 时验证码签发、邮箱注册、验证码登录和邮箱密码重置均为404；保留代码以后再接邮件。开启时邮箱注册路径为 `/api/v1/auth/email/register`，原验证码认证测试继续覆盖此兼容通道。现有验证码发送冷却、异步SMTP等问题尚未扩展，本次不启用邮件。

匿名subject由服务端哈希为 `ins_`，access和refresh都有类型；用户令牌另含凭据版本，校验用户存在且未归档。数据库迁移保留旧账号/业务数据、清空旧refresh，并在迁移前自动一致备份。正式部署仍需先人工备份、轮换 SESSION_SECRET；保留原 PASSWORD_PEPPER 才能验证旧密码，详见 [部署与回滚](deploy/README.md)。

## 鉴权与调用

先创建匿名安装会话：

```powershell
curl.exe -X POST http://127.0.0.1:8000/api/v1/auth/session `
  -H "Content-Type: application/json" `
  -H "X-Request-ID: req-local-auth" `
  -d '{"installation_id":"android-installation-uuid","platform":"ANDROID","app_version":"1.0.0","version_code":1,"device_locale":"zh-CN"}'
```

除创建、刷新会话及健康检查外，接口需携带 `Authorization: Bearer <access_token>`。刷新接口为 `POST /api/v1/auth/session:refresh`，Refresh Token 成功使用后立即失效并返回新 Token。

聚合同步示例：

```powershell
curl.exe -X POST http://127.0.0.1:8000/api/v1/market/sync `
  -H "Authorization: Bearer <access_token>" `
  -H "Content-Type: application/json" `
  -H "X-Request-ID: req-local-sync" `
  -d '{"client_request_id":"sync-1","base_currency":"CNY","instruments":[{"client_ref":"asset-1","instrument_id":"XSHG:600519","asset_type":"STOCK"}],"fx_pairs":[{"client_ref":"usd-cny","base":"USD","quote":"CNY"}]}'
```

OCR 使用 `multipart/form-data`。原图只在请求内存中处理，不落盘；注册用户结构化结果按用户持久化，供审计和归档；Android匿名结果只在至多24小时、有容量上限的内存缓存中保留，不写入用户数据表。用户与匿名安装/IP分别有独立每日预算，注册用户与匿名全局预算互不挤占；失败的上游识别也消耗预算。OCR_DISABLED 返回503，OCR_QUOTA_EXCEEDED返回429：

```powershell
curl.exe -X POST http://127.0.0.1:8000/api/v1/ocr/holdings:parse `
  -H "Authorization: Bearer <access_token>" `
  -F "client_request_id=ocr-uuid-1" `
  -F "parse_mode=HOLDINGS" `
  -F "image=@D:\screenshots\holding.png;type=image/png"
```

## 验证

```powershell
uv run --extra dev pytest -q
uv run --extra dev ruff check .
```

自动化测试还覆盖账本余额计算、同币种转账双边记账、多币种隔离、批量写入原子性、OCR 结果持久化，以及服务重启后的 Refresh Token 轮换与重放拒绝。

## 上线前必须完成

- SESSION_SECRET、PASSWORD_PEPPER 由服务器安全环境文件注入；只有启用邮件通道时才要求 EMAIL_CODE_PEPPER/SMTP。
- 复核所有参考行情提示；真实数据提供器另立项目，接入前取得商用授权。
- 在 TLS 1.2+ 反向代理后部署，配置限流、gzip/br、请求体限制和指标监控。
- 确认 OCR 处理地区、子处理方、保存期限、训练政策及删除验证，并同步到隐私政策。
- 多实例部署前，将当前 SQLite 的 Refresh Token、幂等和 OCR 元数据迁移到共享数据库或缓存，并建立 TTL 清理任务。

## 2026-10-03 halfopen.dev 域名迁移


finunity-api.halfopen.dev 已接入有效 HTTPS，新旧 API readiness 均 200；匿名受保护配置接口 401，docs/redoc/openapi 404。原服务镜像/数据/可信代理边界保持。源码默认隐私 URL 已改新域名，36 项 public_launch 测试通过；实际运行 PRIVACY_POLICY_URL 仍是旧地址并经 301 到新页面，未读取凭据环境文件或重启 Server。 详见 [迁移记录](docs/DOMAIN_MIGRATION_2026-10-03.md)。


## 2026-10-03 当前前端托管

2026-10-03 前端统一 Vercel：主站与四个子站正式 HTTPS 页面均由 Vercel 提供，45 公开页及应用入口、旧域名跳转、API 权限/缓存共 171 项线上检查通过。Cloudflare 看板公开域名与 workers.dev/预览入口均关闭，定时发布改为 Vercel；后端留在原 Ubuntu，未改账号数据或模型权限。 详见 [验收记录](docs/VERCEL_FRONTEND_MIGRATION_2026-10-03.md)。


## 2026-10-21 可选显示名称（本地，未部署）

`POST /api/v1/me/profile` 仅修改当前用户的可选 `display_name`（最多 100 字符，可清空），供 Web 注册后在个人中心设置；账本快照同时新增 `allocation.items[].gap_minor` 统一金额差字段。不改变现有注册与密码协议。相关测试及配套发布要求见 [本地验收](docs/PROFILE_ACCEPTANCE_2026-10-21.md)。


## 2026-10-23 投资纪律初始记录与整改方向

本地新增私有初始状态生成工具，保留来源、历史基线、后续观察与计划草稿，不写真实账号。整改顺序为成交联动、今日任务、可靠提醒；相关功能尚未实现。详见[整改方案](docs/INVESTMENT_DISCIPLINE_PLAN_2026-10-23.md)及[本地验收](docs/INVESTMENT_DISCIPLINE_ACCEPTANCE_2026-10-23.md)。
