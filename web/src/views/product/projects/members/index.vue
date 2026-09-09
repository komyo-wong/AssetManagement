<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useTenantContextStore } from '@/store/modules/tenant-context'
import {
  createProjectRole,
  deleteProjectRole,
  fetchPlatformPermissions,
  fetchProjectMembers,
  fetchProjectRoles,
  fetchTenantPermissions,
  inviteProjectMember,
  removeProjectMember,
  updateProjectMemberRoles,
  updateProjectRole
} from '@/api/asset-platform'
import TablePager from '@/components/business/TablePager.vue'
import ProductTableHeader from '@/components/business/ProductTableHeader.vue'
import { useClientPagination } from '@/composables/useClientPagination'
import { useProductTable } from '@/composables/useProductTable'
import { useTableColumns } from '@/hooks/core/useTableColumns'
import type { ColumnOption } from '@/types/component'

const { t } = useI18n()

const { projectScope, currentTenantId } = storeToRefs(useTenantContextStore())
const { tableSize, isZebra, isBorder } = useProductTable()
const rows = ref<Api.AssetPlatform.ProjectMemberItem[]>([])
const roles = ref<Api.AssetPlatform.ProjectRoleItem[]>([])
const {
  current: pageCurrent,
  size: pageSize,
  total: pageTotal,
  pagedRows,
  onPageChange,
  onSizeChange
} = useClientPagination(rows)

const { columnChecks, resetColumns } = useTableColumns(() => {
  const cols: ColumnOption[] = [
    { prop: 'username', label: t('common.username'), checked: true },
    { prop: 'displayName', label: t('common.displayName'), checked: true },
    { prop: 'status', label: t('common.status'), checked: true },
    { prop: 'roles', label: t('common.roles'), checked: true },
    { prop: '__actions', label: t('common.actions'), width: 80, fixed: 'right', checked: true, disabled: true }
  ]
  return cols
})
watch(() => t('common.actions'), () => resetColumns())
function memberColVisible(prop: string) {
  return columnChecks.value.some((c) => c.prop === prop && (c.checked ?? c.visible ?? true))
}
const {
  current: rolesPageCurrent,
  size: rolesPageSize,
  total: rolesPageTotal,
  pagedRows: pagedRoles,
  onPageChange: onRolesPageChange,
  onSizeChange: onRolesSizeChange
} = useClientPagination(roles)
const permissions = ref<Api.AssetPlatform.PermissionItem[]>([])
const inviteOpen = ref(false)
const roleOpen = ref(false)
const editingRole = ref<Api.AssetPlatform.ProjectRoleItem | null>(null)
const inviteForm = ref({ username: '', roleCodes: [] as string[] })
const roleForm = ref({ code: '', name: '', permissionCodes: [] as string[] })

function isAssignablePermission(item: { code?: string; module?: string | null; category?: string | null }) {
  const code = String(item.code || '').toLowerCase()
  const module = String(item.module || item.category || '').toLowerCase()
  return !!code && module !== 'platform' && !code.startsWith('platform-')
}

async function loadPermissions() {
  if (!currentTenantId.value) {
    permissions.value = []
    return
  }
  try {
    const list = await fetchTenantPermissions(currentTenantId.value)
    if (Array.isArray(list) && list.length > 0) {
      permissions.value = list.filter(isAssignablePermission)
      return
    }
  } catch {
    // fallback below
  }
  try {
    const platformList = await fetchPlatformPermissions()
    permissions.value = (platformList || [])
      .map((item) => ({
        code: String(item.code || ''),
        name: String(item.name || item.code || ''),
        module: item.module == null ? null : String(item.module),
        description: item.description == null ? null : String(item.description)
      }))
      .filter(isAssignablePermission)
  } catch {
    permissions.value = []
  }
}

async function load() {
  if (!projectScope.value || !currentTenantId.value) {
    rows.value = []
    roles.value = []
    permissions.value = []
    return
  }
  const [memberList, roleList] = await Promise.all([
    fetchProjectMembers(projectScope.value).catch(() => [] as Api.AssetPlatform.ProjectMemberItem[]),
    fetchProjectRoles(projectScope.value).catch(() => [] as Api.AssetPlatform.ProjectRoleItem[])
  ])
  rows.value = memberList || []
  roles.value = roleList || []
  await loadPermissions()
}

watch(projectScope, load, { immediate: true })

async function submitInvite() {
  if (!projectScope.value) return
  await inviteProjectMember(projectScope.value, inviteForm.value)
  ElMessage.success(t('product.projects.members.joined'))
  inviteOpen.value = false
  inviteForm.value = { username: '', roleCodes: [] }
  await load()
}

async function changeRoles(row: Api.AssetPlatform.ProjectMemberItem, roleCodes: string[]) {
  if (!projectScope.value) return
  await updateProjectMemberRoles(projectScope.value, row.id, roleCodes)
  ElMessage.success(t('product.projects.members.rolesUpdated'))
  await load()
}

async function remove(row: Api.AssetPlatform.ProjectMemberItem) {
  if (!projectScope.value) return
  await removeProjectMember(projectScope.value, row.id)
  ElMessage.success(t('product.projects.members.removed'))
  await load()
}

async function openRole(row?: Api.AssetPlatform.ProjectRoleItem) {
  editingRole.value = row || null
  roleForm.value = row
    ? { code: row.code, name: row.name, permissionCodes: [...(row.permissionCodes || [])] }
    : { code: '', name: '', permissionCodes: [] }
  if (permissions.value.length === 0) {
    await loadPermissions()
  }
  roleOpen.value = true
}

async function saveRole() {
  if (!projectScope.value) return
  if (editingRole.value) {
    await updateProjectRole(projectScope.value, editingRole.value.id, {
      name: roleForm.value.name,
      permissionCodes: roleForm.value.permissionCodes
    })
  } else {
    await createProjectRole(projectScope.value, roleForm.value)
  }
  ElMessage.success(t('product.projects.members.roleSaved'))
  roleOpen.value = false
  await load()
}

async function removeRole(row: Api.AssetPlatform.ProjectRoleItem) {
  if (!projectScope.value) return
  if (row.protectedRole || row.systemRole) {
    ElMessage.warning(t('product.projects.members.cannotDeleteRole'))
    return
  }
  await ElMessageBox.confirm(t('product.projects.members.deleteRoleConfirm', { name: row.name }), t('product.projects.members.deleteRoleTitle'), {
    type: 'warning'
  })
  await deleteProjectRole(projectScope.value, row.id)
  ElMessage.success(t('product.projects.members.roleDeleted'))
  await load()
}
</script>
<template>
  <div class="page-card art-card" style="padding: 20px">
    <div style="display: flex; justify-content: space-between; margin-bottom: 16px">
      <div>
        <h2 style="margin: 0">{{ t('product.projects.members.title') }}</h2>
        <p style="margin: 4px 0 0; color: var(--el-text-color-secondary)">{{ t('product.projects.members.subtitle') }}</p>
      </div>
      <div style="display: flex; gap: 8px">
        <ElButton :disabled="!projectScope" @click="openRole()">{{ t('product.projects.members.projectRoles') }}</ElButton>
        <ElButton type="primary" :disabled="!projectScope" @click="inviteOpen = true">{{ t('product.projects.members.invite') }}</ElButton>
      </div>
    </div>
    <ElAlert v-if="!projectScope" type="warning" :closable="false" :title="t('common.selectTenantProject')" />
    <template v-else>
      <div class="toolbar art-table-card">
        <ProductTableHeader v-model:columns="columnChecks" full-class="page-card" @refresh="() => load()" />
      </div>
      <ElTable :data="pagedRows" :size="tableSize" :stripe="isZebra" :border="isBorder">
        <ElTableColumn v-if="memberColVisible('username')" prop="username" :label="t('common.username')" width="140" />
        <ElTableColumn v-if="memberColVisible('displayName')" prop="displayName" :label="t('common.displayName')" />
        <ElTableColumn v-if="memberColVisible('status')" prop="status" :label="t('common.status')" width="100" />
        <ElTableColumn v-if="memberColVisible('roles')" :label="t('common.roles')" min-width="220">
          <template #default="{ row }">
            <ElSelect
              :model-value="row.roles || []"
              multiple
              filterable
              collapse-tags
              style="width: 100%"
              @change="(v: string[]) => changeRoles(row, v)"
            >
              <ElOption v-for="r in roles" :key="r.code" :label="r.name" :value="r.code" />
            </ElSelect>
          </template>
        </ElTableColumn>
        <ElTableColumn v-if="memberColVisible('__actions')" :label="t('common.actions')" width="80" fixed="right">
          <template #default="{ row }">
            <ElButton link type="danger" @click="remove(row)">{{ t('common.remove') }}</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <TablePager
        :total="pageTotal"
        :current="pageCurrent"
        :size="pageSize"
        @update:current="onPageChange"
        @update:size="onSizeChange"
      />
    </template>

    <div v-if="projectScope" style="margin-top: 24px">
      <h3>{{ t('product.projects.members.rolesList') }}</h3>
      <ElTable :data="pagedRoles" stripe>
        <ElTableColumn prop="code" :label="t('common.code')" width="160" />
        <ElTableColumn prop="name" :label="t('common.name')" />
        <ElTableColumn :label="t('product.projects.members.permissionCount')" width="100">
          <template #default="{ row }">{{ row.permissionCodes?.length || 0 }}</template>
        </ElTableColumn>
        <ElTableColumn :label="t('common.actions')" width="160">
          <template #default="{ row }">
            <ElButton link type="primary" @click="openRole(row)">{{ t('common.edit') }}</ElButton>
            <ElButton
              link
              type="danger"
              :disabled="!!(row.protectedRole || row.systemRole)"
              @click="removeRole(row)"
            >
              {{ t('common.delete') }}
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <TablePager
        :total="rolesPageTotal"
        :current="rolesPageCurrent"
        :size="rolesPageSize"
        @update:current="onRolesPageChange"
        @update:size="onRolesSizeChange"
      />
    </div>

    <ElDialog v-model="inviteOpen" :title="t('product.projects.members.inviteTitle')" width="420px">
      <ElForm label-position="top">
        <ElFormItem :label="t('common.username')" required><ElInput v-model="inviteForm.username" /></ElFormItem>
        <ElFormItem :label="t('product.projects.members.projectRole')">
          <ElSelect v-model="inviteForm.roleCodes" multiple filterable style="width: 100%">
            <ElOption v-for="r in roles" :key="r.code" :label="r.name" :value="r.code" />
          </ElSelect>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="inviteOpen = false">{{ t('common.cancel') }}</ElButton>
        <ElButton type="primary" @click="submitInvite">{{ t('common.invite') }}</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="roleOpen" :title="editingRole ? t('product.projects.members.roleEdit') : t('product.projects.members.roleCreate')" width="560px">
      <ElForm label-position="top">
        <ElFormItem :label="t('common.code')" required>
          <ElInput v-model="roleForm.code" :disabled="!!editingRole" />
        </ElFormItem>
        <ElFormItem :label="t('common.name')" required><ElInput v-model="roleForm.name" /></ElFormItem>
        <ElFormItem :label="t('common.permissions')">
          <ElSelect v-model="roleForm.permissionCodes" multiple filterable collapse-tags style="width: 100%">
            <ElOption v-for="p in permissions" :key="p.code" :label="`${p.code} · ${p.name}`" :value="p.code" />
          </ElSelect>
          <div v-if="permissions.length === 0" style="margin-top: 8px; color: var(--el-color-danger)">
            {{ t('product.projects.members.noPermissions') }}
          </div>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="roleOpen = false">{{ t('common.cancel') }}</ElButton>
        <ElButton type="primary" @click="saveRole">{{ t('common.save') }}</ElButton>
      </template>
    </ElDialog>
  </div>
</template>
