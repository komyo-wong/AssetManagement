<script setup lang="ts">
  import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { storeToRefs } from 'pinia'
  import { useTenantContextStore } from '@/store/modules/tenant-context'
  import { mapsApi, zonesApi, type NamedResource, type ResourceUpsert } from '@/api/asset-platform'
  import { formatDateTime } from '@/utils/datetime'
  import { useRouter } from 'vue-router'
  import TablePager from '@/components/business/TablePager.vue'
  import ProductTableHeader from '@/components/business/ProductTableHeader.vue'
  import { useClientPagination } from '@/composables/useClientPagination'
  import { useProductTable } from '@/composables/useProductTable'
  import { useTableColumns } from '@/hooks/core/useTableColumns'
  import type { ColumnOption } from '@/types/component'

  const { t } = useI18n()

  const router = useRouter()
  const { projectScope } = storeToRefs(useTenantContextStore())
  const { tableSize, isZebra, isBorder } = useProductTable()
  const loading = ref(false)
  const rows = ref<NamedResource[]>([])
  const maps = ref<NamedResource[]>([])
  const keyword = ref('')
  const drawer = ref(false)
  const editing = ref<NamedResource | null>(null)
  const form = ref<ResourceUpsert>({ code: '', name: '', description: '', fields: { mapId: null } })

  const { columnChecks, resetColumns } = useTableColumns(() => {
    const cols: ColumnOption[] = [
      { prop: 'code', label: t('common.code'), checked: true },
      { prop: 'name', label: t('common.name'), checked: true },
      { prop: 'map', label: t('product.devices.zones.colMap'), checked: true },
      { prop: 'status', label: t('common.status'), checked: true },
      { prop: 'updatedAt', label: t('common.updatedAt'), checked: true },
      { prop: '__actions', label: t('common.actions'), width: 120, fixed: 'right', checked: true, disabled: true }
    ]
    return cols
  })
  watch(() => t('common.actions'), () => resetColumns())
  function zoneColVisible(prop: string) {
    return columnChecks.value.some((c) => c.prop === prop && (c.checked ?? c.visible ?? true))
  }

  const mapOptions = computed(() =>
    maps.value.map((m) => ({
      label: `${m.name} (${m.code})`,
      value: m.id
    }))
  )

  const filteredRows = computed(() => {
    const q = keyword.value.trim().toLowerCase()
    if (!q) return rows.value
    return rows.value.filter((row) => {
      const hay = [row.code, row.name, row.status, row.fields?.mapName]
        .filter((v) => v != null && v !== '')
        .map((v) => String(v).toLowerCase())
      return hay.some((v) => v.includes(q))
    })
  })

  const {
    current: pageCurrent,
    size: pageSize,
    total: pageTotal,
    pagedRows,
    onPageChange,
    onSizeChange
  } = useClientPagination(filteredRows, { resetOn: keyword })

  async function loadMaps() {
    if (!projectScope.value) {
      maps.value = []
      return
    }
    try {
      maps.value = await mapsApi.list(projectScope.value)
    } catch {
      maps.value = []
    }
  }

  async function load() {
    if (!projectScope.value) {
      rows.value = []
      return
    }
    loading.value = true
    try {
      rows.value = await zonesApi.list(projectScope.value)
    } catch {
      rows.value = []
    } finally {
      loading.value = false
    }
  }

  function openCreate() {
    editing.value = null
    form.value = { code: '', name: '', description: '', fields: { mapId: null } }
    void loadMaps()
    drawer.value = true
  }

  function openEdit(row: NamedResource) {
    editing.value = row
    form.value = {
      code: row.code,
      name: row.name,
      description: String(row.fields?.description ?? ''),
      fields: { mapId: (row.fields?.mapId as string | null | undefined) || null }
    }
    void loadMaps()
    drawer.value = true
  }

  async function save() {
    if (!projectScope.value) return
    if (!form.value.code?.trim() || !form.value.name?.trim()) {
      ElMessage.warning(t('product.devices.zones.needCodeName'))
      return
    }
    try {
      const payload: ResourceUpsert = {
        code: form.value.code.trim(),
        name: form.value.name.trim(),
        description: form.value.description,
        fields: { mapId: form.value.fields?.mapId || null }
      }
      if (editing.value) {
        await zonesApi.update(projectScope.value, editing.value.id, payload)
      } else {
        await zonesApi.create(projectScope.value, payload)
      }
      drawer.value = false
      ElMessage.success(t('common.saveSuccess'))
      await load()
    } catch {
      // toasted
    }
  }

  async function doDelete(row: NamedResource) {
    if (!projectScope.value) return
    try {
      await ElMessageBox.confirm(t('product.devices.zones.deleteConfirm', { code: row.code, name: row.name }), t('common.deleteConfirm'), {
        type: 'warning',
        confirmButtonText: t('common.delete'),
        cancelButtonText: t('common.cancel')
      })
      await zonesApi.remove(projectScope.value, row.id)
      ElMessage.success(t('common.deleted'))
      await load()
    } catch {
      // cancelled
    }
  }

  watch(
    projectScope,
    () => {
      void load()
      void loadMaps()
    },
    { immediate: true }
  )
</script>

<template>
  <div class="page-card art-card">
    <div class="header">
      <div>
        <h2>{{ t('product.devices.zones.title') }}</h2>
        <p>{{ t('product.devices.zones.subtitle') }}</p>
      </div>
      <ElButton type="primary" :disabled="!projectScope" @click="openCreate">{{ t('product.devices.zones.create') }}</ElButton>
    </div>

      <div class="toolbar art-table-card">
        <ProductTableHeader v-model:columns="columnChecks" :loading="loading" full-class="page-card" @refresh="() => load()">
          <template #left>
            <div class="toolbar-left">
              <ElInput v-model="keyword" clearable :placeholder="t('product.devices.zones.searchPh')" style="max-width: 320px" />
              <span class="toolbar-count">{{ t('common.totalCount', { count: filteredRows.length }) }}</span>
            </div>
          </template>
        </ProductTableHeader>
      </div>
      <ElTable v-loading="loading" :data="pagedRows" :size="tableSize" :stripe="isZebra" :border="isBorder">
        <ElTableColumn v-if="zoneColVisible('code')" prop="code" :label="t('common.code')" width="140" show-overflow-tooltip />
        <ElTableColumn v-if="zoneColVisible('name')" prop="name" :label="t('common.name')" show-overflow-tooltip />
        <ElTableColumn v-if="zoneColVisible('map')" :label="t('product.devices.zones.colMap')" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.fields?.mapName || '-' }}</template>
        </ElTableColumn>
        <ElTableColumn v-if="zoneColVisible('status')" prop="status" :label="t('common.status')" width="120" />
        <ElTableColumn v-if="zoneColVisible('updatedAt')" :label="t('common.updatedAt')" width="180">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </ElTableColumn>
        <ElTableColumn v-if="zoneColVisible('__actions')" :label="t('common.actions')" width="120" fixed="right">
          <template #default="{ row }">
            <ElButton link type="primary" @click="openEdit(row)">{{ t('common.edit') }}</ElButton>
            <ElButton link type="danger" @click="doDelete(row)">{{ t('common.delete') }}</ElButton>
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
    

    <ElDrawer v-model="drawer" :title="editing ? t('product.devices.zones.drawerEdit') : t('product.devices.zones.drawerCreate')" size="420px">
      <ElForm label-position="top">
        <ElFormItem :label="t('common.code')" required>
          <ElInput v-model="form.code" :disabled="!!editing" />
        </ElFormItem>
        <ElFormItem :label="t('common.name')" required>
          <ElInput v-model="form.name" />
        </ElFormItem>
        <ElFormItem :label="t('common.description')">
          <ElInput v-model="form.description" type="textarea" />
        </ElFormItem>
        <ElFormItem :label="t('product.devices.zones.colMap')">
          <ElSelect
            :model-value="(form.fields?.mapId as string | null | undefined) ?? ''"
            clearable
            filterable
            :placeholder="t('product.devices.zones.mapPh')"
            style="width: 100%"
            @update:model-value="(v: string | null) => (form.fields = { ...form.fields, mapId: v || null })"
          >
            <ElOption v-for="opt in mapOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </ElSelect>
          <div v-if="!mapOptions.length" class="empty-hint">
            {{ t('product.devices.zones.noMaps') }}
            <ElButton link type="primary" @click="router.push('/product/devices/maps')">{{ t('product.devices.zones.goCreateMap') }}</ElButton>
          </div>
        </ElFormItem>
        <ElButton type="primary" @click="save">{{ t('common.save') }}</ElButton>
      </ElForm>
    </ElDrawer>
  </div>
</template>

<style scoped>
  .page-card {
    padding: 20px;
  }
  .header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 16px;
  }
  .toolbar {
    margin-bottom: 12px;
  }
  .toolbar-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .toolbar-count {
    font-size: 13px;
    color: var(--el-text-color-secondary);
    white-space: nowrap;
    flex-shrink: 0;
  }
  h2 {
    margin: 0 0 4px;
  }
  p {
    margin: 0;
    color: var(--el-text-color-secondary);
  }
  .empty-hint {
    margin-top: 8px;
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }
</style>
