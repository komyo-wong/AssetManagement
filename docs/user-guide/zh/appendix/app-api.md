---
description: 现场 App 调用同一套 /api/v1 的登录用法与接口清单
---

# App 接口

{% hint style="info" %}
账号、密码以安装结束界面或服务器 `config.env` 为准。说明书不列出默认账号。现场人员请使用业务角色，不要把超级管理员当日常 App 账号。
{% endhint %}

## 使用方法

1. `POST /api/v1/auth/login`，body 带 `"client": "mobile"`。
2. 从响应 `data` 取出 `accessToken`、`refreshToken`，存到系统钥匙串（不要写日志、不要进普通文件）。
3. 业务请求头：`Authorization: Bearer <accessToken>`。
4. 调用 `GET /api/v1/auth/me`，从 `tenantMemberships[0]` 取 `tenantId`，从其 `projects[0]` 取 `projectId`。后面业务路径都要带这两个 UUID。
5. accessToken 约 2 小时过期后，用 `refreshToken` 调刷新接口，换新的一对令牌。
6. 退出时把当前 `refreshToken` 交给登出接口。

网页登录 **不要** 传 `client`。不传时行为与现在完全一样：refresh 只在 Cookie 里，JSON 里没有 `refreshToken`。

App 不要带网页的 `Origin` 头。一般也不要走浏览器跨站那套 Cookie。

成功响应外层为：

```json
{ "success": true, "code": "OK", "data": { }, "traceId": "...", "timestamp": "..." }
```

下文表格里的路径都在 `data` 里返回业务字段。

## 登录

### 登录

`POST /api/v1/auth/login`

```json
{ "account": "账号或邮箱", "password": "密码", "client": "mobile" }
```

`data`：

| 字段 | 说明 |
| --- | --- |
| `accessToken` | 放请求头，约 2 小时 |
| `expiresInSeconds` | 过期秒数 |
| `refreshToken` | **仅** `client=mobile` 时出现，约 30 天 |

### 刷新

`POST /api/v1/auth/refresh`

```json
{ "refreshToken": "上一次拿到的 refreshToken" }
```

返回新的 `accessToken` 和 `refreshToken`（旧 refresh 立即作废，必须保存新的）。

### 登出

`POST /api/v1/auth/logout`

```json
{ "refreshToken": "当前 refreshToken" }
```

### 当前用户

`GET /api/v1/auth/me`（需 Bearer）

用返回的权限码控制 App 按钮。无权限的接口会 403。

## 建议给 App 的接口

路径前缀 `{base}` = `/api/v1/tenants/{tenantId}/projects/{projectId}`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/public/branding` | 登录页品牌（可无 Token） |
| GET | `/api/v1/auth/me` | 用户、权限、租户/项目 ID |
| PUT | `/api/v1/me/profile` | 改显示名、邮箱 |
| PUT | `/api/v1/me/avatar` | 改头像 |
| PUT | `/api/v1/me/password` | 改自己的密码 |
| GET | `{base}/dashboard/summary` | 总览数字 |
| GET | `{base}/asset-types` | 资产类型 |
| GET | `{base}/assets` | 资产列表 |
| POST | `{base}/assets/{id}/buzzer` | 寻物蜂鸣 |
| GET | `{base}/beacons` | 信标列表 |
| GET | `{base}/beacons/{id}/scans` | 扫描流水 |
| GET | `{base}/beacons/{id}/presence-events` | 在线事件 |
| GET | `{base}/gateways` | 网关列表（不含开户密码） |
| GET | `{base}/maps` | 地图 |
| GET | `{base}/zones` | 区域 |
| GET | `{base}/tracking/live` | 实时位置（可定时轮询） |
| GET | `{base}/tracking/history` | 历史轨迹 |
| GET | `{base}/alerts` | 告警事件 |
| POST | `{base}/alerts/{id}/acknowledge` | 确认告警 |
| POST | `{base}/alerts/{id}/resolve` | 关闭告警 |
| GET | `{base}/inventory-sessions` | 盘点列表 |
| GET | `{base}/inventory-sessions/{id}` | 盘点详情 |
| POST | `{base}/inventory-sessions` | 开始盘点 |
| POST | `{base}/inventory-sessions/{id}/close` | 结束盘点 |

其它写操作（新建资产、导入信标等）仍可用同一套 API，是否开放由该账号的角色权限决定。

## App 不能调用

即使超级管理员用 App 登录，下列路径也会 403：

| 路径 | 原因 |
| --- | --- |
| `/api/v1/platform/ops/**` | 备份、恢复、重启容器 |
| `/api/v1/platform/mail-settings` | 改 SMTP |
| `{base}/gateways/{id}/provision` | 含 MQTT 账号密码 |
| `/actuator/info`、`/actuator/prometheus` | 运维指标 |
| `/v3/api-docs`、`/swagger-ui` | 接口文档页 |

这些请继续用网页。App **不要连接** Worker（8081），也不要连接 MQTT；现场数据仍由网关上报。

## 权限

接口仍按平台角色鉴权。没有 `asset:read` 就看不到资产列表。请在网页 **角色管理** 给现场人员勾选需要的模块。
