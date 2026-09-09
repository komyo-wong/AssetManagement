---
description: systemd、start/stop、备份恢复、HTTPS 证书与域名
---

# 日常命令、备份与 HTTPS

以下命令在 **当前安装目录** 执行（`/etc/asset-management/install-root` 记录该路径）。

| 命令 | 作用 |
| --- | --- |
| `sudo ./start.sh` | 启动全部容器 |
| `sudo ./stop.sh` | 停止容器，**保留数据** |
| `sudo ./restart.sh` | 重建并启动 |
| `sudo ./status.sh` | 容器状态与 API 健康 |
| `sudo ./logs.sh` | 全部日志（可跟 `api`、`worker` 等服务名） |
| `sudo ./backup.sh` | 备份库 + 文档到 `$DATA_DIR/backups/` |
| `sudo ./restore.sh <备份包>` | 用备份覆盖当前库（先停 API/Worker/Web） |
| `sudo ./reset_admin_zh.sh` | 重置网页管理员密码（用户名不变；英文用 `reset_admin_en.sh`） |
| `sudo ./uninstall.sh` | 卸载服务，保留数据目录 |
| `sudo ./uninstall.sh --purge` | 连数据目录一起删除 |

系统服务名：`asset-management`（开机可随 Docker 拉起）。

## 数据目录

默认 `$DATA_DIR=/var/lib/asset-management`：

- `postgres/` 业务库
- `redis/`
- `mosquitto/`
- `documents/` 上传的平面图等
- `backups/` 脚本备份

超级管理员也可在网页 **平台管理 → 系统运维** 做库备份/恢复、流水清理（不删资产/信标/网关/用户）。清理在后台分批执行；新装默认每天自动清理过期流水。容器日志会轮转，避免几天把磁盘写满。也可重启 Postgres / Redis / MQTT / Web / API / Worker。

## HTTPS 与域名证书

证书 **不会自动和域名绑定**，需要三件事写成同一个域名：

1. DNS：域名解析到这台机器
2. 证书：CN / SAN 包含该域名（`fullchain.pem` + `privkey.pem`）
3. 配置：正在使用的 `config.env` 中

```bash
ENABLE_HTTPS=true
PUBLIC_HOST=assets.example.com
AUTH_ALLOWED_ORIGINS=https://assets.example.com,https://assets.example.com:443,http://127.0.0.1,http://localhost
```

`AUTH_ALLOWED_ORIGINS` 必须带上你地址栏里的 `https://域名`，否则登录会提示跨站认证请求被拒绝。改完 `sudo ./restart.sh`（或下面的 `start.sh`）。

把证书放到 **当前安装目录** 的 `certs/`，然后：

```bash
sudo ./start.sh
```

浏览器用 `https://assets.example.com/` 访问。证书域名与 `PUBLIC_HOST` 不一致时，浏览器会报证书错误。

未启用 HTTPS 时，局域网用 IP + 80 端口即可。
