/**
 * API 接口类型定义模块
 *
 * 提供所有后端接口的类型定义
 *
 * ## 主要功能
 *
 * - 通用类型（分页参数、响应结构等）
 * - 认证类型（登录、用户信息等）
 * - 系统管理类型（用户、角色等）
 * - 全局命名空间声明
 *
 * ## 使用场景
 *
 * - API 请求参数类型约束
 * - API 响应数据类型定义
 * - 接口文档类型同步
 *
 * ## 注意事项
 *
 * - 在 .vue 文件使用需要在 eslint.config.mjs 中配置 globals: { Api: 'readonly' }
 * - 使用全局命名空间，无需导入即可使用
 *
 * ## 使用方式
 *
 * ```typescript
 * const params: Api.Auth.LoginParams = { account: '<account>', password: '<password>' }
 * const response: Api.Auth.UserInfo = await fetchUserInfo()
 * ```
 *
 * @module types/api/api
 * @author Art Design Pro Team
 */

declare namespace Api {
  /** 通用类型 */
  namespace Common {
    /** 分页参数 */
    interface PaginationParams {
      /** 当前页码 */
      current: number
      /** 每页条数 */
      size: number
      /** 总条数 */
      total: number
    }

    /** 通用搜索参数 */
    type CommonSearchParams = Pick<PaginationParams, 'current' | 'size'>

    /** 分页响应基础结构 */
    interface PaginatedResponse<T = any> {
      records: T[]
      current: number
      size: number
      total: number
    }

    /** 启用状态 */
    type EnableStatus = '1' | '2'
  }

  /** 认证类型 */
  namespace Auth {
    type MembershipStatus = 'INVITED' | 'ACTIVE' | 'SUSPENDED' | 'REMOVED'

    interface ProjectMembership {
      projectId: string
      projectCode: string
      projectName: string
      status: MembershipStatus
      roles: string[]
      permissions: string[]
    }

    interface TenantMembership {
      tenantId: string
      tenantCode: string
      tenantName: string
      status: MembershipStatus
      roles: string[]
      permissions: string[]
      projects: ProjectMembership[]
    }

    /** 登录参数 */
    interface LoginParams {
      account: string
      password: string
    }

    /** 登录响应 */
    interface LoginResponse {
      accessToken: string
      expiresInSeconds: number
    }

    /** 用户信息 */
    interface UserInfo {
      buttons: string[]
      roles: string[]
      userId: string
      userName: string
      email: string
      displayName?: string
      preferredLocale?: string
      rootAccount: boolean
      platformRoles: string[]
      platformPermissions: string[]
      tenantMemberships: TenantMembership[]
      avatar?: string | null
      alertPopupEnabled?: boolean
      alertSoundEnabled?: boolean
      hasCustomAlertSound?: boolean
      alertSoundFileName?: string | null
      geotagEnabled?: boolean
      license?: {
        notify?: boolean
        loginCopyright?: boolean
        eink?: boolean
        buzz?: boolean
        maxBeacons?: number | null
        maxGateways?: number | null
      }
    }
  }

  /** 系统管理类型 */
  namespace SystemManage {
    /** 用户列表 */
    type UserList = Api.Common.PaginatedResponse<UserListItem>

    /** 用户列表项 */
    interface UserListItem {
      id: number
      avatar: string
      status: string
      userName: string
      userGender: string
      nickName: string
      userPhone: string
      userEmail: string
      userRoles: string[]
      createBy: string
      createTime: string
      updateBy: string
      updateTime: string
    }

    /** 用户搜索参数 */
    type UserSearchParams = Partial<
      Pick<UserListItem, 'id' | 'userName' | 'userGender' | 'userPhone' | 'userEmail' | 'status'> &
        Api.Common.CommonSearchParams
    >

    /** 角色列表 */
    type RoleList = Api.Common.PaginatedResponse<RoleListItem>

    /** 角色列表项 */
    interface RoleListItem {
      roleId: number
      roleName: string
      roleCode: string
      description: string
      enabled: boolean
      createTime: string
    }

    /** 角色搜索参数 */
    type RoleSearchParams = Partial<
      Pick<RoleListItem, 'roleId' | 'roleName' | 'roleCode' | 'description' | 'enabled'> &
        Api.Common.CommonSearchParams
    >
  }

  /** 资产管理平台业务类型 */
  namespace AssetPlatform {

    interface NamedResource {
      id: string
      version: number
      tenantId: string
      projectId: string
      code: string
      name: string
      status: string
      createdAt: string
      updatedAt: string
      fields: Record<string, unknown>
    }

    interface ResourceUpsert {
      code?: string
      name: string
      description?: string
      status?: string
      fields?: Record<string, unknown>
    }

    interface ProjectItem {
      id: string
      version: number
      tenantId: string
      code: string
      name: string
      description?: string | null
      status: string
      defaultLocale: string
      archivedAt?: string | null
      createdAt: string
      updatedAt: string
    }

    interface ProjectUpsert {
      code: string
      name: string
      description?: string
      defaultLocale?: string
    }

    interface ProjectMemberItem {
      id: string
      userId: string
      username: string
      displayName?: string | null
      status: string
      roles: string[]
      joinedAt?: string | null
    }

    interface TenantMemberItem {
      id: string
      userId: string
      username: string
      displayName?: string | null
      email?: string | null
      status: string
      roleCodes: string[]
      joinedAt?: string | null
    }

    interface TenantMemberInvite {
      username: string
      email?: string
      password?: string
      displayName?: string
      roleCodes?: string[]
    }

    interface TenantRoleItem {
      id: string
      code: string
      name: string
      permissionCodes: string[]
      systemRole?: boolean
      protectedRole?: boolean
    }

    interface TenantRoleUpsert {
      code?: string
      name: string
      permissionCodes?: string[]
    }

    interface ProjectRoleItem {
      id: string
      code: string
      name: string
      permissionCodes: string[]
      systemRole?: boolean
      protectedRole?: boolean
    }

    interface ProjectRoleUpsert {
      code?: string
      name: string
      permissionCodes?: string[]
    }

    interface PermissionItem {
      code: string
      name: string
      module?: string | null
      category?: string | null
      description?: string | null
    }

    interface MqttCommandItem {
      id: string
      connectionId: string
      topic: string
      payload: string
      qos: number
      retained: boolean
      status: string
      errorMessage?: string | null
      createdAt: string
      sentAt?: string | null
    }

    interface ProjectScope {
      tenantId: string
      projectId: string
    }

    type DataAvailability = 'unavailable' | 'stale' | 'available'

    interface DashboardSummary {
      projectId: string
      assetTotal: number | null
      onlineGatewayTotal: number | null
      activeAlertTotal: number | null
      rollCallCoverage: number | null
      dataAvailability: DataAvailability
      generatedAt: string | null
      readiness?: DashboardReadinessItem[] | null
    }

    interface DashboardReadinessItem {
      key: string
      status: 'UP' | 'DOWN' | 'NOT_CONFIGURED' | string
      detail?: string | null
    }

    type MqttEnvironment = 'development' | 'test' | 'staging' | 'production'
    type MqttConnectionRole = 'primary' | 'standby'
    type MqttVersion = '3.1.1' | '5.0'
    type MqttConnectionStatus =
      | 'not_configured'
      | 'connecting'
      | 'connected'
      | 'disconnected'
      | 'error'
      | 'disabled'

    /** 连接返回模型不包含密码、私钥或完整凭据。 */
    interface MqttConnection {
      id: string
      version: number
      tenantId: string
      projectId: string
      name: string
      environment: MqttEnvironment
      role: MqttConnectionRole
      brokerUri: string
      mqttVersion: MqttVersion
      clientId: string
      usernameConfigured: boolean
      credentialsConfigured: boolean
      tlsEnabled: boolean
      cleanStart: boolean
      keepAliveSeconds: number
      enabled: boolean
      status: MqttConnectionStatus
      lastConnectedAt: string | null
      lastError: string | null
      createdAt: string
      updatedAt: string
    }

    /** 密码只允许写入；服务端应加密保存并在响应中永久省略。 */
    interface MqttConnectionInput {
      name: string
      environment: MqttEnvironment
      role: MqttConnectionRole
      brokerUri: string
      mqttVersion: MqttVersion
      clientId: string
      username?: string
      password?: string
      tlsEnabled: boolean
      cleanStart: boolean
      keepAliveSeconds: number
      enabled: boolean
      expectedVersion?: number
    }

    interface MqttConnectionTestResult {
      success: boolean
      latencyMs: number | null
      testedAt: string
      errorCode?: string
      message?: string
    }

    type MqttMessageType = 'asset_status' | 'location' | 'alert' | 'heartbeat' | 'custom'
    type MqttParseStatus = 'pending' | 'parsed' | 'rejected' | 'failed'
    type MqttRouteDirection = 'uplink' | 'downlink' | 'acknowledgement'

    interface MqttTopicRoute {
      id: string
      version: number
      tenantId: string
      projectId: string
      connectionId: string
      name: string
      direction: MqttRouteDirection
      topicPattern: string
      messageType: string
      parserKey: string
      qos: 0 | 1 | 2
      retained: boolean
      enabled: boolean
      createdAt: string
      updatedAt: string
    }

    interface MqttTopicRouteInput {
      connectionId: string
      name: string
      direction: MqttRouteDirection
      topicPattern: string
      messageType: string
      parserKey: string
      qos: 0 | 1 | 2
      retained: boolean
      enabled: boolean
      expectedVersion?: number
    }

    interface MqttMessageLog {
      id: string
      projectId: string
      connectionName: string
      gatewayName?: string
      gatewayMac?: string
      topic: string
      qos: 0 | 1 | 2
      retained: boolean
      payloadPreview: string
      payload?: string
      parseStatus: MqttParseStatus
      receivedAt: string
    }

    interface MqttMessageQuery extends Api.Common.CommonSearchParams {
      connectionId?: string
      gatewayId?: string
      topic?: string
      parseStatus?: MqttParseStatus
      from?: string
      to?: string
    }
  }
}
