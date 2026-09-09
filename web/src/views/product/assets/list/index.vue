<script setup lang="ts">
import { computed, onActivated, onDeactivated, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import BatteryPill from '@/components/business/BatteryPill.vue'
import PresencePill from '@/components/business/PresencePill.vue'
import { useTenantContextStore } from '@/store/modules/tenant-context'
import {
  assetTypesApi,
  assetsApi,
  beaconsApi,
  bindAssetBeacon,
  buzzAsset,
  unbindAssetBeacon,
  type AssetBuzzerMode,
  type NamedResource,
  type ResourceUpsert
} from '@/api/asset-platform'
import { formatDateTime } from '@/utils/datetime'
import { queryText } from '@/utils/route-query'
import { usePersistedColumnWidths } from '@/hooks/core/usePersistedColumnWidths'
import { useTableColumns } from '@/hooks/core/useTableColumns'
import { useTableStore } from '@/store/modules/table'
import type { ColumnOption } from '@/types/component'
import TablePager from '@/components/business/TablePager.vue'
import EinkPushDialog from '@/components/business/EinkPushDialog.vue'
import AssetPhotoThumb from '@/components/business/AssetPhotoThumb.vue'
import AssetPhotoPicker from '@/components/business/AssetPhotoPicker.vue'
import { useClientPagination } from '@/composables/useClientPagination'

defineOptions({ name: 'AssetList' })

const { t } = useI18n()
const route = useRoute()

const { projectScope } = storeToRefs(useTenantContextStore())
const tableStore = useTableStore()
const { tableSize, isZebra, isBorder } = storeToRefs(tableStore)
const loading = ref(false)
const rows = ref<NamedResource[]>([])
const types = ref<NamedResource[]>([])
const filterKeyword = ref('')
const filterTypeId = ref('')
const filterGatewayId = ref('')
const filterPresence = ref('ALL')
const filterProtocol = ref('ALL')
const filterBound = ref('ALL')
const drawer = ref(false)
const editing = ref<NamedResource | null>(null)
const { colWidth, fitWidth, onHeaderDragEnd } = usePersistedColumnWidths('assets-list', {
  photo: 56,
  code: 150,
  assetType: 160,
  boundBeacon: 150,
  protocol: 120,
  battery: 120,
  location: 140,
  lastGateway: 140,
  status: 100,
  lastSeenAt: 180,
  __actions: 200
})
const form = ref<ResourceUpsert>({ code: '', name: '', description: '', fields: {} })
const bindOpen = ref(false)
const buzzBusyId = ref<string | null>(null)
const einkOpen = ref(false)
const einkAsset = ref<NamedResource | null>(null)
const current = ref<NamedResource | null>(null)
const beacons = ref<NamedResource[]>([])
const selectedBeaconId = ref('')
const selectedProtocolType = ref<'AUTO' | 'FINDMY' | 'BEACON'>('AUTO')
const lastRefreshedAt = ref<Date | null>(null)
let timer: ReturnType<typeof setInterval> | null = null
let loadInFlight = false

const typeOptions = computed(() =>
  types.value.map((t) => ({
    id: t.id,
    label: `${t.code} · ${t.name}`
  }))
)

const gatewayOptions = computed(() => {
  const map = new Map<string, string>()
  for (const row of rows.value) {
    const id = String(row.fields?.lastGatewayId || '').trim()
    if (!id) continue
    const name = String(row.fields?.lastGatewayName || id).trim()
    if (!map.has(id)) map.set(id, name)
  }
  return [...map.entries()]
    .map(([id, name]) => ({ id, name }))
    .sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))
})

const hasActiveFilters = computed(
  () =>
    !!filterKeyword.value.trim() ||
    !!filterTypeId.value ||
    !!filterGatewayId.value ||
    filterPresence.value !== 'ALL' ||
    filterProtocol.value !== 'ALL' ||
    filterBound.value !== 'ALL'
)

const filteredRows = computed(() => {
  const q = filterKeyword.value.trim().toLowerCase()
  return rows.value.filter((row) => {
    if (filterTypeId.value) {
      const typeId = String(row.fields?.assetTypeId || '')
      if (typeId !== filterTypeId.value) return false
    }
    if (filterGatewayId.value) {
      if (String(row.fields?.lastGatewayId || '') !== filterGatewayId.value) return false
    }
    if (filterPresence.value !== 'ALL' && row.status !== filterPresence.value) return false
    if (filterProtocol.value !== 'ALL') {
      const bound = String(row.fields?.protocolType || '').toUpperCase()
      const detected = String(row.fields?.signalProfile || '').toUpperCase()
      if (filterProtocol.value === 'NONE') {
        if (bound === 'FINDMY' || bound === 'BEACON' || bound === 'AUTO') return false
      } else if (filterProtocol.value === 'AUTO') {
        if (bound !== 'AUTO') return false
      } else if (bound !== filterProtocol.value && detected !== filterProtocol.value) {
        return false
      }
    }
    if (filterBound.value === 'BOUND' && !row.fields?.boundBeaconId) return false
    if (filterBound.value === 'UNBOUND' && row.fields?.boundBeaconId) return false
    if (!q) return true
    const hay = [
      row.code,
      row.name,
      row.fields?.lastGatewayName,
      row.fields?.boundBeaconName,
      row.fields?.boundBeaconMac
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
} = useClientPagination(filteredRows, { resetOn: filterKeyword })

const nameColWidth = computed(() =>
  fitWidth(
    'name',
    rows.value.map((r) => r.name),
    { header: t('common.name'), min: 88, max: 200 }
  )
)

const { columns: visibleAssetColumns, columnChecks, resetColumns } = useTableColumns(() => {
  const cols: ColumnOption<NamedResource>[] = [
    { prop: 'photo', label: t('product.assets.list.colPhoto'), width: 56, checked: true },
    { prop: 'code', label: t('product.assets.list.colCode'), width: 150, checked: true },
    { prop: 'name', label: t('common.name'), checked: true },
    { prop: 'assetType', label: t('product.assets.types.title'), width: 160, checked: true },
    { prop: 'boundBeacon', label: t('product.assets.list.colBeacon'), width: 150, checked: true },
    { prop: 'protocol', label: t('product.assets.list.protocolPh'), width: 120, checked: true },
    { prop: 'battery', label: t('product.assets.list.colBattery'), width: 120, checked: true },
    { prop: 'location', label: t('product.assets.list.colLocation'), width: 140, checked: true },
    { prop: 'lastGateway', label: t('product.assets.list.gatewayPh'), width: 140, checked: true },
    { prop: 'status', label: t('common.status'), width: 100, checked: true },
    { prop: 'lastSeenAt', label: t('product.assets.list.colLastSeen'), width: 180, checked: true },
    {
      prop: '__actions',
      label: t('common.actions'),
      width: 200,
      fixed: 'right',
      checked: true,
      disabled: true
    }
  ]
  return cols
})

watch(
  () =>
    [
      t('product.assets.list.colPhoto'),
      t('product.assets.list.colCode'),
      t('common.name'),
      t('product.assets.types.title'),
      t('common.actions')
    ].join('|'),
  () => resetColumns()
)

/** Data columns in ArtTableHeader drag order (excludes actions). */
const orderedDataProps = computed(() =>
  visibleAssetColumns.value
    .filter((c) => c.prop && c.prop !== '__actions')
    .map((c) => String(c.prop))
)

function colVisible(prop: string) {
  return visibleAssetColumns.value.some((c) => c.prop === prop)
}

const refreshHint = computed(() =>
  lastRefreshedAt.value
    ? t('product.assets.list.refreshLive', { time: formatDateTime(lastRefreshedAt.value) })
    : t('product.assets.list.refreshIdle')
)

function resetFilters() {
  filterKeyword.value = ''
  filterTypeId.value = ''
  filterGatewayId.value = ''
  filterPresence.value = 'ALL'
  filterProtocol.value = 'ALL'
  filterBound.value = 'ALL'
}

function applyRouteFilters() {
  resetFilters()
  const q = queryText(route.query, 'q')
  const presence = queryText(route.query, 'presence').toUpperCase()
  const bound = queryText(route.query, 'bound').toUpperCase()
  if (q) filterKeyword.value = q
  if (presence === 'ONLINE' || presence === 'OFFLINE' || presence === 'UNBOUND') {
    filterPresence.value = presence
  }
  if (bound === 'BOUND' || bound === 'UNBOUND') {
    filterBound.value = bound
  }
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

function protocolLabel(row: NamedResource) {
  const bound = String(row.fields?.protocolType || '').toUpperCase()
  const detected = String(row.fields?.signalProfile || '').toUpperCase()
  const kind = detected || (bound === 'AUTO' ? '' : bound)
  const kindText = kind === 'FINDMY' ? 'Find My' : kind === 'BEACON' ? 'Beacon' : ''
  if (bound === 'AUTO' || !bound) {
    return kindText ? t('product.assets.list.autoProtocol', { kind: kindText }) : t('product.assets.list.autoOnly')
  }
  if (bound === 'FINDMY') return 'Find My'
  if (bound === 'BEACON') return 'Beacon'
  return kindText || '-'
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
    const [assetRows, typeRows] = await Promise.all([
      assetsApi.list(projectScope.value),
      assetTypesApi.list(projectScope.value)
    ])
    rows.value = assetRows
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

function clearTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function setupPolling() {
  clearTimer()
  timer = setInterval(() => {
    if (document.visibilityState === 'hidden') return
    void load(true)
  }, 3000)
}

function openCreate() {
  editing.value = null
  form.value = {
    code: '',
    name: '',
    description: '',
    status: 'ACTIVE',
    fields: { assetTypeId: '', einkCapable: false, einkProfile: '', imageUrl: '' }
  }
  drawer.value = true
}

function openEdit(row: NamedResource) {
  editing.value = row
  const protocol = String(row.fields?.protocolType || 'AUTO').toUpperCase()
  form.value = {
    code: row.code,
    name: row.name,
    description: String(row.fields?.description ?? ''),
    status: String(row.fields?.lifecycleStatus || 'ACTIVE'),
    fields: {
      ...(row.fields || {}),
      assetTypeId: String(row.fields?.assetTypeId || ''),
      protocolType:
        protocol === 'BEACON' ? 'BEACON' : protocol === 'FINDMY' ? 'FINDMY' : 'AUTO',
      boundBeaconId: row.fields?.boundBeaconId || null,
      einkCapable: !!row.fields?.einkCapable,
      einkProfile: String(row.fields?.einkProfile || ''),
      imageUrl: String(row.fields?.imageUrl || '')
    }
  }
  drawer.value = true
}

async function save() {
  if (!projectScope.value) return
  if (!form.value.name?.trim()) {
    ElMessage.warning(t('product.assets.list.needName'))
    return
  }
  const einkCapable = !!form.value.fields?.einkCapable
  const einkProfile = String(form.value.fields?.einkProfile || '').trim()
  const fields: Record<string, unknown> = {
    locationLabel: form.value.fields?.locationLabel || '',
    assetTypeId: form.value.fields?.assetTypeId || null,
    lifecycleStatus: form.value.status || 'ACTIVE',
    imageUrl: String(form.value.fields?.imageUrl || '')
  }
  if (editing.value || einkCapable) {
    fields.einkCapable = einkCapable
    fields.einkProfile = einkCapable && einkProfile ? einkProfile : null
  }
  if (editing.value?.fields?.boundBeaconId) {
    const p = String(form.value.fields?.protocolType || 'AUTO').toUpperCase()
    fields.protocolType = p === 'BEACON' ? 'BEACON' : p === 'FINDMY' ? 'FINDMY' : 'AUTO'
  }
  const payload: ResourceUpsert = {
    code: editing.value ? editing.value.code : '',
    name: form.value.name,
    description: form.value.description,
    status: String(form.value.status || form.value.fields?.lifecycleStatus || 'ACTIVE'),
    fields
  }
  if (editing.value) {
    await assetsApi.update(projectScope.value, editing.value.id, payload)
  } else {
    await assetsApi.create(projectScope.value, payload)
  }
  drawer.value = false
  ElMessage.success(t('common.saveSuccess'))
  await load()
}

async function openBind(row: NamedResource) {
  if (!projectScope.value) return
  current.value = row
  selectedBeaconId.value = String(row.fields?.boundBeaconId || '')
  const existingProtocol = String(row.fields?.protocolType || 'AUTO').toUpperCase()
  selectedProtocolType.value =
    existingProtocol === 'BEACON' ? 'BEACON' : existingProtocol === 'FINDMY' ? 'FINDMY' : 'AUTO'
  beacons.value = (await beaconsApi.list(projectScope.value)).filter((b) => !b.fields?.boundAssetId)
  bindOpen.value = true
}

const selectedBeacon = computed(
  () => beacons.value.find((b) => b.id === selectedBeaconId.value) || null
)

function beaconMac(b: NamedResource) {
  return String(b.fields?.macAddress || b.code || '').trim()
}

function beaconDeviceName(b: NamedResource) {
  const mac = beaconMac(b)
  const name = String(b.name || '').trim()
  if (!name) return ''
  if (mac && name.toLowerCase() === mac.toLowerCase()) return ''
  return name
}

function beaconOptionLabel(b: NamedResource) {
  const mac = beaconMac(b)
  const name = beaconDeviceName(b)
  if (name && mac) return `${name} · ${mac}`
  return name || mac || b.id
}

function displayOrDash(value: unknown) {
  if (value == null || value === '') return '—'
  return String(value)
}

async function confirmBind() {
  if (!projectScope.value || !current.value || !selectedBeaconId.value) return
  await bindAssetBeacon(
    projectScope.value,
    current.value.id,
    selectedBeaconId.value,
    selectedProtocolType.value
  )
  ElMessage.success(t('product.assets.list.boundOk'))
  bindOpen.value = false
  await load()
}

async function doUnbind(row: NamedResource) {
  if (!projectScope.value) return
  try {
    await ElMessageBox.confirm(
      t('product.assets.list.unbindConfirm', {
        name: row.name || row.code,
        beacon: row.fields?.boundBeaconMac || row.fields?.boundBeaconName || '—'
      }),
      t('product.assets.list.unbindTitle'),
      {
        type: 'warning',
        confirmButtonText: t('product.assets.list.unbind'),
        cancelButtonText: t('common.cancel')
      }
    )
    await unbindAssetBeacon(projectScope.value, row.id)
    ElMessage.success(t('product.assets.list.unboundOk'))
    await load()
  } catch {
    // cancelled
  }
}

async function onBuzzCommand(row: NamedResource, mode: AssetBuzzerMode) {
  if (!projectScope.value || !row.fields?.boundBeaconId) return
  buzzBusyId.value = row.id
  try {
    const result = await buzzAsset(projectScope.value, row.id, mode)
    if (mode === 'LONG') {
      ElMessage.success(t('product.assets.list.buzzLongStarted'))
    } else {
      ElMessage.success(t('product.assets.list.buzzShortSent'))
    }
    if (result && result.targetedGateway === false) {
      ElMessage.warning(t('product.assets.list.buzzNoGateway'))
    }
  } finally {
    buzzBusyId.value = null
  }
}

function openEink(row: NamedResource) {
  if (!row.fields?.einkCapable) {
    ElMessage.warning(t('product.assets.list.einkNotCapable'))
    return
  }
  einkAsset.value = row
  einkOpen.value = true
}

async function doDelete(row: NamedResource) {
  if (!projectScope.value) return
  try {
    await ElMessageBox.confirm(t('product.assets.list.deleteConfirm', { code: row.code, name: row.name }), t('common.deleteConfirm'), {
      type: 'warning',
      confirmButtonText: t('common.delete'),
      cancelButtonText: t('common.cancel')
    })
    await assetsApi.remove(projectScope.value, row.id)
    ElMessage.success(t('common.deleted'))
    await load()
  } catch {
    // cancelled
  }
}

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
onDeactivated(() => clearTimer())
onUnmounted(() => clearTimer())
</script>

<template>
  <div class="page-card art-card" style="padding: 20px">
    <div style="display: flex; justify-content: space-between; margin-bottom: 16px; gap: 12px">
      <div>
        <h2 style="margin: 0">{{ t('product.assets.list.title') }}</h2>
        <p style="margin: 4px 0 0; color: var(--el-text-color-secondary)">
          {{ t('product.assets.list.subtitle', { refreshHint }) }}
        </p>
      </div>
      <ElButton type="primary" :disabled="!projectScope" @click="openCreate">{{ t('product.assets.list.create') }}</ElButton>
    </div>

      <div class="filter-bar art-table-card">
        <ArtTableHeader
          v-model:columns="columnChecks"
          :loading="loading"
          layout="refresh,size,fullscreen,columns,settings"
          full-class="page-card"
          @refresh="() => load()"
        >
          <template #left>
            <div class="filter-bar__left">
              <ElInput
                v-model="filterKeyword"
                clearable
                :placeholder="t('product.assets.list.searchPh')"
                style="width: 180px"
              />
              <ElSelect v-model="filterTypeId" clearable filterable :placeholder="t('product.assets.types.title')" style="width: 200px">
                <ElOption v-for="opt in typeOptions" :key="opt.id" :label="opt.label" :value="opt.id" />
              </ElSelect>
              <ElSelect v-model="filterGatewayId" clearable filterable :placeholder="t('product.assets.list.gatewayPh')" style="width: 180px">
                <ElOption v-for="opt in gatewayOptions" :key="opt.id" :label="opt.name" :value="opt.id" />
              </ElSelect>
              <ElSelect v-model="filterPresence" :placeholder="t('product.assets.list.presencePh')" style="width: 130px">
                <ElOption :label="t('product.assets.list.allPresence')" value="ALL" />
                <ElOption :label="t('product.assets.list.online')" value="ONLINE" />
                <ElOption :label="t('product.overview.reasonOffline')" value="OFFLINE" />
                <ElOption :label="t('product.assets.list.unbound')" value="UNBOUND" />
              </ElSelect>
              <ElSelect v-model="filterProtocol" :placeholder="t('product.assets.list.protocolPh')" style="width: 140px">
                <ElOption :label="t('product.assets.list.allProtocols')" value="ALL" />
                <ElOption :label="t('product.assets.list.autoDetect')" value="AUTO" />
                <ElOption label="Find My" value="FINDMY" />
                <ElOption label="Beacon" value="BEACON" />
                <ElOption :label="t('product.assets.list.noProtocol')" value="NONE" />
              </ElSelect>
              <ElSelect v-model="filterBound" :placeholder="t('product.assets.list.boundPh')" style="width: 130px">
                <ElOption :label="t('product.assets.list.allBound')" value="ALL" />
                <ElOption :label="t('product.assets.list.bound')" value="BOUND" />
                <ElOption :label="t('product.assets.list.unbound')" value="UNBOUND" />
              </ElSelect>
              <ElButton v-if="hasActiveFilters" @click="resetFilters">{{ t('product.assets.list.clearFilters') }}</ElButton>
              <span class="filter-bar__count">{{ t('product.assets.list.filterCount', { filtered: filteredRows.length, total: rows.length }) }}</span>
            </div>
          </template>
        </ArtTableHeader>
      </div>
      <ElTable
        v-loading="loading"
        :data="pagedRows"
        :size="tableSize"
        :stripe="isZebra"
        :border="isBorder"
        @header-dragend="onHeaderDragEnd"
      >
      <template v-for="prop in orderedDataProps" :key="prop">
      <ElTableColumn
        v-if="prop === 'photo'"
        column-key="photo"
        :label="t('product.assets.list.colPhoto')"
        :width="colWidth('photo', 56)"
      >
        <template #default="{ row }">
          <AssetPhotoThumb :src="String(row.fields?.imageUrl || '')" :name="row.name" />
        </template>
      </ElTableColumn>
      <ElTableColumn
        v-else-if="prop === 'code'"
        prop="code"
        :label="t('product.assets.list.colCode')"
        :width="colWidth('code', 150)"
        sortable
      />
      <ElTableColumn
        v-else-if="prop === 'name'"
        prop="name"
        :label="t('common.name')"
        :width="nameColWidth"
        sortable
        show-overflow-tooltip
      />
      <ElTableColumn
        v-else-if="prop === 'assetType'"
        column-key="assetType"
        :label="t('product.assets.types.title')"
        :width="colWidth('assetType', 160)"
        sortable
        :sort-method="(a: NamedResource, b: NamedResource) => String(a.fields?.assetTypeName || '').localeCompare(String(b.fields?.assetTypeName || ''))"
      >
        <template #default="{ row }">{{ row.fields?.assetTypeName || '-' }}</template>
      </ElTableColumn>
      <ElTableColumn
        v-else-if="prop === 'boundBeacon'"
        column-key="boundBeacon"
        :label="t('product.assets.list.colBeacon')"
        :width="colWidth('boundBeacon', 150)"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.fields?.boundBeaconName || row.fields?.boundBeaconMac || '-' }}
        </template>
      </ElTableColumn>
      <ElTableColumn
        v-else-if="prop === 'protocol'"
        column-key="protocol"
        :label="t('product.assets.list.protocolPh')"
        :width="colWidth('protocol', 120)"
        sortable
        :sort-method="(a: NamedResource, b: NamedResource) => protocolLabel(a).localeCompare(protocolLabel(b), 'zh-CN')"
      >
        <template #default="{ row }">
          {{ protocolLabel(row) }}
        </template>
      </ElTableColumn>
      <ElTableColumn
        v-else-if="prop === 'battery'"
        column-key="battery"
        :label="t('product.assets.list.colBattery')"
        :width="colWidth('battery', 120)"
        sortable
        :sort-method="(a: NamedResource, b: NamedResource) => batterySortValue(a) - batterySortValue(b)"
      >
        <template #default="{ row }">
          <BatteryPill
            :protocol-type="(row.fields?.signalProfile || row.fields?.protocolType) as string | undefined"
            :label="row.fields?.batteryLabel as string | undefined"
            :percent="row.fields?.batteryPercent as number | undefined"
            :level="row.fields?.batteryLevel as number | undefined"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn
        v-else-if="prop === 'location'"
        column-key="location"
        :label="t('product.assets.list.colLocation')"
        :width="colWidth('location', 140)"
        show-overflow-tooltip
      >
        <template #default="{ row }">{{ row.fields?.locationLabel || '-' }}</template>
      </ElTableColumn>
      <ElTableColumn
        v-else-if="prop === 'lastGateway'"
        column-key="lastGateway"
        :label="t('product.assets.list.gatewayPh')"
        :width="colWidth('lastGateway', 140)"
        sortable
        show-overflow-tooltip
        :sort-method="(a: NamedResource, b: NamedResource) => String(a.fields?.lastGatewayName || '').localeCompare(String(b.fields?.lastGatewayName || ''), 'zh-CN')"
      >
        <template #default="{ row }">{{ row.fields?.lastGatewayName || '-' }}</template>
      </ElTableColumn>
      <ElTableColumn
        v-else-if="prop === 'status'"
        prop="status"
        :label="t('common.status')"
        :width="colWidth('status', 100)"
        sortable
      >
        <template #default="{ row }">
          <PresencePill :status="row.status" />
        </template>
      </ElTableColumn>
      <ElTableColumn
        v-else-if="prop === 'lastSeenAt'"
        column-key="lastSeenAt"
        :label="t('product.assets.list.colLastSeen')"
        :width="colWidth('lastSeenAt', 180)"
        sortable
        :sort-method="(a: NamedResource, b: NamedResource) => String(a.fields?.lastSeenAt || '').localeCompare(String(b.fields?.lastSeenAt || ''))"
      >
        <template #default="{ row }">
          {{ formatDateTime(row.fields?.lastSeenAt as string | null | undefined) }}
        </template>
      </ElTableColumn>
      </template>
      <ElTableColumn
        v-if="colVisible('__actions')"
        column-key="__actions"
        :label="t('common.actions')"
        min-width="168"
        width="200"
        fixed="right"
      >
        <template #default="{ row }">
          <div class="row-actions">
            <ElButton link type="primary" @click="openEdit(row)">{{ t('common.edit') }}</ElButton>
            <template v-if="row.fields?.boundBeaconId">
              <ElDropdown
                trigger="click"
                :disabled="buzzBusyId === row.id"
                @command="(cmd: AssetBuzzerMode) => onBuzzCommand(row, cmd)"
              >
                <ElButton link type="primary" :loading="buzzBusyId === row.id">
                  {{ t('product.assets.list.buzz') }}
                </ElButton>
                <template #dropdown>
                  <ElDropdownMenu>
                    <ElDropdownItem command="SHORT">{{ t('product.assets.list.buzzShort') }}</ElDropdownItem>
                    <ElDropdownItem command="LONG">{{ t('product.assets.list.buzzLong') }}</ElDropdownItem>
                  </ElDropdownMenu>
                </template>
              </ElDropdown>
              <ElButton
                v-if="row.fields?.einkCapable"
                link
                type="primary"
                @click="openEink(row)"
              >
                {{ t('product.assets.list.eink') }}
              </ElButton>
              <ElButton link type="danger" @click="doUnbind(row)">{{ t('product.assets.list.unbind') }}</ElButton>
            </template>
            <ElButton v-else link type="primary" @click="openBind(row)">{{ t('product.assets.list.bindBeacon') }}</ElButton>
            <ElButton link type="danger" @click="doDelete(row)">{{ t('common.delete') }}</ElButton>
          </div>
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
    

    <ElDrawer v-model="drawer" :title="editing ? t('product.assets.list.drawerEdit') : t('product.assets.list.drawerCreate')" size="460px">
      <ElForm label-position="top">
        <ElFormItem :label="t('product.assets.list.photo')">
          <AssetPhotoPicker
            :model-value="String(form.fields?.imageUrl || '')"
            :project-scope="projectScope"
            @update:model-value="(v: string) => (form.fields = { ...form.fields, imageUrl: v })"
          />
        </ElFormItem>
        <ElFormItem v-if="editing" :label="t('product.assets.list.colCode')">
          <ElInput :model-value="form.code" disabled />
          <p style="margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: 12px">
            {{
              form.fields?.boundBeaconId
                ? t('product.assets.list.codeHintBound')
                : t('product.assets.list.codeHintUnbound')
            }}
          </p>
        </ElFormItem>
        <ElFormItem
          v-if="editing && form.fields?.boundBeaconMac"
          :label="t('product.devices.beacons.macField')"
        >
          <ElInput :model-value="String(form.fields.boundBeaconMac)" disabled />
        </ElFormItem>
        <ElFormItem :label="t('common.name')">
          <ElInput v-model="form.name" />
        </ElFormItem>
        <ElFormItem :label="t('product.assets.list.typeLabel')">
          <ElSelect
            :model-value="String(form.fields?.assetTypeId || '')"
            clearable
            filterable
            :placeholder="t('product.assets.list.typePhSelect')"
            style="width: 100%"
            @update:model-value="(v: string) => (form.fields = { ...form.fields, assetTypeId: v || null })"
          >
            <ElOption v-for="opt in typeOptions" :key="opt.id" :label="opt.label" :value="opt.id" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem :label="t('product.assets.list.locationLabel')">
          <ElInput
            :model-value="String(form.fields?.locationLabel ?? '')"
            @update:model-value="(v: string) => (form.fields = { ...form.fields, locationLabel: v })"
          />
        </ElFormItem>
        <ElFormItem :label="t('common.description')">
          <ElInput v-model="form.description" type="textarea" />
        </ElFormItem>
        <ElFormItem :label="t('product.assets.list.einkAssetFlag')">
          <ElCheckbox
            :model-value="!!form.fields?.einkCapable"
            @update:model-value="(v: boolean | string | number) => (form.fields = { ...form.fields, einkCapable: !!v })"
          >
            {{ t('product.assets.list.einkAssetFlagCheck') }}
          </ElCheckbox>
          <p style="margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: 12px">
            {{ t('product.assets.list.einkAssetFlagHint') }}
          </p>
          <ElRadioGroup
            v-if="form.fields?.einkCapable"
            :model-value="String(form.fields?.einkProfile || '')"
            style="display: flex; margin-top: 10px"
            @update:model-value="(v) => (form.fields = { ...form.fields, einkProfile: String(v ?? '') })"
          >
            <ElRadioButton value="elnk">{{ t('product.assets.list.einkProfileElnk') }}</ElRadioButton>
            <ElRadioButton value="za25">{{ t('product.assets.list.einkProfileZa25') }}</ElRadioButton>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem v-if="editing && form.fields?.boundBeaconId" :label="t('product.assets.list.protocolLabel')">
          <ElRadioGroup
            :model-value="String(form.fields?.protocolType || 'AUTO')"
            @update:model-value="(v) => (form.fields = { ...form.fields, protocolType: String(v ?? 'AUTO') })"
          >
            <ElRadioButton value="AUTO">{{ t('product.assets.list.autoDetect') }}</ElRadioButton>
            <ElRadioButton value="FINDMY">Find My</ElRadioButton>
            <ElRadioButton value="BEACON">Beacon</ElRadioButton>
          </ElRadioGroup>
          <p style="margin: 8px 0 0; color: var(--el-text-color-secondary); font-size: 12px">
            {{ t('product.assets.list.protocolHintEdit') }}
          </p>
        </ElFormItem>
        <ElButton type="primary" @click="save">{{ t('common.save') }}</ElButton>
      </ElForm>
    </ElDrawer>

    <ElDialog v-model="bindOpen" :title="t('product.assets.list.bindTitle')" width="460px">
      <ElForm label-position="top">
        <ElFormItem :label="t('product.assets.list.beaconDevice')">
          <ElSelect
            v-model="selectedBeaconId"
            filterable
            :placeholder="beacons.length ? t('product.assets.list.selectBeacon') : t('product.assets.list.noUnboundBeacon')"
            style="width: 100%"
          >
            <ElOption
              v-for="b in beacons"
              :key="b.id"
              :label="beaconOptionLabel(b)"
              :value="b.id"
            />
          </ElSelect>
        </ElFormItem>
        <div v-if="selectedBeacon" class="beacon-preview">
          <div class="beacon-preview__row">
            <span class="beacon-preview__label">{{ t('product.devices.beacons.macField') }}</span>
            <span class="beacon-preview__value">{{ displayOrDash(beaconMac(selectedBeacon)) }}</span>
          </div>
          <div v-if="beaconDeviceName(selectedBeacon)" class="beacon-preview__row">
            <span class="beacon-preview__label">{{ t('product.assets.list.deviceName') }}</span>
            <span class="beacon-preview__value">{{ beaconDeviceName(selectedBeacon) }}</span>
          </div>
          <div class="beacon-preview__row">
            <span class="beacon-preview__label">iBeacon UUID</span>
            <span class="beacon-preview__value">{{ displayOrDash(selectedBeacon.fields?.ibeaconUuid) }}</span>
          </div>
          <div class="beacon-preview__row">
            <span class="beacon-preview__label">Major</span>
            <span class="beacon-preview__value">{{ displayOrDash(selectedBeacon.fields?.ibeaconMajor) }}</span>
          </div>
          <div class="beacon-preview__row">
            <span class="beacon-preview__label">Minor</span>
            <span class="beacon-preview__value">{{ displayOrDash(selectedBeacon.fields?.ibeaconMinor) }}</span>
          </div>
        </div>
        <ElFormItem :label="t('product.assets.list.protocolLabel')">
          <ElRadioGroup v-model="selectedProtocolType">
            <ElRadioButton value="AUTO">{{ t('product.assets.list.autoDetect') }}</ElRadioButton>
            <ElRadioButton value="FINDMY">Find My</ElRadioButton>
            <ElRadioButton value="BEACON">Beacon</ElRadioButton>
          </ElRadioGroup>
          <p style="margin: 8px 0 0; color: var(--el-text-color-secondary); font-size: 12px">
            {{ t('product.assets.list.protocolHintBind') }}
          </p>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="bindOpen = false">{{ t('common.cancel') }}</ElButton>
        <ElButton type="primary" :disabled="!selectedBeaconId" @click="confirmBind">{{ t('common.confirm') }}</ElButton>
      </template>
    </ElDialog>

    <EinkPushDialog
      v-model="einkOpen"
      :asset="einkAsset"
      :project-scope="projectScope"
    />
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
.row-actions {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px 4px;
  max-width: 200px;
}
.beacon-preview {
  margin: -4px 0 16px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  display: grid;
  gap: 6px;
}
.beacon-preview__row {
  display: grid;
  grid-template-columns: 110px 1fr;
  gap: 8px;
  align-items: start;
  font-size: 13px;
  line-height: 1.4;
}
.beacon-preview__label {
  color: var(--el-text-color-secondary);
}
.beacon-preview__value {
  color: var(--el-text-color-primary);
  word-break: break-all;
}
</style>
