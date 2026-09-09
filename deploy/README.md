# Docker 部署（开发机）

当前应用版本：**1.0.7**

将 API / mqtt-worker / Web 打包为容器，接入已有的 `asset-management-backend` 网络（Postgres / Redis / Mosquitto，见 `infra/docker-compose.yml`）。业务数据在 Docker named volumes 中持久化。

目标主机不要写进仓库：设置 `REMOTE_HOST`，或把主机名/IP 写进 `deploy/.remote-host`（已 gitignore）。

## 一键部署

```bash
export REMOTE_HOST=192.0.2.10          # 或写入 deploy/.remote-host（已 gitignore）
# SSH 用密钥登录；不要把口令写进仓库
bash deploy/deploy-remote.sh
```

访问：

- Web: `http://<REMOTE_HOST>/`
- API health: `http://<REMOTE_HOST>:8080/actuator/health`

开发环境超级管理员由 `dev` 引导创建。账号看启动日志，密码用 `DEV_ROOT_PASSWORD`，不要把口令写进 Git。

## 说明

- 后端 jar 在本机 Maven 构建；前端使用已有 `web/dist`（无本机 Node 时）。
- 基础镜像默认走 DaoCloud：`docker.m.daocloud.io/library/...`
- Worker 管理端口仅绑定远程 `127.0.0.1:8081`

## 客户离线安装包

给全新 Ubuntu 打 tar.gz（含镜像和 Docker .deb）。产物不进 Git，发布到 GitHub Releases：

```bash
bash deploy/offline/pack-offline.sh
```

说明见 `deploy/offline/README.md`。客户解压后只改 `config.env`，然后 `sudo ./install.sh`。
