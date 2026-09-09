<script setup lang="ts">
  import { computed, onMounted, ref, watch } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import {
    createPlatformRole,
    deletePlatformRole,
    fetchPlatformPermissions,
    fetchPlatformRoles,
    updatePlatformRole
  } from '@/api/asset-platform'
  import {
    isPlatformAdminAssignablePermission,
    permissionAction,
    permissionActionLabel,
    permissionFeatureLabel,
    permissionModuleLabel,
    permissionResource,
    platformScopeLabel
  } from '@/utils/permission-i18n'
  import TablePager from '@/components/business/TablePager.vue'
  import ProductTableHeader from '@/components/business/ProductTableHeader.vue'
  import { useClientPagination } from '@/composables/useClientPagination'
  import { useProductTable } from '@/composables/useProductTable'
  import { useTableColumns } from '@/hooks/core/useTableColumns'
  import type { ColumnOption } from '@/types/component'

  defineOptions({ name: 'PlatformRoles' })

  type PermRow = Record<string, unknown>

  type FeatureGroup = {
    resource: string
    label: string
    actions: Array<{ code: string; action: string; label: string }>
  }

  type ModuleGroup = {
    module: string
    label: string
    features: FeatureGroup[]
    codes: string[]
  }

  const { t, locale } = useI18n()
  const { tableSize, isZebra, isBorder } = useProductTable()
  const rows = ref<PermRow[]>([])
  const {
    current: pageCurrent,
    size: pageSize,
    total: pageTotal,
    pagedRows,
    onPageChange,
    onSizeChange
  } = useClientPagination(rows)
  const permissions = ref<PermRow[]>([])
  const loading = ref(false)
  const open = ref(false)
  const editing = ref<PermRow | null>(null)
  const form = ref({ code: '', name: '', permissionCodes: [] as string[] })

  const { columnChecks, resetColumns } = useTableColumns(() => {
    const cols: ColumnOption[] = [
      { prop: 'code', label: t('platformAdmin.roles.code'), checked: true },
      { prop: 'name', label: t('platformAdmin.roles.name'), checked: true },
      { prop: 'scope', label: t('platformAdmin.roles.scope'), checked: true },
      { prop: 'system', label: t('platformAdmin.roles.system'), checked: true },
      { prop: 'protected', label: t('platformAdmin.roles.protected'), checked: true },
      { prop: 'permissionCount', label: t('platformAdmin.roles.permissionCount'), checked: true },
      { prop: '__actions', label: t('platformAdmin.roles.actions'), width: 120, fixed: 'right', checked: true, disabled: true }
    ]
    return cols
  })
  watch(() => t('platformAdmin.roles.actions'), () => resetColumns())
  function roleColVisible(prop: string) {
    return columnChecks.value.some((c) => c.prop === prop && (c.checked ?? c.visible ?? true))
  }

  const selected = computed({
    get: () => new Set(form.value.permissionCodes),
    set: (set: Set<string>) => {
      form.value.permissionCodes = [...set]
    }
  })

  const moduleGroups = computed(() => {
    void locale.value
    const byModule = new Map<string, PermRow[]>()
    for (const p of permissions.value) {
      const code = String(p.code || '')
      if (!code || !isPlatformAdminAssignablePermission(code)) continue
      const module = String(p.module || 'other')
      if (!byModule.has(module)) byModule.set(module, [])
      byModule.get(module)!.push(p)
    }

    const groups: ModuleGroup[] = []
    for (const [module, items] of [...byModule.entries()].sort(([a], [b]) => a.localeCompare(b))) {
      const byResource = new Map<string, PermRow[]>()
      for (const item of items) {
        const resource = permissionResource(String(item.code))
        if (!byResource.has(resource)) byResource.set(resource, [])
        byResource.get(resource)!.push(item)
      }
      const features: FeatureGroup[] = [...byResource.entries()]
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([resource, perms]) => ({
          resource,
          label: permissionFeatureLabel(resource),
          actions: [...perms]
            .sort((a, b) => String(a.code).localeCompare(String(b.code)))
            .map((p) => {
              const code = String(p.code)
              const action = permissionAction(code)
              return {
                code,
                action,
                label: permissionActionLabel(action)
              }
            })
        }))
      const codes = features.flatMap((f) => f.actions.map((a) => a.code))
      groups.push({
        module,
        label: permissionModuleLabel(module),
        features,
        codes
      })
    }
    return groups
  })

  function hasCode(code: string) {
    return selected.value.has(code)
  }

  function toggleCode(code: string, on: boolean) {
    const next = new Set(selected.value)
    if (on) next.add(code)
    else next.delete(code)
    selected.value = next
  }

  function featureState(feature: FeatureGroup) {
    const codes = feature.actions.map((a) => a.code)
    const hit = codes.filter((c) => selected.value.has(c)).length
    return {
      checked: hit === codes.length && codes.length > 0,
      indeterminate: hit > 0 && hit < codes.length
    }
  }

  function moduleState(group: ModuleGroup) {
    const hit = group.codes.filter((c) => selected.value.has(c)).length
    return {
      checked: hit === group.codes.length && group.codes.length > 0,
      indeterminate: hit > 0 && hit < group.codes.length
    }
  }

  function toggleFeature(feature: FeatureGroup, on: boolean) {
    const next = new Set(selected.value)
    for (const a of feature.actions) {
      if (on) next.add(a.code)
      else next.delete(a.code)
    }
    selected.value = next
  }

  function toggleModule(group: ModuleGroup, on: boolean) {
    const next = new Set(selected.value)
    for (const code of group.codes) {
      if (on) next.add(code)
      else next.delete(code)
    }
    selected.value = next
  }

  async function load() {
    loading.value = true
    try {
      const [roleList, permList] = await Promise.all([
        fetchPlatformRoles(),
        fetchPlatformPermissions()
      ])
      rows.value = roleList || []
      permissions.value = permList || []
    } finally {
      loading.value = false
    }
  }

  function openCreate() {
    editing.value = null
    form.value = { code: '', name: '', permissionCodes: [] }
    open.value = true
  }

  function openEdit(row: PermRow) {
    editing.value = row
    const codes = Array.isArray(row.permissionCodes) ? [...(row.permissionCodes as string[])] : []
    form.value = {
      code: String(row.code || ''),
      name: String(row.name || ''),
      permissionCodes: codes.filter((c) => isPlatformAdminAssignablePermission(c))
    }
    open.value = true
  }

  async function save() {
    if (editing.value) {
      await updatePlatformRole(String(editing.value.id), {
        name: form.value.name,
        permissionCodes: form.value.permissionCodes
      })
    } else {
      await createPlatformRole(form.value)
    }
    ElMessage.success(t('platformAdmin.roles.saved'))
    open.value = false
    await load()
  }

  async function remove(row: PermRow) {
    if (row.protectedRole || row.systemRole) {
      ElMessage.warning(t('platformAdmin.roles.cannotDelete'))
      return
    }
    await ElMessageBox.confirm(
      t('platformAdmin.roles.deleteConfirm', { name: String(row.name || row.code || '') }),
      t('platformAdmin.roles.deleteConfirmTitle'),
      { type: 'warning' }
    )
    await deletePlatformRole(String(row.id))
    ElMessage.success(t('platformAdmin.roles.deleted'))
    await load()
  }

  onMounted(load)
</script>

<template>
  <div class="page-card art-card platform-page" v-loading="loading">
    <header class="platform-page__head">
      <div>
        <h2>{{ t('platformAdmin.roles.title') }}</h2>
        <p>{{ t('platformAdmin.roles.subtitle') }}</p>
      </div>
      <ElButton type="primary" @click="openCreate">{{ t('platformAdmin.roles.create') }}</ElButton>
    </header>

    <div class="toolbar art-table-card">
      <ProductTableHeader v-model:columns="columnChecks" :loading="loading" full-class="page-card" @refresh="() => load()" />
    </div>

    <ElTable :data="pagedRows" :size="tableSize" :stripe="isZebra" :border="isBorder">
      <ElTableColumn v-if="roleColVisible('code')" prop="code" :label="t('platformAdmin.roles.code')" width="180" />
      <ElTableColumn v-if="roleColVisible('name')" prop="name" :label="t('platformAdmin.roles.name')" />
      <ElTableColumn v-if="roleColVisible('scope')" :label="t('platformAdmin.roles.scope')" width="120">
        <template #default="{ row }">
          {{ platformScopeLabel(String(row.scope || '')) }}
        </template>
      </ElTableColumn>
      <ElTableColumn v-if="roleColVisible('system')" :label="t('platformAdmin.roles.system')" width="90">
        <template #default="{ row }">
          {{ row.systemRole ? t('common.yes') : t('common.no') }}
        </template>
      </ElTableColumn>
      <ElTableColumn v-if="roleColVisible('protected')" :label="t('platformAdmin.roles.protected')" width="100">
        <template #default="{ row }">
          {{ row.protectedRole ? t('common.yes') : t('common.no') }}
        </template>
      </ElTableColumn>
      <ElTableColumn v-if="roleColVisible('permissionCount')" :label="t('platformAdmin.roles.permissionCount')" width="100">
        <template #default="{ row }">
          {{ Array.isArray(row.permissionCodes) ? row.permissionCodes.length : 0 }}
        </template>
      </ElTableColumn>
      <ElTableColumn v-if="roleColVisible('__actions')" :label="t('platformAdmin.roles.actions')" width="120" fixed="right">
        <template #default="{ row }">
          <ElButton link type="primary" :disabled="!!row.protectedRole" @click="openEdit(row)">
            {{ t('platformAdmin.roles.edit') }}
          </ElButton>
          <ElButton
            link
            type="danger"
            :disabled="!!(row.protectedRole || row.systemRole)"
            @click="remove(row)"
          >
            {{ t('platformAdmin.roles.delete') }}
          </ElButton>
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

    <ElDialog
      v-model="open"
      :title="editing ? t('platformAdmin.roles.editTitle') : t('platformAdmin.roles.createTitle')"
      width="820px"
      top="5vh"
      class="role-dialog"
    >
      <ElForm label-position="top">
        <ElFormItem :label="t('platformAdmin.roles.code')" required>
          <ElInput v-model="form.code" :disabled="!!editing" />
        </ElFormItem>
        <ElFormItem :label="t('platformAdmin.roles.name')" required>
          <ElInput v-model="form.name" />
        </ElFormItem>
        <ElFormItem :label="t('platformAdmin.roles.permissions')">
          <p class="hint">{{ t('platformAdmin.roles.permissionsHint') }}</p>
          <div v-if="!moduleGroups.length" class="empty">
            {{ t('platformAdmin.roles.noPermissions') }}
          </div>
          <div v-else class="matrix">
            <section v-for="group in moduleGroups" :key="group.module" class="matrix__module">
              <header class="matrix__module-head">
                <ElCheckbox
                  :model-value="moduleState(group).checked"
                  :indeterminate="moduleState(group).indeterminate"
                  @change="(v: boolean | string | number) => toggleModule(group, !!v)"
                >
                  <strong>{{ group.label }}</strong>
                </ElCheckbox>
              </header>
              <div class="matrix__features">
                <div v-for="feature in group.features" :key="feature.resource" class="matrix__feature">
                  <ElCheckbox
                    class="feature-all"
                    :model-value="featureState(feature).checked"
                    :indeterminate="featureState(feature).indeterminate"
                    @change="(v: boolean | string | number) => toggleFeature(feature, !!v)"
                  >
                    {{ feature.label }}
                  </ElCheckbox>
                  <div class="matrix__actions">
                    <ElCheckbox
                      v-for="action in feature.actions"
                      :key="action.code"
                      :model-value="hasCode(action.code)"
                      @change="(v: boolean | string | number) => toggleCode(action.code, !!v)"
                    >
                      {{ action.label }}
                    </ElCheckbox>
                  </div>
                </div>
              </div>
            </section>
          </div>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="open = false">{{ t('platformAdmin.cancel') }}</ElButton>
        <ElButton type="primary" @click="save">{{ t('platformAdmin.save') }}</ElButton>
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
    max-width: 720px;
  }

  .hint {
    margin: 0 0 10px;
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }

  .matrix {
    max-height: min(52vh, 560px);
    overflow: auto;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 12px;
    padding: 8px;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .matrix__module {
    border: 1px solid var(--el-border-color-extra-light);
    border-radius: 10px;
    padding: 10px 12px;
    background: var(--el-fill-color-blank);
  }

  .matrix__module-head {
    margin-bottom: 8px;
  }

  .matrix__features {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding-left: 8px;
  }

  .matrix__feature {
    display: grid;
    grid-template-columns: minmax(140px, 220px) 1fr;
    gap: 8px 12px;
    align-items: start;
  }

  .matrix__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 4px 14px;
  }

  .empty {
    color: var(--el-text-color-secondary);
    padding: 16px;
  }

  @media (max-width: 720px) {
    .matrix__feature {
      grid-template-columns: 1fr;
    }
  }
</style>
