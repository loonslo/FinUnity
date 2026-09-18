# FinUnity 专属服务项目：接口需求清单

> 审计日期：2026-08-30
> 使用方：FinUnity Android App（衡仓）
> 提供方：独立的 FinUnity 专属服务项目
> 本文件只描述专属服务需要提供的接口，以及 Android 替换旧接口所需的联调任务；不保留已经完成的 App 功能任务。

## 0. 强制架构约定

- [ ] Android App 的所有远端数据只请求 FinUnity 专属服务域名，例如 `https://api.finunity.example.com/api/v1`。
- [ ] Android 不再直接请求 Yahoo Finance，也不直接集成任何基金、汇率、行情或云 OCR 供应商接口。
- [ ] Yahoo 或其他数据商只能作为专属服务的上游；上游 URL、密钥、鉴权方式和原始响应不得出现在 APK 中。
- [ ] 专属服务负责统一股票、ETF、基金、汇率、历史数据、指标数据和图片解析的供应商差异。
- [ ] 专属服务返回 FinUnity 自己定义的稳定 JSON；更换上游数据商时 Android 不需要升级。
- [ ] 当前本地 Room 数据仍是 App 的业务主存储；专属服务首期只提供数据和解析能力，不直接修改用户本地资产。
- [ ] 所有价格、净值、汇率和 OCR 结果都必须由用户可追溯到数据时间、数据质量和服务端请求 ID。

## 1. 服务项目首期交付范围

| ID | 优先级 | 专属服务必须提供的能力 | 当前 App 缺口 |
|---|---|---|---|
| API-00 | P0 | 客户端鉴权、版本和服务能力 | 当前没有自有服务鉴权 |
| API-01 | P0 | 证券/基金搜索与代码解析 | 当前只靠本地正则猜代码和市场 |
| API-02 | P0 | 一次性资产数据同步接口 | 当前 App 逐个请求 Yahoo 股票和汇率 |
| API-03 | P0 | 股票、ETF 最新价与昨收 | 当前使用 Yahoo 非正式接口 |
| API-04 | P0 | 公募基金净值 | 当前 `FUND` 完全手工维护，不自动同步 |
| API-05 | P0 | 外汇汇率 | 当前只请求 Yahoo `XXXYYY=X` |
| API-06 | P0 | 持仓截图解析 | 当前只有本地 ML Kit + 单行正则，没有专属服务接口 |
| API-07 | P1 | 历史价格和历史净值 | 当前只能从 App 开始使用后逐日积累 |
| API-08 | P1 | 基金分类、限购和市场指标 | 当前全部手工填写 |
| API-09 | P1 | 公司行动 | 当前无法自动识别拆股、分红、代码变更 |
| API-10 | P1 | 服务状态和数据源健康 | 当前无法判断专属服务或上游是否异常 |

首期 P0 完成后，Android 应能完全删除对 `query1.finance.yahoo.com` 的直接访问，并能通过专属服务完成股票/ETF/基金/汇率同步和图片解析。

## 2. 统一协议

### 2.1 HTTP 与鉴权

- Base URL：`https://<专属域名>/api/v1`
- 传输：只允许 TLS 1.2 及以上。
- JSON：`Content-Type: application/json; charset=utf-8`。
- 图片：`multipart/form-data`。
- 鉴权：`Authorization: Bearer <access_token>`。
- 链路追踪：Android 每次传 `X-Request-ID`；服务端原样返回或生成新的 `request_id`。
- 幂等：写入/任务类接口支持 `Idempotency-Key`。
- 压缩：支持 gzip/br，Android 至少使用 gzip。
- 语言：`Accept-Language: zh-CN`，错误码保持英文稳定枚举，错误文案可中文化。

### 2.2 基础数据格式

- 日期：`YYYY-MM-DD`。
- 时间：RFC 3339 UTC，例如 `2026-08-30T11:20:30Z`。
- 金额、价格、数量、净值、汇率、比例：JSON 十进制字符串，例如 `"1418.01"`。
- 未知数值：返回 `null`；禁止用 `0`、空串、`NaN` 或 `Infinity` 表示未知。
- 币种：ISO 4217 大写三位码，例如 `CNY`、`USD`、`HKD`。
- 市场：优先使用 ISO 10383 MIC，例如 `XSHG`、`XSHE`、`XHKG`、`XNAS`、`XNYS`。
- 服务端 ID：不透明字符串；客户端关联字段统一叫 `client_ref`，服务端必须原样返回。
- 枚举：大写蛇形命名；新增值必须向后兼容。

### 2.3 成功响应包络

```json
{
  "request_id": "req_01J6ABCD",
  "data": {},
  "warnings": [],
  "partial": false,
  "server_time": "2026-08-30T11:20:30Z"
}
```

### 2.4 错误响应包络

```json
{
  "request_id": "req_01J6ABCD",
  "error": {
    "code": "RATE_LIMITED",
    "message": "请求过于频繁",
    "retryable": true,
    "retry_after_seconds": 60,
    "details": []
  },
  "server_time": "2026-08-30T11:20:30Z"
}
```

HTTP 状态约定：

| HTTP | 含义 |
|---|---|
| 200 | 成功；批量部分失败也返回 200，并设置 `partial=true` |
| 400 | 请求字段/格式错误 |
| 401 | Token 缺失、无效或过期 |
| 403 | 客户端无权限、完整性校验失败 |
| 404 | 单资源不存在 |
| 409 | 幂等键或资源状态冲突 |
| 413 | 图片或批量请求过大 |
| 415 | 不支持的图片/媒体格式 |
| 422 | 图片可读取但无法解析为持仓，或业务字段校验失败 |
| 429 | 限流；必须返回 `Retry-After` |
| 500/502/503/504 | 专属服务或上游错误，可按 `retryable` 决定重试 |

批量接口的单项状态：`OK / NOT_FOUND / INVALID / AMBIGUOUS / STALE / RATE_LIMITED / PROVIDER_ERROR`。整批不能因为一个项目失败而丢弃其他成功数据。

## 3. API-00 `[ ]`：客户端鉴权和能力配置

### 3.1 获取匿名客户端会话

`POST /api/v1/auth/session`

请求：

```json
{
  "installation_id": "android-installation-uuid",
  "platform": "ANDROID",
  "app_version": "1.0.0",
  "version_code": 1,
  "device_locale": "zh-CN",
  "integrity_token": "optional-play-integrity-token"
}
```

响应：

```json
{
  "request_id": "req_auth_01",
  "data": {
    "access_token": "short-lived-token",
    "token_type": "Bearer",
    "expires_in_seconds": 3600,
    "refresh_token": "rotating-refresh-token",
    "refresh_expires_in_seconds": 2592000
  },
  "warnings": [],
  "partial": false,
  "server_time": "2026-08-30T11:20:30Z"
}
```

要求：

- [ ] APK 内不得内置可调用上游供应商的长期密钥。
- [ ] `installation_id` 不是用户身份，不得用于跨应用追踪。
- [ ] Access Token 短期有效，Refresh Token 必须轮换并可吊销。
- [ ] 如果不采用匿名会话，服务项目必须提供等价安全方案；不能用一个永不过期的静态 API Key。

### 3.2 刷新会话

`POST /api/v1/auth/session:refresh`

请求：`{"refresh_token":"rotating-refresh-token"}`；响应字段与 3.1 相同。

### 3.3 客户端配置

`GET /api/v1/app/config?platform=ANDROID&version_code=1`

至少返回：

```json
{
  "request_id": "req_config_01",
  "data": {
    "minimum_supported_version_code": 1,
    "latest_version_code": 1,
    "maintenance": false,
    "features": {
      "market_sync": true,
      "fund_nav": true,
      "remote_ocr": true,
      "market_history": true,
      "market_metrics": false
    },
    "limits": {
      "market_sync_instruments": 100,
      "ocr_image_bytes": 10485760,
      "ocr_image_megapixels": 20
    },
    "privacy_policy_url": "https://www.example.com/privacy"
  }
}
```

## 4. API-01 `[ ]`：证券主数据、搜索和解析

专属服务必须建立稳定的 `instrument_id`，不能让 Android 把 Yahoo 代码当作业务主键。

### 4.1 统一证券对象

```json
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
```

`type`：`STOCK / ETF / FUND / LOF / REIT / BOND / FX`。
`status`：`ACTIVE / SUSPENDED / DELISTED / CLOSED / UNKNOWN`。

### 4.2 搜索

`GET /api/v1/instruments/search?q={名称或代码}&types=STOCK,ETF,FUND&markets=XSHG,XSHE,XHKG,XNAS,XNYS&limit=20&cursor={可选}`

响应：`items: Instrument[]`、`next_cursor`。

搜索要求：

- [ ] 支持中文名称、简称、拼音首字母、原始代码和带后缀代码。
- [ ] 支持 A 股、港股、美股、场内 ETF、LOF、境内公募基金和 QDII。
- [ ] 相同六位代码必须结合 `type/market` 区分股票和公募基金。
- [ ] 美股代码允许点号和连字符，不能用“1～5 个字母”的本地限制。

### 4.3 批量解析

`POST /api/v1/instruments/resolve`

请求：

```json
{
  "queries": [
    {"client_ref": "asset-1", "text": "600519", "type_hint": "STOCK", "market_hint": "XSHG"},
    {"client_ref": "asset-2", "text": "00700.HK"},
    {"client_ref": "asset-3", "text": "000001", "type_hint": "FUND"}
  ]
}
```

响应单项：

```json
{
  "client_ref": "asset-1",
  "status": "RESOLVED",
  "instrument": {
    "instrument_id": "XSHG:600519",
    "symbol": "600519",
    "canonical_symbol": "600519.SS",
    "name": "贵州茅台",
    "type": "STOCK",
    "market": "XSHG",
    "currency": "CNY",
    "status": "ACTIVE"
  },
  "confidence": 0.99,
  "candidates": [],
  "review_reasons": []
}
```

解析状态：`RESOLVED / AMBIGUOUS / NOT_FOUND / INVALID`。`AMBIGUOUS` 必须返回候选项，专属服务不能静默猜交易所或资产类型。

### 4.4 代码兼容规则

- 上海：接受 `.SH/.SS`，统一 `market=XSHG`，兼容当前 App 输出 `600519.SS`。
- 深圳：接受 `.SZ`，统一 `market=XSHE`，输出 `159919.SZ`。
- 港股：业务主代码保留五位，例如 `00700`；供应商适配代码单独维护，不得丢失前导零。
- 美股：大写原始 ticker，例如 `AAPL`、`BRK.B`。
- 公募基金：保存六位 `fund_code` 和独立 `instrument_id`，不得仅靠数字形态推断为 A 股。

## 5. API-02 `[ ]`：APP 一次性资产数据同步

这是 Android 后台 Worker 和手动刷新优先调用的聚合接口。服务端内部可以并行调用行情、基金和汇率数据源，但对 Android 只返回统一结果。

`POST /api/v1/market/sync`

### 5.1 请求

```json
{
  "client_request_id": "sync-android-uuid",
  "base_currency": "CNY",
  "instruments": [
    {"client_ref": "record-1", "instrument_id": "XSHG:600519", "input_code": "600519.SS", "asset_type": "STOCK"},
    {"client_ref": "record-2", "instrument_id": "XSHE:159919", "input_code": "159919.SZ", "asset_type": "ETF"},
    {"client_ref": "record-3", "instrument_id": "CNFUND:000001", "input_code": "000001", "asset_type": "FUND"},
    {"client_ref": "record-4", "instrument_id": "XNAS:AAPL", "input_code": "AAPL", "asset_type": "STOCK"}
  ],
  "fx_pairs": [
    {"client_ref": "fx-usd-cny", "base": "USD", "quote": "CNY"},
    {"client_ref": "fx-hkd-cny", "base": "HKD", "quote": "CNY"}
  ],
  "include": ["LATEST_VALUE", "PREVIOUS_CLOSE", "BASIC_METADATA"],
  "allow_delayed": true
}
```

`instrument_id` 在旧数据尚未迁移时允许为 `null`；服务端需要用 `input_code + asset_type` 解析，并在响应里返回解析后的 instrument。

### 5.2 响应

```json
{
  "request_id": "req_sync_01",
  "data": {
    "instruments": [
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
          "source_time": "2026-08-28T07:00:00Z",
          "received_at": "2026-08-28T07:00:03Z",
          "quality": "DELAYED",
          "delay_seconds": 900,
          "source": "provider-name"
        },
        "error": null
      },
      {
        "client_ref": "record-3",
        "status": "OK",
        "instrument": {
          "instrument_id": "CNFUND:000001",
          "canonical_symbol": "000001",
          "name": "基金名称",
          "type": "FUND",
          "market": "CNFUND",
          "currency": "CNY"
        },
        "value": {
          "value_type": "OFFICIAL_NAV",
          "current": "1.2345",
          "previous_close": "1.2300",
          "value_date": "2026-08-28",
          "source_time": "2026-08-29T01:30:00Z",
          "received_at": "2026-08-29T01:30:05Z",
          "quality": "OFFICIAL",
          "delay_seconds": null,
          "source": "provider-name"
        },
        "error": null
      }
    ],
    "fx_rates": [
      {
        "client_ref": "fx-usd-cny",
        "status": "OK",
        "pair": "USD/CNY",
        "rate": "7.123456",
        "rate_type": "MID",
        "source_time": "2026-08-28T16:00:00Z",
        "received_at": "2026-08-28T16:00:02Z",
        "quality": "EOD",
        "source": "provider-name",
        "error": null
      }
    ]
  },
  "warnings": [],
  "partial": false,
  "server_time": "2026-08-30T11:20:30Z"
}
```

### 5.3 强制行为

- [ ] 单批至少支持 100 个 instrument 和 20 个货币对。
- [ ] 服务端按资产类型选择数据：股票/ETF 用市场价，场外公募基金用正式净值。
- [ ] 基金估值不得覆盖同一净值日的正式净值。
- [ ] 休市、停牌和周末不能简单作为失败；返回最后有效值、真实日期和状态。
- [ ] `previous_close` 或前一净值未知时返回 `null`，不能返回 `0`。
- [ ] 单项失败放入该项 `error`，其他成功项照常返回。
- [ ] 服务端结果可缓存，但必须返回原始数据时间，不能用缓存读取时间冒充行情时间。
- [ ] 相同 `client_request_id` 的重复请求必须幂等。

## 6. API-03 `[ ]`：股票/ETF 最新行情明细

供资产详情页或问题排查单独查询。

`POST /api/v1/market/quotes/batch`

请求：`instrument_ids`，最多 100 个；可选 `fields=[LAST,PREVIOUS_CLOSE,OHLC,VOLUME]`。

每项至少返回：

```json
{
  "instrument_id": "XNAS:AAPL",
  "status": "OK",
  "last": "229.10",
  "previous_close": "227.16",
  "open": "228.20",
  "high": "230.40",
  "low": "227.80",
  "volume": "38521000",
  "currency": "USD",
  "market_status": "CLOSED",
  "source_time": "2026-08-28T20:00:00Z",
  "quality": "DELAYED",
  "source": "provider-name"
}
```

`market_status`：`PRE_OPEN / OPEN / LUNCH_BREAK / CLOSED / SUSPENDED / UNKNOWN`。
`quality`：`REALTIME / DELAYED / EOD / STALE / FALLBACK`。

## 7. API-04 `[ ]`：公募基金净值和基金资料

### 7.1 基金净值批量

`POST /api/v1/funds/nav/batch`

请求：

```json
{
  "funds": [
    {"client_ref": "fund-1", "instrument_id": "CNFUND:000001", "fund_code": "000001"},
    {"client_ref": "fund-2", "fund_code": "161725"}
  ],
  "as_of_date": "2026-08-28",
  "include_estimate": true
}
```

响应单项：

```json
{
  "client_ref": "fund-1",
  "status": "OK",
  "instrument_id": "CNFUND:000001",
  "fund_code": "000001",
  "name": "基金名称",
  "unit_nav": "1.2345",
  "accumulated_nav": "3.4567",
  "previous_unit_nav": "1.2300",
  "nav_date": "2026-08-28",
  "currency": "CNY",
  "value_type": "OFFICIAL_NAV",
  "estimated_nav": null,
  "estimated_at": null,
  "source_time": "2026-08-29T01:30:00Z",
  "received_at": "2026-08-29T01:30:05Z",
  "source": "provider-name"
}
```

`value_type`：`OFFICIAL_NAV / ESTIMATED_NAV / PREVIOUS_OFFICIAL_NAV`。

### 7.2 基金详情

`GET /api/v1/funds/{fund_code}`

至少返回：

- `instrument_id/fund_code/name/short_name/currency/status`
- `fund_type`：`EQUITY / BOND / MIXED / MONEY_MARKET / INDEX / QDII / REIT / FOF / OTHER`
- `qdii/exchange_traded/benchmark/management_company/inception_date`
- `purchase_status`：`OPEN / RESTRICTED / SUSPENDED / CLOSED / UNKNOWN`
- `purchase_limit`：`amount/currency/effective_at/channel_scope`，未知为 `null`
- `suggested_bucket`：`DEFENSIVE / BALANCED / AGGRESSIVE`，只是录入默认建议，不能替用户做最终分类
- `source/as_of_date`

验收基金类型：主动权益、债券、货币、指数、QDII、LOF/场内基金至少各 1 个。

## 8. API-05 `[ ]`：汇率

`POST /api/v1/fx/rates/batch`

请求：

```json
{
  "pairs": [
    {"client_ref": "usd-cny", "base": "USD", "quote": "CNY"},
    {"client_ref": "hkd-cny", "base": "HKD", "quote": "CNY"}
  ],
  "rate_type": "MID"
}
```

响应单项：

```json
{
  "client_ref": "usd-cny",
  "status": "OK",
  "pair": "USD/CNY",
  "rate": "7.123456",
  "rate_type": "MID",
  "source_time": "2026-08-28T16:00:00Z",
  "received_at": "2026-08-28T16:00:02Z",
  "quality": "EOD",
  "source": "provider-name"
}
```

- [ ] 首期必须支持 `USD/CNY`、`HKD/CNY`，并允许服务端扩展其他 ISO 4217 货币。
- [ ] 同币种换算由 App 本地使用 `1`，不请求服务端。
- [ ] 缺汇率必须明确失败；服务端和 Android 都不能按 `1:1` 兜底。
- [ ] 明确 `MID/CLOSE` 口径；不能把银行现钞买入价和市场中间价混用。

## 9. API-06 `[ ]`：持仓截图解析

专属服务需要完成 OCR、表格结构恢复、字段语义识别和证券代码解析；不能只返回一段 OCR 文本。

### 9.1 同步解析接口

`POST /api/v1/ocr/holdings:parse`
`Content-Type: multipart/form-data`

字段：

| 字段 | 必填 | 格式 | 说明 |
|---|---|---|---|
| `image` | 是 | JPEG/PNG/WebP，建议支持 HEIC | 用户选择的持仓截图 |
| `client_request_id` | 是 | UUID | 幂等与追踪 |
| `locale` | 否 | `zh-CN` | 默认中文 |
| `default_currency` | 否 | ISO 4217 | 页面未标币种时的提示，不可无依据强行赋值 |
| `broker_hint` | 否 | 字符串 | 券商或基金 App 名称 |
| `parse_mode` | 是 | `HOLDINGS` | 后续可扩展 `TRANSACTIONS/STATEMENT` |

图片限制：最小 480×480，最大 20MP，压缩后最大 10MB；支持 EXIF 方向。超限分别返回 `IMAGE_TOO_SMALL / IMAGE_TOO_LARGE / UNSUPPORTED_MEDIA_TYPE`。

### 9.2 响应

```json
{
  "request_id": "req_ocr_01",
  "data": {
    "document_id": "doc_ephemeral_01",
    "document_type": "HOLDINGS",
    "image_sha256": "sha256-hex",
    "broker": {"name": "识别到的券商", "confidence": 0.82},
    "as_of_date": "2026-08-28",
    "rows": [
      {
        "row_id": "row-1",
        "raw_text": "贵州茅台 600519 持仓100 市值141801 成本120000",
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
        "total_cost": "120000.00",
        "currency": "CNY",
        "confidence": 0.96,
        "field_confidence": {
          "name": 0.98,
          "security_code": 0.99,
          "quantity": 0.97,
          "current_price": 0.94,
          "market_value": 0.95,
          "total_cost": 0.90,
          "currency": 0.92
        },
        "review_reasons": [],
        "bounding_box": {"x": 24, "y": 310, "width": 980, "height": 84}
      }
    ],
    "unparsed_blocks": [],
    "warnings": []
  },
  "warnings": [],
  "partial": false,
  "server_time": "2026-08-30T11:20:30Z"
}
```

### 9.3 强制解析规则

- [ ] 数量、价格、市值、成本使用十进制字符串或 `null`。
- [ ] 截图没有明确“成本/持仓成本/成本金额”时，`total_cost` 必须为 `null`。
- [ ] 禁止把市值、可用资金、总资产或累计收益当成成本。
- [ ] `market_value` 与 `quantity × current_price` 不一致时返回 `VALUE_MISMATCH`，不得静默改数。
- [ ] 代码有多个候选时返回 `AMBIGUOUS + candidates`，Android 交给用户确认。
- [ ] 每行保留原文、总置信度、字段置信度、复核原因和坐标。
- [ ] 支持跨行名称、表头、千分位、负盈亏、人民币/港币/美元符号和一图多币种。
- [ ] 相同 `client_request_id + image_sha256` 重复提交必须幂等。

### 9.4 隐私和删除

- [ ] 图片只通过 TLS 上传，不进入普通访问日志、分析平台或模型训练集。
- [ ] 默认解析完成即删除原始图片；如需暂存，最长保留时间必须配置并写入隐私政策。
- [ ] `DELETE /api/v1/ocr/documents/{document_id}`：立即删除图片、派生文件和可识别原文。
- [ ] 日志对姓名、账户号、资产总额和图片 URL 脱敏。
- [ ] 服务项目提供数据处理地区、子处理方和保存期限，供 App 隐私政策使用。

### 9.5 异步扩展

如果 15 秒内无法稳定完成解析，增加：

- `POST /api/v1/ocr/jobs`：返回 `job_id/status=PENDING`。
- `GET /api/v1/ocr/jobs/{job_id}`：返回 `PENDING/RUNNING/SUCCEEDED/FAILED/EXPIRED`。
- `DELETE /api/v1/ocr/jobs/{job_id}`：取消任务并删除数据。

## 10. API-07 `[ ]`：历史行情和基金净值

`GET /api/v1/market/history?instrument_id={id}&start_date=YYYY-MM-DD&end_date=YYYY-MM-DD&interval=1d&adjustment=TOTAL_RETURN&cursor={可选}`

股票/ETF 数据点：

```json
{
  "date": "2026-08-28",
  "open": "1400.00",
  "high": "1425.00",
  "low": "1398.00",
  "close": "1418.01",
  "previous_close": "1402.50",
  "adjusted_close": "1418.01",
  "volume": "2410000",
  "currency": "CNY",
  "source": "provider-name"
}
```

基金数据点：`date/unit_nav/accumulated_nav/value_type/currency/source`。

- [ ] 支持 `NONE / SPLIT_ADJUSTED / TOTAL_RETURN` 复权口径并在响应中回显。
- [ ] 支持分页；单个 instrument 至少可查询 5 年日线。
- [ ] “交易日无数据”与“服务请求失败”必须区分。
- [ ] 历史行情用于标的曲线，不能伪造成用户过去的真实组合快照。

## 11. API-08 `[ ]`：基金分类、限购与市场指标

### 11.1 批量指标

`POST /api/v1/market/metrics/batch`

请求：`instrument_ids`，最多 100 个。

每项返回：

```json
{
  "instrument_id": "XSHE:159919",
  "status": "OK",
  "as_of": "2026-08-28T07:00:00Z",
  "pe_ttm": "13.52",
  "pb": "1.46",
  "dividend_yield": "0.0312",
  "market_price": "4.223",
  "iopv_or_nav": "4.210",
  "premium_rate": "0.003087",
  "purchase_status": "OPEN",
  "purchase_limit": null,
  "source": "provider-name",
  "quality": "DELAYED"
}
```

- [ ] 不适用或未知字段返回 `null`，不能返回 `0`。
- [ ] 溢价率必须同时返回计算所用市价、IOPV/NAV 和各自时间。
- [ ] QDII 限购返回币种、金额、生效时间和渠道范围。
- [ ] 旧净值和新市价时间不一致时必须给出 `STALE_COMPONENT` 警告。

## 12. API-09 `[ ]`：公司行动

`GET /api/v1/corporate-actions?instrument_id={id}&since=YYYY-MM-DD&cursor={可选}`

事件：`CASH_DIVIDEND / STOCK_DIVIDEND / SPLIT / REVERSE_SPLIT / RIGHTS_ISSUE / FUND_DISTRIBUTION / SYMBOL_CHANGE / DELISTING`。

每项返回：`event_id/instrument_id/type/announcement_date/ex_date/record_date/pay_date/ratio/cash_per_share/currency/old_symbol/new_symbol/source/status`。

专属服务只提供事实数据；Android 首期生成“待确认调整”，不能后台静默修改数量和成本。

## 13. API-10 `[ ]`：服务状态

### 13.1 存活检查

`GET /health/live`：只表示服务进程存活，不访问上游。

### 13.2 就绪检查

`GET /health/ready`：检查数据库、缓存和关键依赖是否可用。

### 13.3 数据能力状态

`GET /api/v1/status`

```json
{
  "request_id": "req_status_01",
  "data": {
    "status": "DEGRADED",
    "capabilities": {
      "stock_quotes": "AVAILABLE",
      "fund_nav": "DEGRADED",
      "fx": "AVAILABLE",
      "ocr": "AVAILABLE",
      "history": "AVAILABLE"
    },
    "incidents": [
      {"code": "FUND_NAV_DELAYED", "message": "部分基金净值延迟", "started_at": "2026-08-30T08:00:00Z"}
    ]
  }
}
```

不得在公开状态接口暴露上游密钥、内部 URL、数据库信息或用户数据。

## 14. 第二阶段专属服务接口（未实现功能）

这些能力当前 App 也未实现，不阻塞首期替换 Yahoo/OCR，但专属服务项目需预留版本空间。

### 14.1 券商只读同步 `[ ]`

- `POST /api/v1/broker/connections/start`
- `POST /api/v1/broker/connections/complete`
- `GET /api/v1/broker/connections/{id}`
- `POST /api/v1/broker/connections/{id}/sync`
- `GET /api/v1/broker/sync-jobs/{job_id}`
- `GET /api/v1/broker/connections/{id}/snapshot?cursor=...`
- `DELETE /api/v1/broker/connections/{id}`

快照需返回账户、持仓、交易三类外部稳定 ID，所有数值仍使用十进制字符串。要求只读权限、OAuth PKCE、令牌服务端加密、可撤销、增量游标和幂等同步。

### 14.2 端到端加密云同步 `[ ]`

在确定账户体系、设备密钥、恢复方案、冲突合并、删除传播和设备撤销前不实施。服务端只能保存密文，不能直接上传明文 Room 数据库或 JSON 备份。

## 15. Android 替换旧接口任务

以下任务属于 FinUnity Android 项目，但依赖上述专属服务完成。

- [x] 新建 `FinUnityServiceApi`，所有 URL 指向专属服务 Base URL。
- [x] 删除 `NetworkModule` 中硬编码的 `https://query1.finance.yahoo.com/`。
- [x] 删除 Android 对 `YahooFinanceApi` 原始 DTO 的依赖。
- [x] `PriceSyncWorker` 和手动刷新统一调用 `/market/sync`，不再逐个代码请求。
- [x] 股票、ETF、基金、汇率按 `client_ref` 映射回 Room 记录。
- [x] Room 增加 `instrumentId/source/sourceTime/receivedAt/quality/valueType/errorCode` 等字段。
- [x] `FUND` 加入自动净值同步，不再一律跳过。
- [x] 资产录入通过 `/instruments/search` 选择稳定 `instrument_id`；OCR 导入保存服务端返回的 `instrument_id`，不再用资产名称冒充证券代码。
- [x] 截图识别改为 `/ocr/holdings:parse`，移除运行时 ML Kit 依赖；服务错误直接显示且不生成模拟数据。
- [x] 上传截图前明确提示图片会发送到专属服务；用户取消时不上传，服务解析后调用删除接口并要求确认删除成功。
- [ ] 更新隐私政策，写明服务域名、处理目的、保存期限和删除方式。
- [ ] 401 刷新 Token，429 遵守 `Retry-After`，5xx/超时指数退避，4xx 参数错误不盲目重试。
- [ ] 部分成功只重试失败项；已成功项立即安全落库。
- [ ] 离线时使用最后有效缓存并显示原数据时间，不能把缓存读取时间显示为同步时间。
- [x] Base URL 只通过构建配置注入且强制 HTTPS，Manifest 禁止明文 HTTP；源码和 APK 不再包含上游地址。

## 16. 专属服务项目交付物

- [ ] OpenAPI 3.1 文件，包含全部 P0 端点、模型、枚举和错误码。
- [ ] 测试环境 Base URL 与正式环境 Base URL。
- [ ] 可直接连接真实测试环境的契约测试；不得用 Fake 行情、净值、汇率或 OCR 数据替代服务端错误。
- [ ] Postman/Bruno/curl 真实测试环境示例，至少覆盖成功、部分失败、401、429、超时和上游错误。
- [ ] 数据源和许可证说明：股票、ETF、基金、汇率、OCR 各自的上游、商用权限和缓存限制。
- [ ] 数据更新频率/SLA：行情延迟、基金净值发布时间、汇率时间和历史修正策略。
- [ ] 错误码表和 Android 重试建议。
- [ ] OCR 隐私说明：处理地区、子处理方、是否用于训练、保存期限和删除验证。
- [ ] 监控：请求量、P50/P95/P99、上游成功率、缓存命中率、429、OCR 失败率。
- [ ] 变更策略：URL 使用 `/api/v1`；破坏性字段变更必须发布新版本，不能直接改变已有语义。

## 17. 联调数据与验收标准

### 17.1 固定联调样本

- A 股：`600519.SS`。
- 深市 ETF：`159919.SZ`。
- 港股：`00700.HK`，同时验证前导零。
- 美股：`AAPL`，并增加一个带点号或连字符的代码。
- 公募基金：主动权益、债券、货币、指数、QDII、LOF 各 1 个。
- 汇率：`USD/CNY`、`HKD/CNY`。
- OCR：A 股券商、港股券商、美股券商、公募基金、深色模式、长截图、模糊图、多币种、成本缺失。

### 17.2 故障样本

- 无效代码、代码歧义、退市、停牌、休市。
- 基金净值尚未公布、只有估值、净值修正。
- 单项失败但整批其他项成功。
- Token 过期、403、429、上游 5xx、超时、服务降级。
- OCR 图片过大、格式不支持、无法识别、字段冲突、重复上传和主动删除。

### 17.3 完成定义

- [ ] P0 接口全部有 OpenAPI、可调用的真实测试环境和真实错误样例。
- [ ] Android 网络抓包只出现专属服务域名，不再出现 Yahoo 或其他数据商域名。
- [ ] `600519.SS / 159919.SZ / 00700.HK / AAPL` 可取得当前值、昨收、币种、数据时间、来源和质量。
- [ ] 六类基金可取得正式净值、净值日期和基金分类；未公布净值不会伪报为成功的新净值。
- [ ] `USD/CNY`、`HKD/CNY` 可用，缺失时不会发生 1:1 假换算。
- [ ] OCR 成本缺失时返回 `null`，不会把市值误当成本；低置信结果必须可复核。
- [ ] 批量部分失败、缓存回退、Token 刷新和 429 重试均有自动化契约测试。
- [ ] Release APK 不包含上游密钥、上游接口地址或图片解析供应商密钥。
