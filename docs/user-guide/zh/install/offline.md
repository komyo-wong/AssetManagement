---
description: 在全新 Ubuntu 上解压离线包并安装，装完即可登录
---

# 离线安装

适用 **Ubuntu 22.04 / 24.04 x86_64** 全新机器。现场不需要公网、不需要编译。

{% hint style="danger" %}
机器上 **已经有本系统数据** 时，不要再跑安装脚本。请使用 [升级](upgrade.md)。
{% endhint %}

## 准备

- 服务器：x86_64，Ubuntu 22.04（jammy）或 24.04（noble）
- 内存建议 8 GB 以上（含 Postgres、Redis、API、Worker、网页）
- 防火墙放行 **80**（网页）、**1883**（网关 MQTT）；启用 HTTPS 时再放行 **443**

## 解压

```bash
tar -xzf asset-management-*-linux-amd64.tar.gz
cd asset-management-<版本>
```

{% hint style="warning" %}
请解压到 **新目录**，不要覆盖正在运行的旧安装目录。
{% endhint %}

中文说明见包内 `README_zh.txt`，英文见 `README_en.txt`。

## 执行安装

中文提示：

```bash
sudo ./install_zh.sh
```

英文提示：

```bash
sudo ./install_en.sh
```

脚本会：

1. 若尚无 `config.env`，从 `config.env.example` 复制一份
2. 询问网页管理员**用户名和密码**（也可预先写在 `config.env`；留空则**停止安装**）
3. 若无 Docker，用包内离线 `.deb` 安装（jammy / noble）
4. 导入镜像（Postgres / Redis / Mosquitto / API / Worker / Web）
5. `PUBLIC_HOST` 留空时取第一块网卡 IPv4（云主机经常是内网地址，见下一节）
6. 启动全栈，用你设定的管理员账号初始化，并写入本机 MQTT 与接入规则
7. 注册 systemd 服务 `asset-management`

首次建库可能需要 1–3 分钟。网页管理员密码不会预置，也不会随机生成。

## 装完打开

安装结束时，终端会打印网页地址、登录账号和密码，以及网关 MQTT 地址、用户名和密码。**请以该界面为准**，说明书不列出明文密码。

浏览器访问打印出的网页地址（一般为 `http://<PUBLIC_HOST>/`）。请用 **config.env 里的 `PUBLIC_HOST` 对应的那个网址** 打开，不要混用内网 IP、公网 IP 和域名。

{% hint style="info" %}
以后若需要再查账号密码，请到服务器安装目录查看 `config.env`（限制谁能登录服务器）。投产请尽快在 **个人中心** 修改管理员密码。
{% endhint %}

## 访问地址（云服务器必看）

登录只认「浏览器地址栏里的那一套」。安装时若没填 `PUBLIC_HOST`，会写成第一块网卡的 IP。云主机这块网卡常常是 **内网地址**（例如 `10.x`、`172.x`），你实际用的是 **公网 IP 或域名**，再加 HTTPS 就更容易对不上。对不上时，登录页会提示 **跨站认证请求被拒绝**，网页能开但登不进去。

装之前可先在 `config.env` 写上你准备给用户用的地址；装完再改也行。例如只用公网 IP：

```bash
PUBLIC_HOST=203.0.113.10
AUTH_ALLOWED_ORIGINS=http://203.0.113.10,http://203.0.113.10:80,http://127.0.0.1,http://localhost
```

上了域名和 HTTPS 时（与 [日常命令、备份与 HTTPS](ops.md) 一起改）：

```bash
ENABLE_HTTPS=true
PUBLIC_HOST=assets.example.com
AUTH_ALLOWED_ORIGINS=https://assets.example.com,https://assets.example.com:443,http://127.0.0.1,http://localhost
```

改完在安装目录执行 `sudo ./restart.sh`。网页和接口必须是 **同一个网址**（官方安装是 80/443 反代 `/api`）。不要网页走域名、接口另开 `:8080`。

网关 MQTT 也用同一个 `PUBLIC_HOST`（`mqtt://<PUBLIC_HOST>:1883`）。地址改了，网关里的 Broker 地址也要改。

端口与数据目录见 [端口与数据目录](../appendix/defaults.md)。

## 若安装被拒绝

提示「已有数据」时：

- 数据目录（默认 `/var/lib/asset-management`）里已有 Postgres
- 或 `/etc/asset-management/install-root` 指向旧安装

此时应 [升级](upgrade.md)，而不是再次安装。
