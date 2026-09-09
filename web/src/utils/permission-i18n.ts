import i18n from '@/locales'

/**
 * Permission display helpers.
 * Codes are stable API contracts; localized labels live under permissionCatalog.*
 * Adding a language: copy permissionCatalog + platformAdmin (+ presence) into a new locale file
 * and register it in locales/index.ts.
 */

/**
 * Hidden from platform role / permission screens:
 * - organization-member IAM (product entry removed)
 * - project IAM / docs / tasks (no separate project-admin surface)
 * - tracking & roll-call (not used in current product)
 * Business permissions such as asset / device / alert / mqtt remain visible.
 */
const HIDDEN_PLATFORM_ADMIN_RESOURCES = new Set([
  'tenant',
  'tenant-member',
  'tenant-role',
  'tenant-project',
  'tenant-broker',
  'tenant-audit',
  'project',
  'project-member',
  'project-role',
  'project-task',
  'project-document',
  'tracking',
  'roll-call'
])

function te(key: string) {
  return i18n.global.te(key)
}

function t(key: string, fallback?: string) {
  if (te(key)) return String((i18n.global as { t: (k: string) => unknown }).t(key))
  return fallback ?? key
}

export function permissionResource(code: string) {
  return String(code || '').split(':')[0] || ''
}

export function permissionAction(code: string) {
  const parts = String(code || '').split(':')
  return parts[1] || ''
}

/** asset:read -> permissionCatalog.codes.asset.read */
export function permissionI18nPath(code: string) {
  const resource = permissionResource(code)
  const action = permissionAction(code)
  if (!resource || !action) return ''
  return `permissionCatalog.codes.${resource}.${action}`
}

export function permissionName(code: string, fallback?: string | null) {
  const base = permissionI18nPath(code)
  if (!base) return fallback || code
  return t(`${base}.name`, fallback || code)
}

export function permissionDescription(code: string, fallback?: string | null) {
  const base = permissionI18nPath(code)
  if (!base) return fallback || ''
  return t(`${base}.description`, fallback || '')
}

export function permissionModuleLabel(module: string, fallback?: string | null) {
  const key = `permissionCatalog.modules.${module}`
  return t(key, fallback || module)
}

export function permissionFeatureLabel(resource: string, fallback?: string | null) {
  const key = `permissionCatalog.features.${resource}`
  return t(key, fallback || resource)
}

export function permissionActionLabel(action: string, fallback?: string | null) {
  const key = `permissionCatalog.actions.${action}`
  return t(key, fallback || action)
}

export function permissionOptionLabel(code: string, fallbackName?: string | null) {
  return `${code} · ${permissionName(code, fallbackName)}`
}

export function isPlatformAdminAssignablePermission(code: string) {
  return !HIDDEN_PLATFORM_ADMIN_RESOURCES.has(permissionResource(code))
}

/** @deprecated use !isPlatformAdminAssignablePermission */
export function isHiddenPlatformAdminPermission(code: string) {
  return !isPlatformAdminAssignablePermission(code)
}

export function platformScopeLabel(scope: string) {
  const key = `platformAdmin.scope.${String(scope || '').toUpperCase()}`
  return t(key, scope)
}

export function platformUserStatusLabel(status: string) {
  const key = `platformAdmin.userStatus.${String(status || '').toLowerCase()}`
  return t(key, status)
}

export function presenceLabel(status: string | null | undefined) {
  const upper = String(status || 'UNKNOWN').toUpperCase()
  return t(`presence.${upper}`, upper)
}
