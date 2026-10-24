# 可靠性改造验收（2026-10-05）

范围：本机代码、配置、测试与文档改动；**未提交、未推送、未部署、未连生产**。改造依据是 2026-10-05 对五个在线项目做的只读体检（问题编号沿用体检报告）。本页只记录本项目的部分；日期、环境、结果和未验证项都写在下面，旧的验收文字没有被改写。

## 改了什么、为什么、怎么验证

| 编号 | 问题 | 改动 | 本机验证 |
| --- | --- | --- | --- |
| F1 | OCR 上传先把整张图解码，再检查像素上限：一个几百 KB 的小文件可以声明几亿像素，拖垮内存 | `src/finunity_server/v1_api.py` 的 `check_image`：先只读文件头判断格式、最小尺寸和像素预算，通过后才解码一次；`DecompressionBombError` 也映射为 413 | `tests/test_image_guard.py`。2026-10-05 用最终代码重测：9000×9000（81 MP，231 KB 的 PNG）返回 413，约 19 ms，内存增量约 1 MB（修复前约 +619 MB、0.33 s）；14000×14000（196 MP，558 KB）返回 413，约 17 ms（修复前返回 500）。把“先解码”还原后，该测试文件里有 2 项失败 |
| F2 | 匿名会话接口（`/api/v1/auth/session`、`session:refresh`）无登录、无限频，可无限创建会话，`refresh_sessions` 表无限增长 | `config.py` 新增 4 个限频参数（IP 每小时 60、刷新 IP 每小时 300、安装标识每小时 10、全站每日 20,000）；路由改为同步 `def`，限频不再占用事件循环；`store.py` 给过期/主体列加索引，并按主体只保留最新会话（用户 20、匿名安装 3）；`deploy/nginx-proxy.conf.example` 增加边缘每 IP 限速 | `tests/test_session_limits.py`。2026-10-05 用最终代码重测：会话表从 0 行到 60 万行，创建一次匿名会话都是约 12.3–12.9 ms（修复前 60 万行时约 158 ms）。把单 IP 限频或会话上限还原后，对应测试失败 |
| F4 + X2 | 一个测试靠“默认当月”，到 2026-10-01 起就失败；没有 CI | 该测试显式传 `month=2026-09`；新增 `tests/time_travel.py` 与 `--time-offset-days`，把应用时钟拨快后跑全套；新增 `.github/workflows/ci.yml`（推送、拉取请求与每日定时触发；ruff、测试矩阵 +0/+35/+400 天、镜像构建并探活，动作固定到提交 SHA） | 见下面的复测数字；变异检查：重新引入“默认当月”后，在 +35 天时钟下该测试失败 |
| X3 | 线上跑的是哪个提交无法确认 | `Dockerfile` 与 `docker-compose.yml` 注入 `GIT_SHA`、`BUILD_TIME`，`GET /health/version` 返回它们 | `tests/test_health.py` |
| X5 | 健康检查只是“进程在”，数据库坏了也返回 200 | `/health/live`（进程）、`/health/ready`（真的读库并拿到写锁，失败返回 503；缺 OCR 密钥只标 degraded，不会为探活去调用收费的上游）、`/health/version` | `tests/test_health.py` |
| X4 | 数据库与备份只在同一台服务器上，没有恢复演练 | `ops/offsite_backup.py`（备份、校验、加密推送、保留、恢复演练、心跳）、4 个 systemd 单元模板、`ops/README.md`（含 RPO、灾难恢复步骤、保留期说明） | `tests/test_offsite_backup.py` 17 项，走真实 SQLite；详见 `ops/README.md` |
| X1 | 私钥文件在工作区根目录，且没有 `.gitignore` | 工作区根新增 `.gitignore`（不属于本仓库，记录在体检报告里） | `git check-ignore` 确认该文件被忽略；我没有读取、移动或使用该文件 |

补充：`docker-compose.yml` 给容器加了内存上限（默认 512m，可用 `FINUNITY_MEM_LIMIT` 调整，PaddleOCR 方案需要更多）、`pids_limit`、`cap_drop: [ALL]` 与 `no-new-privileges`。这是新增的部署约束，没有在真机上验证。

### 发现并修复的一个回归（本次自查）

异地备份最初的保留策略是日 14、周 8、月 12，而隐私页面承诺“业务备份最多保留 14 天”，注销的账号会在异地多留约 12 个月。已改为只保留 14 天（restic 用 `--keep-within 14d`，目录后端同样清理），常量 `RETENTION_DAYS` 由测试固定，并在 `ops/README.md` 说明。另外，演练日志目录原来要求非 root 用户在 `/var/lib` 下建目录，现在由 systemd 的 `StateDirectory=` 创建。

## 验证记录

- 日期与环境：2026-10-05，Windows 10，Python 3.12.13（项目 `.venv`，与 CI 使用的 3.12 一致），本机无 Docker、nginx、restic。
- `ruff check .`：通过。
- `pytest -q`：126 通过。
- `pytest -q --time-offset-days=35`：125 通过、1 跳过。`--time-offset-days=400`：125 通过、1 跳过。跳过的是“嵌套拨钟”那一项，已经拨过钟时按设计跳过。
- 离站备份脚本：`tests/test_offsite_backup.py` 17 项覆盖（含在真实 SQLite 上走通备份、恢复演练和清理）；另用项目 `.venv`（Python 3.12.13）在命令行以目录后端实跑一次备份与演练：两次退出码都是 0，演练记录 `integrity: ok`、`foreign_key_errors: 0`、`restored_bytes: 176128`，临时目录已删除。数据量很小，`restore_seconds` 为 0.0，不代表真实数据量下的耗时。没有在真实服务器上运行。

## 未验证

- Docker 镜像构建、容器内存/权限限制、`GIT_SHA` 注入后的 `/health/version`（本机无 Docker）。
- `.github/workflows/ci.yml` 从未真正运行过；固定的动作 SHA 来自此前核对的发布版本，首次运行以 GitHub Actions 为准。
- `deploy/nginx-proxy.conf.example` 没有执行 `nginx -t`；在 Vercel 与代理之后，限速和应用限频是否按真实客户端 IP 归因（体检里的 X8）没有验证。
- 真实的 `restic`、R2/B2、systemd 单元、与现有 `finunity-public-backup.timer` 并行运行、整机重建耗时。
- 中国大陆网络访问没有实测。

## 需要你来做

1. 查看并提交改动（我没有执行任何 `git commit`/`push`）。
2. 部署时重建镜像并传入 `GIT_SHA=$(git rev-parse HEAD)`、`BUILD_TIME`；确认 OCR 提供方后再决定容器内存上限；合并 nginx 配置并先 `nginx -t`。
3. 推送后检查 GitHub Actions 首次运行结果。
4. 按 `ops/README.md` 的“一次性准备”开通存储桶、restic、healthchecks 与 systemd。
5. 把外部监控的探活地址改为 `/health/ready`，并用两个不同出口 IP 各测一次限速（X8）。

## 提交与发布跟进（2026-10-06）

以下更新本页早期“未提交/未部署”的状态；旧验收保留原日期与环境。

提交 2f4b565、ee8270d 已推送 main。GitHub Actions run 37340370886 成功，包含镜像构建及容器探活。本机 Windows / Python 3.12.13：Ruff 通过，126 项 pytest 通过。后端未部署：没有确认的服务器 SSH 目标或 Docker CLI；生产 SESSION_SECRET、PASSWORD_PEPPER、数据库及备份配置未提供。真实服务器、nginx、R2/B2/restic/systemd 仍未验收。
