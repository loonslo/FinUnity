# 2026-10-15 生产升级验收

环境：Windows 本机 Python 3.12 项目环境；既有 Ubuntu 生产主机与 Docker Compose；公开 HTTPS。

- 部署前确认旧镜像为 finunity-server:20260929-8d1836f，落后于已提交可靠性修复。
- 本机最终 126 项 pytest 通过、Ruff 通过。前两轮失败是旧临时目录权限和父目录不存在，改为已建立父目录中的全新 basetemp 后通过，未改测试或业务代码。存在 FastAPI TestClient 弃用警告。
- 从干净 Git 提交 55cf875b9d0350e80cfed14b971b7bec8e22b50b 导出源码并在服务器构建 finunity-server:20261015-55cf875；镜像 ID sha256:8a8e6ca50c4c74dedbcd3ddc1bff50ca662d486390e962d0a9562d19659fa1af。当前 OCR 为 qwen，不安装 PaddleOCR。
- 升级前 SQLite 在线备份、完整性和外键检查通过；在无网络临时容器中用数据库副本预演，2 个账号保留；停止旧容器后再建立切换时的一致备份。原镜像、原发布目录及生产数据目录均保留。
- 备份目录 /home/ubuntu/finunity-backups/20261015-55cf875：finunity.db 为预演前副本，cutover.db 为切换前正本，rehearsal/ 为演练副本。权限受限；含用户数据，不进入 Git。此发布专用目录未纳入旧 daily-*.db 自动保留规则，需在 14 天内由运维按恢复需要处置。
- 保留既有生产环境文件、密钥、数据卷、Compose 项目和精确代理信任 172.20.0.3；不读取或打印凭据。首次用 docker run 读 Compose 格式 env 文件时因引号格式验证失败，改为 Compose run 后生产配置通过，没有轮换密钥。
- 发布目录 /home/ubuntu/finunity-releases/20261015-55cf875，数据卷仍为 /home/ubuntu/finunity-server/data。只替换 API 容器，增加仓库既有内存/权限限制；nginx 语法通过。
- 发布后数据库 integrity=ok、foreign_key_errors=0、users=2。四入口（两个域的 Web 与 Android API）的 ready/version 为 200、匿名 app/config 与 me 为 401、docs 与 openapi 为 404，共 24 项通过；版本均为 55cf875，详见 release-http-2026-10-15.json。
- 全站监控另有 18 项 HTTP 与 12 项 TLS 通过。后端健康并不代表真实账号/OCR收费上游/两出口限流/异地备份已验收；这些本次未验证。未新增云服务或通知。

## 推送后的时钟矩阵检查

首次云端 run 38707833036：镜像构建/探活、+0天与+35天测试成功；+400天 124通过、1跳过、1失败，失败为限频测试的两次请求跨过整分钟。仅在该测试固定当前 time.time，保持现有时钟偏移，避免窗口边界漂移；未改业务限流策略。Windows本机完整+400天复验125通过、1跳过（31.31秒），Ruff通过。生产仍运行55cf875，相比后续提交仅测试/验收文档差异，不重复重启。

最终生产只读复核：新API镜像healthy，约65.67MiB/512MiB；PersonalCharacter与Knowledge亦healthy。FinUnity与PersonalCharacter最近本机备份任务Result=success、退出0；未检查或声称异地副本可恢复。
