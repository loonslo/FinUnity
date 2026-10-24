# 服务器部署验收

日期：2026-10-21

部署源码：私有 GitHub 仓库 `main`，提交 `b3f1ac4666ca329d1d623cfd929a37be91e5813b`。

运行方式：Docker Compose；服务端口只发布到服务器 `127.0.0.1:8011`。

## 验收结果

- Compose 镜像构建成功，包含前端生产构建和 Python 3.12 后端依赖。
- 容器状态为 `healthy`；`GET /api/health` 返回 `{"status":"ok","service":"finunity-local-prototype"}`。
- 服务器监听检查确认 `127.0.0.1:8011`，没有配置公网反向代理或 DNS。
- 部署使用独立 SQLite 数据库，没有复制本机数据库、真实账目、`.env` 或凭据。
- 本次没有重跑完整测试套件。前端生产构建成功；本次部署验证以镜像构建、容器健康检查和 HTTP 健康接口为准。

## 私下访问

在自己的电脑上建立 SSH 隧道，将 `<user>@<server>` 替换为服务器 SSH 目标：

```powershell
ssh -N -L 18011:127.0.0.1:8011 <user>@<server>
```

保持隧道终端运行，再打开 `http://127.0.0.1:18011`。原型不提供登录和多用户隔离，不得将 8011 转发到公网或配置共享反向代理。

## 服务器运维

在部署目录执行：

```bash
sudo docker compose -f deploy/compose.prototype.yaml ps
sudo docker compose -f deploy/compose.prototype.yaml logs --tail=100 planner
curl --fail http://127.0.0.1:8011/api/health
```

数据库保存在 `deploy/prototype-data/`，不纳入 Git。备份或迁移前先停止并备份该目录；不要覆盖或合并本机的 `backend/finunity-prototype.db`。
