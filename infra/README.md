# 本地基础设施

这里提供资产管理平台首期本地开发依赖：

- PostgreSQL 18 + PostGIS 3.6
- Redis 8（AOF 持久化）
- Eclipse Mosquitto 2（MQTT 及 WebSocket）

Compose 不包含应用服务，也不包含任何商业 MQTT 组件。所有端口默认只绑定到宿主机 `127.0.0.1`。

## 前置条件

- Docker Engine / Docker Desktop
- Docker Compose v2（`docker compose`）
- 本机端口 `5432`、`6379`、`1883`、`9001` 未被占用；也可以在 `.env` 中改端口

## 首次初始化

在 `infra` 目录执行：

```bash
cp .env.example .env
```

编辑 `.env`，至少替换以下三项，不能保留 `CHANGE_ME`：

```dotenv
POSTGRES_PASSWORD=<本地强密码>
REDIS_PASSWORD=<本地强密码>
MQTT_PASSWORD=<本地强密码>
```

`.env` 已被本目录的 `.gitignore` 排除，禁止提交。`MQTT_USERNAME` 可以修改，但必须与下一步生成的 Mosquitto 账号相同。

### 生成 Mosquitto 密码文件

以下命令会交互式询问两次密码，密码不会作为命令行参数出现。输入的密码必须与 `.env` 中 `MQTT_PASSWORD` 相同。示例用户名与 `.env.example` 的默认值一致；如果修改过，请同步替换命令末尾用户名。

```bash
docker run --rm -it \
  --user "$(id -u):$(id -g)" \
  -v "$PWD/mosquitto/secrets:/mosquitto/secrets" \
  eclipse-mosquitto:2 \
  mosquitto_passwd -c /mosquitto/secrets/password_file asset_platform_local
```

生成的 `mosquitto/secrets/password_file` 仅含加盐哈希，但仍属于敏感文件，已被 Git 忽略。不要提交、截图或通过聊天发送。

如果 `.env` 覆盖了 `MOSQUITTO_IMAGE`，生成密码文件时也应把命令中的镜像名改成同一个受信镜像。

## 启动与验证

```bash
docker compose config
docker compose up -d
docker compose ps
```

三个容器最终都应显示为 `healthy`。查看服务日志：

```bash
docker compose logs -f postgres redis mosquitto
```

停止服务但保留数据：

```bash
docker compose down
```

不要随意执行 `docker compose down -v`；`-v` 会永久删除本项目的数据库、Redis 和 MQTT 持久化卷。

## 默认连接

| 服务 | 宿主机地址 | 容器网络地址 |
|---|---|---|
| PostgreSQL/PostGIS | `127.0.0.1:${POSTGRES_PORT}` | `postgres:5432` |
| Redis | `127.0.0.1:${REDIS_PORT}` | `redis:6379` |
| MQTT | `mqtt://127.0.0.1:${MQTT_PORT}` | `mqtt://mosquitto:1883` |
| MQTT WebSocket | `ws://127.0.0.1:${MQTT_WS_PORT}` | `ws://mosquitto:9001` |

应用容器加入 `${COMPOSE_PROJECT_NAME}-backend` 网络后，应使用表中的容器网络地址，不能使用 `localhost`。

## MQTT ACL

初始 ACL 有意保持最小权限：

- 账号只能向 `_health/<自己的用户名>` 发布健康检查消息；
- 账号只能读写 `sandbox/<自己的用户名>/#`；
- 不允许匿名连接，也没有全主题通配权限。

设备 Topic、平台订阅 Topic、下行指令和 ACK Topic 确认后，再在 `mosquitto/config/acl` 中按账号职责增加精确规则。修改配置或密码文件后重启 Mosquitto：

```bash
docker compose restart mosquitto
```

## 数据库初始化边界

`postgres/init/00_extensions.sql` 只负责启用 PostGIS。业务表、索引、RLS、初始 Root 账号等全部交给后端 Flyway 迁移管理，避免数据库初始化脚本与应用版本漂移。

初始化目录仅在 PostgreSQL 数据卷第一次创建时执行。已有数据卷上修改该 SQL 不会自动重跑，应通过新的 Flyway 迁移完成数据库变更。

## 生产环境说明

本 Compose 仅用于本地开发，不可直接作为生产部署：

- 数据库、Redis 和 MQTT 需要 TLS、备份、监控、资源限制和独立密钥管理；
- 镜像应锁定到经过验证的精确版本或 digest；
- Mosquitto 适合单节点开发或小规模部署，不等同于高可用集群；
- 若生产规模需要 MQTT 集群，可另行评估开源 VerneMQ，并单独设计节点发现、持久化、负载均衡、TLS、ACL 和故障演练。VerneMQ 不混入本地首期 Compose，以免引入未经确认的生产假设。
