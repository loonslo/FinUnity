# Web 入口配套接口 · 本地验收

日期：2026-10-21。为 Web《产品即首页》方案中“注册仅用户名/密码，显示名称移至个人中心”提供接口。未提交、未推送、未部署。

`POST /api/v1/me/profile` 需要用户 Bearer 会话，只接受 `display_name`（最多 100 字符），去掉两端空白，空名称清除为默认显示。更新由会话解析出的用户，不能指定他人 ID，不修改用户名、密码、恢复码或会话。返回现有 `public_user` 投影，无需数据库迁移。前端应与此服务端改动同批发布；旧注册接口保持兼容。

`GET /api/v1/ledger/summary` 的 `allocation.items[].gap_minor` 统一返回目标金额减实际金额（最小货币单位）。使用 Decimal ROUND_HALF_UP；无有效资产时为 null。内存示例用 BigInt 对齐此规则。现有舍入一致性测试新增 305 分 × 30% 的半分边界断言。

2026-10-21，Windows，项目现有 `.venv`：

- `python -m pytest tests/test_profile.py tests/test_password_auth.py tests/test_web_api.py tests/test_ledger_consistency.py -q`：29 项通过。
- `ruff check src/finunity_server/contracts.py src/finunity_server/store.py src/finunity_server/v1_api.py tests/test_profile.py tests/test_ledger_consistency.py`：通过。
- 新测试验证不填名称注册、未登录拒绝、修改后持久可读、不影响其他用户、拒绝越界字段和超长名称、清空名称、不返回密码哈希。
- 依赖存在 Starlette/httpx 弃用警告，本次未调整依赖。未运行全部 Python 测试，未做真实账号或生产验收。

已有未跟踪的 `tmp/` 保留，未读取或改动。项目摘要只维护本项目；未调用日常库同步工具。
