# FinUnity Server API 接口文档

版本：`v1`  
协议版本：OpenAPI 3.1  
Base URL：`https://<专属域名>/api/v1`  
本地联调：`http://127.0.0.1:8000/api/v1`

完整的机器可读契约见项目根目录 [openapi.json](../openapi.json)。启动服务后也可访问 `/docs` 和 `/openapi.json`。

## 1. 通用协议

### 1.1 请求头

| 请求头 | 必填 | 说明 |
| --- | --- | --- |
| `Authorization` | 业务接口必填 | `Bearer <access_token>` |
| `Content-Type` | 是 | JSON 使用 `application/json`；图片使用 `multipart/form-data` |
| `X-Request-ID` | 建议 | 客户端链路 ID；服务端原样返回，缺失时自动生成 |
| `Accept-Language` | 否 | 推荐 `zh-CN` |
| `Idempotency-Key` | 任务类接口建议 | 业务幂等键；聚合同步和 OCR 另有必填客户端请求 ID |

以下接口不需要 Bearer Token：

- `POST /auth/session`
- `POST /auth/session:refresh`
- `GET /health/live`
- `GET /health/ready`

### 1.2 数据格式

- 日期：`YYYY-MM-DD`
- 时间：RFC 3339 UTC，例如 `2026-08-30T11:20:30Z`
- 金额、价格、数量、净值、汇率和比例：十进制字符串
- 未知值：`null`，不使用 `0`、空串、`NaN` 或 `Infinity`
- 币种：ISO 4217 大写三位码
- 市场：优先使用 MIC，例如 `XSHG`、`XSHE`、`XHKG`、`XNAS`、`XNYS`

### 1.3 成功包络

```json
{
  "request_id": "req_01J6ABCD",
  "data": {},
  "warnings": [],
  "partial": false,
  "server_time": "2026-08-30T11:20:30Z"
}
```

批量接口部分失败时仍返回 HTTP 200，并设置 `partial=true`。失败项目包含自己的 `status` 和 `error`，成功项目不会被丢弃。

### 1.4 错误包络

```json
{
  "request_id": "req_01J6ABCD",
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "请求字段或格式错误",
    "retryable": false,
    "retry_after_seconds": null,
    "details": []
  },
  "server_time": "2026-08-30T11:20:30Z"
}
```

详细错误码及 Android 处理建议见 [error-codes.md](error-codes.md)。

## 2. 接口总览

| 能力 | 方法 | 路径 |
| --- | --- | --- |
| 创建会话 | POST | `/auth/session` |
| 刷新会话 | POST | `/auth/session:refresh` |
| 账户列表/保存/归档 | GET/POST | `/accounts`、`/accounts/{id}:archive` |
| 持仓列表/批量保存/归档 | GET/POST | `/holdings`、`/holdings:batch-upsert`、`/holdings/{id}:archive` |
| 流水列表/批量保存/归档 | GET/POST | `/transactions`、`/transactions:batch-upsert`、`/transactions/{id}:archive` |
| 账本汇总 | GET | `/ledger/summary?month=YYYY-MM` |
| 客户端配置 | GET | `/app/config` |
| 证券搜索 | GET | `/instruments/search` |
| 证券批量解析 | POST | `/instruments/resolve` |
| 资产聚合同步 | POST | `/market/sync` |
| 批量行情 | POST | `/market/quotes/batch` |
| 基金净值 | POST | `/funds/nav/batch` |
| 基金详情 | GET | `/funds/{fund_code}` |
| 批量汇率 | POST | `/fx/rates/batch` |
| 持仓截图解析 | POST | `/ocr/holdings:parse` |
| OCR 识别记录 | GET | `/ocr/documents` |
| 归档 OCR 文档 | POST | `/ocr/documents/{document_id}:archive` |
| 历史行情/净值 | GET | `/market/history` |
| 批量市场指标 | POST | `/market/metrics/batch` |
| 公司行动 | GET | `/corporate-actions` |
| 数据能力状态 | GET | `/status` |
| 存活检查 | GET | `/health/live`（不带 `/api/v1`） |
| 就绪检查 | GET | `/health/ready`（不带 `/api/v1`） |

账户的 `opening_balance` 是建账时点的现金余额。账本汇总按账户币种分别计算现金、持仓成本、市值和浮动盈亏；只有主币种进入 `base`，其他币种列在 `excluded_currencies`，绝不按 1:1 混算。`TRANSFER` 必须提供 `to_account_id`，且当前仅允许同币种账户间转账。持仓和流水的批量保存为原子操作：任一条冲突时整批回滚。

## 3. 客户端会话

### 3.1 创建匿名会话

`POST /auth/session`

请求：

```json
{
  "installation_id": "android-installation-uuid",
  "platform": "ANDROID",
  "app_version": "1.0.0",
  "version_code": 1,
  "device_locale": "zh-CN",
  "integrity_token": null
}
```

`installation_id` 只标识当前安装实例，不是用户身份。

响应 `data`：

```json
{
  "access_token": "short-lived-token",
  "token_type": "Bearer",
  "expires_in_seconds": 3600,
  "refresh_token": "rotating-refresh-token",
  "refresh_expires_in_seconds": 2592000
}
```

### 3.2 刷新会话

`POST /auth/session:refresh`

```json
{"refresh_token":"rotating-refresh-token"}
```

成功响应字段与创建会话一致。Refresh Token 采用轮换机制：成功使用后旧 Token 立即失效，再次使用返回 `REFRESH_TOKEN_INVALID`。

### 3.3 客户端配置

`GET /app/config?platform=ANDROID&version_code=1`

响应包含：

- `minimum_supported_version_code`
- `latest_version_code`
- `maintenance`
- `features`
- `limits`
- `privacy_policy_url`

## 4. 证券主数据

### 4.1 搜索证券

`GET /instruments/search`

查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `q` | 是 | 名称、简称或代码 |
| `types` | 否 | 逗号分隔：`STOCK,ETF,FUND` |
| `markets` | 否 | 逗号分隔的 MIC |
| `limit` | 否 | 1～100，默认 20 |
| `cursor` | 否 | 分页游标 |

响应 `data`：

```json
{
  "items": [
    {
      "instrument_id": "XSHG:600519",
      "symbol": "600519",
      "canonical_symbol": "600519.SS",
      "name": "贵州茅台",
      "short_name": "贵州茅台",
      "type": "STOCK",
      "market": "XSHG",
      "currency": "CNY",
      "status": "ACTIVE",
      "aliases": ["600519.SH", "600519.SS"],
      "fund_code": null
    }
  ],
  "next_cursor": null
}
```

### 4.2 批量解析代码

`POST /instruments/resolve`

单批最多 100 项。

```json
{
  "queries": [
    {
      "client_ref": "asset-1",
      "text": "600519",
      "type_hint": "STOCK",
      "market_hint": "XSHG"
    },
    {
      "client_ref": "asset-2",
      "text": "00700.HK"
    }
  ]
}
```

状态：`RESOLVED / AMBIGUOUS / NOT_FOUND / INVALID`。歧义结果会返回 `candidates`，不会静默猜测。

## 5. 市场数据

### 5.1 聚合资产同步

`POST /market/sync`

单批最多 100 个资产、20 个货币对。相同 `client_request_id` 和相同请求内容返回相同业务结果；相同 ID 但请求内容不同返回 HTTP 409 `IDEMPOTENCY_CONFLICT`。

```json
{
  "client_request_id": "sync-android-uuid",
  "base_currency": "CNY",
  "instruments": [
    {
      "client_ref": "record-1",
      "instrument_id": "XSHG:600519",
      "input_code": "600519.SS",
      "asset_type": "STOCK"
    },
    {
      "client_ref": "record-2",
      "instrument_id": "CNFUND:000001",
      "input_code": "000001",
      "asset_type": "FUND"
    }
  ],
  "fx_pairs": [
    {"client_ref": "fx-usd-cny", "base": "USD", "quote": "CNY"}
  ],
  "include": ["LATEST_VALUE", "PREVIOUS_CLOSE", "BASIC_METADATA"],
  "allow_delayed": true
}
```

资产单项响应：

```json
{
  "client_ref": "record-1",
  "status": "OK",
  "instrument": {
    "instrument_id": "XSHG:600519",
    "canonical_symbol": "600519.SS",
    "name": "贵州茅台",
    "type": "STOCK",
    "market": "XSHG",
    "currency": "CNY"
  },
  "value": {
    "value_type": "MARKET_PRICE",
    "current": "1418.01",
    "previous_close": "1402.50",
    "value_date": "2026-08-28",
    "source_time": "2026-08-28T20:00:00Z",
    "received_at": "2026-08-28T20:00:00Z",
    "quality": "STALE",
    "delay_seconds": null,
    "source": "finunity-reference-data"
  },
  "error": null
}
```

### 5.2 批量股票/ETF 行情

`POST /market/quotes/batch`

```json
{
  "instrument_ids": ["XNAS:AAPL", "XSHG:600519"],
  "fields": ["LAST", "PREVIOUS_CLOSE", "OHLC", "VOLUME"]
}
```

每项返回 `last/previous_close/open/high/low/volume/currency/market_status/source_time/quality/source`。

`market_status`：`PRE_OPEN / OPEN / LUNCH_BREAK / CLOSED / SUSPENDED / UNKNOWN`。  
`quality`：`REALTIME / DELAYED / EOD / STALE / FALLBACK`。

### 5.3 历史行情或净值

`GET /market/history?instrument_id=XNAS:AAPL&start_date=2026-01-01&end_date=2026-08-30&interval=1d&adjustment=TOTAL_RETURN`

`adjustment`：`NONE / SPLIT_ADJUSTED / TOTAL_RETURN`。响应回显复权方式并返回 `items` 和 `next_cursor`。

### 5.4 批量市场指标

`POST /market/metrics/batch`

```json
{"instrument_ids":["XSHE:159919"]}
```

返回 `pe_ttm/pb/dividend_yield/market_price/iopv_or_nav/premium_rate/purchase_status/purchase_limit/source/quality`。未知或不适用的指标为 `null`。

### 5.5 公司行动

`GET /corporate-actions?instrument_id=XNAS:AAPL&since=2025-01-01`

返回事实事件列表和分页游标。事件类型包括分红、拆股、配股、代码变更和退市等；服务端不会修改客户端持仓。

## 6. 基金

### 6.1 批量基金净值

`POST /funds/nav/batch`

```json
{
  "funds": [
    {
      "client_ref": "fund-1",
      "instrument_id": "CNFUND:000001",
      "fund_code": "000001"
    }
  ],
  "as_of_date": "2026-08-28",
  "include_estimate": true
}
```

每项返回 `unit_nav/accumulated_nav/previous_unit_nav/nav_date/value_type/source_time/received_at/source`。正式净值不会被同一净值日的估值覆盖。

### 6.2 基金详情

`GET /funds/{fund_code}`

返回基金主数据、类型、QDII/场内属性、申购状态、限购信息、建议分类、来源及数据日期。

## 7. 汇率

### 7.1 批量汇率

`POST /fx/rates/batch`

```json
{
  "pairs": [
    {"client_ref": "usd-cny", "base": "USD", "quote": "CNY"},
    {"client_ref": "hkd-cny", "base": "HKD", "quote": "CNY"}
  ],
  "rate_type": "MID"
}
```

`rate_type` 支持 `MID / CLOSE`。同币种换算应由客户端本地使用 `1`；服务端拒绝同币种请求。找不到汇率时返回单项失败和 `rate=null`，绝不按 1:1 回退。

## 8. OCR 持仓解析

### 8.1 解析持仓截图

`POST /ocr/holdings:parse`  
`Content-Type: multipart/form-data`

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `image` | 是 | JPEG、PNG 或 WebP；最大 10 MB、20 MP，最小 480×480 |
| `client_request_id` | 是 | 客户端请求 ID |
| `locale` | 否 | 默认 `zh-CN` |
| `default_currency` | 否 | ISO 4217 币种提示，不覆盖图片事实 |
| `broker_hint` | 否 | 券商或基金 App 提示 |
| `parse_mode` | 是 | 当前固定为 `HOLDINGS` |

相同 `client_request_id + image_sha256` 重复提交返回同一解析结果。

响应 `data`：

```json
{
  "document_id": "doc_ephemeral_01",
  "document_type": "HOLDINGS",
  "image_sha256": "sha256-hex",
  "broker": {"name": "某券商", "confidence": 0.8},
  "as_of_date": "2026-08-28",
  "rows": [
    {
      "row_id": "row-1",
      "raw_text": null,
      "name": "贵州茅台",
      "security_code": {
        "raw": "600519",
        "normalized": "600519.SS",
        "instrument_id": "XSHG:600519",
        "market": "XSHG",
        "status": "RESOLVED",
        "confidence": 0.99,
        "candidates": [],
        "needs_confirmation": false
      },
      "asset_type": "STOCK",
      "quantity": "100",
      "current_price": "1418.01",
      "market_value": "141801.00",
      "total_cost": null,
      "currency": "CNY",
      "confidence": 0.96,
      "field_confidence": {},
      "review_reasons": ["截图未明确显示持仓成本"],
      "bounding_box": null
    }
  ],
  "unparsed_blocks": [],
  "warnings": []
}
```

成本缺失时 `total_cost=null`。市值和数量乘价格偏差超过 1% 时加入 `VALUE_MISMATCH`。证券代码无法唯一解析时要求客户端确认。

### 8.2 删除 OCR 文档

`POST /ocr/documents/{document_id}:archive`

```json
{"deleted":true}
```

删除关联的解析缓存和可识别派生数据。当前实现不将原始图片写入本地磁盘。

## 9. 服务状态

### 9.1 数据能力状态

`GET /status`

返回整体状态、`stock_quotes/fund_nav/fx/ocr/history` 能力状态和当前事件，不暴露内部 URL、密钥或数据库信息。

### 9.2 存活检查

`GET /health/live`

只表示进程存活，不访问上游。

### 9.3 就绪检查

`GET /health/ready`

检查服务是否可以接收请求。生产接入数据库和缓存后，应在此加入关键依赖检查。

## 10. HTTP 状态码

| HTTP | 含义 |
| ---: | --- |
| 200 | 成功；批量部分失败也返回 200 |
| 400 | 请求字段或格式错误 |
| 401 | Token 缺失、无效或过期 |
| 403 | 客户端无权限或完整性失败 |
| 404 | 单资源不存在 |
| 409 | 幂等键或资源状态冲突 |
| 413 | 图片或批量请求过大 |
| 415 | 不支持的媒体格式 |
| 422 | 图片有效但无法解析持仓，或业务校验失败 |
| 429 | 限流；客户端遵守 `Retry-After` |
| 500/502/503/504 | 服务或上游错误，按 `retryable` 决定是否重试 |

批量单项状态：`OK / NOT_FOUND / INVALID / AMBIGUOUS / STALE / RATE_LIMITED / PROVIDER_ERROR`。

## 11. 当前联调数据说明

当前 `ReferenceDataProvider` 是可复现的 Mock/联调提供器，包含 `600519.SS`、`159919.SZ`、`00700.HK`、`AAPL`、`BRK.B`、基金样本以及 `USD/CNY`、`HKD/CNY`。返回数据标记为 `STALE`，聚合同步响应带有 `REFERENCE_DATA` 警告。

这些数值不是实时行情，不得用于生产交易决策。正式部署前必须替换为具有商用许可的数据源，并将 Refresh Token、幂等结果及 OCR 元数据从进程内存迁移到带 TTL 的持久化存储。
# Web 账本快照与每日价格更新（2026-09-05）

`GET /api/v1/ledger/summary?month=YYYY-MM` 为 Web 统一读取入口，Bearer 用户鉴权。月份只筛选流水统计，资产是当前值。响应 `data` 保留原有 base/by_currency/accounts，并增加：

- records：同一 SQLite 读事务取得的 accounts、holdings、transactions。
- holdings 的 market_value_minor、cost_minor、unrealized_profit_minor、valuation_status；盈亏在缺成本时为 null，缺价格时以成本估算。
- goals：目标比例和版本。
- allocation：含现金的主币种配置，各项 value、share_pct、target_pct、deviation_pct；负现金或零资产时 valid=false，比例/评分为 null。
- categories：主币种所选月份的流水分类金额。
- generated_at 与 price_refresh：快照生成时间及最近行情任务状态。

`POST /api/v1/holdings:refresh` 无请求体，仅刷新已登录用户的持仓。返回 status（unconfigured/pending/success/partial/failed）、updated_count、unavailable_count、attempted_at、run_date。未配置不会写入参考行情。前端完成后重新取得账本快照。版本随价格写入递增，旧版本编辑继续返回 409。

后台在北京时间 `HOLDING_PRICES_HOUR`（默认 18）之后按小时检查；当天收盘后成功即不再重复，失败下次重试。服务必须常驻。配置 `HOLDING_PRICES_URL` 为真实行情适配器，`HOLDING_PRICES_TOKEN` 可选，只存服务端。适配器接收 POST JSON：

```json
{"instruments":[{"instrument_id":"XSHG:510300","currency":"CNY"}]}
```

返回格式（时间仅为格式示例，生产需返回真实价格时间）：

```json
{"items":[{"instrument_id":"XSHG:510300","currency":"CNY","price":"4.015","as_of":"2026-09-04T07:00:00Z","source":"provider-name"}]}
```

报价须有带时区的时间、有限非负价格、匹配的证券和币种；未来和七日前报价不落库。若已有价格日期较新，保留已有价格。上游失败不清空持仓，任务状态单独持久化。本次只提供适配器契约，尚未选定/接通真实供应商；原有 ReferenceDataProvider 不用于持仓刷新。
