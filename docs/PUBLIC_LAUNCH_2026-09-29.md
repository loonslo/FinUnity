# 2026-09-29 公网上线配套改造验收

本次按公网上线清单修改 Android 默认 HTTPS 服务地址、截图上传说明、隐私政策与服务错误提示。资产计算、Room schema 和账本导入规则沿用现有实现。

- 默认 API 为 `https://finunity-api.baikai.site/api/v1/`；构建属性或环境变量仍可覆盖，明文访问仍被禁用。默认值不代表公网服务已可用。
- 匿名安装识别由后端支持；配额 429 提示今日识别次数已用完，其他限速 429 提示稍后再试，503 区分停用和暂不可用。新增四项错误提示单元测试。
- 隐私政策、应用内政策和上传确认同步说明新加坡服务器、现有 DashScope 中国站、原图在 FinUnity 请求内存中处理、匿名结果至多 24 小时幂等缓存及本地确认保存。联系邮箱使用用户提供的值。公开 Android 政策由 Web 的显式同步脚本从本仓库 Markdown 生成。
- DashScope 国际站尚未选定，沿用现有中国站；没有承诺上游零留存或全部推理发生在新加坡。

2026-09-29 使用 JDK 17 执行 `gradlew.bat :app:testDebugUnitTest :app:assembleDebug`，BUILD SUCCESSFUL；JUnit XML 合计 23 套件、146 项测试，0 失败、0 错误。产出 Debug APK，未构建正式签名 APK/AAB。

尚未执行 `connectedDebugAndroidTest`、真机/模拟器安装升级与状态恢复、真实匿名 OCR、正式域名证书链及实际行情同步验证。后端 OCR 默认关闭，生产政策公开、预算预警及真实识别验收完成后由用户启用。

跨仓库完整对照与服务器步骤在同工作区 `FinUnityServer/deploy/IMPLEMENTATION_2026-09-29.md`、`FinUnityServer/deploy/README.md`。这里仅记录本次实际执行的 Android 验收。

## 后续服务端部署

2026-09-29 用户另行授权后，API 已部署至正式 HTTPS 域名，OCR 已开启。匿名安装会话通过生产 API 调用真实 Qwen，识别合成测试图成功；公开 Android 政策可访问。Android `8ec0922` 已推送，尚未通过真机应用调用生产服务，正式签名和设备门禁继续待执行。生产详细记录见 Server 的 `deploy/PRODUCTION_2026-09-29.md`。
