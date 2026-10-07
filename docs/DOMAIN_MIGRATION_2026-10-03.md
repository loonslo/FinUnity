> 历史记录：2026-10-07 已撤销域名迁站，当前以 [双域名部署记录](DUAL_DOMAIN_DEPLOYMENT_2026-10-07.md) 为准；以下原日期及验收不作本次复测。

# 2026-10-03 域名迁移验收

Android 默认 API 与隐私说明源码已更新为新域名；generateDebugBuildConfig 成功，生成 HTTPS API 地址正确，新旧 API readiness 已验收 200。已安装客户端继续使用旧 API 兼容入口；本次未签名/打包/分发、未做真机与完整业务网络验收。

精确更新 app/build.gradle、PrivacyScreen.kt 与 README，原字节已备份。Gradle 8.13 :app:generateDebugBuildConfig 成功；本次没有 assemble、完整单测、release 签名、APK 分发或真实账号调用。旧 API 长期兼容供尚未更新的已安装客户端使用。

下一步：正式签名构建、Android 真机网络与发布门禁仍待单独执行；当前已安装应用无需因域名更换停用。

域名旧记录是历史资料，本次未全局改写历史验收。Google实际处理状态以最新 Search Console 为准。

## 提交与发布跟进（2026-10-06）

以下更新本页早期“未提交/未部署”的状态；旧验收保留原日期与环境。

提交 9d2a3e3 已推送 master。本机 assembleDebug 与 test 成功；此前记录 146 项 JVM 单测通过。GitHub workflow 与 run 查询为空，仓库未配置 CI。本次未签名、未分发 APK、未发布应用商店，真机网络未验收。
