<script setup lang="ts">
  import { computed, onActivated, ref, watch } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { useRouter } from 'vue-router'
  import { storeToRefs } from 'pinia'
  import { fetchGeotagDevices } from '@/api/asset-platform'
  import ProductTableHeader from '@/components/business/ProductTableHeader.vue'
  import TablePager from '@/components/business/TablePager.vue'
  import { useClientPagination } from '@/composables/useClientPagination'
  import { useTableColumns } from '@/hooks/core/useTableColumns'
  import { useTenantContextStore } from '@/store/modules/tenant-context'
  import { useTableStore } from '@/store/modules/table'
  import type { ColumnOption } from '@/types/component'
  import { formatDateTime } from '@/utils/datetime'

  defineOptions({ name: 'GeotagDevices' })

  type DeviceRow = Record<string, unknown>

  const { t } = useI18n()
  const router = useRouter()
  const { projectScope } = storeToRefs(useTenantContextStore())
  const tableStore = useTableStore()
  const { tableSize, isZebra, isBorder } = storeToRefs(tableStore)
  const loading = ref(false)
  const items = ref<DeviceRow[]>([])
  const mock = ref(false)
  const cloudSyncError = ref('')
  const filterKeyword = ref('')
  const filterTypeId = ref('')
  const filterStatus = ref('ALL')
  const filterPoint = ref('ALL')
  let loadInFlight = false

  const { columnChecks, resetColumns } = useTableColumns(() => {
    const cols: ColumnOption[] = [
      { prop: 'asset', label: t('product.geotag.asset'), checked: true },
      { prop: 'assetType', label: t('product.geotag.assetType'), checked: true },
      { prop: 'sn', label: t('product.geotag.sn'), checked: true },
      { prop: 'status', label: t('product.geotag.status'), checked: true },
      { prop: 'battery', label: t('product.geotag.battery'), checked: true },
      { prop: 'address', label: t('product.geotag.address'), checked: true },
      { prop: 'locationTime', label: t('product.geotag.locationTime'), checked: true }
    ]
    return cols
  })
  watch(() => t('product.geotag.asset'), () => resetColumns())

  function colVisible(prop: string) {
    return columnChecks.value.some((c) => c.prop === prop && (c.checked ?? c.visible ?? true))
  }

  const typeOptions = computed(() => {
    const names = new Map<string, string>()
    for (const item of items.value) {
      const id = String(item.assetTypeId || '__none__')
      names.set(id, String(item.assetTypeName || t('product.geotag.unclassified')))
    }
    return [...names.entries()].map(([id, name]) => ({ id, name }))
  })

  const hasActiveFilters = computed(
    () =>
      !!filterKeyword.value.trim()
      || !!filterTypeId.value
      || filterStatus.value !== 'ALL'
      || filterPoint.value !== 'ALL'
  )

  function hasPoint(row: DeviceRow) {
    return typeof row.lat === 'number' && typeof row.lng === 'number'
  }

  const filteredRows = computed(() => {
    const q = filterKeyword.value.trim().toLowerCase()
    return items.value.filter((row) => {
      if (filterTypeId.value) {
        const id = String(row.assetTypeId || '__none__')
        if (id !== filterTypeId.value) return false
      }
      if (filterStatus.value === 'ON' && row.cloudStatus !== 1) return false
      if (filterStatus.value === 'OFF' && row.cloudStatus !== 0) return false
      if (filterStatus.value === 'UNKNOWN' && row.cloudStatus !== null && row.cloudStatus !== undefined) {
        return false
      }
      if (filterPoint.value === 'YES' && !hasPoint(row)) return false
      if (filterPoint.value === 'NO' && hasPoint(row)) return false
      if (q) {
        const hay = [
          row.assetName,
          row.sn,
          row.address,
          row.assetTypeName,
          row.beaconMac,
          row.cloudMac
        ]
          .map((value) => String(value || '').toLowerCase())
          .join(' ')
        if (!hay.includes(q)) return false
      }
      return true
    })
  })

  const filterKey = computed(
    () =>
      `${filterKeyword.value}|${filterTypeId.value}|${filterStatus.value}|${filterPoint.value}`
  )
  const {
    current: pageCurrent,
    size: pageSize,
    total: pageTotal,
    pagedRows,
    onPageChange,
    onSizeChange
  } = useClientPagination(filteredRows, { resetOn: filterKey })

  async function load(silent = false) {
    if (!projectScope.value) {
      items.value = []
      return
    }
    if (loadInFlight) return
    loadInFlight = true
    if (!silent) loading.value = true
    try {
      const data = await fetchGeotagDevices(projectScope.value)
      items.value = Array.isArray(data.items) ? (data.items as DeviceRow[]) : []
      mock.value = !!data.mock
      cloudSyncError.value = String(data.cloudSyncError || '')
    } finally {
      loadInFlight = false
      if (!silent) loading.value = false
    }
  }

  function statusText(row: DeviceRow) {
    if (row.cloudStatus === 1) return t('product.geotag.enabled')
    if (row.cloudStatus === 0) return t('product.geotag.disabled')
    return '—'
  }

  function resetFilters() {
    filterKeyword.value = ''
    filterTypeId.value = ''
    filterStatus.value = 'ALL'
    filterPoint.value = 'ALL'
  }

  function openTrack(row: DeviceRow) {
    const sn = String(row.sn || '').trim()
    if (!sn) return
    void router.push({ name: 'GeotagTrack', query: { sn } })
  }

  watch(projectScope, () => {
    void load()
  })

  onActivated(() => {
    void load()
  })
</script>

<template>
  <div class="page-card art-card geotag-devices">
    <div class="head">
      <div>
        <h2>{{ t('menus.geotag.devices') }}</h2>
        <p v-if="mock" class="hint">{{ t('product.geotag.mock') }}</p>
        <p v-if="cloudSyncError" class="warn">{{ t('product.geotag.cloudSyncError') }}</p>
      </div>
    </div>

    <div class="filter-bar art-table-card">
      <ProductTableHeader
        v-model:columns="columnChecks"
        :loading="loading"
        full-class="page-card"
        @refresh="() => load()"
      >
        <template #left>
          <div class="filter-bar__left">
            <ElInput
              v-model="filterKeyword"
              clearable
              :placeholder="t('product.geotag.searchPh')"
              style="width: 220px"
            />
            <ElSelect
              v-model="filterTypeId"
              clearable
              filterable
              :placeholder="t('product.geotag.assetType')"
              style="width: 180px"
            >
              <ElOption v-for="opt in typeOptions" :key="opt.id" :label="opt.name" :value="opt.id" />
            </ElSelect>
            <ElSelect v-model="filterStatus" :placeholder="t('product.geotag.status')" style="width: 140px">
              <ElOption :label="t('product.geotag.allStatus')" value="ALL" />
              <ElOption :label="t('product.geotag.enabled')" value="ON" />
              <ElOption :label="t('product.geotag.disabled')" value="OFF" />
              <ElOption :label="t('product.geotag.statusUnknown')" value="UNKNOWN" />
            </ElSelect>
            <ElSelect v-model="filterPoint" :placeholder="t('product.geotag.pointFilter')" style="width: 130px">
              <ElOption :label="t('product.geotag.allPoints')" value="ALL" />
              <ElOption :label="t('product.geotag.hasPoint')" value="YES" />
              <ElOption :label="t('product.geotag.noPointFilter')" value="NO" />
            </ElSelect>
            <ElButton v-if="hasActiveFilters" @click="resetFilters">
              {{ t('product.assets.list.clearFilters') }}
            </ElButton>
            <span class="filter-bar__count">
              {{ t('product.assets.list.filterCount', { filtered: filteredRows.length, total: items.length }) }}
            </span>
          </div>
        </template>
      </ProductTableHeader>
    </div>

    <ElTable
      v-loading="loading"
      :data="pagedRows"
      :size="tableSize"
      :stripe="isZebra"
      :border="isBorder"
    >
      <ElTableColumn
        v-if="colVisible('asset')"
        :label="t('product.geotag.asset')"
        min-width="140"
        sortable
        :sort-method="(a: DeviceRow, b: DeviceRow) => String(a.assetName || '').localeCompare(String(b.assetName || ''))"
      >
        <template #default="{ row }">
          <ElButton link type="primary" @click="openTrack(row)">
            {{ row.assetName || row.sn }}
          </ElButton>
        </template>
      </ElTableColumn>
      <ElTableColumn v-if="colVisible('assetType')" :label="t('product.geotag.assetType')" min-width="120">
        <template #default="{ row }">{{ row.assetTypeName || t('product.geotag.unclassified') }}</template>
      </ElTableColumn>
      <ElTableColumn v-if="colVisible('sn')" :label="t('product.geotag.sn')" prop="sn" min-width="160" sortable />
      <ElTableColumn v-if="colVisible('status')" :label="t('product.geotag.status')" min-width="100">
        <template #default="{ row }">{{ statusText(row) }}</template>
      </ElTableColumn>
      <ElTableColumn v-if="colVisible('battery')" :label="t('product.geotag.battery')" width="90" sortable>
        <template #default="{ row }">{{ row.battery == null ? '—' : `${row.battery}%` }}</template>
      </ElTableColumn>
      <ElTableColumn v-if="colVisible('address')" :label="t('product.geotag.address')" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.address || '—' }}</template>
      </ElTableColumn>
      <ElTableColumn v-if="colVisible('locationTime')" :label="t('product.geotag.locationTime')" min-width="170" sortable>
        <template #default="{ row }">{{ formatDateTime(row.locationTime as string) }}</template>
      </ElTableColumn>
      <template #empty>{{ t('product.geotag.empty') }}</template>
    </ElTable>

    <TablePager
      :total="pageTotal"
      :current="pageCurrent"
      :size="pageSize"
      @update:current="onPageChange"
      @update:size="onSizeChange"
    />
  </div>
</template>

<style scoped>
  .geotag-devices {
    padding: 20px;
  }

  .head {
    margin-bottom: 16px;
  }

  .head h2 {
    margin: 0 0 4px;
  }

  .hint,
  .warn {
    margin: 0;
    color: var(--el-text-color-secondary);
  }

  .warn {
    color: var(--el-color-warning);
  }

  .filter-bar {
    margin-bottom: 14px;
  }

  .filter-bar__left {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 10px;
  }

  .filter-bar__count {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }
</style>
