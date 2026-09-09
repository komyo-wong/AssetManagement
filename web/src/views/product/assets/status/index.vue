<script setup lang="ts">
import { computed, onActivated, onDeactivated, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import BatteryPill from '@/components/business/BatteryPill.vue'
import PresencePill from '@/components/business/PresencePill.vue'
import { useTenantContextStore } from '@/store/modules/tenant-context'
import { assetTypesApi, assetsApi, fetchBeaconScans, gatewaysApi, type NamedResource } from '@/api/asset-platform'
import { asAssetStatusRow, isGatewayAsset } from '@/utils/gateway-as-asset'
import { formatDateTime } from '@/utils/datetime'
import { formatLiveLocation } from '@/utils/locale-labels'
import { queryText } from '@/utils/route-query'
import { usePersistedColumnWidths } from '@/hooks/core/usePersistedColumnWidths'
import { useTableColumns } from '@/hooks/core/useTableColumns'
import { useProductTable } from '@/composables/useProductTable'
import type { ColumnOption } from '@/types/component'
import ProductTableHeader from '@/components/business/ProductTableHeader.vue'
import TablePager from '@/components/business/TablePager.vue'
import { useClientPagination } from '@/composables/useClientPagination'

defineOptions({ name: 'AssetStatus' })

const { t } = useI18n()
const route = useRoute()

const { projectScope } = storeToRefs(useTenantContextStore())
const { tableSize, isZebra, isBorder } = useProductTable()
const loading = ref(false)
const rows = ref<NamedResource[]>([])
const types = ref<NamedResource[]>([])
const filterKeyword = ref('')
const filterTypeId = ref('')
const filterPresence = ref('ALL')
const filterProtocol = ref('ALL')
const filterGateway = ref('')
const filterBattery = ref<'ALL' | 'LOW'>('ALL')
const lastRefreshedAt = ref<Date | null>(null)
const historyOpen = ref(false)
const historyLoading = ref(false)
const historyAsset = ref<NamedResource | null>(null)
const scans = ref<NamedResource[]>([])
const { colWidth, fitWidth, onHeaderDragEnd } = usePersistedColumnWidths('assets-status', {
  code: 130,
  assetType: 120,
  status: 90,
  battery: 120,
  rssi: 100,
  liveLocation: 280,
  zone: 110,
  map: 110,
  location: 110,
  lastGateway: 120,
  boundBeacon: 140,
  lastSeenAt: 170,
  __actions: 100
})

const { columnChecks, resetColumns } = useTableColumns(() => {
  const cols: ColumnOption[] = [
    { prop: 'code', label: t('common.code'), checked: true },
    { prop: 'name', label: t('common.name'), checked: true },
    { prop: 'assetType', label: t('product.assets.types.title'), checked: true },
    { prop: 'status', label: t('common.status'), checked: true },
    { prop: 'battery', label: t('product.assets.list.colBattery'), checked: true },
    { prop: 'rssi', label: 'RSSI', checked: true },
    { prop: 'liveLocation', label: t('product.assets.status.colLiveLocation'), checked: true },
    { prop: 'zone', label: t('product.assets.status.colZone'), checked: true },
    { prop: 'map', label: t('product.assets.status.colMap'), checked: true },
    { prop: 'location', label: t('product.assets.status.colRegisteredLocation'), checked: true },
    { prop: 'lastGateway', label: t('product.assets.list.gatewayPh'), checked: true },
    { prop: 'boundBeacon', label: t('product.assets.list.colBeacon'), checked: true },
    { prop: 'lastSeenAt', label: t('product.assets.list.colLastSeen'), checked: true },
    { prop: '__actions', label: t('common.actions'), fixed: 'right', checked: true, disabled: true }
  ]
  return cols
})
watch(() => t('common.actions'), () => resetColumns())

function statusColVisible(prop: string) {
  return columnChecks.value.some((c) => c.prop === prop && (c.checked ?? c.visible ?? true))
}

let timer: ReturnType<typeof setInterval> | null = null
let historyTimer: ReturnType<typeof setInterval> | null = null
let loadInFlight = false

const refreshHint = computed(() =>
  lastRefreshedAt.value
    ? t('product.assets.list.refreshLive', { time: formatDateTime(lastRefreshedAt.value) })
    : t('product.assets.list.refreshIdle')
)

const GATEWAY_TYPE_FILTER = '__GATEWAY__'

const typeOptions = computed(() => {
  const opts = types.value.map((item) => ({
    id: item.id,
    label: `${item.code} · ${item.name}`
  }))
  if (rows.value.some(isGatewayAsset)) {
    opts.unshift({ id: GATEWAY_TYPE_FILTER, label: t('product.assets.status.gatewayType') })
  }
  return opts
})

const presenceOptions = computed(() => {
  const counts = new Map<string, number>()
  for (const row of rows.value) {
    const status = String(row.status || 'UNKNOWN')
    counts.set(status, (counts.get(status) || 0) + 1)
  }
  const order = ['ONLINE', 'OFFLINE', 'UNBOUND']
  const keys = [
    ...order.filter((k) => counts.has(k)),
    ...[...counts.keys()].filter((k) => !order.includes(k) && k !== 'ARCHIVED').sort()
  ]
  return [
    { label: t('product.assets.status.allPresenceCount', { count: rows.value.length }), value: 'ALL' },
    ...keys.map((status) => ({
      label: `${statusLabel(status)} (${counts.get(status) || 0})`,
      value: status
    }))
  ]
})

const gatewayOptions = computed(() => {
  const map = new Map<string, string>()
  for (const row of rows.value) {
    const name = String(row.fields?.lastGatewayName || '').trim()
    if (name) map.set(name, name)
  }
  return [...map.keys()].sort((a, b) => a.localeCompare(b, 'zh-CN'))
})

const hasActiveFilters = computed(
  () =>
    !!filterKeyword.value.trim() ||
    !!filterTypeId.value ||
    filterPresence.value !== 'ALL' ||
    filterProtocol.value !== 'ALL' ||
    !!filterGateway.value ||
    filterBattery.value !== 'ALL'
)

const filtered = computed(() => {
  const q = filterKeyword.value.trim().toLowerCase()
  return rows.value.filter((row) => {
    if (filterPresence.value !== 'ALL' && row.status !== filterPresence.value) return false
    if (filterTypeId.value) {
      if (filterTypeId.value === GATEWAY_TYPE_FILTER) {
        if (!isGatewayAsset(row)) return false
      } else {
        const typeId = String(row.fields?.assetTypeId || '')
        if (typeId !== filterTypeId.value) return false
      }
    }
    if (filterProtocol.value !== 'ALL') {
      const protocol = String(row.fields?.protocolType || '').toUpperCase()
      if (filterProtocol.value === 'NONE') {
        if (protocol === 'FINDMY' || protocol === 'BEACON' || protocol === 'GATEWAY') return false
      } else if (protocol !== filterProtocol.value) {
        return false
      }
    }
    if (filterGateway.value) {
      if (String(row.fields?.lastGatewayName || '') !== filterGateway.value) return false
    }
    if (filterBattery.value === 'LOW' && !isLowBattery(row)) return false
    if (!q) return true
    const hay = [
      row.code,
      row.name,
      row.fields?.boundBeaconName,
      row.fields?.boundBeaconMac,
      row.fields?.locationLabel,
      row.fields?.lastGatewayName,
      formatLiveLocation(row.fields, t),
      row.fields?.lastZoneName,
      row.fields?.lastMapName,
      row.fields?.lastGatewayName,
      row.fields?.macAddress
    ]
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
} = useClientPagination(filtered, { resetOn: filterKeyword })

const {
  current: scansPageCurrent,
  size: scansPageSize,
  total: scansPageTotal,
  pagedRows: pagedScans,
  onPageChange: onScansPageChange,
  onSizeChange: onScansSizeChange
} = useClientPagination(scans)

const nameColWidth = computed(() =>
  fitWidth(
    'name',
    rows.value.map((r) => r.name),
    { header: t('common.name'), min: 88, max: 200 }
  )
)

function statusLabel(status: string) {
  switch (status) {
    case 'ONLINE':
      return t('product.assets.status.online')
    case 'OFFLINE':
      return t('product.assets.status.offline')
    case 'UNBOUND':
      return t('product.assets.status.unbound')
    default:
      return status
  }
}

function resetFilters() {
  filterKeyword.value = ''
  filterTypeId.value = ''
  filterPresence.value = 'ALL'
  filterProtocol.value = 'ALL'
  filterGateway.value = ''
  filterBattery.value = 'ALL'
}

function isLowBattery(row: NamedResource) {
  const pct = row.fields?.batteryPercent
  if (typeof pct === 'number' && Number.isFinite(pct) && pct <= 20) return true
  const label = String(row.fields?.batteryLabel || '')
  return /low|低|empty|空/i.test(label)
}

function applyRouteFilters() {
  resetFilters()
  const q = queryText(route.query, 'q')
  const presence = queryText(route.query, 'presence').toUpperCase()
  const battery = queryText(route.query, 'battery').toLowerCase()
  const kind = queryText(route.query, 'kind').toUpperCase()
  if (q) filterKeyword.value = q
  if (presence === 'ONLINE' || presence === 'OFFLINE' || presence === 'UNBOUND') {
    filterPresence.value = presence
  }
  if (battery === 'low') filterBattery.value = 'LOW'
  if (kind === 'GATEWAY') filterTypeId.value = GATEWAY_TYPE_FILTER
}

/** Higher = more charge; unknown sorts last. */
function batterySortValue(row: NamedResource) {
  const percent = row.fields?.batteryPercent
  if (percent != null && Number.isFinite(Number(percent))) return Number(percent)
  const level = row.fields?.batteryLevel
  if (level != null && Number.isFinite(Number(level))) {
    return [100, 66, 33, 10][Number(level)] ?? -1
  }
  return -1
}

function rssiText(value: unknown) {
  if (value == null || value === '') return '-'
  const n = Number(value)
  return Number.isFinite(n) ? `${n} dBm` : String(value)
}

function rssiTone(value: unknown) {
  const n = Number(value)
  if (!Number.isFinite(n)) return ''
  if (n >= -65) return 'rssi--strong'
  if (n >= -80) return 'rssi--mid'
  return 'rssi--weak'
}

async function load(silent = false) {
  if (!projectScope.value) {
    rows.value = []
    types.value = []
    return
  }
  if (loadInFlight) return
  loadInFlight = true
  if (!silent) loading.value = true
  try {
    const [assetRows, gatewayRows, typeRows] = await Promise.all([
      assetsApi.list(projectScope.value),
      gatewaysApi.list(projectScope.value),
      assetTypesApi.list(projectScope.value)
    ])
    const gatewayType = t('product.assets.status.gatewayType')
    rows.value = [
      ...assetRows,
      ...gatewayRows
        .filter((row) => row.status !== 'ARCHIVED')
        .map((row) => asAssetStatusRow(row, gatewayType))
    ]
    types.value = typeRows
    lastRefreshedAt.value = new Date()
  } catch {
    if (!silent) {
      rows.value = []
      types.value = []
    }
  } finally {
    loadInFlight = false
    if (!silent) loading.value = false
  }
}

async function loadHistory(silent = false) {
  const beaconId = historyAsset.value?.fields?.boundBeaconId
  if (!projectScope.value || !beaconId) {
    scans.value = []
    return
  }
  if (!silent) historyLoading.value = true
  try {
    const page = await fetchBeaconScans(projectScope.value, String(beaconId), { current: 1, size: 100 })
    scans.value = page.records || []
  } catch {
    if (!silent) scans.value = []
  } finally {
    if (!silent) historyLoading.value = false
  }
}

function clearTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function clearHistoryTimer() {
  if (historyTimer) {
    clearInterval(historyTimer)
    historyTimer = null
  }
}

function setupPolling() {
  clearTimer()
  timer = setInterval(() => {
    if (document.visibilityState === 'hidden') return
    void load(true)
  }, 3000)
}

function setupHistoryPolling() {
  clearHistoryTimer()
  if (!historyOpen.value) return
  historyTimer = setInterval(() => {
    if (document.visibilityState === 'hidden') return
    void loadHistory(true)
  }, 5000)
}

async function openHistory(row: NamedResource) {
  if (!row.fields?.boundBeaconId) return
  historyAsset.value = row
  scans.value = []
  if (historyOpen.value) {
    await onHistoryOpened()
    return
  }
  historyOpen.value = true
}

async function onHistoryOpened() {
  await loadHistory()
  setupHistoryPolling()
}

watch(historyOpen, (open) => {
  if (!open) {
    clearHistoryTimer()
    historyAsset.value = null
    scans.value = []
  }
})

watch(
  projectScope,
  () => {
    resetFilters()
    applyRouteFilters()
    void load()
    setupPolling()
  },
  { immediate: true }
)
watch(
  () => route.query,
  () => applyRouteFilters()
)
onActivated(() => {
  applyRouteFilters()
  void load(true)
  setupPolling()
})
onDeactivated(() => {
  clearTimer()
  clearHistoryTimer()
})
onUnmounted(() => {
  clearTimer()
  clearHistoryTimer()
})
</script>

<template>
  <div class="page-card art-card" style="padding: 20px" v-loading="loading">
    <div style="margin-bottom: 16px">
      <h2 style="margin: 0">{{ t('product.overview.assetStatus') }}</h2>
      <p style="margin: 4px 0 0; color: var(--el-text-color-secondary)">
        {{ t('product.assets.status.subtitle', { refreshHint }) }}
      </p>
    </div>

      <div class="filter-bar art-table-card">
        <ProductTableHeader v-model:columns="columnChecks" :loading="loading" full-class="page-card" @refresh="() => load()">
          <template #left>
            <div class="filter-bar__left">
        <ElInput
          v-model="filterKeyword"
          clearable
          :placeholder="t('product.assets.status.searchPh')"
          style="width: 220px"
        />
        <ElSelect v-model="filterTypeId" clearable filterable :placeholder="t('product.assets.types.title')" style="width: 200px">
          <ElOption v-for="opt in typeOptions" :key="opt.id" :label="opt.label" :value="opt.id" />
        </ElSelect>
        <ElSelect v-model="filterPresence" :placeholder="t('product.assets.list.presencePh')" style="width: 170px">
          <ElOption v-for="opt in presenceOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </ElSelect>
        <ElSelect v-model="filterProtocol" :placeholder="t('product.assets.list.protocolPh')" style="width: 130px">
          <ElOption :label="t('product.assets.list.allProtocols')" value="ALL" />
          <ElOption label="Find My" value="FINDMY" />
          <ElOption label="Beacon" value="BEACON" />
          <ElOption :label="t('product.assets.status.gatewayType')" value="GATEWAY" />
          <ElOption :label="t('product.assets.list.noProtocol')" value="NONE" />
        </ElSelect>
        <ElSelect
          v-model="filterGateway"
          clearable
          filterable
          :placeholder="t('product.assets.list.gatewayPh')"
          style="width: 180px"
        >
          <ElOption v-for="name in gatewayOptions" :key="name" :label="name" :value="name" />
        </ElSelect>
        <ElSelect v-model="filterBattery" :placeholder="t('product.assets.status.batteryPh')" style="width: 130px">
          <ElOption :label="t('product.assets.status.allBattery')" value="ALL" />
          <ElOption :label="t('product.assets.status.lowBattery')" value="LOW" />
        </ElSelect>
        <ElButton v-if="hasActiveFilters" @click="resetFilters">{{ t('product.assets.list.clearFilters') }}</ElButton>
        <span class="filter-bar__count">{{ t('product.assets.status.filterCount', { filtered: filtered.length, total: rows.length }) }}</span>
            </div>
          </template>
        </ProductTableHeader>
      </div>
      <ElTable
        :data="pagedRows"
        :size="tableSize"
        :stripe="isZebra"
        :border="isBorder"
        @header-dragend="onHeaderDragEnd"
      >
        <ElTableColumn v-if="statusColVisible('code')" prop="code" :label="t('common.code')" :width="colWidth('code', 130)" sortable />
        <ElTableColumn v-if="statusColVisible('name')" prop="name" :label="t('common.name')" :width="nameColWidth" sortable show-overflow-tooltip />
        <ElTableColumn
          v-if="statusColVisible('assetType')"
          column-key="assetType"
          :label="t('product.assets.types.title')"
          :width="colWidth('assetType', 120)"
          sortable
          :sort-method="(a: NamedResource, b: NamedResource) => String(a.fields?.assetTypeName || '').localeCompare(String(b.fields?.assetTypeName || ''))"
        >
          <template #default="{ row }">{{ row.fields?.assetTypeName || '-' }}</template>
        </ElTableColumn>
        <ElTableColumn v-if="statusColVisible('status')" prop="status" :label="t('common.status')" :width="colWidth('status', 90)" sortable>
          <template #default="{ row }">
            <PresencePill :status="row.status" />
          </template>
        </ElTableColumn>
        <ElTableColumn
          v-if="statusColVisible('battery')"
          column-key="battery"
          :label="t('product.assets.list.colBattery')"
          :width="colWidth('battery', 120)"
          sortable
          :sort-method="(a: NamedResource, b: NamedResource) => batterySortValue(a) - batterySortValue(b)"
        >
          <template #default="{ row }">
            <BatteryPill
              :protocol-type="row.fields?.protocolType as string | undefined"
              :label="row.fields?.batteryLabel as string | undefined"
              :percent="row.fields?.batteryPercent as number | undefined"
              :level="row.fields?.batteryLevel as number | undefined"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn
          v-if="statusColVisible('rssi')"
          column-key="rssi"
          :label="t('product.assets.status.colSignal')"
          :width="colWidth('rssi', 100)"
          sortable
          :sort-method="(a: NamedResource, b: NamedResource) => Number(a.fields?.lastRssi ?? -999) - Number(b.fields?.lastRssi ?? -999)"
        >
          <template #default="{ row }">
            <span :class="['rssi', rssiTone(row.fields?.lastRssi)]">{{ rssiText(row.fields?.lastRssi) }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn v-if="statusColVisible('liveLocation')" column-key="liveLocation" :label="t('product.assets.status.colLiveLocation')" :min-width="Math.max(Number(colWidth('liveLocation', 240)), 240)" show-overflow-tooltip>
          <template #default="{ row }">{{ formatLiveLocation(row.fields, t) || '-' }}</template>
        </ElTableColumn>
        <ElTableColumn v-if="statusColVisible('zone')" column-key="zone" :label="t('product.assets.status.colZone')" :width="colWidth('zone', 110)" show-overflow-tooltip>
          <template #default="{ row }">{{ row.fields?.lastZoneName || '-' }}</template>
        </ElTableColumn>
        <ElTableColumn v-if="statusColVisible('map')" column-key="map" :label="t('product.assets.status.colMap')" :width="colWidth('map', 110)" show-overflow-tooltip>
          <template #default="{ row }">{{ row.fields?.lastMapName || '-' }}</template>
        </ElTableColumn>
        <ElTableColumn v-if="statusColVisible('location')" column-key="location" :label="t('product.assets.status.colRegisteredLocation')" :width="colWidth('location', 110)" show-overflow-tooltip>
          <template #default="{ row }">{{ row.fields?.locationLabel || '-' }}</template>
        </ElTableColumn>
        <ElTableColumn v-if="statusColVisible('lastGateway')" column-key="lastGateway" :label="t('product.assets.list.gatewayPh')" :width="colWidth('lastGateway', 120)" show-overflow-tooltip>
          <template #default="{ row }">{{ row.fields?.lastGatewayName || '-' }}</template>
        </ElTableColumn>
        <ElTableColumn v-if="statusColVisible('boundBeacon')" column-key="boundBeacon" :label="t('product.assets.list.colBeacon')" :width="colWidth('boundBeacon', 140)" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.fields?.boundBeaconName || row.fields?.boundBeaconMac || row.fields?.boundBeaconCode || row.fields?.macAddress || '-' }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          v-if="statusColVisible('lastSeenAt')"
          column-key="lastSeenAt"
          :label="t('product.assets.list.colLastSeen')"
          :width="colWidth('lastSeenAt', 170)"
          sortable
          :sort-method="(a: NamedResource, b: NamedResource) => String(a.fields?.lastSeenAt || '').localeCompare(String(b.fields?.lastSeenAt || ''))"
        >
          <template #default="{ row }">
            {{ formatDateTime(row.fields?.lastSeenAt as string | null | undefined) }}
          </template>
        </ElTableColumn>
        <ElTableColumn v-if="statusColVisible('__actions')" column-key="__actions" :label="t('common.actions')" width="100" fixed="right">
          <template #default="{ row }">
            <ElButton
              link
              type="primary"
              :disabled="!row.fields?.boundBeaconId"
              @click="openHistory(row)"
            >
              {{ t('product.assets.status.historySignal') }}
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

    <ElDrawer
      v-model="historyOpen"
      :title="t('product.assets.status.historyTitle', { name: historyAsset?.name || '' })"
      size="560px"
      destroy-on-close
      @opened="onHistoryOpened"
    >
      <div v-loading="historyLoading">
        <p style="margin: 0 0 12px; color: var(--el-text-color-secondary); font-size: 13px">
          {{ t('product.assets.status.historyHint', { beacon: historyAsset?.fields?.boundBeaconMac || historyAsset?.fields?.boundBeaconName || '-' }) }}
        </p>
        <ElTable :data="pagedScans" stripe height="calc(100vh - 180px)">
          <ElTableColumn :label="t('common.time')" min-width="160">
            <template #default="{ row }">
              {{ formatDateTime(row.fields?.receivedAt as string | null | undefined) }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="RSSI" width="100">
            <template #default="{ row }">
              <span :class="['rssi', rssiTone(row.fields?.rssi)]">{{ rssiText(row.fields?.rssi) }}</span>
            </template>
          </ElTableColumn>
          <ElTableColumn :label="t('product.overview.gateways')" min-width="140">
            <template #default="{ row }">
              {{ row.fields?.gatewayName || row.fields?.gatewayMac || row.fields?.gatewayId || '-' }}
            </template>
          </ElTableColumn>
        </ElTable>
        <TablePager
          :total="scansPageTotal"
          :current="scansPageCurrent"
          :size="scansPageSize"
          @update:current="onScansPageChange"
          @update:size="onScansSizeChange"
        />
      </div>
    </ElDrawer>
  </div>
</template>

<style scoped>
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
.rssi {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.rssi--strong {
  color: #16a34a;
}
.rssi--mid {
  color: #ca8a04;
}
.rssi--weak {
  color: #dc2626;
}
</style>
