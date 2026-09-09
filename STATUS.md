# 项目进度报告 — Asset Management

> 最后更新：2026-08-11

## 一、已完成

### 1. 基础设施（infra/）
- [x] `docker-compose.yml`：PostgreSQL 18 + PostGIS 3.6、Redis 8、Mosquitto 2
- [x] 环境变量模板 `.env.example`
- [x] 数据库 Flyway 迁移脚本：
  - `V1__initial_platform_schema.sql` — 基础表结构
  - `V2__saas_tenancy_and_auth.sql` — 租户/项目/用户/角色/权限
  - `V3__mqtt_configuration_lifecycle.sql` — MQTT 连接与主题路由

### 2. 后端平台核心（platform-core/，44 个 Java 文件）

| 模块 | 状态 | 说明 |
|---|---|---|
| `iam`（用户/角色/权限） | ✅ 领域模型 + 仓储 | User、Role、Permission、RoleScope、UserStatus |
| `tenant`（租户） | ✅ 领域模型 + 仓储 | Tenant、TenantMember、TenantStatus |
| `project`（项目） | ✅ 领域模型 + 仓储 | Project、ProjectMember、ProjectStatus |
| `mqtt`（MQTT 配置） | ✅ 领域模型 + 仓储 | MqttConnection、MqttTopicRoute、各枚举类型 |
| `audit`（审计日志） | ✅ 领域模型 + 仓储 | AuditLog |
| `shared` | ✅ 基础设施 | BaseEntity、ApiResponse、BusinessException、SecretCipher |
| `mqtt.port.MqttGateway` | ✅ 端口接口 | Worker 与 API 的 MQTT 交互契约 |

### 3. 后端平台 API（platform-api/，53 个 Java 文件）

**认证与授权（完整）**
- [x] `AuthController` — 登录 `/api/v1/auth/login`、刷新 `/api/v1/auth/refresh`、登出 `/api/v1/auth/logout`、获取当前用户 `/api/v1/auth/me`
- [x] `AuthService` + `AccountAuthenticationService` — 密码校验、会话管理
- [x] `AccessTokenService` — JWT 生成与验证
- [x] `RedisRefreshSessionStore` — Refresh Token 持久化到 Redis
- [x] `RefreshCookieService` — HttpOnly Secure 刷新 Cookie
- [x] `LoginRateLimiter` — 登录频率限制
- [x] `SecurityConfig` — Spring Security 配置
- [x] `AccessTokenAuthenticationFilter` — JWT 认证过滤器
- [x] `TenantAuthorizationService` — 租户级权限校验
- [x] `ProjectAuthorizationService` — 项目级权限校验
- [x] `RlsContextExecutor` — 行级安全（PostgreSQL RLS）
- [x] `CurrentUserProvider` / `CurrentUserPrincipal` — 当前用户上下文
- [x] `AuthBrowserRequestGuardFilter` — 浏览器请求守卫
- [x] `AuthAuditService` — 关键认证操作审计
- [x] `TraceIdFilter` — 全链路请求 ID
- [x] `GlobalExceptionHandler` / `ApiEnvelopeAdvice` — 统一错误响应

**MQTT 管理（领域服务完整，REST 层缺失）**
- [x] `MqttConnectionManagementService` — 连接 CRUD、主备切换、端点策略
- [x] `MqttSecretService` — 密码/证书加解密
- [x] `MqttTopicValidator` — Topic 格式校验
- [x] `MqttConnectionProbe` + `HiveMqConnectionProbe` — 连接测试（HiveMQ 适配器）
- [x] `MqttAuditRecorder` — MQTT 操作审计
- [x] `AesGcmSecretCipher` + `SecretCipherProperties` — AES-GCM 加解密
- [x] `PemTlsMaterial` — TLS 证书管理
- [x] `MqttEndpointPolicy` — 端点接入策略
- [x] 全部 MQTT API 请求/响应 DTO
- [ ] **❌ 缺少 `MqttController`** — MQTT 配置的 HTTP REST 端点尚未实现

**其他业务域（缺失）**
- [ ] ❌ 无 Dashboard / 总览 Controller
- [ ] ❌ 无 Project 管理 Controller
- [ ] ❌ 无 Asset 资产 Controller
- [ ] ❌ 无 Device 设备 Controller
- [ ] ❌ 无 Location/Tracking 定位 Controller
- [ ] ❌ 无 Alert 告警 Controller
- [ ] ❌ 无 Analytics 分析 Controller

### 4. MQTT Worker（mqtt-worker/，3 个 Java 文件）

- [x] `MqttWorkerApplication` — 独立启动入口
- [x] `MqttWorkerConfiguration` — 启用开关、健康检查
- [x] `MqttWorkerProperties` — 配置属性
- [ ] **❌ 缺少真实 MQTT 接入实现**（无 Broker 连接、无消息处理）

### 5. 前端 web/（95 个 Vue 文件）

**已完成页面**
- [x] 登录 `/auth/login`
- [x] 注册 `/auth/register`
- [x] 忘记密码 `/auth/forget-password`
- [x] 系统用户管理 `/system/user`（含搜索、新增、编辑）
- [x] 系统角色管理 `/system/role`（含权限分配）
- [x] 系统菜单管理 `/system/menu`
- [x] 用户中心 `/system/user-center`
- [x] 资产管理总览 `/overview`（项目选择、仪表板骨架）
- [x] MQTT 管理页 `/product/mqtt`（1302 行，含连接管理、主题路由、消息监听三个 Tab）
  - [x] 主备连接卡片展示
  - [x] 连接创建/编辑抽屉（`MqttConnectionDrawer.vue`）
  - [x] 主题路由创建/编辑抽屉（`MqttTopicRouteDrawer.vue`）
  - [x] 连接测试功能
  - [x] 消息监听 Tab（含过滤、分页）
- [x] API 接口层 `api/asset-platform.ts`（完整的 MQTT + Dashboard API 定义）
- [x] 路由框架 `router/modules/asset-platform.ts`（完整路由结构，含权限码）

**占位页面（未实现）**
- [ ] ❌ 项目列表 `/projects/project-list`
- [ ] ❌ 项目任务 `/projects/tasks`
- [ ] ❌ 项目成员 `/projects/members`
- [ ] ❌ 项目文档 `/projects/documents`
- [ ] ❌ 资产列表 `/assets/list`
- [ ] ❌ 资产类型 `/assets/types`
- [ ] ❌ 资产状态 `/assets/status`
- [ ] ❌ 资产盘点 `/assets/inventory`
- [ ] ❌ 信标管理 `/devices/beacons`
- [ ] ❌ 网关管理 `/devices/gateways`
- [ ] ❌ 地图管理 `/devices/maps`
- [ ] ❌ 区域管理 `/devices/zones`
- [ ] ❌ 实时定位 `/tracking/live`
- [ ] ❌ 轨迹历史 `/tracking/history`
- [ ] ❌ 点名 `/tracking/roll-call`
- [ ] ❌ 告警事件 `/alerts/events`
- [ ] ❌ 告警规则 `/alerts/rules`
- [ ] ❌ 数据分析 `/analytics/assets`
- [ ] ❌ 每日汇总 `/analytics/daily-summary`
- [ ] ❌ 平台用户 `/platform/users`
- [ ] ❌ 平台角色 `/platform/roles`
- [ ] ❌ 平台权限 `/platform/permissions`
- [ ] ❌ 审计日志 `/platform/audit`

---

## 二、未完成

### 后端（优先级排序）

1. **`MqttController`（最紧急）** — 将 `MqttConnectionManagementService` 暴露为 REST 端点
   - `GET/POST /api/v1/tenants/{tid}/projects/{pid}/mqtt/connections`
   - `PUT /api/v1/tenants/{tid}/projects/{pid}/mqtt/connections/{id}`
   - `POST /api/v1/tenants/{tid}/projects/{pid}/mqtt/connections/{id}/test`
   - `GET /api/v1/tenants/{tid}/projects/{pid}/mqtt/topic-routes`
   - `POST/PUT /api/v1/tenants/{tid}/projects/{pid}/mqtt/topic-routes`
   - `GET /api/v1/tenants/{tid}/projects/{pid}/mqtt/messages`

2. **`DashboardController`** — 实现 `/dashboard/summary` 接口

3. **`ProjectController`** — 项目 CRUD + 成员管理

4. **`AssetController` / `DeviceController` / `AlertController` / `AnalyticsController`** — 各业务域

5. **`MqttWorker` 真实接入** — 实现 Broker 连接、消息订阅、协议解析

### 前端（按路由顺序）

1. 项目列表页（`/projects/project-list`）
2. 资产列表页（`/assets/list`）
3. 设备管理页（信标/网关）
4. 定位与告警页
5. 数据分析页

---

## 三、架构要点

- **多租户多项目**：所有业务接口路径含 `tenants/{tid}/projects/{pid}`，权限由 `TenantAuthorizationService` + `ProjectAuthorizationService` 双重校验
- **MQTT 配置与设备接入分离**：API 管理配置，Worker 负责实时数据流
- **密码/证书加密**：`AesGcmSecretCipher`，密钥由 `SecretCipherProperties` 注入
- **审计**：关键操作写入 `audit_logs` 表
- **前端权限**：路由级 `permissions` + 按钮级 `v-auth` 指令
