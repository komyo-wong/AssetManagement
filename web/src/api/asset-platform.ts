import request from '@/utils/http'

function projectEndpoint(scope: Api.AssetPlatform.ProjectScope, suffix: string) {
  const tenantId = encodeURIComponent(scope.tenantId)
  const projectId = encodeURIComponent(scope.projectId)
  return `/api/v1/tenants/${tenantId}/projects/${projectId}${suffix}`
}

function tenantEndpoint(tenantId: string, suffix: string) {
  return `/api/v1/tenants/${encodeURIComponent(tenantId)}${suffix}`
}

export function fetchDashboardSummary(scope: Api.AssetPlatform.ProjectScope) {
  return request.get<Api.AssetPlatform.DashboardSummary>({
    url: projectEndpoint(scope, '/dashboard/summary'),
    showErrorMessage: false
  })
}

export function fetchMqttConnections(scope: Api.AssetPlatform.ProjectScope) {
  return request.get<Api.AssetPlatform.MqttConnection[]>({
    url: projectEndpoint(scope, '/mqtt/connections'),
    showErrorMessage: false
  })
}

export function createMqttConnection(
  scope: Api.AssetPlatform.ProjectScope,
  input: Api.AssetPlatform.MqttConnectionInput
) {
  return request.post<Api.AssetPlatform.MqttConnection>({
    url: projectEndpoint(scope, '/mqtt/connections'),
    data: input
  })
}

export function updateMqttConnection(
  scope: Api.AssetPlatform.ProjectScope,
  connectionId: string,
  input: Api.AssetPlatform.MqttConnectionInput
) {
  return request.put<Api.AssetPlatform.MqttConnection>({
    url: projectEndpoint(scope, `/mqtt/connections/${encodeURIComponent(connectionId)}`),
    data: input
  })
}

export function testMqttConnection(scope: Api.AssetPlatform.ProjectScope, connectionId: string) {
  return request.post<Api.AssetPlatform.MqttConnectionTestResult>({
    url: projectEndpoint(scope, `/mqtt/connections/${encodeURIComponent(connectionId)}/test`)
  })
}

export function fetchMqttTopicRoutes(scope: Api.AssetPlatform.ProjectScope) {
  return request.get<Api.AssetPlatform.MqttTopicRoute[]>({
    url: projectEndpoint(scope, '/mqtt/topic-routes'),
    showErrorMessage: false
  })
}

export function createMqttTopicRoute(
  scope: Api.AssetPlatform.ProjectScope,
  input: Api.AssetPlatform.MqttTopicRouteInput
) {
  return request.post<Api.AssetPlatform.MqttTopicRoute>({
    url: projectEndpoint(scope, '/mqtt/topic-routes'),
    data: input
  })
}

export function updateMqttTopicRoute(
  scope: Api.AssetPlatform.ProjectScope,
  routeId: string,
  input: Api.AssetPlatform.MqttTopicRouteInput
) {
  return request.put<Api.AssetPlatform.MqttTopicRoute>({
    url: projectEndpoint(scope, `/mqtt/topic-routes/${encodeURIComponent(routeId)}`),
    data: input
  })
}

export function fetchMqttMessages(
  scope: Api.AssetPlatform.ProjectScope,
  params: Api.AssetPlatform.MqttMessageQuery
) {
  return request.get<Api.Common.PaginatedResponse<Api.AssetPlatform.MqttMessageLog>>({
    url: projectEndpoint(scope, '/mqtt/messages'),
    params,
    showErrorMessage: false
  })
}

export type NamedResource = Api.AssetPlatform.NamedResource
export type ResourceUpsert = Api.AssetPlatform.ResourceUpsert

export function fetchPresenceSettings(scope: Api.AssetPlatform.ProjectScope) {
  return request.get<{ gatewayOnlineTtlSeconds: number; beaconOnlineTtlSeconds: number }>({
    url: projectEndpoint(scope, '/presence-settings'),
    showErrorMessage: false
  })
}

export function updatePresenceSettings(
  scope: Api.AssetPlatform.ProjectScope,
  input: { gatewayOnlineTtlSeconds: number; beaconOnlineTtlSeconds: number }
) {
  return request.put<{ gatewayOnlineTtlSeconds: number; beaconOnlineTtlSeconds: number }>({
    url: projectEndpoint(scope, '/presence-settings'),
    data: input
  })
}

export function fetchProjects(tenantId: string) {
  return request.get<Api.AssetPlatform.ProjectItem[]>({
    url: tenantEndpoint(tenantId, '/projects'),
    showErrorMessage: false
  })
}

export function createProject(tenantId: string, input: Api.AssetPlatform.ProjectUpsert) {
  return request.post<Api.AssetPlatform.ProjectItem>({
    url: tenantEndpoint(tenantId, '/projects'),
    data: input
  })
}

export function updateProject(tenantId: string, projectId: string, input: Api.AssetPlatform.ProjectUpsert) {
  return request.put<Api.AssetPlatform.ProjectItem>({
    url: tenantEndpoint(tenantId, `/projects/${encodeURIComponent(projectId)}`),
    data: input
  })
}

export function fetchProjectMembers(scope: Api.AssetPlatform.ProjectScope) {
  return request.get<Api.AssetPlatform.ProjectMemberItem[]>({
    url: projectEndpoint(scope, '/members'),
    showErrorMessage: false
  })
}

function resourceApi(path: string) {
  return {
    list(scope: Api.AssetPlatform.ProjectScope) {
      return request.get<NamedResource[]>({
        url: projectEndpoint(scope, path),
        showErrorMessage: false
      })
    },
    create(scope: Api.AssetPlatform.ProjectScope, input: ResourceUpsert) {
      return request.post<NamedResource>({ url: projectEndpoint(scope, path), data: input })
    },
    update(scope: Api.AssetPlatform.ProjectScope, id: string, input: ResourceUpsert) {
      return request.put<NamedResource>({
        url: projectEndpoint(scope, `${path}/${encodeURIComponent(id)}`),
        data: input
      })
    },
    remove(scope: Api.AssetPlatform.ProjectScope, id: string) {
      return request.del<void>({
        url: projectEndpoint(scope, `${path}/${encodeURIComponent(id)}`)
      })
    }
  }
}

export const assetTypesApi = resourceApi('/asset-types')
export const assetsApi = {
  ...resourceApi('/assets'),
  uploadImage(scope: Api.AssetPlatform.ProjectScope, file: File) {
    const form = new FormData()
    form.append('file', file)
    return request.post<{
      imageUrl: string
      documentId: string
      fileName: string
      contentType: string
      sizeBytes: number
    }>({
      url: projectEndpoint(scope, '/assets/images'),
      data: form
    })
  }
}
export const gatewaysApi = {
  ...resourceApi('/gateways'),
  nextCode(scope: Api.AssetPlatform.ProjectScope) {
    return request.get<{ code: string }>({
      url: projectEndpoint(scope, '/gateways/next-code')
    })
  },
  provision(scope: Api.AssetPlatform.ProjectScope, id: string) {
    return request.get<Record<string, unknown>>({
      url: projectEndpoint(scope, `/gateways/${encodeURIComponent(id)}/provision`)
    })
  }
}
export const beaconsApi = {
  ...resourceApi('/beacons'),
  batchDelete(scope: Api.AssetPlatform.ProjectScope, ids: string[]) {
    return request.post<{
      requested: number
      deleted: number
      blocked: Array<Record<string, unknown>>
      missing: string[]
    }>({
      url: projectEndpoint(scope, '/beacons/batch-delete'),
      data: { ids }
    })
  },
  async downloadImportTemplate(scope: Api.AssetPlatform.ProjectScope) {
    const blob = await request.get<Blob>({
      url: projectEndpoint(scope, '/beacons/import-template'),
      responseType: 'blob',
      rawBlob: true
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'beacon-import-template.csv'
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  },
  importCsv(scope: Api.AssetPlatform.ProjectScope, file: File) {
    const form = new FormData()
    form.append('file', file)
    return request.post<{
      created: number
      updated: number
      skipped: number
      failed: number
      errors: Array<{ row: number; message: string }>
      errorTruncated?: boolean
    }>({
      url: projectEndpoint(scope, '/beacons/import'),
      data: form
    })
  }
}
export const mapsApi = {
  ...resourceApi('/maps'),
  uploadImage(scope: Api.AssetPlatform.ProjectScope, file: File) {
    const form = new FormData()
    form.append('file', file)
    return request.post<{
      imageUrl: string
      documentId: string
      fileName: string
      contentType: string
      sizeBytes: number
    }>({
      url: projectEndpoint(scope, '/maps/images'),
      data: form
    })
  }
}
export const zonesApi = resourceApi('/zones')
export const alertRulesApi = resourceApi('/alert-rules')
export const tasksApi = resourceApi('/tasks')
export const documentsApi = {
  ...resourceApi('/documents'),
  upload(scope: Api.AssetPlatform.ProjectScope, file: File, title?: string) {
    const form = new FormData()
    form.append('file', file)
    if (title?.trim()) form.append('title', title.trim())
    return request.post<NamedResource>({
      url: projectEndpoint(scope, '/documents/upload'),
      data: form
    })
  },
  remove(scope: Api.AssetPlatform.ProjectScope, id: string) {
    return request.del<void>({
      url: projectEndpoint(scope, `/documents/${encodeURIComponent(id)}`)
    })
  },
  async download(scope: Api.AssetPlatform.ProjectScope, id: string, fileName?: string) {
    const blob = await request.get<Blob>({
      url: projectEndpoint(scope, `/documents/${encodeURIComponent(id)}/content`),
      responseType: 'blob',
      rawBlob: true
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = fileName || 'download'
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  }
}

export function fetchBeaconScans(
  scope: Api.AssetPlatform.ProjectScope,
  beaconId: string,
  params: { current?: number; size?: number } = {}
) {
  return request.get<Api.Common.PaginatedResponse<NamedResource>>({
    url: projectEndpoint(scope, `/beacons/${encodeURIComponent(beaconId)}/scans`),
    params,
    showErrorMessage: false
  })
}

export function fetchBeaconPresenceEvents(
  scope: Api.AssetPlatform.ProjectScope,
  beaconId: string,
  params: { current?: number; size?: number; days?: number } = {}
) {
  return request.get<Api.Common.PaginatedResponse<NamedResource>>({
    url: projectEndpoint(scope, `/beacons/${encodeURIComponent(beaconId)}/presence-events`),
    params: { days: 30, ...params },
    showErrorMessage: false
  })
}

export function bindAssetBeacon(
  scope: Api.AssetPlatform.ProjectScope,
  assetId: string,
  beaconId: string,
  protocolType: 'AUTO' | 'FINDMY' | 'BEACON' = 'AUTO'
) {
  return request.post<NamedResource>({
    url: projectEndpoint(scope, `/assets/${encodeURIComponent(assetId)}/bind-beacon/${encodeURIComponent(beaconId)}`),
    // http 层会把 POST params 挪到 body；因此这里显式走 data，并与后端 body/query 双读对齐
    data: { protocolType }
  })
}

export function unbindAssetBeacon(scope: Api.AssetPlatform.ProjectScope, assetId: string) {
  return request.post<NamedResource>({
    url: projectEndpoint(scope, `/assets/${encodeURIComponent(assetId)}/unbind-beacon`)
  })
}

export type AssetBuzzerMode = 'SHORT' | 'LONG' | 'STOP'

export function buzzAsset(
  scope: Api.AssetPlatform.ProjectScope,
  assetId: string,
  mode: AssetBuzzerMode
) {
  return request.post<{
    mode: AssetBuzzerMode
    commandId: string
    beaconMac: string
    gatewayMac?: string | null
    gatewayName?: string | null
    targetedGateway: boolean
    autoStopMs?: number | null
  }>({
    url: projectEndpoint(scope, `/assets/${encodeURIComponent(assetId)}/buzzer`),
    data: { mode }
  })
}

export function updateBeaconEinkSettings(
  scope: Api.AssetPlatform.ProjectScope,
  beaconId: string,
  input: {
    einkCapable?: boolean
    einkPasskey?: string | null
    einkProfile?: string | null
    preferredGatewayId?: string | null
  }
) {
  return request.put<{
    einkCapable: boolean
    einkProfile: string | null
    einkPasskeyConfigured: boolean
    preferredGatewayId: string | null
    lastEinkGatewayId: string | null
    lastEinkAt: string | null
  }>({
    url: projectEndpoint(scope, `/beacons/${encodeURIComponent(beaconId)}/eink-settings`),
    data: input
  })
}

export function pushAssetEinkJob(
  scope: Api.AssetPlatform.ProjectScope,
  assetId: string,
  input: {
    bw?: string
    red?: string
    frame?: string
    profile?: 'elnk' | 'za25'
    orient?: 'landscape' | 'portrait'
    gatewayId?: string | null
    templateId?: string | null
    title?: string | null
    editor?: Record<string, unknown> | null
  }
) {
  return request.post<{
    jobId: string
    commandId: string
    status: string
    outboundCommandId: string
    beaconMac: string
    gatewayId: string
    gatewayMac: string
    gatewayName: string
    targetedGateway: boolean
    note?: string
  }>({
    url: projectEndpoint(scope, `/assets/${encodeURIComponent(assetId)}/eink-jobs`),
    data: input
  })
}

export function fetchAssetEinkLast(scope: Api.AssetPlatform.ProjectScope, assetId: string) {
  return request.get<{ editor: Record<string, unknown> | null; lastEinkAt: string | null }>({
    url: projectEndpoint(scope, `/assets/${encodeURIComponent(assetId)}/eink-last`),
    showErrorMessage: false
  })
}

export function fetchAssetEinkJobs(
  scope: Api.AssetPlatform.ProjectScope,
  assetId: string,
  params: { current?: number; size?: number } = {}
) {
  return request.get<Api.Common.PaginatedResponse<Record<string, unknown>>>({
    url: projectEndpoint(scope, `/assets/${encodeURIComponent(assetId)}/eink-jobs`),
    params,
    showErrorMessage: false
  })
}

export function fetchEinkJob(scope: Api.AssetPlatform.ProjectScope, jobId: string) {
  return request.get<Record<string, unknown>>({
    url: projectEndpoint(scope, `/eink-jobs/${encodeURIComponent(jobId)}`),
    showErrorMessage: false
  })
}

export function publishMqttCommand(
  scope: Api.AssetPlatform.ProjectScope,
  input: { topic: string; payload: string; qos?: number; retained?: boolean; connectionId?: string }
) {
  return request.post<Api.AssetPlatform.MqttCommandItem>({
    url: projectEndpoint(scope, '/mqtt/commands'),
    data: input
  })
}

export function fetchMqttCommands(
  scope: Api.AssetPlatform.ProjectScope,
  params: { current?: number; size?: number } = {}
) {
  return request.get<Api.Common.PaginatedResponse<Api.AssetPlatform.MqttCommandItem>>({
    url: projectEndpoint(scope, '/mqtt/commands'),
    params,
    showErrorMessage: false
  })
}

export function fetchTenantMembers(tenantId: string) {
  return request.get<Api.AssetPlatform.TenantMemberItem[]>({
    url: tenantEndpoint(tenantId, '/members'),
    showErrorMessage: false
  })
}

export function inviteTenantMember(tenantId: string, input: Api.AssetPlatform.TenantMemberInvite) {
  return request.post<Api.AssetPlatform.TenantMemberItem>({
    url: tenantEndpoint(tenantId, '/members'),
    data: input
  })
}

export function updateTenantMemberRoles(tenantId: string, memberId: string, roleCodes: string[]) {
  return request.put<Api.AssetPlatform.TenantMemberItem>({
    url: tenantEndpoint(tenantId, `/members/${encodeURIComponent(memberId)}/roles`),
    data: { roleCodes }
  })
}

export function removeTenantMember(tenantId: string, memberId: string) {
  return request.post<Api.AssetPlatform.TenantMemberItem>({
    url: tenantEndpoint(tenantId, `/members/${encodeURIComponent(memberId)}/remove`)
  })
}

export function fetchTenantRoles(tenantId: string) {
  return request.get<Api.AssetPlatform.TenantRoleItem[]>({
    url: tenantEndpoint(tenantId, '/roles'),
    showErrorMessage: false
  })
}

export function createTenantRole(tenantId: string, input: Api.AssetPlatform.TenantRoleUpsert) {
  return request.post<Api.AssetPlatform.TenantRoleItem>({
    url: tenantEndpoint(tenantId, '/roles'),
    data: input
  })
}

export function updateTenantRole(tenantId: string, roleId: string, input: Api.AssetPlatform.TenantRoleUpsert) {
  return request.put<Api.AssetPlatform.TenantRoleItem>({
    url: tenantEndpoint(tenantId, `/roles/${encodeURIComponent(roleId)}`),
    data: input
  })
}

export function deleteTenantRole(tenantId: string, roleId: string) {
  return request.del<void>({
    url: tenantEndpoint(tenantId, `/roles/${encodeURIComponent(roleId)}`)
  })
}

export function fetchTenantPermissions(tenantId: string) {
  return request.get<Api.AssetPlatform.PermissionItem[]>({
    url: tenantEndpoint(tenantId, '/permissions'),
    showErrorMessage: true
  })
}

export function inviteProjectMember(
  scope: Api.AssetPlatform.ProjectScope,
  input: { username?: string; userId?: string; roleCodes?: string[] }
) {
  return request.post<Api.AssetPlatform.ProjectMemberItem>({
    url: projectEndpoint(scope, '/members'),
    data: input
  })
}

export function updateProjectMemberRoles(scope: Api.AssetPlatform.ProjectScope, memberId: string, roleCodes: string[]) {
  return request.put<Api.AssetPlatform.ProjectMemberItem>({
    url: projectEndpoint(scope, `/members/${encodeURIComponent(memberId)}/roles`),
    data: { roleCodes }
  })
}

export function removeProjectMember(scope: Api.AssetPlatform.ProjectScope, memberId: string) {
  return request.post<Api.AssetPlatform.ProjectMemberItem>({
    url: projectEndpoint(scope, `/members/${encodeURIComponent(memberId)}/remove`)
  })
}

export function fetchProjectRoles(scope: Api.AssetPlatform.ProjectScope) {
  return request.get<Api.AssetPlatform.ProjectRoleItem[]>({
    url: projectEndpoint(scope, '/roles'),
    showErrorMessage: false
  })
}

export function createProjectRole(scope: Api.AssetPlatform.ProjectScope, input: Api.AssetPlatform.ProjectRoleUpsert) {
  return request.post<Api.AssetPlatform.ProjectRoleItem>({
    url: projectEndpoint(scope, '/roles'),
    data: input
  })
}

export function updateProjectRole(
  scope: Api.AssetPlatform.ProjectScope,
  roleId: string,
  input: Api.AssetPlatform.ProjectRoleUpsert
) {
  return request.put<Api.AssetPlatform.ProjectRoleItem>({
    url: projectEndpoint(scope, `/roles/${encodeURIComponent(roleId)}`),
    data: input
  })
}

export function deleteProjectRole(scope: Api.AssetPlatform.ProjectScope, roleId: string) {
  return request.del<void>({
    url: projectEndpoint(scope, `/roles/${encodeURIComponent(roleId)}`)
  })
}

export function archiveProject(tenantId: string, projectId: string) {
  return request.post<Api.AssetPlatform.ProjectItem>({
    url: `/api/v1/tenants/${encodeURIComponent(tenantId)}/projects/${encodeURIComponent(projectId)}/archive`
  })
}

export function fetchPlatformUsers() {
  return request.get<Record<string, unknown>[]>({ url: '/api/v1/platform/users', showErrorMessage: false })
}

export function createPlatformUser(input: {
  username: string
  email: string
  password: string
  displayName?: string
  roleCodes?: string[]
}) {
  return request.post<Record<string, unknown>>({ url: '/api/v1/platform/users', data: input })
}

export function updatePlatformUser(userId: string, input: { displayName?: string; email?: string }) {
  return request.put<Record<string, unknown>>({
    url: `/api/v1/platform/users/${encodeURIComponent(userId)}`,
    data: input
  })
}

export function resetPlatformUserPassword(userId: string, password: string) {
  return request.put<Record<string, unknown>>({
    url: `/api/v1/platform/users/${encodeURIComponent(userId)}/password`,
    data: { password }
  })
}

export function activatePlatformUser(userId: string) {
  return request.post<Record<string, unknown>>({
    url: `/api/v1/platform/users/${encodeURIComponent(userId)}/activate`
  })
}

export function suspendPlatformUser(userId: string) {
  return request.post<Record<string, unknown>>({
    url: `/api/v1/platform/users/${encodeURIComponent(userId)}/suspend`
  })
}

export function replacePlatformUserRoles(userId: string, roleCodes: string[]) {
  return request.put<Record<string, unknown>>({
    url: `/api/v1/platform/users/${encodeURIComponent(userId)}/roles`,
    data: { roleCodes }
  })
}

export function fetchPlatformRoles() {
  return request.get<Record<string, unknown>[]>({ url: '/api/v1/platform/roles', showErrorMessage: false })
}

export function createPlatformRole(input: { code: string; name: string; permissionCodes?: string[] }) {
  return request.post<Record<string, unknown>>({ url: '/api/v1/platform/roles', data: input })
}

export function updatePlatformRole(roleId: string, input: { name: string; permissionCodes?: string[] }) {
  return request.put<Record<string, unknown>>({
    url: `/api/v1/platform/roles/${encodeURIComponent(roleId)}`,
    data: input
  })
}

export function deletePlatformRole(roleId: string) {
  return request.del<void>({
    url: `/api/v1/platform/roles/${encodeURIComponent(roleId)}`
  })
}

export function fetchPlatformPermissions() {
  return request.get<Record<string, unknown>[]>({ url: '/api/v1/platform/permissions', showErrorMessage: false })
}
export function fetchPlatformAudit(params: {
  current?: number
  size?: number
  actor?: string
  includeSessionRefresh?: boolean
}) {
  return request.get<Api.Common.PaginatedResponse<Record<string, unknown>>>({
    url: '/api/v1/platform/audit',
    params,
    showErrorMessage: false
  })
}

export function fetchPlatformGateways() {
  return request.get<Record<string, unknown>[]>({
    url: '/api/v1/platform/gateways',
    showErrorMessage: false
  })
}

export function fetchPlatformMailSettings() {
  return request.get<Record<string, unknown>>({
    url: '/api/v1/platform/mail-settings',
    showErrorMessage: false
  })
}

export function updatePlatformMailSettings(data: Record<string, unknown>) {
  return request.put<Record<string, unknown>>({
    url: '/api/v1/platform/mail-settings',
    data
  })
}

export function testPlatformMail(data: { to: string }) {
  return request.post<Record<string, unknown>>({
    url: '/api/v1/platform/mail-settings/test',
    data
  })
}

export function fetchPlatformBrandingSettings() {
  return request.get<Record<string, unknown>>({
    url: '/api/v1/platform/branding-settings',
    showErrorMessage: false
  })
}

export function updatePlatformBrandingSettings(data: Record<string, unknown>) {
  return request.put<Record<string, unknown>>({
    url: '/api/v1/platform/branding-settings',
    data
  })
}

const OPS_LONG_TIMEOUT = 10 * 60 * 1000

export function fetchPlatformLicense() {
  return request.get<{
    installId: string
    activated: boolean
    who?: string | null
    until?: string | null
    features: string[]
    notify: boolean
    loginCopyright: boolean
    eink: boolean
    buzz: boolean
    maxBeacons?: number | null
    maxGateways?: number | null
  }>({
    url: '/api/v1/platform/ops/license',
    showErrorMessage: false
  })
}

export function activatePlatformLicense(token: string) {
  return request.put<{
    installId: string
    activated: boolean
    who?: string | null
    until?: string | null
    features: string[]
    notify: boolean
    loginCopyright: boolean
    eink: boolean
    buzz: boolean
    maxBeacons?: number | null
    maxGateways?: number | null
  }>({
    url: '/api/v1/platform/ops/license',
    data: { token }
  })
}

export function fetchPlatformOps() {
  return request.get<Record<string, unknown>>({
    url: '/api/v1/platform/ops',
    showErrorMessage: false
  })
}

export function previewPlatformOpsCleanup(data: Record<string, unknown>) {
  return request.post<Record<string, unknown>>({
    url: '/api/v1/platform/ops/cleanup/preview',
    data,
    timeout: 120000
  })
}

export function executePlatformOpsCleanup(data: Record<string, unknown>) {
  return request.post<Record<string, unknown>>({
    url: '/api/v1/platform/ops/cleanup',
    data,
    timeout: 30000
  })
}

export function fetchPlatformOpsCleanupStatus() {
  return request.get<Record<string, unknown>>({
    url: '/api/v1/platform/ops/cleanup/status',
    showErrorMessage: false
  })
}

export function updatePlatformOpsCleanupSchedule(data: {
  enabled: boolean
  intervalDays: number
  rules: { id: string; days: number }[]
}) {
  return request.put<Record<string, unknown>>({
    url: '/api/v1/platform/ops/cleanup/schedule',
    data
  })
}

export function createPlatformOpsBackup() {
  return request.post<Record<string, unknown>>({
    url: '/api/v1/platform/ops/backups',
    timeout: OPS_LONG_TIMEOUT
  })
}

export async function downloadPlatformOpsBackup(fileName: string) {
  const blob = await request.get<Blob>({
    url: `/api/v1/platform/ops/backups/${encodeURIComponent(fileName)}`,
    responseType: 'blob',
    rawBlob: true,
    timeout: OPS_LONG_TIMEOUT
  })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

export function deletePlatformOpsBackup(fileName: string) {
  return request.del<Record<string, unknown>>({
    url: `/api/v1/platform/ops/backups/${encodeURIComponent(fileName)}`
  })
}

export function restorePlatformOpsBackup(fileName: string, confirm: string) {
  return request.post<Record<string, unknown>>({
    url: '/api/v1/platform/ops/restore',
    data: { fileName, confirm },
    timeout: OPS_LONG_TIMEOUT
  })
}

export function restorePlatformOpsUpload(file: File, confirm: string) {
  const form = new FormData()
  form.append('file', file)
  return request.post<Record<string, unknown>>({
    url: `/api/v1/platform/ops/restore/upload?confirm=${encodeURIComponent(confirm)}`,
    data: form,
    timeout: OPS_LONG_TIMEOUT
  })
}

export function restartPlatformOpsService(service: string, confirm: string) {
  return request.post<Record<string, unknown>>({
    url: '/api/v1/platform/ops/restart',
    data: { service, confirm },
    timeout: 120000
  })
}


export function fetchAlerts(scope: Api.AssetPlatform.ProjectScope, params: { current?: number; size?: number }) {
  return request.get<Api.Common.PaginatedResponse<NamedResource>>({
    url: projectEndpoint(scope, '/alerts'),
    params,
    showErrorMessage: false
  })
}

export function acknowledgeAlert(scope: Api.AssetPlatform.ProjectScope, id: string) {
  return request.post<NamedResource>({ url: projectEndpoint(scope, `/alerts/${encodeURIComponent(id)}/acknowledge`) })
}

export function resolveAlert(scope: Api.AssetPlatform.ProjectScope, id: string) {
  return request.post<NamedResource>({ url: projectEndpoint(scope, `/alerts/${encodeURIComponent(id)}/resolve`) })
}

export function fetchInventorySessions(scope: Api.AssetPlatform.ProjectScope) {
  return request.get<NamedResource[]>({ url: projectEndpoint(scope, '/inventory-sessions'), showErrorMessage: false })
}

export function fetchInventorySession(scope: Api.AssetPlatform.ProjectScope, id: string) {
  return request.get<NamedResource>({
    url: projectEndpoint(scope, `/inventory-sessions/${encodeURIComponent(id)}`),
    showErrorMessage: false
  })
}

export function startInventorySession(scope: Api.AssetPlatform.ProjectScope, input: ResourceUpsert) {
  return request.post<NamedResource>({ url: projectEndpoint(scope, '/inventory-sessions'), data: input })
}

export function closeInventorySession(scope: Api.AssetPlatform.ProjectScope, id: string) {
  return request.post<NamedResource>({
    url: projectEndpoint(scope, `/inventory-sessions/${encodeURIComponent(id)}/close`)
  })
}

export function deleteInventorySession(scope: Api.AssetPlatform.ProjectScope, id: string) {
  return request.del({
    url: projectEndpoint(scope, `/inventory-sessions/${encodeURIComponent(id)}`)
  })
}

export function fetchAnalyticsSummary(scope: Api.AssetPlatform.ProjectScope) {
  return request.get<Record<string, unknown>>({
    url: projectEndpoint(scope, '/analytics/summary'),
    showErrorMessage: false
  })
}

export function fetchPlatformGeotagSettings() {
  return request.get<Record<string, unknown>>({
    url: '/api/v1/platform/geotag-settings',
    showErrorMessage: false
  })
}

export function updatePlatformGeotagSettings(data: Record<string, unknown>) {
  return request.put<Record<string, unknown>>({
    url: '/api/v1/platform/geotag-settings',
    data
  })
}

export function testPlatformGeotag() {
  return request.post<Record<string, unknown>>({
    url: '/api/v1/platform/geotag-settings/test'
  })
}

export function testPlatformGeotagWebhook(data?: Record<string, unknown>) {
  return request.post<Record<string, unknown>>({
    url: '/api/v1/platform/geotag-settings/test-webhook',
    data: data || {}
  })
}

export function rotatePlatformGeotagKeys() {
  return request.post<Record<string, unknown>>({
    url: '/api/v1/platform/geotag-settings/rotate-keys'
  })
}

export function simulateGeotagPoint(data: Record<string, unknown>) {
  return request.post<Record<string, unknown>>({
    url: '/api/v1/platform/geotag-settings/simulate-point',
    data
  })
}

export function fetchGeotagDevices(scope: Api.AssetPlatform.ProjectScope) {
  return request.get<Record<string, unknown>>({
    url: projectEndpoint(scope, '/geotag/devices'),
    showErrorMessage: false
  })
}

export function fetchGeotagPositions(scope: Api.AssetPlatform.ProjectScope) {
  return request.get<Record<string, unknown>>({
    url: projectEndpoint(scope, '/geotag/positions'),
    showErrorMessage: false
  })
}

export function fetchGeotagTracks(
  scope: Api.AssetPlatform.ProjectScope,
  params: { sn: string; from?: string; to?: string }
) {
  return request.get<Record<string, unknown>>({
    url: projectEndpoint(scope, '/geotag/tracks'),
    params,
    showErrorMessage: false
  })
}
