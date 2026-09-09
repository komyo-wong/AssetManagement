import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

function isActive(status: Api.Auth.MembershipStatus): boolean {
  return status === 'ACTIVE'
}

/**
 * 固定工作区上下文（内部 demo 租户/项目）。
 * 权限以平台 RBAC（/me.platformPermissions）为准；此处 membership 仅提供 API scope ID。
 */
export const useTenantContextStore = defineStore('tenantContextStore', () => {
  const tenantMemberships = ref<Api.Auth.TenantMembership[]>([])
  const currentTenantId = ref('')
  const currentProjectId = ref('')

  const activeTenants = computed(() =>
    tenantMemberships.value.filter((item) => isActive(item.status))
  )

  const currentTenant = computed(
    () => activeTenants.value.find((item) => item.tenantId === currentTenantId.value) ?? null
  )

  const activeProjects = computed(() =>
    (currentTenant.value?.projects ?? []).filter((item) => isActive(item.status))
  )

  const currentProject = computed(
    () => activeProjects.value.find((item) => item.projectId === currentProjectId.value) ?? null
  )

  const projectScope = computed<Api.AssetPlatform.ProjectScope | null>(() => {
    if (!currentTenant.value || !currentProject.value) return null
    return {
      tenantId: currentTenant.value.tenantId,
      projectId: currentProject.value.projectId
    }
  })

  const currentPermissions = computed(() => {
    const values = [
      ...(currentTenant.value?.permissions ?? []),
      ...(currentProject.value?.permissions ?? [])
    ]
    return [...new Set(values)]
  })

  /** 菜单可发现性：工作区权限并集（平台权限在 MenuProcessor / useAuth 另行合并）。 */
  const accessiblePermissions = computed(() => {
    const values: string[] = []
    for (const tenant of activeTenants.value) {
      values.push(...tenant.permissions)
      for (const project of tenant.projects) {
        if (isActive(project.status)) values.push(...project.permissions)
      }
    }
    return [...new Set(values)]
  })

  function hydrate(memberships: Api.Auth.TenantMembership[]): void {
    tenantMemberships.value = Array.isArray(memberships) ? memberships : []
    const tenant = activeTenants.value[0] ?? null
    currentTenantId.value = tenant?.tenantId ?? ''
    const project = (tenant?.projects ?? []).find((item) => isActive(item.status))
    currentProjectId.value = project?.projectId ?? ''
  }

  function setTenant(_tenantId: string): void {
    // 工作区已固定，忽略切换
  }

  function setProject(_projectId: string): void {
    // 工作区已固定，忽略切换
  }

  function clear(): void {
    tenantMemberships.value = []
    currentTenantId.value = ''
    currentProjectId.value = ''
  }

  return {
    tenantMemberships,
    currentTenantId,
    currentProjectId,
    activeTenants,
    currentTenant,
    activeProjects,
    currentProject,
    projectScope,
    currentPermissions,
    accessiblePermissions,
    hydrate,
    setTenant,
    setProject,
    clear
  }
})
