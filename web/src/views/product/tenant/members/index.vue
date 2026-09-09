<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useTenantContextStore } from '@/store/modules/tenant-context'
import {
  createTenantRole,
  deleteTenantRole,
  fetchPlatformPermissions,
  fetchTenantMembers,
  fetchTenantPermissions,
  fetchTenantRoles,
  inviteTenantMember,
  removeTenantMember,
  updateTenantMemberRoles,
  updateTenantRole
} from '@/api/asset-platform'
import TablePager from '@/components/business/TablePager.vue'
import { useClientPagination } from '@/composables/useClientPagination'

const { t } = useI18n()

const { currentTenantId } = storeToRefs(useTenantContextStore())
const members = ref<Api.AssetPlatform.TenantMemberItem[]>([])
const roles = ref<Api.AssetPlatform.TenantRoleItem[]>([])
const {
  current: pageCurrent,
  size: pageSize,
  total: pageTotal,
  pagedRows,
  onPageChange,
  onSizeChange
} = useClientPagination(members)
const {
  current: rolesPageCurrent,
  size: rolesPageSize,
  total: rolesPageTotal,
  pagedRows: pagedRoles,
  onPageChange: onRolesPageChange,
  onSizeChange: onRolesSizeChange
} = useClientPagination(roles)
const permissions = ref<Api.AssetPlatform.PermissionItem[]>([])
const loading = ref(false)
const inviteOpen = ref(false)
const roleOpen = ref(false)
const editingRole = ref<Api.AssetPlatform.TenantRoleItem | null>(null)
const inviteForm = ref({ username: '', email: '', password: '', displayName: '', roleCodes: [] as string[] })
const roleForm = ref({ code: '', name: '', permissionCodes: [] as string[] })

const hasTenant = computed(() => !!currentTenantId.value)
const permissionOptions = computed(() =>
  [...permissions.value].sort((a, b) => String(a.code).localeCompare(String(b.code)))
)

function isAssignablePermission(item: { code?: string; module?: string | null; category?: string | null }) {
  const code = String(item.code || '').toLowerCase()
  const module = String(item.module || item.category || '').toLowerCase()
  return !!code && module !== 'platform' && !code.startsWith('platform-')
}

async function loadPermissions(tenantId: string) {
  try {
    const list = await fetchTenantPermissions(tenantId)
    if (Array.isArray(list) && list.length > 0) {
      permissions.value = list.filter(isAssignablePermission)
      return
    }
  } catch {
    // fall through to platform catalog
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
  if (!currentTenantId.value) {
    members.value = []
    roles.value = []
    permissions.value = []
    return
  }
  const tid = currentTenantId.value
  loading.value = true
  try {
    const [memberList, roleList] = await Promise.all([
      fetchTenantMembers(tid).catch(() => [] as Api.AssetPlatform.TenantMemberItem[]),
      fetchTenantRoles(tid).catch(() => [] as Api.AssetPlatform.TenantRoleItem[])
    ])
    members.value = memberList || []
    roles.value = roleList || []
    await loadPermissions(tid)
  } finally {
    loading.value = false
  }
}

watch(currentTenantId, load, { immediate: true })

async function submitInvite() {
  if (!currentTenantId.value) return
  await inviteTenantMember(currentTenantId.value, { ...inviteForm.value })
  ElMessage.success(t('product.tenantMembers.invited'))
  inviteOpen.value = false
  inviteForm.value = { username: '', email: '', password: '', displayName: '', roleCodes: [] }
  await load()
}

async function changeRoles(row: Api.AssetPlatform.TenantMemberItem, roleCodes: string[]) {
  if (!currentTenantId.value) return
  await updateTenantMemberRoles(currentTenantId.value, row.id, roleCodes)
  ElMessage.success(t('product.tenantMembers.rolesUpdated'))
  await load()
}

async function remove(row: Api.AssetPlatform.TenantMemberItem) {
  if (!currentTenantId.value) return
  await removeTenantMember(currentTenantId.value, row.id)
  ElMessage.success(t('product.tenantMembers.removed'))
  await load()
}

async function openRole(row?: Api.AssetPlatform.TenantRoleItem) {
  editingRole.value = row || null
  roleForm.value = row
    ? { code: row.code, name: row.name, permissionCodes: [...(row.permissionCodes || [])] }
    : { code: '', name: '', permissionCodes: [] }
  if (currentTenantId.value && permissions.value.length === 0) {
    await loadPermissions(currentTenantId.value)
  }
  roleOpen.value = true
}

async function saveRole() {
  if (!currentTenantId.value) return
  if (editingRole.value) {
    await updateTenantRole(currentTenantId.value, editingRole.value.id, {
      name: roleForm.value.name,
      permissionCodes: roleForm.value.permissionCodes
    })
  } else {
    await createTenantRole(currentTenantId.value, roleForm.value)
  }
  ElMessage.success(t('product.tenantMembers.roleSaved'))
  roleOpen.value = false
  await load()
}

async function removeRole(row: Api.AssetPlatform.TenantRoleItem) {
  if (!currentTenantId.value) return
  if (row.protectedRole || row.systemRole) {
    ElMessage.warning(t('product.tenantMembers.cannotDeleteRole'))
    return
  }
  await ElMessageBox.confirm(t('product.tenantMembers.deleteRoleConfirm', { name: row.name }), t('product.tenantMembers.deleteRoleTitle'), {
    type: 'warning'
  })
  await deleteTenantRole(currentTenantId.value, row.id)
  ElMessage.success(t('product.tenantMembers.roleDeleted'))
  await load()
}
</script>

<template>
  <div class="page-card art-card" style="padding: 20px" v-loading="loading">
    <div style="display: flex; justify-content: space-between; margin-bottom: 16px">
      <div>
        <h2 style="margin: 0">{{ t('product.tenantMembers.title') }}</h2>
        <p style="margin: 4px 0 0; color: var(--el-text-color-secondary)">{{ t('product.tenantMembers.subtitle') }}</p>
      </div>
      <ElButton type="primary" :disabled="!hasTenant" @click="inviteOpen = true">{{ t('product.tenantMembers.invite') }}</ElButton>
    </div>
    <ElAlert v-if="!hasTenant" type="warning" :closable="false" :title="t('common.selectTenant')" />
    <ElTable v-else :data="pagedRows" stripe>
      <ElTableColumn prop="username" :label="t('common.username')" width="140" />
      <ElTableColumn prop="displayName" :label="t('common.displayName')" />
      <ElTableColumn prop="status" :label="t('common.status')" width="100" />
      <ElTableColumn :label="t('common.roles')" min-width="220">
        <template #default="{ row }">
          <ElSelect
            :model-value="row.roleCodes || []"
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
      <ElTableColumn :label="t('common.actions')" width="100">
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

    <div style="display: flex; justify-content: space-between; margin: 28px 0 12px">
      <h3 style="margin: 0">{{ t('product.tenantMembers.rolesTitle') }}</h3>
      <ElButton :disabled="!hasTenant" @click="openRole()">{{ t('product.tenantMembers.createRole') }}</ElButton>
    </div>
    <ElTable v-if="hasTenant" :data="pagedRoles" stripe>
      <ElTableColumn prop="code" :label="t('common.code')" width="160" />
      <ElTableColumn prop="name" :label="t('common.name')" />
      <ElTableColumn :label="t('product.tenantMembers.permissionCount')" width="100">
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

    <ElDialog v-model="inviteOpen" :title="t('product.tenantMembers.inviteTitle')" width="480px">
      <ElForm label-position="top" autocomplete="off">
        <input type="text" class="autofill-trap" tabindex="-1" aria-hidden="true" autocomplete="username" />
        <input
          type="password"
          class="autofill-trap"
          tabindex="-1"
          aria-hidden="true"
          autocomplete="current-password"
        />
        <ElFormItem :label="t('common.username')" required>
          <ElInput v-model="inviteForm.username" name="invite-username" autocomplete="off" />
        </ElFormItem>
        <ElFormItem :label="t('common.email')">
          <ElInput v-model="inviteForm.email" name="invite-email" autocomplete="off" />
        </ElFormItem>
        <ElFormItem :label="t('product.tenantMembers.initialPassword')">
          <ElInput
            v-model="inviteForm.password"
            type="password"
            show-password
            name="invite-password"
            autocomplete="new-password"
          />
        </ElFormItem>
        <ElFormItem :label="t('common.displayName')">
          <ElInput v-model="inviteForm.displayName" name="invite-display" autocomplete="off" />
        </ElFormItem>
        <ElFormItem :label="t('common.roles')">
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

    <ElDialog v-model="roleOpen" :title="editingRole ? t('product.tenantMembers.roleEdit') : t('product.tenantMembers.roleCreate')" width="560px">
      <ElForm label-position="top">
        <ElFormItem :label="t('common.code')" required>
          <ElInput v-model="roleForm.code" :disabled="!!editingRole" />
        </ElFormItem>
        <ElFormItem :label="t('common.name')" required><ElInput v-model="roleForm.name" /></ElFormItem>
        <ElFormItem :label="t('common.permissions')">
          <ElSelect
            v-model="roleForm.permissionCodes"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            clearable
            :placeholder="t('product.tenantMembers.selectPermissions')"
            style="width: 100%"
          >
            <ElOption
              v-for="p in permissionOptions"
              :key="p.code"
              :label="`${p.code} · ${p.name}`"
              :value="p.code"
            />
          </ElSelect>
          <div v-if="permissionOptions.length === 0" style="margin-top: 8px; color: var(--el-color-danger)">
            {{ t('product.tenantMembers.noPermissions') }}
          </div>
          <div v-else style="margin-top: 8px; color: var(--el-text-color-secondary)">
            {{ t('product.tenantMembers.permissionCountHint', { count: permissionOptions.length }) }}
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

<style scoped>
  .autofill-trap {
    position: absolute;
    left: -9999px;
    width: 1px;
    height: 1px;
    opacity: 0;
    pointer-events: none;
  }
</style>
