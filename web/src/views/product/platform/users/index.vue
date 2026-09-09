<script setup lang="ts">
  import { onMounted, ref, watch } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import {
    activatePlatformUser,
    createPlatformUser,
    fetchPlatformRoles,
    fetchPlatformUsers,
    replacePlatformUserRoles,
    resetPlatformUserPassword,
    suspendPlatformUser,
    updatePlatformUser
  } from '@/api/asset-platform'
  import { platformUserStatusLabel } from '@/utils/permission-i18n'
  import TablePager from '@/components/business/TablePager.vue'
  import ProductTableHeader from '@/components/business/ProductTableHeader.vue'
  import { useClientPagination } from '@/composables/useClientPagination'
  import { useProductTable } from '@/composables/useProductTable'
  import { useTableColumns } from '@/hooks/core/useTableColumns'
  import type { ColumnOption } from '@/types/component'

  defineOptions({ name: 'PlatformUsers' })

  const { t } = useI18n()
  const { tableSize, isZebra, isBorder } = useProductTable()
  const rows = ref<Record<string, unknown>[]>([])
  const {
    current: pageCurrent,
    size: pageSize,
    total: pageTotal,
    pagedRows,
    onPageChange,
    onSizeChange
  } = useClientPagination(rows)
  const roles = ref<Record<string, unknown>[]>([])
  const loading = ref(false)

  const { columnChecks, resetColumns } = useTableColumns(() => {
    const cols: ColumnOption[] = [
      { prop: 'username', label: t('platformAdmin.users.username'), checked: true },
      { prop: 'displayName', label: t('platformAdmin.users.displayName'), checked: true },
      { prop: 'email', label: t('platformAdmin.users.email'), checked: true },
      { prop: 'status', label: t('platformAdmin.users.status'), checked: true },
      { prop: 'platformRoles', label: t('platformAdmin.users.platformRoles'), checked: true },
      { prop: 'root', label: t('platformAdmin.users.root'), checked: true },
      { prop: '__actions', label: t('platformAdmin.users.actions'), width: 260, fixed: 'right', checked: true, disabled: true }
    ]
    return cols
  })
  watch(() => t('platformAdmin.users.actions'), () => resetColumns())
  function userColVisible(prop: string) {
    return columnChecks.value.some((c) => c.prop === prop && (c.checked ?? c.visible ?? true))
  }

  const createOpen = ref(false)
  const editOpen = ref(false)
  const rolesOpen = ref(false)
  const passwordOpen = ref(false)
  const current = ref<Record<string, unknown> | null>(null)
  const createForm = ref({
    username: '',
    email: '',
    password: '',
    displayName: '',
    roleCodes: [] as string[]
  })
  const editForm = ref({ displayName: '', email: '' })
  const roleCodes = ref<string[]>([])
  const newPassword = ref('')

  async function load() {
    loading.value = true
    try {
      const [users, roleList] = await Promise.all([fetchPlatformUsers(), fetchPlatformRoles()])
      rows.value = users || []
      roles.value = roleList || []
    } finally {
      loading.value = false
    }
  }

  function openCreate() {
    createForm.value = { username: '', email: '', password: '', displayName: '', roleCodes: [] }
    createOpen.value = true
  }

  async function submitCreate() {
    const username = createForm.value.username.trim()
    const email = createForm.value.email.trim()
    const password = createForm.value.password
    const displayName = createForm.value.displayName.trim()
    if (!username) {
      ElMessage.warning(t('platformAdmin.users.usernameRequired'))
      return
    }
    if (!email) {
      ElMessage.warning(t('platformAdmin.users.emailRequired'))
      return
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      ElMessage.warning(t('platformAdmin.users.emailInvalid'))
      return
    }
    if (password.length < 8) {
      ElMessage.warning(t('platformAdmin.users.passwordMin'))
      return
    }
    await createPlatformUser({
      username,
      email,
      password,
      displayName: displayName || undefined,
      roleCodes: createForm.value.roleCodes
    })
    ElMessage.success(t('platformAdmin.users.created'))
    createOpen.value = false
    await load()
  }

  function openEdit(row: Record<string, unknown>) {
    current.value = row
    editForm.value = {
      displayName: String(row.displayName || ''),
      email: String(row.email || '')
    }
    editOpen.value = true
  }

  async function submitEdit() {
    if (!current.value?.id) return
    const mail = editForm.value.email.trim()
    if (!mail) {
      ElMessage.warning(t('platformAdmin.users.emailRequired'))
      return
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(mail)) {
      ElMessage.warning(t('platformAdmin.users.emailInvalid'))
      return
    }
    await updatePlatformUser(String(current.value.id), {
      displayName: editForm.value.displayName.trim(),
      email: mail
    })
    ElMessage.success(t('platformAdmin.users.updated'))
    editOpen.value = false
    await load()
  }

  function openRoles(row: Record<string, unknown>) {
    current.value = row
    roleCodes.value = Array.isArray(row.platformRoleCodes)
      ? [...(row.platformRoleCodes as string[])]
      : []
    rolesOpen.value = true
  }

  async function submitRoles() {
    if (!current.value?.id) return
    await replacePlatformUserRoles(String(current.value.id), roleCodes.value)
    ElMessage.success(t('platformAdmin.users.rolesUpdated'))
    rolesOpen.value = false
    await load()
  }

  function openPassword(row: Record<string, unknown>) {
    current.value = row
    newPassword.value = ''
    passwordOpen.value = true
  }

  async function submitPassword() {
    if (!current.value?.id) return
    await resetPlatformUserPassword(String(current.value.id), newPassword.value)
    ElMessage.success(t('platformAdmin.users.passwordReset'))
    passwordOpen.value = false
  }

  async function activate(row: Record<string, unknown>) {
    await activatePlatformUser(String(row.id))
    ElMessage.success(t('platformAdmin.users.activated'))
    await load()
  }

  async function suspend(row: Record<string, unknown>) {
    await ElMessageBox.confirm(
      t('platformAdmin.users.suspendConfirm', { username: String(row.username || '') }),
      t('platformAdmin.users.suspendConfirmTitle'),
      { type: 'warning' }
    )
    await suspendPlatformUser(String(row.id))
    ElMessage.success(t('platformAdmin.users.suspended'))
    await load()
  }

  const canManage = (row: Record<string, unknown>) => !row.rootAccount && !row.protectedAccount

  onMounted(load)
</script>

<template>
  <div class="page-card art-card platform-page" v-loading="loading">
    <header class="platform-page__head">
      <div>
        <h2>{{ t('platformAdmin.users.title') }}</h2>
        <p>{{ t('platformAdmin.users.subtitle') }}</p>
      </div>
      <ElButton type="primary" @click="openCreate">{{ t('platformAdmin.users.create') }}</ElButton>
    </header>

    <div class="toolbar art-table-card">
      <ProductTableHeader v-model:columns="columnChecks" :loading="loading" full-class="page-card" @refresh="() => load()" />
    </div>

    <ElTable :data="pagedRows" :size="tableSize" :stripe="isZebra" :border="isBorder">
      <ElTableColumn v-if="userColVisible('username')" prop="username" :label="t('platformAdmin.users.username')" width="140" />
      <ElTableColumn v-if="userColVisible('displayName')" prop="displayName" :label="t('platformAdmin.users.displayName')" />
      <ElTableColumn v-if="userColVisible('email')" prop="email" :label="t('platformAdmin.users.email')" min-width="180" />
      <ElTableColumn v-if="userColVisible('status')" :label="t('platformAdmin.users.status')" width="110">
        <template #default="{ row }">
          {{ platformUserStatusLabel(String(row.status || '')) }}
        </template>
      </ElTableColumn>
      <ElTableColumn v-if="userColVisible('platformRoles')" :label="t('platformAdmin.users.platformRoles')" min-width="180">
        <template #default="{ row }">
          {{ (row.platformRoleCodes || []).join(', ') || '—' }}
        </template>
      </ElTableColumn>
      <ElTableColumn v-if="userColVisible('root')" :label="t('platformAdmin.users.root')" width="80">
        <template #default="{ row }">
          {{ row.rootAccount ? t('common.yes') : t('common.no') }}
        </template>
      </ElTableColumn>
      <ElTableColumn v-if="userColVisible('__actions')" :label="t('platformAdmin.users.actions')" width="260" fixed="right">
        <template #default="{ row }">
          <template v-if="canManage(row)">
            <ElButton link type="primary" @click="openEdit(row)">
              {{ t('platformAdmin.users.edit') }}
            </ElButton>
            <ElButton link type="primary" @click="openRoles(row)">
              {{ t('platformAdmin.users.roles') }}
            </ElButton>
            <ElButton link type="primary" @click="openPassword(row)">
              {{ t('platformAdmin.users.resetPassword') }}
            </ElButton>
            <ElButton
              v-if="row.status !== 'active'"
              link
              type="success"
              @click="activate(row)"
            >
              {{ t('platformAdmin.users.activate') }}
            </ElButton>
            <ElButton v-else link type="danger" @click="suspend(row)">
              {{ t('platformAdmin.users.suspend') }}
            </ElButton>
          </template>
          <span v-else class="muted">{{ t('platformAdmin.users.protected') }}</span>
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

    <ElDialog v-model="createOpen" :title="t('platformAdmin.users.createTitle')" width="480px">
      <ElForm label-position="top" autocomplete="off">
        <input type="text" class="autofill-trap" tabindex="-1" aria-hidden="true" autocomplete="username" />
        <input
          type="password"
          class="autofill-trap"
          tabindex="-1"
          aria-hidden="true"
          autocomplete="current-password"
        />
        <ElFormItem :label="t('platformAdmin.users.username')" required>
          <ElInput
            v-model="createForm.username"
            name="new-user-username"
            autocomplete="off"
          />
        </ElFormItem>
        <ElFormItem :label="t('platformAdmin.users.email')" required>
          <ElInput v-model="createForm.email" name="new-user-email" autocomplete="off" />
        </ElFormItem>
        <ElFormItem :label="t('platformAdmin.users.initialPassword')" required>
          <ElInput
            v-model="createForm.password"
            type="password"
            show-password
            name="new-user-password"
            autocomplete="new-password"
            :placeholder="t('platformAdmin.users.initialPasswordPh')"
          />
        </ElFormItem>
        <ElFormItem :label="t('platformAdmin.users.displayName')">
          <ElInput v-model="createForm.displayName" name="new-user-display" autocomplete="off" />
        </ElFormItem>
        <ElFormItem :label="t('platformAdmin.users.platformRoles')">
          <ElSelect v-model="createForm.roleCodes" multiple filterable style="width: 100%">
            <ElOption
              v-for="r in roles"
              :key="String(r.code)"
              :label="`${r.code} · ${r.name}`"
              :value="String(r.code)"
            />
          </ElSelect>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="createOpen = false">{{ t('platformAdmin.cancel') }}</ElButton>
        <ElButton type="primary" @click="submitCreate">{{ t('platformAdmin.create') }}</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="editOpen" :title="t('platformAdmin.users.editTitle')" width="420px">
      <ElForm label-position="top">
        <ElFormItem :label="t('platformAdmin.users.displayName')">
          <ElInput v-model="editForm.displayName" />
        </ElFormItem>
        <ElFormItem :label="t('platformAdmin.users.email')" required>
          <ElInput v-model="editForm.email" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="editOpen = false">{{ t('platformAdmin.cancel') }}</ElButton>
        <ElButton type="primary" @click="submitEdit">{{ t('platformAdmin.save') }}</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="rolesOpen" :title="t('platformAdmin.users.rolesTitle')" width="480px">
      <ElSelect v-model="roleCodes" multiple filterable style="width: 100%">
        <ElOption
          v-for="r in roles"
          :key="String(r.code)"
          :label="`${r.code} · ${r.name}`"
          :value="String(r.code)"
        />
      </ElSelect>
      <template #footer>
        <ElButton @click="rolesOpen = false">{{ t('platformAdmin.cancel') }}</ElButton>
        <ElButton type="primary" @click="submitRoles">{{ t('platformAdmin.save') }}</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="passwordOpen" :title="t('platformAdmin.users.passwordTitle')" width="420px">
      <ElForm autocomplete="off" @submit.prevent>
        <input type="password" class="autofill-trap" tabindex="-1" aria-hidden="true" autocomplete="current-password" />
        <ElInput
          v-model="newPassword"
          type="password"
          show-password
          name="reset-user-password"
          autocomplete="new-password"
          :placeholder="t('platformAdmin.users.passwordPlaceholder')"
        />
      </ElForm>
      <template #footer>
        <ElButton @click="passwordOpen = false">{{ t('platformAdmin.cancel') }}</ElButton>
        <ElButton type="primary" :disabled="newPassword.length < 8" @click="submitPassword">
          {{ t('platformAdmin.confirm') }}
        </ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
  .platform-page {
    padding: 20px;
  }

  .platform-page__head {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 16px;
  }

  .platform-page__head h2 {
    margin: 0;
    font-size: 20px;
    font-weight: 650;
  }

  .platform-page__head p {
    margin: 4px 0 0;
    color: var(--el-text-color-secondary);
    font-size: 13px;
    max-width: 640px;
  }

  .muted {
    color: var(--el-text-color-secondary);
  }

  .autofill-trap {
    position: absolute;
    left: -9999px;
    width: 1px;
    height: 1px;
    opacity: 0;
    pointer-events: none;
  }
</style>
