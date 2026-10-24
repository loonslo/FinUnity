# 错误码与客户端处理建议

| 错误码 | HTTP | 可重试 | Android 建议 |
| --- | ---: | --- | --- |
| `VALIDATION_ERROR` | 400 | 否 | 修正请求，不自动重试 |
| `UNAUTHORIZED` / `TOKEN_INVALID` | 401 | 否 | 尝试刷新 Token；刷新失败则新建会话 |
| `REFRESH_TOKEN_INVALID` | 401 | 否 | 新建匿名会话 |
| `IDEMPOTENCY_CONFLICT` | 409 | 否 | 为不同业务操作生成新的请求 ID |
| `IMAGE_TOO_LARGE` | 413 | 否 | 客户端缩放或压缩后使用新请求 ID 上传 |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | 否 | 转换为 JPEG、PNG 或 WebP |
| `IMAGE_TOO_SMALL` / `HOLDINGS_NOT_FOUND` | 422 | 否 | 提示用户更换清晰截图或使用本地录入 |
| `RATE_LIMITED` | 429 | 是 | 严格遵守 `Retry-After` |
| `MODEL_ERROR` | 502 | 是 | 指数退避并保留原数据时间 |
| `MODEL_NOT_CONFIGURED` | 503 | 否 | 服务端配置问题，不在客户端密集重试 |
| `MODEL_TIMEOUT` | 504 | 是 | 指数退避；避免重复并发上传 |

批量接口的单项错误位于对应项目的 `error` 字段，整批仍返回 HTTP 200 并设置 `partial=true`。客户端只重试失败项目，成功项目可立即落库。缺少汇率时禁止按 1:1 兜底。
