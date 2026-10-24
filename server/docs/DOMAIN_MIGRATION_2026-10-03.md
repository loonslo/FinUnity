> 历史记录：2026-10-07 已撤销域名迁站，当前以 [双域名部署记录](DUAL_DOMAIN_DEPLOYMENT_2026-10-07.md) 为准；以下原日期及验收不作本次复测。

# 2026-10-03 域名迁移验收

finunity-api.halfopen.dev 已接入有效 HTTPS，新旧 API readiness 均 200；匿名受保护配置接口 401，docs/redoc/openapi 404。原服务镜像/数据/可信代理边界保持。源码默认隐私 URL 已改新域名，36 项 public_launch 测试通过；实际运行 PRIVACY_POLICY_URL 仍是旧地址并经 301 到新页面，未读取凭据环境文件或重启 Server。

公网 TLS 与入口由既有 nginx 提供，Caddy/nginx 示例均已体现新主机、旧 API 和旧 Web API/health 兼容。旧接口不作会改变 POST 方法的 301。源码默认隐私 URL 精确备份后变更，并用独立 basetemp 验证 36 测试；首次测试被 Windows 共享 pytest 临时目录权限阻挡，重跑通过。实际 Server 运行环境未整体复制：compose config 验证因既有环境文件路径未知而未通过，未执行 Server up/restart；非敏感旧隐私 URL 经 301 工作正常。接口 GET 就绪 200 只证明传输/服务就绪，不代替登录/OCR/记账验收。

三个服务器子站与 API 共享新证书 halfopen-subsites，2027-01-01 到期；新增每日续期任务并纳入旧 finunity.baikai.site 证书，service 实际执行成功。旧角色/知识证书的原续期任务保留。所有对外页面迁移后才启用旧公开主机 301；旧 API 保持直接代理。原配置、旧镜像及静态版本保留可回退；未读取私钥、凭据环境内容或业务数据。Google 所有权、地址变更确认和 sitemap 提交不等于实际抓取/收录成功。

下一步：保留旧 API 兼容及其证书续期；后续正式后端发布使用新隐私 URL，现存旧隐私 URL 跳转可用。

域名旧记录是历史资料，本次未全局改写历史验收。Google实际处理状态以最新 Search Console 为准。
