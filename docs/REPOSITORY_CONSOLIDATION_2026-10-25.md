# FinUnity 单仓库核对与旧仓库归档

验收日期：2026-10-25。环境：Windows、GitHub 已登录账号 loonslo、Vercel 已登录控制台、Node 22.22.3、Server 现有 Python 3.12.13 环境。

## 实际结果与未完成项

- 唯一维护入口为 `https://github.com/loonslo/FinUnity`，默认分支 `master`。Android 保持仓库根目录，后端在 `server/`，Web 在 `web/`，旧原型在 `planner-prototype-reference/`，仍分别构建和部署。
- 工作区根目录核对仅有一个 FinUnity 工作副本。旧 sibling 工作副本已在此前操作中移入回收站，本次没有重复搬迁，也没有清空回收站。
- `loonslo/FinUnityWeb`、`loonslo/FinUnityServer`、`loonslo/FinUnityPrototype` 已通过 GitHub API 设置为 `archived: true`，归档后再次核对成功；FinUnity 自身未归档。
- 用户要求删除三个旧仓库，但包含该删除操作的命令在执行前被自动审批以 `blocked by policy` 拒绝。因此三个旧仓库**仍存在，仅已归档**；不能把这次处理称为已删除。删除仍需用户在 GitHub 处理。
- 没有将三个私有旧仓库的历史对象推送到公开 FinUnity。完整历史保存在工作区外的本机备份；不要将这些备份或旧仓库历史直接导入公开仓库。

## 源码与备份核对

已为三个旧仓库创建包含全部 refs 的完整 Git bundle，`git bundle verify` 均通过；同时保存仓库元数据、分支、标签、Issue、PR、Release、部署记录和逐文件对比清单。备份清单和归档结果保存在同一备份目录，不作为公开源码提交。三个旧仓库均只有一个 `main` 分支，没有 Issue、PR 或 Release；备份后再次核对远程 HEAD，未出现新提交。

| 旧仓库 | 备份 HEAD | 维护模块 |
|---|---|---|
| FinUnityWeb | `435a60bc27649780a57586b58cc8ca501ccfdaae` | `web/` |
| FinUnityServer | `e69f09f18f85cb6516093e739282adba73d1284a` | `server/` |
| FinUnityPrototype | `9bee2b2404a4675afa31bfa214ac1d3490a8de61` | `planner-prototype-reference/`，仅参考 |

逐文件比较旧远程默认分支与当前 monorepo 模块后，未发现缺失的业务源码。Web 差异包括已经更新的业务实现、历史文档和已由跳转替代的旧 product 页面；其历史文档在本机被忽略，旧远程原件由完整备份保留。Server 不再保留运行数据目录的 `data/.gitkeep`。原型的 `frontend/vite.config.ts` 是先前明确清理的配置，未在本次恢复。其余差异沿用已有 monorepo 导入与后续清理，没有以旧远程文件覆盖当前实现。

## 托管边界

Vercel `finunity-web` 的源码连接已从 `loonslo/FinUnityWeb` 切换为 `loonslo/FinUnity`；Root Directory 已保存为 `web`，生产分支核对为 `master`，并启用根目录及依赖没有变化时跳过部署。保留现有站点、域名、环境变量和生产部署，不手工触发新版本发布。整理记录推送期间临时解除源码连接，推送后再恢复，避免仅为整理仓库而发布尚未单独验收的业务版本。旧域 `finunity-web-baikai` 的独立托管边界沿用原记录，不将其当成待删除的源码仓库。

Server 仍沿用既有独立镜像发布方式；本次未登录生产服务器、迁移数据或更新服务镜像。源码入口归并不表示线上业务版本已升级；双域同版本发布及 Android 设备验收仍须单独执行。

## 本次验证

- Server：`uv run --extra dev python -m pytest -q --basetemp=.pytest-consolidation-20261025`，140 项通过；`uv run --extra dev python -m ruff check .` 通过。存在一项已有 Starlette/httpx 弃用警告。测试临时目录随后移入回收站。
- Web：`npm test`，44 项通过；`npm run lint` 和 `npm run build` 均通过。使用现有依赖环境，本次未重新执行 `npm ci` 或升级依赖。
- 公网只读核对：`finunity.halfopen.dev`、`finunity.baikai.site` 两个首页，两域 Web `/health/ready`，以及 `finunity-api.halfopen.dev`、`finunity-api.baikai.site` 的 `/health/ready`，共 6 项均为 HTTP 200；首页与 Web 健康检查响应均带 CSP。
- 未运行 Android 构建、设备迁移或页面验收，未运行历史原型，未验证真实账号写入、OCR、行情或完整交易流程。此前 Android 154 项测试及构建结果仍按 2026-10-24 历史验收记录引用，不是本次复测。

前次源码导入与验收范围见 [monorepo 迁移记录](MONOREPO_MIGRATION_2026-10-24.md)。本记录补充 2026-10-25 的实际状态，不改写旧记录的历史事实。
