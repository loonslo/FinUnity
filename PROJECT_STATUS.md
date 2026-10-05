---
project_id: FinUnityWorkspace-FinUnity
updated: 2026-10-03
status: active
overview: 多币种家庭资产账本与三桶规划 Android 应用。
progress: 公网上线配套地址、匿名 OCR 提示与隐私材料已改造；当前工作树 Debug 构建及 146 项 JVM 单测通过，设备验收未完成。 2026-10-03 域名迁移：Android 默认 API 与隐私说明源码已更新为新域名；generateDebugBuildConfig 成功，生成 HTTPS API 地址正确，新旧 API readiness 已验收 200。已安装客户端继续使用旧 API 兼容入口；本次未签名/打包/分发、未做真机与完整业务网络验收。
next: 域名迁移：正式签名构建、Android 真机网络与发布门禁仍待单独执行；当前已安装应用无需因域名更换停用。 原其他事项：后端 HTTPS 与真实匿名 Qwen 调用已部署验证；继续完成正式签名构建及 Android 真机/模拟器网络和发布门禁。
evidence:
- README.md
- ROADMAP.md
- docs/PUBLIC_LAUNCH_2026-09-29.md
- docs/DOMAIN_MIGRATION_2026-10-03.md
---

# FinUnity Android · 项目概览与进度

本页是本项目的概览与进度摘要。更新进度时先核对证据文件及实际验收；任务细节仍以项目原有的 TASKS、ROADMAP 或验收记录为准。只根据有证据的变化修改状态与日期；未验证事项保留“待核实”，不把文件存在或 Git 改动当作完成。


## 2026-10-03 域名迁移


Android 默认 API 与隐私说明源码已更新为新域名；generateDebugBuildConfig 成功，生成 HTTPS API 地址正确，新旧 API readiness 已验收 200。已安装客户端继续使用旧 API 兼容入口；本次未签名/打包/分发、未做真机与完整业务网络验收。 详见 docs/DOMAIN_MIGRATION_2026-10-03.md。
