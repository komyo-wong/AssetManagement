<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import BatteryPill from '@/components/business/BatteryPill.vue'
import ResourceCrudPage from '@/views/product/shared/ResourceCrudPage.vue'
import { useTenantContextStore } from '@/store/modules/tenant-context'
import {
  beaconsApi,
  fetchBeaconPresenceEvents,
  fetchBeaconScans,
  type NamedResource
} from '@/api/asset-platform'
import { formatDateTime } from '@/utils/datetime'
import { queryText } from '@/utils/route-query'

const { t } = useI18n()

const route = useRoute()
const { projectScope } = storeToRefs(useTenantContextStore())
const historyOpen = ref(false)
const historyTab = ref<'signals' | 'presence'>('signals')
const current = ref<NamedResource | null>(null)
const scans = ref<NamedResource[]>([])
const signalsLoading = ref(false)
const presenceEvents = ref<NamedResource[]>([])
const presencePage = ref(1)
const presenceTotal = ref(0)
const presenceLoading = ref(false)
const PRESENCE_PAGE_SIZE = 50
const HISTORY_TABLE_HEIGHT = 'calc(100vh - 180px)'
const pageRef = ref<{
  reload: () => Promise<void>
  clearSelection: () => void
  getSelected: () => NamedResource[]
} | null>(null)
const batchDeleting = ref(false)
const importOpen = ref(false)
const importing = ref(false)
const importFile = ref<File | null>(null)
const importResult = ref<{
  created: number
  updated: number
  skipped: number
  failed: number
  errors: Array<{ row: number; message: string }>
  errorTruncated?: boolean
} | null>(null)
const cachedRows = ref<NamedResource[]>([])
const filterBound = ref('ALL')
const filterPresence = ref('ALL')
const filterBattery = ref('ALL')
const filterProtocol = ref('ALL')
let historyTimer: ReturnType<typeof setInterval> | null = null
let historyLoadToken = 0

const historyTitle = computed(() => {
  const name = current.value?.name || ''
  return historyTab.value === 'presence' ? t('product.devices.beacons.historyTitlePresence', { name }) : t('product.devices.beacons.historyTitleSignals', { name })
})

const hasActiveFilters = computed(
  () =>
    filterBound.value !== 'ALL' ||
    filterPresence.value !== 'ALL' ||
    filterBattery.value !== 'ALL' ||
    filterProtocol.value !== 'ALL'
)

const filterKey = computed(
  () => `${filterBound.value}|${filterPresence.value}|${filterBattery.value}|${filterProtocol.value}`
)

const beaconExtraFields = computed(() => [
  { key: 'macAddress', label: t('product.devices.beacons.macField'), placeholder: t('product.devices.beacons.macPh') }
])

/** Accept AA:BB:… / AA-BB-… / AABB… / spaced / dotted; return lowercase aa:bb:… or '' */
function normalizeMacInput(raw: unknown) {
  const text = String(raw ?? '').trim()
  if (!text) return ''
  let normalized = text
  if (normalized.toLowerCase().startsWith('0x')) normalized = normalized.slice(2)
  const hex = normalized.replace(/[:\-.\s]/g, '').toLowerCase()
  if (!/^[0-9a-f]{12}$/.test(hex)) return ''
  return hex.replace(/(.{2})(?=.)/g, '$1:')
}

async function createBeacon(scope: Api.AssetPlatform.ProjectScope, input: {
  code?: string
  name?: string
  description?: string
  fields?: Record<string, unknown>
}) {
  const mac = normalizeMacInput(input.fields?.macAddress || input.code || input.name)
  if (!mac) {
    ElMessage.warning(t('product.devices.beacons.macInvalid'))
    throw new Error('invalid mac')
  }
  return beaconsApi.create(scope, {
    code: mac,
    name: mac,
    fields: { macAddress: mac }
  })
}

const beaconColumns = computed(() => [
  { prop: 'name', label: t('common.name') },
  { prop: 'macAddress', label: t('product.devices.beacons.colMac'), width: 150 },
  { prop: 'bindStatus', label: t('product.devices.beacons.colBind'), width: 100 },
  { prop: 'boundAssetName', label: t('product.devices.beacons.colAsset'), width: 120 },
  { prop: 'lastRssi', label: 'RSSI', width: 80 },
  { prop: 'lastSeenAt', label: t('product.devices.beacons.colLastSeen'), width: 170 },
  { prop: 'lastGatewayName', label: t('product.devices.beacons.colGateway'), width: 120 },
  { prop: 'protocolType', label: t('product.devices.beacons.colProtocol'), width: 110 },
  { prop: 'batteryLabel', label: t('product.devices.beacons.colBattery'), width: 120 },
  { prop: 'status', label: t('common.status'), width: 100 }
])

function isBound(row: NamedResource) {
  return !!row.fields?.boundAssetId
}

function protocolKind(row: NamedResource) {
  const bound = String(row.fields?.protocolType || '').toUpperCase()
  const detected = String(row.fields?.signalProfile || '').toUpperCase()
  return { bound, detected }
}

function protocolLabel(row: NamedResource) {
  const { bound, detected } = protocolKind(row)
  const kind = detected || (bound === 'AUTO' ? '' : bound)
  const kindText = kind === 'FINDMY' ? 'Find My' : kind === 'BEACON' ? 'Beacon' : ''
  if (bound === 'AUTO' || (!bound && kindText)) {
    return kindText ? t('product.assets.list.autoProtocol', { kind: kindText }) : t('product.assets.list.autoOnly')
  }
  if (bound === 'FINDMY') return 'Find My'
  if (bound === 'BEACON') return 'Beacon'
  return kindText || '—'
}

function batteryBucket(row: NamedResource) {
  const level = row.fields?.batteryLevel
  if (level != null && Number.isFinite(Number(level))) {
    const n = Number(level)
    if (n === 0) return 'FULL'
    if (n === 1) return 'MEDIUM'
    if (n === 2) return 'LOW'
    if (n === 3) return 'CRITICAL'
  }
  const percent = row.fields?.batteryPercent
  if (percent != null && Number.isFinite(Number(percent))) {
    const p = Number(percent)
    if (p >= 80) return 'FULL'
    if (p >= 50) return 'MEDIUM'
    if (p >= 20) return 'LOW'
    return 'CRITICAL'
  }
  return 'UNKNOWN'
}

function matchBeaconFilters(row: NamedResource) {
  if (filterBound.value === 'BOUND' && !isBound(row)) return false
  if (filterBound.value === 'UNBOUND' && isBound(row)) return false
  if (filterPresence.value !== 'ALL') {
    const status = String(row.status || row.fields?.status || '').toUpperCase()
    if (status !== filterPresence.value) return false
  }
  if (filterBattery.value !== 'ALL' && batteryBucket(row) !== filterBattery.value) return false
  if (filterProtocol.value !== 'ALL') {
    const { bound, detected } = protocolKind(row)
    if (filterProtocol.value === 'NONE') {
      if (bound === 'FINDMY' || bound === 'BEACON' || bound === 'AUTO' || detected === 'FINDMY' || detected === 'BEACON') {
        return false
      }
    } else if (filterProtocol.value === 'AUTO') {
      if (bound !== 'AUTO') return false
    } else if (bound !== filterProtocol.value && detected !== filterProtocol.value) {
      return false
    }
  }
  return true
}

function resetFilters() {
  filterBound.value = 'ALL'
  filterPresence.value = 'ALL'
  filterBattery.value = 'ALL'
  filterProtocol.value = 'ALL'
}

function applyRouteFilters() {
  const bound = queryText(route.query, 'bound').toUpperCase()
  if (bound === 'BOUND' || bound === 'UNBOUND') {
    filterBound.value = bound
  }
}

function displayOrDash(value: unknown) {
  if (value == null || value === '') return '—'
  return String(value)
}

function beaconDeviceName(row: NamedResource) {
  const mac = String(row.fields?.macAddress || row.code || '').trim()
  const name = String(row.name || '').trim()
  if (!name) return ''
  if (mac && name.toLowerCase() === mac.toLowerCase()) return ''
  return name
}

function reasonLabel(reason: unknown) {
  switch (String(reason || '')) {
    case 'SCAN':
      return t('product.devices.beacons.reasonScanOnline')
    case 'TTL_EXPIRED':
      return t('product.devices.beacons.reasonTimeout')
    case 'GATEWAY_OFFLINE':
      return t('product.devices.beacons.reasonGatewayOffline')
    default:
      return String(reason || '—')
  }
}

function statusLabel(status: unknown) {
  return String(status || '').toUpperCase() === 'ONLINE' ? t('product.devices.beacons.statusOnline') : t('product.devices.beacons.statusOffline')
}

async function listBeacons(scope: Api.AssetPlatform.ProjectScope) {
  const rows = await beaconsApi.list(scope)
  cachedRows.value = rows
  return rows
}

async function removeBeacon(scope: Api.AssetPlatform.ProjectScope, id: string) {
  const target = cachedRows.value.find((item) => item.id === id)
  if (target?.fields?.boundAssetId) {
    ElMessage.warning(t('product.devices.beacons.cannotDeleteBound'))
    throw new Error('bound')
  }
  return beaconsApi.remove(scope, id)
}

async function loadSignals(silent = false) {
  if (!projectScope.value || !current.value) {
    scans.value = []
    return
  }
  const token = ++historyLoadToken
  const beaconId = current.value.id
  if (!silent) signalsLoading.value = true
  try {
    const page = await fetchBeaconScans(projectScope.value, beaconId, { current: 1, size: 100 })
    if (token !== historyLoadToken || current.value?.id !== beaconId) return
    scans.value = page.records || []
  } catch {
    if (token !== historyLoadToken) return
    if (!silent) scans.value = []
  } finally {
    if (token === historyLoadToken && !silent) signalsLoading.value = false
  }
}

async function loadPresence(silent = false, page = presencePage.value) {
  if (!projectScope.value || !current.value) {
    presenceEvents.value = []
    presenceTotal.value = 0
    return
  }
  const token = ++historyLoadToken
  const beaconId = current.value.id
  if (!silent) presenceLoading.value = true
  try {
    const res = await fetchBeaconPresenceEvents(projectScope.value, beaconId, {
      current: page,
      size: PRESENCE_PAGE_SIZE,
      days: 30
    })
    if (token !== historyLoadToken || current.value?.id !== beaconId) return
    presenceEvents.value = res.records || []
    presenceTotal.value = Number(res.total || 0)
    presencePage.value = page
  } catch {
    if (token !== historyLoadToken) return
    if (!silent) {
      presenceEvents.value = []
      presenceTotal.value = 0
    }
  } finally {
    if (token === historyLoadToken && !silent) presenceLoading.value = false
  }
}

async function loadActiveHistory(silent = false) {
  if (historyTab.value === 'presence') {
    await loadPresence(silent)
  } else {
    await loadSignals(silent)
  }
}

function clearHistoryTimer() {
  if (historyTimer) {
    clearInterval(historyTimer)
    historyTimer = null
  }
}

function setupHistoryPolling() {
  clearHistoryTimer()
  if (!historyOpen.value) return
  historyTimer = setInterval(() => {
    if (document.visibilityState === 'hidden') return
    void loadActiveHistory(true)
  }, 5000)
}

function openHistory(row: NamedResource) {
  if (!projectScope.value) return
  current.value = row
  historyTab.value = 'signals'
  presencePage.value = 1
  scans.value = []
  presenceEvents.value = []
  presenceTotal.value = 0
  if (historyOpen.value) {
    void onHistoryOpened()
    return
  }
  historyOpen.value = true
}

async function onHistoryOpened() {
  await loadActiveHistory(false)
  setupHistoryPolling()
}

watch(historyOpen, (open) => {
  if (!open) {
    clearHistoryTimer()
    historyLoadToken += 1
  }
})

watch(historyTab, async () => {
  if (!historyOpen.value) return
  if (historyTab.value === 'presence') {
    presencePage.value = 1
  }
  await loadActiveHistory(false)
  setupHistoryPolling()
})

watch(
  () => route.query,
  () => applyRouteFilters(),
  { immediate: true }
)

onUnmounted(() => clearHistoryTimer())

async function batchDelete(selected: NamedResource[]) {
  if (!projectScope.value || !selected.length) return
  const bound = selected.filter((row) => row.fields?.boundAssetId)
  const unbound = selected.filter((row) => !row.fields?.boundAssetId)
  if (!unbound.length) {
    ElMessage.warning(t('product.devices.beacons.allBoundSelected'))
    return
  }
  const tip =
    bound.length > 0
      ? t('product.devices.beacons.batchDeletePartial', { unbound: unbound.length, bound: bound.length })
      : t('product.devices.beacons.batchDeleteAll', { count: unbound.length })
  await ElMessageBox.confirm(tip, t('product.devices.beacons.batchDeleteTitle'), {
    type: 'warning',
    confirmButtonText: t('common.delete'),
    cancelButtonText: t('common.cancel')
  })
  batchDeleting.value = true
  try {
    const result = await beaconsApi.batchDelete(
      projectScope.value,
      unbound.map((row) => row.id)
    )
    const blocked = Array.isArray(result.blocked) ? result.blocked.length : 0
    if (blocked > 0 || bound.length > 0) {
      ElMessage.warning(t('product.devices.beacons.deletedPartial', { deleted: result.deleted, skipped: blocked + bound.length }))
    } else {
      ElMessage.success(t('product.devices.beacons.deletedOk', { deleted: result.deleted }))
    }
    pageRef.value?.clearSelection()
    await pageRef.value?.reload()
  } finally {
    batchDeleting.value = false
  }
}

async function downloadTemplate() {
  if (!projectScope.value) return
  await beaconsApi.downloadImportTemplate(projectScope.value)
  ElMessage.success(t('product.devices.beacons.templateDownloading'))
}

function openImport() {
  importFile.value = null
  importResult.value = null
  importOpen.value = true
}

function onImportFileChange(file: { raw?: File } | null) {
  importFile.value = file?.raw || null
  importResult.value = null
}

async function confirmImport() {
  if (!projectScope.value || !importFile.value) return
  importing.value = true
  try {
    const result = await beaconsApi.importCsv(projectScope.value, importFile.value)
    importResult.value = result
    const parts = [t('product.devices.beacons.importCreated', { n: result.created }), t('product.devices.beacons.importUpdated', { n: result.updated })]
    if (result.skipped) parts.push(t('product.devices.beacons.importSkipped', { n: result.skipped }))
    if (result.failed) parts.push(t('product.devices.beacons.importFailed', { n: result.failed }))
    if (result.failed > 0) {
      ElMessage.warning(parts.join(' · '))
    } else {
      ElMessage.success(parts.join(' · '))
    }
    await pageRef.value?.reload()
  } finally {
    importing.value = false
  }
}
</script>
<template>
  <ResourceCrudPage
    ref="pageRef"
    :title="t('product.devices.beacons.title')"
    :subtitle="t('product.devices.beacons.subtitle')"
    :list="listBeacons"
    :create="createBeacon"
    :remove="removeBeacon"
    :poll-interval-ms="5000"
    :action-width="200"
    enable-selection
    enable-detail
    hide-code
    hide-name
    hide-description
    :create-label="t('product.devices.beacons.create')"
    :search-placeholder="t('product.devices.beacons.searchPh')"
    :search-keys="['code', 'name', 'status', 'macAddress', 'boundAssetName', 'protocolType']"
    :extra-fields="beaconExtraFields"
    table-key="beacons"
    :columns="beaconColumns"
    :row-filter="matchBeaconFilters"
    :filter-key="filterKey"
  >
    <template #toolbar="{ selected }">
      <ElButton plain :disabled="!projectScope" @click="downloadTemplate">{{ t('product.devices.beacons.downloadTemplate') }}</ElButton>
      <ElButton plain :disabled="!projectScope" @click="openImport">{{ t('product.devices.beacons.import') }}</ElButton>
      <ElButton
        type="danger"
        plain
        :disabled="!selected.length"
        :loading="batchDeleting"
        @click="batchDelete(selected)"
      >
        {{ selected.length ? t('product.devices.beacons.batchDeleteCount', { count: selected.length }) : t('product.devices.beacons.batchDelete') }}
      </ElButton>
    </template>
    <template #filters>
      <ElSelect v-model="filterBound" :placeholder="t('product.devices.beacons.filterBound')">
        <ElOption :label="t('product.devices.beacons.filterAllBound')" value="ALL" />
        <ElOption :label="t('product.devices.beacons.filterBoundYes')" value="BOUND" />
        <ElOption :label="t('product.devices.beacons.filterBoundNo')" value="UNBOUND" />
      </ElSelect>
      <ElSelect v-model="filterPresence" :placeholder="t('product.devices.beacons.filterPresence')">
        <ElOption :label="t('product.devices.beacons.filterAllPresence')" value="ALL" />
        <ElOption :label="t('product.devices.beacons.filterOnline')" value="ONLINE" />
        <ElOption :label="t('product.devices.beacons.filterOffline')" value="OFFLINE" />
      </ElSelect>
      <ElSelect v-model="filterBattery" :placeholder="t('product.devices.beacons.filterBattery')">
        <ElOption :label="t('product.devices.beacons.filterAllBattery')" value="ALL" />
        <ElOption :label="t('product.battery.full')" value="FULL" />
        <ElOption :label="t('product.battery.medium')" value="MEDIUM" />
        <ElOption :label="t('product.battery.low')" value="LOW" />
        <ElOption :label="t('product.battery.critical')" value="CRITICAL" />
        <ElOption :label="t('product.devices.beacons.filterBatteryUnknown')" value="UNKNOWN" />
      </ElSelect>
      <ElSelect v-model="filterProtocol" :placeholder="t('product.devices.beacons.filterProtocol')" style="width: 128px">
        <ElOption :label="t('product.devices.beacons.filterAllProtocol')" value="ALL" />
        <ElOption :label="t('product.assets.list.autoDetect')" value="AUTO" />
        <ElOption label="Find My" value="FINDMY" />
        <ElOption label="Beacon" value="BEACON" />
        <ElOption :label="t('product.devices.beacons.filterNoProtocol')" value="NONE" />
      </ElSelect>
      <ElButton v-if="hasActiveFilters" @click="resetFilters">{{ t('product.devices.beacons.clearFilters') }}</ElButton>
    </template>
    <template #cell-bindStatus="{ row }">
      <ElTag :type="isBound(row) ? 'success' : 'info'" size="small" effect="light">
        {{ isBound(row) ? t('product.devices.beacons.filterBoundYes') : t('product.devices.beacons.filterBoundNo') }}
      </ElTag>
    </template>
    <template #cell-protocolType="{ row }">
      {{ protocolLabel(row) }}
    </template>
    <template #cell-batteryLabel="{ row }">
      <BatteryPill
        :protocol-type="(row.fields?.signalProfile || row.fields?.protocolType) as string | undefined"
        :label="row.fields?.batteryLabel as string | undefined"
        :percent="row.fields?.batteryPercent as number | undefined"
        :level="row.fields?.batteryLevel as number | undefined"
      />
    </template>
    <template #form-extra="{ form, editing, readOnly }">
      <template v-if="readOnly && editing">
        <ElFormItem v-if="beaconDeviceName(editing)" :label="t('product.devices.beacons.deviceName')">
          <ElInput :model-value="beaconDeviceName(editing)" disabled />
        </ElFormItem>
        <ElFormItem :label="t('product.devices.beacons.ibeaconUuid')">
          <ElInput :model-value="displayOrDash(form.fields?.ibeaconUuid)" disabled />
        </ElFormItem>
        <ElFormItem label="Major">
          <ElInput :model-value="displayOrDash(form.fields?.ibeaconMajor)" disabled />
        </ElFormItem>
        <ElFormItem label="Minor">
          <ElInput :model-value="displayOrDash(form.fields?.ibeaconMinor)" disabled />
        </ElFormItem>
        <ElFormItem :label="t('product.devices.beacons.colProtocol')">
          <ElInput :model-value="protocolLabel(editing)" disabled />
        </ElFormItem>
        <ElFormItem :label="t('product.devices.beacons.colBattery')">
          <BatteryPill
            :protocol-type="(editing.fields?.signalProfile || editing.fields?.protocolType) as string | undefined"
            :label="editing.fields?.batteryLabel as string | undefined"
            :percent="editing.fields?.batteryPercent as number | undefined"
            :level="editing.fields?.batteryLevel as number | undefined"
          />
        </ElFormItem>
        <ElFormItem label="RSSI">
          <ElInput :model-value="displayOrDash(form.fields?.lastRssi)" disabled />
        </ElFormItem>
        <ElFormItem :label="t('product.devices.beacons.colGateway')">
          <ElInput :model-value="displayOrDash(form.fields?.lastGatewayName || form.fields?.lastGatewayMac)" disabled />
        </ElFormItem>
        <ElFormItem :label="t('product.devices.beacons.colLastSeen')">
          <ElInput :model-value="formatDateTime(form.fields?.lastSeenAt as string | null | undefined) || '—'" disabled />
        </ElFormItem>
      </template>
    </template>
    <template #actions="{ row }">
      <ElButton link type="primary" @click="openHistory(row)">{{ t('product.devices.beacons.history') }}</ElButton>
    </template>
  </ResourceCrudPage>

  <ElDrawer
    v-model="historyOpen"
    :title="historyTitle"
    size="620px"
    destroy-on-close
    class="history-drawer"
    @opened="onHistoryOpened"
  >
    <div class="history-body">
      <ElTabs v-model="historyTab">
        <ElTabPane :label="t('product.assets.status.historySignal')" name="signals" />
        <ElTabPane :label="t('product.devices.beacons.historyPresence')" name="presence" />
      </ElTabs>
      <div v-if="historyTab === 'signals'" class="presence-panel">
        <p class="presence-hint">{{ t('product.devices.beacons.signalsHint') }}</p>
        <ElTable
          v-loading="signalsLoading"
          :data="scans"
          stripe
          :height="HISTORY_TABLE_HEIGHT"
        >
        <ElTableColumn :label="t('common.time')" min-width="160">
          <template #default="{ row }">{{ formatDateTime(row.fields?.receivedAt as string | null | undefined) }}</template>
        </ElTableColumn>
        <ElTableColumn label="RSSI" width="80">
          <template #default="{ row }">{{ row.fields?.rssi }}</template>
        </ElTableColumn>
        <ElTableColumn :label="t('product.overview.gateways')" min-width="140">
          <template #default="{ row }">{{ row.fields?.gatewayName || row.fields?.gatewayMac || row.fields?.gatewayId }}</template>
        </ElTableColumn>
        </ElTable>
      </div>
      <div v-else class="presence-panel">
        <p class="presence-hint">{{ t('product.devices.beacons.presenceHint') }}</p>
        <ElTable
          v-loading="presenceLoading"
          :data="presenceEvents"
          stripe
          :height="HISTORY_TABLE_HEIGHT"
        >
          <ElTableColumn :label="t('common.time')" min-width="160">
            <template #default="{ row }">{{ formatDateTime(row.fields?.changedAt as string | null | undefined) }}</template>
          </ElTableColumn>
          <ElTableColumn :label="t('common.status')" width="80">
            <template #default="{ row }">{{ statusLabel(row.fields?.status) }}</template>
          </ElTableColumn>
          <ElTableColumn :label="t('product.devices.beacons.colReason')" min-width="110">
            <template #default="{ row }">{{ reasonLabel(row.fields?.reason) }}</template>
          </ElTableColumn>
          <ElTableColumn :label="t('product.overview.gateways')" min-width="120">
            <template #default="{ row }">{{ row.fields?.gatewayName || row.fields?.gatewayMac || '—' }}</template>
          </ElTableColumn>
        </ElTable>
        <div class="presence-pager">
          <ElPagination
            layout="prev, pager, next, total"
            :current-page="presencePage"
            :page-size="PRESENCE_PAGE_SIZE"
            :total="presenceTotal"
            @current-change="(p: number) => loadPresence(false, p)"
          />
        </div>
      </div>
    </div>
  </ElDrawer>

  <ElDialog v-model="importOpen" :title="t('product.devices.beacons.importTitle')" width="520px" destroy-on-close>
    <p style="margin: 0 0 12px; color: var(--el-text-color-secondary); font-size: 13px">
      {{ t('product.devices.beacons.importHint') }}
    </p>
    <ElUpload
      drag
      :auto-upload="false"
      :limit="1"
      accept=".csv,text/csv"
      :on-change="onImportFileChange"
      :on-remove="() => onImportFileChange(null)"
    >
      <div class="el-upload__text">{{ t('product.devices.beacons.dropHint') }}<em>{{ t('product.devices.beacons.clickSelect') }}</em></div>
    </ElUpload>
    <div v-if="importResult" class="import-result">
      <p>
        {{ t('product.devices.beacons.importResult', { created: importResult.created, updated: importResult.updated, skipped: importResult.skipped, failed: importResult.failed }) }}
      </p>
      <ElTable
        v-if="importResult.errors?.length"
        :data="importResult.errors"
        size="small"
        max-height="220"
        stripe
      >
        <ElTableColumn prop="row" :label="t('product.devices.beacons.colRow')" width="70" />
        <ElTableColumn prop="message" :label="t('product.devices.beacons.colReason')" />
      </ElTable>
      <p v-if="importResult.errorTruncated" class="import-result__hint">{{ t('product.devices.beacons.errorTruncated') }}</p>
    </div>
    <template #footer>
      <ElButton @click="importOpen = false">{{ t('common.close') }}</ElButton>
      <ElButton type="primary" :loading="importing" :disabled="!importFile" @click="confirmImport">
        {{ t('product.devices.beacons.startImport') }}
      </ElButton>
    </template>
  </ElDialog>

</template>

<style scoped>
.history-body {
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.presence-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.presence-hint {
  margin: 0 0 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.presence-pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.import-result {
  margin-top: 14px;
}
.import-result p {
  margin: 0 0 8px;
  font-size: 13px;
}
.import-result__hint {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
