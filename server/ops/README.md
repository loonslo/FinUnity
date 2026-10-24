# 异地加密备份与恢复演练

目的：数据库和备份不再只存在于同一台服务器上，并且“能恢复”是被定期演练证明的，而不是假设的。脚本是 `ops/offsite_backup.py`（只用标准库），在 `deploy/database.py` 已有的一致性备份之上增加三件事：加密后推送到异地、异地保留策略、定期恢复演练。

## 数字

| 指标 | 现在 | 说明 |
| --- | --- | --- |
| RPO（最多丢多久的数据） | 每日一次备份，最坏约 24 小时 | 演练会强制检查最新异地备份不超过 36 小时，否则失败并告警 |
| 恢复一份异地备份并通过审计 | 由演练实测，记录在 `restore_seconds` | 只量“拉回、解密、审计”这一步 |
| 整机重建的 RTO | **没有演练过，不写数字** | 新服务器开通、Docker、nginx、证书、恢复、启动，需要你完整演练一次后再填 |
| 异地副本保留期 | **14 天，与隐私页面一致** | 见下文“保留期与隐私页面” |

## 组成

| 文件 | 作用 |
| --- | --- |
| `ops/offsite_backup.py backup` | 一致性备份 → 审计 → 推送异地 → 异地保留 → 清理本机自己命名的过期备份 → 心跳 |
| `ops/offsite_backup.py drill` | 校验仓库 → 恢复最新异地备份到临时目录 → 审计 → 检查年龄 → 记录耗时 → 删除临时副本 → 心跳 |
| `ops/finunity-offsite-backup.service/.timer` | 每天 03:40 备份（systemd 模板，路径是占位符） |
| `ops/finunity-restore-drill.service/.timer` | 每月 1 日 04:30 演练 |

异地后端：默认 `restic`（加密、去重，写入 Cloudflare R2、Backblaze B2 等 S3 兼容存储，免费额度以官网为准）；`OFFSITE_BACKEND=dir` 只复制到另一个目录（第二块盘、挂载的共享盘、或做演练彩排），不加密。

## 保留期与隐私页面

隐私页面（`FinUintyWeb` 的 `/privacy`）告诉用户：“业务备份最多保留 14 天”，并说明注销不会自动改写离线备份。异地副本因此只保留 14 天（restic 用 `--keep-within 14d`，目录后端用 `prune_local`），**不做周、月归档**：长期归档会让已注销账号的数据在异地多留数月，与页面矛盾。常量 `RETENTION_DAYS` 由测试 `test_retention_stays_at_the_published_14_days` 固定；想要更长的月度归档，必须先改隐私页面，再改这个常量。

异地副本放在第三方对象存储（内容已加密，服务商看不到明文）。隐私页面是否需要补这一句，由你决定。

## 一次性准备（需要你来做）

1. 在 Cloudflare R2 或 Backblaze B2 建一个 bucket，再建一个**只能访问这个 bucket** 的 API 令牌。
2. 在服务器安装 `restic`。
3. 创建 `/etc/finunity-offsite.env`（权限 600，**不要提交到 Git**），只写变量，值由你填：

   ```
   OFFSITE_BACKEND=restic
   RESTIC_REPOSITORY=s3:https://<账号>.r2.cloudflarestorage.com/<bucket>
   RESTIC_PASSWORD_FILE=/etc/finunity-restic.pass
   AWS_ACCESS_KEY_ID=
   AWS_SECRET_ACCESS_KEY=
   HC_PING_URL=
   ```

   `/etc/finunity-restic.pass` 是 restic 仓库的加密口令（权限 600）。**这个口令丢了，异地备份就永远解不开**：另存一份到密码管理器。
4. 初始化仓库（只做一次）：`set -a; . /etc/finunity-offsite.env; set +a; restic init`。
5. 在 healthchecks.io（或同类服务）建两个检查：备份（周期 1 天、宽限 6 小时）和演练（周期 31 天、宽限 2 天），各自的 ping 地址分别写入 `HC_PING_URL`；演练的地址放进 `/etc/finunity-drill.env`（它会覆盖备份的那个）。
6. 把 `ops/*.service`、`ops/*.timer` 复制到 `/etc/systemd/system/`，**按服务器实际情况修改 `User=`、`WorkingDirectory=` 和路径**，然后 `systemctl daemon-reload && systemctl enable --now finunity-offsite-backup.timer finunity-restore-drill.timer`。
7. 手动各跑一次并看结果：

   ```bash
   sudo systemctl start finunity-offsite-backup.service && journalctl -u finunity-offsite-backup.service -n 20
   sudo systemctl start finunity-restore-drill.service  && journalctl -u finunity-restore-drill.service -n 20
   ```

## 与现有生产脚本的关系

`PRODUCTION_2026-09-29.md` 记录的 `finunity-public-backup.timer`（每日一致备份加 14 天轮换，脚本只在生产机）保持不动。本方案另起一套文件名 `finunity-<UTC时间>.db`，并且只清理自己命名的过期文件，不会碰现有备份。建议头一周并行运行，确认异地备份和演练都正常后，再决定旧定时器是否退役。

## 灾难恢复（服务器丢了）

1. 新服务器上安装 restic 和 Docker，恢复 `/etc/finunity-offsite.env` 与口令文件（口令来自你的密码管理器）。
2. `set -a; . /etc/finunity-offsite.env; set +a`
3. `restic restore latest --tag finunity --host finunity --target /tmp/restore`，在其中找到最新的 `finunity-*.db`。
4. `python3 deploy/database.py audit --database <该文件>` 确认 `integrity` 为 `ok`、`foreign_key_errors` 为 0。
5. 放到数据卷 `data/finunity.db`，按 `deploy/README.md` 启动应用；`SESSION_SECRET` 需要重新生成（旧会话作废），详见 `deploy/README.md` 的回滚与会话说明。
6. 记录这次花了多久：这就是第一个真实的整机 RTO。

## 已验证与未验证

已验证（本机，`tests/test_offsite_backup.py` 17 项）：在真实 SQLite 数据库上走通“备份 → 审计 → 推送 → 恢复演练 → 记录 → 清理”；损坏的本机备份不会被推送；异地副本损坏、过期（超过 RPO）、仓库为空时演练失败并上报；心跳失败不影响任务；本机和目录后端只清理自己命名的过期文件，其他文件不动；保留期固定为 14 天；restic 命令行的构造与输出解析（用假的 `restic` 校验，包括快照乱序时取最新的、`--keep-within 14d`）。

**未验证**：真实的 `restic`、R2 或 B2、systemd 单元在生产机上的行为、与现有备份定时器并行运行、整机重建的耗时。这些需要你在服务器上执行第 7 步之后才算数。
