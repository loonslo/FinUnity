---
project_id: FinUnityWorkspace-FinUnity
updated: 2026-10-07
status: active
overview: 多币种家庭资产账本与三桶规划 Android 应用。
progress: "Android 新增两域构建参数，旧域生成 URL 核对成功，默认域 Kotlin 编译通过；两域 API 保持在线，未发行新版 APK。"
next: "审阅并发布双域构建配置，完成同版本双项目发布自动化；后续观察 Google 重新抓取与收录。"
evidence:
- README.md
- ROADMAP.md
- docs/PUBLIC_LAUNCH_2026-09-29.md
- docs/DOMAIN_MIGRATION_2026-10-03.md
- docs/DUAL_DOMAIN_DEPLOYMENT_2026-10-07.md
- docs/dual-domain-http-2026-10-07.json
---

# FinUnity Android · 项目概览与进度

本页是本项目的概览与进度摘要。更新进度时先核对证据文件及实际验收；任务细节仍以项目原有的 TASKS、ROADMAP 或验收记录为准。只根据有证据的变化修改状态与日期；未验证事项保留“待核实”，不把文件存在或 Git 改动当作完成。


## 2026-10-03 域名迁移


Android 默认 API 与隐私说明源码已更新为新域名；generateDebugBuildConfig 成功，生成 HTTPS API 地址正确，新旧 API readiness 已验收 200。已安装客户端继续使用旧 API 兼容入口；本次未签名/打包/分发、未做真机与完整业务网络验收。 详见 docs/DOMAIN_MIGRATION_2026-10-03.md。
