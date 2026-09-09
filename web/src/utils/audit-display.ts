type Translate = (key: string, params?: Record<string, unknown>) => string

const ACTION_KEYS: Record<string, string> = {
  AUTH_LOGIN_SUCCEEDED: 'platformAdmin.audit.actions.loginSucceeded',
  AUTH_LOGIN_FAILED: 'platformAdmin.audit.actions.loginFailed',
  AUTH_SESSION_REFRESHED: 'platformAdmin.audit.actions.sessionRefreshed',
  AUTH_SESSION_REVOKED: 'platformAdmin.audit.actions.sessionRevoked',
  'mqtt.connection.create': 'platformAdmin.audit.actions.mqttConnectionCreate',
  'mqtt.connection.update': 'platformAdmin.audit.actions.mqttConnectionUpdate',
  'mqtt.connection.test': 'platformAdmin.audit.actions.mqttConnectionTest',
  'mqtt.topic_route.create': 'platformAdmin.audit.actions.mqttTopicRouteCreate',
  'mqtt.topic_route.update': 'platformAdmin.audit.actions.mqttTopicRouteUpdate',
  'mqtt.topic_route.delete': 'platformAdmin.audit.actions.mqttTopicRouteDelete',
  'platform.user.create': 'platformAdmin.audit.actions.userCreate',
  'platform.user.update': 'platformAdmin.audit.actions.userUpdate',
  'platform.user.reset_password': 'platformAdmin.audit.actions.userResetPassword',
  'platform.user.activate': 'platformAdmin.audit.actions.userActivate',
  'platform.user.suspend': 'platformAdmin.audit.actions.userSuspend',
  'platform.user.replace_roles': 'platformAdmin.audit.actions.userReplaceRoles',
  'platform.role.create': 'platformAdmin.audit.actions.roleCreate',
  'platform.role.update': 'platformAdmin.audit.actions.roleUpdate',
  'platform.role.delete': 'platformAdmin.audit.actions.roleDelete',
  'platform.mail.update': 'platformAdmin.audit.actions.mailUpdate',
  'platform.branding.update': 'platformAdmin.audit.actions.brandingUpdate',
  'platform.ops.cleanup': 'platformAdmin.audit.actions.opsCleanup',
  'platform.ops.auto_cleanup': 'platformAdmin.audit.actions.opsAutoCleanup',
  'platform.ops.backup': 'platformAdmin.audit.actions.opsBackup',
  'platform.ops.backup_delete': 'platformAdmin.audit.actions.opsBackupDelete',
  'platform.ops.restore': 'platformAdmin.audit.actions.opsRestore',
  'platform.ops.restart': 'platformAdmin.audit.actions.opsRestart',
  'asset_type.create': 'platformAdmin.audit.actions.assetTypeCreate',
  'asset_type.update': 'platformAdmin.audit.actions.assetTypeUpdate',
  'asset_type.delete': 'platformAdmin.audit.actions.assetTypeDelete',
  'asset.create': 'platformAdmin.audit.actions.assetCreate',
  'asset.update': 'platformAdmin.audit.actions.assetUpdate',
  'asset.delete': 'platformAdmin.audit.actions.assetDelete',
  'asset.bind_beacon': 'platformAdmin.audit.actions.assetBindBeacon',
  'asset.unbind_beacon': 'platformAdmin.audit.actions.assetUnbindBeacon',
  'asset.buzzer': 'platformAdmin.audit.actions.assetBuzzer',
  'asset.eink_push': 'platformAdmin.audit.actions.assetEinkPush',
  'beacon.create': 'platformAdmin.audit.actions.beaconCreate',
  'beacon.update': 'platformAdmin.audit.actions.beaconUpdate',
  'beacon.delete': 'platformAdmin.audit.actions.beaconDelete',
  'beacon.batch_delete': 'platformAdmin.audit.actions.beaconBatchDelete',
  'beacon.import': 'platformAdmin.audit.actions.beaconImport',
  'beacon.eink_settings': 'platformAdmin.audit.actions.beaconEinkSettings',
  'gateway.create': 'platformAdmin.audit.actions.gatewayCreate',
  'gateway.update': 'platformAdmin.audit.actions.gatewayUpdate',
  'gateway.delete': 'platformAdmin.audit.actions.gatewayDelete',
  'map.create': 'platformAdmin.audit.actions.mapCreate',
  'map.update': 'platformAdmin.audit.actions.mapUpdate',
  'map.delete': 'platformAdmin.audit.actions.mapDelete',
  'map.upload_image': 'platformAdmin.audit.actions.mapUploadImage',
  'zone.create': 'platformAdmin.audit.actions.zoneCreate',
  'zone.update': 'platformAdmin.audit.actions.zoneUpdate',
  'zone.delete': 'platformAdmin.audit.actions.zoneDelete',
  'alert_rule.create': 'platformAdmin.audit.actions.alertRuleCreate',
  'alert_rule.update': 'platformAdmin.audit.actions.alertRuleUpdate',
  'alert.acknowledge': 'platformAdmin.audit.actions.alertAcknowledge',
  'alert.resolve': 'platformAdmin.audit.actions.alertResolve',
  'inventory.start': 'platformAdmin.audit.actions.inventoryStart',
  'inventory.close': 'platformAdmin.audit.actions.inventoryClose',
  'inventory.delete': 'platformAdmin.audit.actions.inventoryDelete',
  'roll_call.start': 'platformAdmin.audit.actions.rollCallStart',
  'task.create': 'platformAdmin.audit.actions.taskCreate',
  'task.update': 'platformAdmin.audit.actions.taskUpdate',
  'document.create': 'platformAdmin.audit.actions.documentCreate',
  'document.upload': 'platformAdmin.audit.actions.documentUpload',
  'document.update': 'platformAdmin.audit.actions.documentUpdate',
  'document.delete': 'platformAdmin.audit.actions.documentDelete'
}

const RESOURCE_KEYS: Record<string, string> = {
  AUTH_SESSION: 'platformAdmin.audit.resources.authSession',
  mqtt_connection: 'platformAdmin.audit.resources.mqttConnection',
  mqtt_topic_route: 'platformAdmin.audit.resources.mqttTopicRoute',
  platform_user: 'platformAdmin.audit.resources.platformUser',
  platform_role: 'platformAdmin.audit.resources.platformRole',
  platform_mail: 'platformAdmin.audit.resources.platformMail',
  platform_branding: 'platformAdmin.audit.resources.platformBranding',
  platform_ops: 'platformAdmin.audit.resources.platformOps',
  asset_type: 'platformAdmin.audit.resources.assetType',
  asset: 'platformAdmin.audit.resources.asset',
  beacon: 'platformAdmin.audit.resources.beacon',
  gateway: 'platformAdmin.audit.resources.gateway',
  map: 'platformAdmin.audit.resources.map',
  zone: 'platformAdmin.audit.resources.zone',
  alert_rule: 'platformAdmin.audit.resources.alertRule',
  alert_event: 'platformAdmin.audit.resources.alertEvent',
  inventory_session: 'platformAdmin.audit.resources.inventorySession',
  roll_call: 'platformAdmin.audit.resources.rollCall',
  project_task: 'platformAdmin.audit.resources.projectTask',
  project_document: 'platformAdmin.audit.resources.projectDocument'
}

function tKey(t: Translate, key: string, fallback: string, params?: Record<string, unknown>) {
  const translated = String(t(key as never, params as never))
  return translated === key ? fallback : translated
}

function formatDetailValue(value: unknown, t: Translate): string {
  if (Array.isArray(value)) return value.map((v) => String(v)).join(', ') || '—'
  if (typeof value === 'boolean') {
    return value
      ? tKey(t, 'platformAdmin.audit.yes', '是')
      : tKey(t, 'platformAdmin.audit.no', '否')
  }
  return String(value)
}

export function auditActorLabel(row: Record<string, unknown>, t: Translate): string {
  const display = String(row.actorDisplayName || '').trim()
  const username = String(row.actorUsername || '').trim()
  if (display && username && display !== username) {
    return `${display}（${username}）`
  }
  if (display || username) return display || username
  return tKey(t, 'platformAdmin.audit.actorUnknown', '未知用户')
}

export function auditActionLabel(action: unknown, t: Translate): string {
  const code = String(action || '')
  const key = ACTION_KEYS[code]
  if (key) return tKey(t, key, code)
  return code || '—'
}

export function auditResourceLabel(resourceType: unknown, t: Translate): string {
  const code = String(resourceType || '')
  const key = RESOURCE_KEYS[code]
  if (key) return tKey(t, key, code)
  return code || '—'
}

export function auditChangeSummary(row: Record<string, unknown>, t: Translate): string {
  const details =
    row.details && typeof row.details === 'object' ? (row.details as Record<string, unknown>) : {}
  const parts: string[] = []

  const resource = auditResourceLabel(row.resourceType, t)
  const targetName =
    details.username || details.code || details.name || details.systemName || details.host || null
  if (targetName != null && String(targetName)) {
    parts.push(`${resource}：${formatDetailValue(targetName, t)}`)
  } else {
    const resourceId = String(row.resourceId || '').trim()
    if (resourceId) {
      const shortId =
        resourceId.length > 18 ? `${resourceId.slice(0, 8)}…${resourceId.slice(-6)}` : resourceId
      parts.push(
        tKey(t, 'platformAdmin.audit.summary.object', '{resource}（{id}）', { resource, id: shortId })
      )
    } else {
      parts.push(resource)
    }
  }

  const detailOrder = [
    'email',
    'displayName',
    'roleCodes',
    'permissionCount',
    'enabled',
    'port',
    'fromAddress',
    'username',
    'passwordChanged',
    'loginTitle',
    'hasLoginBackground',
    'hasTitleLogo',
    'reason',
    'scope',
    'environment',
    'resultCode',
    'sessionId'
  ] as const

  const used = new Set<string>(['username', 'code', 'name', 'systemName', 'host'])
  for (const key of detailOrder) {
    if (!(key in details) || details[key] == null || details[key] === '') continue
    // username/code already used as target title when present
    if (key === 'username' && targetName === details.username) continue
    const value = formatDetailValue(details[key], t)
    parts.push(
      tKey(t, `platformAdmin.audit.summary.${key}`, `${key}：{value}`, { value })
    )
    used.add(key)
  }

  for (const [key, value] of Object.entries(details)) {
    if (used.has(key) || value == null || value === '') continue
    if (typeof value === 'object' && !Array.isArray(value)) continue
    parts.push(`${key}：${formatDetailValue(value, t)}`)
  }

  if (row.ipAddress) {
    parts.push(tKey(t, 'platformAdmin.audit.summary.ip', 'IP：{value}', { value: row.ipAddress }))
  }

  return parts.filter(Boolean).join(' · ') || '—'
}
