# 双域名独立部署与验收（2026-10-07）

当前决策：相同公开内容，baikai.site 与 halfopen.dev 两域名均开放搜索。2026-10-03 的迁站文档仅保留历史记录；其中跨域 301 与单域 canonical 要求已被本记录替代。

## 架构与边界

五个前端各自有两个 Vercel 项目，Cloudflare 使用 DNS-only CNAME 分别指向各项目。www 仅重定向到同域根域名。公开页面的站内链接、canonical、hreflang、robots.txt、sitemap 都使用本部署域名；登录与私有路径保留 noindex、鉴权和文件白名单。

后端与数据继续共享；两个前端部署不代表数据库或账户隔离。旧 Character、Knowledge、FinUnity Web 的 API 经对应 halfopen.dev 前端网关转发，网关继续向受保护源站附加已有密钥。旧域名部署使用 deploy/vercel.baikai.json，不需要复制密钥。此路径多一层转发，并依赖 halfopen.dev 网关；源站精确接受两域 Origin，未知 Origin 仍拒绝。Android 两个 API 域名均保留。

服务器 Nginx 已更新精确 Origin/Host 映射；旧服务器站点取消跨域跳转，改为同域 HTTPS 和 Vercel 回退。配置备份位于服务器受限目录 /home/ubuntu/dual-domain-20261007-nginx-backup；nginx -t 与 reload 已成功。随后 SSH 连接被服务器关闭，因此没有继续读取或修改密钥，也没有取消任何源站保护。

## Google 与搜索

Search Console 已撤销旧根域、www URL-prefix 与 character、knowledge、finunity、dashboards 五个域名属性共六项地址迁移。旧根域属性覆盖的五份 sitemap 已逐一重新提交，Google 确认提交成功；新域名属性保留；halfopen.dev 根域属性的五份 sitemap 本次 UI 核对均为 Success（最后读取 10-04 至 10-06）。验证 TXT 与邮件 MX/SPF/DKIM 保留。

旧 sitemap 均显示 Success，提交日期 2026-10-07。最后读取日期：主站 10-05，dashboards 10-07，FinUnity 10-05，Knowledge 10-07，Character 10-06。因此不能把提交成功当成本次全部重新抓取或重新收录。相同内容即使各自声明 canonical，Google 仍可能合并选择一个域名；本次验收不保证两域同时展示搜索结果。

参考：https://support.google.com/webmasters/answer/9370220?hl=en 与 https://developers.google.com/search/docs/crawling-indexing/consolidate-duplicate-urls 。未创建或修改没有发现对应配置的 Analytics、广告、OAuth 等 Google 产品。

## 本次验收

日期 2026-10-07，环境为公开 HTTPS 生产站、Vercel API、已登录 Cloudflare/Search Console UI、本机 Windows。公开页面共 240 项检查通过：两域页面 GET/HEAD 200、各自 canonical、robots sitemap 与地图同域、私有源文件与未知路径 404。API 与边界共 23 项通过：健康检查、未认证接口 401、允许 Origin 空输入 400、未知 Origin 403、受保护源站直接访问 403、www 路径和查询保留。Knowledge readiness 503 是原有未启用状态，未批准语料、模型调用或生成。

本次旧域部署复用对应 halfopen.dev 正式上线版本的 Git SHA，避免顺带发布未验收业务改动。未测试真实登录、账户写入、交易、完整聊天/生成或浏览器跨域会话；两域 Cookie/本地存储彼此隔离。

## 后续发布与未验证项

已在线的旧域五个项目来自独立 CLI 发布，尚未接入 Git 自动发布；本机代码与配置尚未提交/推送。今后的内容发布必须将同一已批准版本分别发布到两个项目，不能只更新其中一个。新版 halfopen.dev 继续使用根 vercel.json，旧域三个有 API 的项目使用 deploy/vercel.baikai.json；其余项目复用根配置。旧域项目环境 SITE_DOMAIN_ROOT=baikai.site；新版默认为 halfopen.dev。构建后 domain_variant.py 只改允许的公开站点主机，不修改邮箱、任意外部网址或受保护 API 主机。

在已批准版本的独立发布目录中，用 .vercel/project.json 明确 orgId/projectId 后执行 vercel deploy --prod；旧域有独立配置时使用 --local-config deploy/vercel.baikai.json。不得在已有 halfopen 项目链接目录里直接执行旧域发布。Git 自动双发布流水线、密钥配置与执行尚未验收，不能宣称持续同步完成。

## Android / 服务端

Android 新增 FINUNITY_DOMAIN_ROOT 构建参数，默认 halfopen.dev；旧域构建参数为 `-PFINUNITY_DOMAIN_ROOT=baikai.site`。生成的 API 与隐私政策 URL 均已核对。`:app:compileDebugKotlin` 默认域编译成功；旧域 BuildConfig 生成成功。未签名、发行或安装新版 APK，服务端数据与认证不变。

## 本批次代码提交授权（2026-10-07）

用户随后明确授权本批次提交、推送。本提交只含双域配置、工具、监控/Android 域名选择及验收记录，未包含其他业务改动。前文“尚未提交/推送”描述的是首次线上验收时点；本批次 Git 执行由对应仓库提交历史核对。Git 自动双发布仍未接入，不能把推送等同于部署验收。
