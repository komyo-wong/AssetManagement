<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useTenantContextStore } from '@/store/modules/tenant-context'
import {
  assetTypesApi,
  assetsApi,
  closeInventorySession,
  deleteInventorySession,
  fetchInventorySession,
  fetchInventorySessions,
  gatewaysApi,
  startInventorySession,
  type NamedResource
} from '@/api/asset-platform'
import { formatDateTime } from '@/utils/datetime'
import { localizeInventoryName } from '@/utils/locale-labels'
import TablePager from '@/components/business/TablePager.vue'
import ProductTableHeader from '@/components/business/ProductTableHeader.vue'
import { useClientPagination } from '@/composables/useClientPagination'
import { useProductTable } from '@/composables/useProductTable'
import { useTableColumns } from '@/hooks/core/useTableColumns'
import type { ColumnOption } from '@/types/component'

const { t } = useI18n()

type ScopeMode = 'ALL' | 'TYPES' | 'ASSETS'
type ReportItem = Record<string, unknown>

const { projectScope } = storeToRefs(useTenantContextStore())
const { tableSize, isZebra, isBorder } = useProductTable()
const rows = ref<NamedResource[]>([])
const loading = ref(false)
const createOpen = ref(false)
const reportOpen = ref(false)
const starting = ref(false)
const closing = ref(false)

const formName = ref('')
const windowMinutes = ref(5)
const scopeMode = ref<ScopeMode>('ALL')
const selectedTypeIds = ref<string[]>([])
const selectedAssetIds = ref<string[]>([])
const selectedGatewayIds = ref<string[]>([])
const assetTypes = ref<NamedResource[]>([])
const assets = ref<NamedResource[]>([])
const gateways = ref<NamedResource[]>([])

const active = ref<NamedResource | null>(null)
const report = ref<NamedResource | null>(null)
const reportFilter = ref<'ALL' | 'FOUND' | 'MISSING'>('ALL')
/** 按开始日期筛选；null = 不限 */
const dateRange = ref<[Date, Date] | null>(null)

const { columnChecks, resetColumns } = useTableColumns(() => {
  const cols: ColumnOption[] = [
    { prop: 'code', label: t('common.code'), checked: true },
    { prop: 'name', label: t('common.name'), checked: true },
    { prop: 'status', label: t('common.status'), checked: true },
    { prop: 'scope', label: t('product.assets.inventory.colScope'), checked: true },
    { prop: 'gateways', label: t('product.overview.gateways'), checked: true },
    { prop: 'duration', label: t('product.assets.inventory.colDuration'), checked: true },
    { prop: 'foundExpected', label: t('product.assets.inventory.colFoundExpected'), checked: true },
    { prop: 'coverage', label: t('product.overview.coverage'), checked: true },
    { prop: 'started', label: t('product.assets.inventory.colStarted'), checked: true },
    { prop: '__actions', label: t('common.actions'), width: 120, fixed: 'right', checked: true, disabled: true }
  ]
  return cols
})
watch(() => t('common.actions'), () => resetColumns())
function invColVisible(prop: string) {
  return columnChecks.value.some((c) => c.prop === prop && (c.checked ?? c.visible ?? true))
}

let listTimer: ReturnType<typeof setInterval> | null = null
let detailTimer: ReturnType<typeof setInterval> | null = null

const remainingText = computed(() => formatCountdown(Number(active.value?.fields?.remainingSeconds || 0)))

const filteredRows = computed(() => {
  const range = dateRange.value
  if (!range || !range[0] || !range[1]) return rows.value
  const start = startOfLocalDay(range[0]).getTime()
  const end = endOfLocalDay(range[1]).getTime()
  return rows.value.filter((row) => {
    const raw = row.fields?.startedAt || row.createdAt
    if (!raw) return false
    const t = new Date(String(raw)).getTime()
    if (Number.isNaN(t)) return false
    return t >= start && t <= end
  })
})

const {
  current: pageCurrent,
  size: pageSize,
  total: pageTotal,
  pagedRows,
  onPageChange,
  onSizeChange
} = useClientPagination(filteredRows, { resetOn: dateRange })

const reportItems = computed(() => {
  const items = (report.value?.fields?.items as ReportItem[] | undefined) || []
  if (reportFilter.value === 'FOUND') return items.filter((i) => Boolean(i.found))
  if (reportFilter.value === 'MISSING') return items.filter((i) => !i.found)
  return items
})

const coveragePct = computed(() => Math.round(Number(report.value?.fields?.coverage || active.value?.fields?.coverage || 0) * 100))

function startOfLocalDay(d: Date) {
  const x = new Date(d)
  x.setHours(0, 0, 0, 0)
  return x
}

function endOfLocalDay(d: Date) {
  const x = new Date(d)
  x.setHours(23, 59, 59, 999)
  return x
}

function formatCountdown(totalSeconds: number) {
  const s = Math.max(0, Math.floor(totalSeconds))
  const m = Math.floor(s / 60)
  const r = s % 60
  return `${String(m).padStart(2, '0')}:${String(r).padStart(2, '0')}`
}

function statusLabel(status: string) {
  if (status === 'OPEN') return t('product.assets.inventory.statusOpen')
  if (status === 'CLOSED') return t('product.assets.inventory.statusClosed')
  return status
}

function scopeLabel(row: NamedResource) {
  const type = String(row.fields?.scopeType || 'ALL')
  if (type === 'TYPES') return t('product.assets.inventory.scopeTypes')
  if (type === 'ASSETS') return t('product.assets.inventory.scopeAssets')
  return t('product.assets.inventory.scopeAll')
}

function gatewayLabel(row: NamedResource) {
  const list = (row.fields?.gateways as Array<{ name?: string; code?: string }> | undefined) || []
  if (!list.length) return '-'
  if (list.length <= 2) return list.map((g) => g.name || g.code).join(', ')
  return t('product.assets.inventory.andMore', { name: list[0]?.name || list[0]?.code, count: list.length })
}

async function loadList(silent = false) {
  if (!projectScope.value) {
    rows.value = []
    active.value = null
    return
  }
  if (!silent) loading.value = true
  try {
    rows.value = await fetchInventorySessions(projectScope.value)
    const open = rows.value.find((r) => r.status === 'OPEN') || null
    if (open) {
      await loadActive(open.id, true)
    } else if (!reportOpen.value) {
      active.value = null
    }
  } catch {
    if (!silent) rows.value = []
  } finally {
    if (!silent) loading.value = false
  }
}

async function loadActive(id: string, silent = false) {
  if (!projectScope.value) return
  try {
    const detail = await fetchInventorySession(projectScope.value, id)
    if (detail.status === 'OPEN') {
      active.value = detail
      if (reportOpen.value && report.value?.id === id) {
        report.value = detail
      }
    } else {
      active.value = null
      if (reportOpen.value && report.value?.id === id) {
        report.value = detail
      }
      await loadList(true)
    }
  } catch {
    if (!silent) active.value = null
  }
}

async function openCreate() {
  if (!projectScope.value) return
  formName.value = t('product.assets.inventory.defaultName', { time: new Date().toLocaleString() })
  windowMinutes.value = 5
  scopeMode.value = 'ALL'
  selectedTypeIds.value = []
  selectedAssetIds.value = []
  selectedGatewayIds.value = []
  assetTypes.value = await assetTypesApi.list(projectScope.value)
  assets.value = await assetsApi.list(projectScope.value)
  gateways.value = (await gatewaysApi.list(projectScope.value)).filter((g) => g.status !== 'ARCHIVED')
  createOpen.value = true
}

async function start() {
  if (!projectScope.value) return
  if (selectedGatewayIds.value.length === 0) {
    ElMessage.warning(t('product.assets.inventory.needGateway'))
    return
  }
  if (scopeMode.value === 'TYPES' && selectedTypeIds.value.length === 0) {
    ElMessage.warning(t('product.assets.inventory.needType'))
    return
  }
  if (scopeMode.value === 'ASSETS' && selectedAssetIds.value.length === 0) {
    ElMessage.warning(t('product.assets.inventory.needAsset'))
    return
  }
  starting.value = true
  try {
    const fields: Record<string, unknown> = {
      windowSeconds: Math.max(1, windowMinutes.value) * 60,
      gatewayIds: selectedGatewayIds.value
    }
    if (scopeMode.value === 'TYPES') fields.assetTypeIds = selectedTypeIds.value
    if (scopeMode.value === 'ASSETS') fields.assetIds = selectedAssetIds.value
    const created = await startInventorySession(projectScope.value, {
      name: formName.value.trim() || t('product.assets.inventory.defaultName', { time: new Date().toLocaleString() }),
      fields
    })
    createOpen.value = false
    ElMessage.success(t('product.assets.inventory.started'))
    active.value = created
    report.value = created
    reportOpen.value = true
    reportFilter.value = 'ALL'
    await loadList(true)
    setupDetailPolling()
  } finally {
    starting.value = false
  }
}

async function openReport(row: NamedResource) {
  if (!projectScope.value) return
  reportOpen.value = true
  reportFilter.value = 'ALL'
  report.value = await fetchInventorySession(projectScope.value, row.id)
  if (report.value.status === 'OPEN') {
    active.value = report.value
    setupDetailPolling()
  }
}

async function finishNow() {
  if (!projectScope.value || !active.value) return
  closing.value = true
  try {
    const closed = await closeInventorySession(projectScope.value, active.value.id)
    ElMessage.success(t('product.assets.inventory.finished'))
    active.value = null
    report.value = closed
    reportOpen.value = true
    await loadList(true)
  } finally {
    closing.value = false
  }
}

async function removeSession(row: NamedResource) {
  if (!projectScope.value) return
  try {
    await ElMessageBox.confirm(
      t('product.assets.inventory.deleteConfirm', { name: localizeInventoryName(row.name, t) }),
      t('common.deleteConfirm'),
      { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel') }
    )
  } catch {
    return
  }
  await deleteInventorySession(projectScope.value, row.id)
  ElMessage.success(t('common.deleted'))
  if (active.value?.id === row.id) active.value = null
  if (report.value?.id === row.id) {
    reportOpen.value = false
    report.value = null
  }
  await loadList(true)
}

function csvCell(value: unknown) {
  const raw = value == null ? '' : String(value)
  if (/[",\n\r]/.test(raw)) return `"${raw.replace(/"/g, '""')}"`
  return raw
}

function exportReport() {
  if (!report.value) return
  const items = reportItems.value
  const header = [t('product.assets.inventory.csv.code'), t('product.assets.inventory.csv.name'), t('product.assets.inventory.csv.type'), t('product.assets.inventory.csv.result'), t('product.assets.inventory.csv.rssi'), t('product.assets.inventory.csv.gateway'), t('product.assets.inventory.csv.foundAt')]
  const lines = [header.join(',')]
  for (const row of items) {
    const result = row.found ? t('product.assets.inventory.found') : row.bound === false ? t('product.assets.inventory.unbound') : t('product.assets.inventory.missing')
    lines.push(
      [
        csvCell(row.assetCode),
        csvCell(row.assetName),
        csvCell(row.assetTypeName || ''),
        csvCell(result),
        csvCell(row.lastRssi == null ? '' : row.lastRssi),
        csvCell(row.gatewayName || row.gatewayMac || ''),
        csvCell(row.foundAt ? formatDateTime(row.foundAt as string) : '')
      ].join(',')
    )
  }
  const blob = new Blob(['\uFEFF' + lines.join('\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = t('product.assets.inventory.csvFileName', { name: report.value.code || report.value.name || 'export' })
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
  ElMessage.success(t('product.assets.inventory.exported'))
}

function clearTimers() {
  if (listTimer) {
    clearInterval(listTimer)
    listTimer = null
  }
  if (detailTimer) {
    clearInterval(detailTimer)
    detailTimer = null
  }
}

function setupListPolling() {
  if (listTimer) clearInterval(listTimer)
  listTimer = setInterval(() => {
    if (document.visibilityState === 'hidden') return
    void loadList(true)
  }, 5000)
}

function setupDetailPolling() {
  if (detailTimer) clearInterval(detailTimer)
  detailTimer = setInterval(() => {
    if (document.visibilityState === 'hidden') return
    const id = active.value?.id || (report.value?.status === 'OPEN' ? report.value.id : null)
    if (id) void loadActive(id, true)
  }, 2000)
}

watch(
  projectScope,
  () => {
    void loadList()
    setupListPolling()
  },
  { immediate: true }
)

watch(reportOpen, (open) => {
  if (!open) {
    if (!active.value || active.value.status !== 'OPEN') {
      if (detailTimer) {
        clearInterval(detailTimer)
        detailTimer = null
      }
    }
  } else {
    setupDetailPolling()
  }
})

onUnmounted(() => clearTimers())
</script>

<template>
  <div class="page-card art-card inventory-page" v-loading="loading">
    <div class="inventory-page__head">
      <div>
        <h2>{{ t('product.assets.inventory.title') }}</h2>
        <p>
          {{ t('product.assets.inventory.subtitle') }}
        </p>
      </div>
      <ElButton type="primary" :disabled="!projectScope || !!active" @click="openCreate">{{ t('product.assets.inventory.start') }}</ElButton>
    </div>

      <div v-if="active" class="live-panel">
        <div class="live-panel__main">
          <div>
            <div class="live-panel__label">{{ t('product.assets.inventory.inProgress', { name: localizeInventoryName(active.name, t) }) }}</div>
            <div class="live-panel__countdown">{{ remainingText }}</div>
            <div class="live-panel__meta">
              {{ t('product.assets.inventory.foundProgress', { found: active.fields?.foundCount || 0, expected: active.fields?.expectedCount || 0 }) }}
              · {{ t('product.assets.inventory.coverage', { pct: Math.round(Number(active.fields?.coverage || 0) * 100) }) }}
              · {{ scopeLabel(active) }}
              · {{ t('product.assets.inventory.gatewayPart', { label: gatewayLabel(active) }) }}
            </div>
            <div class="live-panel__tip">{{ t('product.assets.inventory.autoCloseTip') }}</div>
          </div>
          <div class="live-panel__actions">
            <ElButton @click="openReport(active)">{{ t('product.assets.inventory.viewDetail') }}</ElButton>
            <ElButton type="danger" :loading="closing" @click="finishNow">{{ t('product.assets.inventory.finishEarly') }}</ElButton>
          </div>
        </div>
        <ElProgress
          :percentage="Math.min(100, Math.round(Number(active.fields?.coverage || 0) * 100))"
          :stroke-width="10"
          status="success"
        />
      </div>

      <div class="filter-bar art-table-card">
        <ProductTableHeader v-model:columns="columnChecks" :loading="loading" full-class="page-card" @refresh="() => loadList()">
          <template #left>
            <div class="filter-bar__left">
              <ElDatePicker
                v-model="dateRange"
                type="daterange"
                :range-separator="t('common.to')"
                :start-placeholder="t('product.assets.inventory.dateStart')"
                :end-placeholder="t('product.assets.inventory.dateEnd')"
                clearable
                :editable="false"
                style="width: 280px"
              />
              <ElButton v-if="dateRange" @click="dateRange = null">{{ t('product.assets.inventory.clearDate') }}</ElButton>
              <span class="filter-bar__count">{{ t('product.assets.inventory.filterCount', { filtered: filteredRows.length, total: rows.length }) }}</span>
            </div>
          </template>
        </ProductTableHeader>
      </div>

      <ElTable :data="pagedRows" :size="tableSize" :stripe="isZebra" :border="isBorder">
        <ElTableColumn v-if="invColVisible('code')" prop="code" :label="t('common.code')" width="160" />
        <ElTableColumn v-if="invColVisible('name')" :label="t('common.name')" min-width="160">
          <template #default="{ row }">{{ localizeInventoryName(row.name, t) }}</template>
        </ElTableColumn>
        <ElTableColumn v-if="invColVisible('status')" :label="t('common.status')" width="100">
          <template #default="{ row }">{{ statusLabel(String(row.status || '')) }}</template>
        </ElTableColumn>
        <ElTableColumn v-if="invColVisible('scope')" :label="t('product.assets.inventory.colScope')" width="110">
          <template #default="{ row }">{{ scopeLabel(row) }}</template>
        </ElTableColumn>
        <ElTableColumn v-if="invColVisible('gateways')" :label="t('product.overview.gateways')" min-width="140">
          <template #default="{ row }">{{ gatewayLabel(row) }}</template>
        </ElTableColumn>
        <ElTableColumn v-if="invColVisible('duration')" :label="t('product.assets.inventory.colDuration')" width="90">
          <template #default="{ row }">{{ t('product.assets.inventory.minutesUnit', { n: Math.round(Number(row.fields?.windowSeconds || 0) / 60) }) }}</template>
        </ElTableColumn>
        <ElTableColumn v-if="invColVisible('foundExpected')" :label="t('product.assets.inventory.colFoundExpected')" width="110">
          <template #default="{ row }">{{ row.fields?.foundCount }}/{{ row.fields?.expectedCount }}</template>
        </ElTableColumn>
        <ElTableColumn v-if="invColVisible('coverage')" :label="t('product.overview.coverage')" width="90">
          <template #default="{ row }">{{ Math.round(Number(row.fields?.coverage || 0) * 100) }}%</template>
        </ElTableColumn>
        <ElTableColumn v-if="invColVisible('started')" :label="t('product.assets.inventory.colStarted')" width="170">
          <template #default="{ row }">{{ formatDateTime(row.fields?.startedAt as string | null | undefined) }}</template>
        </ElTableColumn>
        <ElTableColumn v-if="invColVisible('__actions')" :label="t('common.actions')" width="120" fixed="right">
          <template #default="{ row }">
            <ElButton link type="primary" @click="openReport(row)">{{ t('product.assets.inventory.report') }}</ElButton>
            <ElButton link type="danger" @click="removeSession(row)">{{ t('common.delete') }}</ElButton>
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
    

    <ElDialog v-model="createOpen" :title="t('product.assets.inventory.start')" width="560px" destroy-on-close>
      <ElForm label-position="top">
        <ElFormItem :label="t('common.name')">
          <ElInput v-model="formName" maxlength="80" />
        </ElFormItem>
        <ElFormItem :label="t('product.assets.inventory.formCountdown')">
          <ElInputNumber v-model="windowMinutes" :min="1" :max="240" />
        </ElFormItem>
        <ElFormItem :label="t('product.assets.inventory.formGateways')" required>
          <ElSelect
            v-model="selectedGatewayIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            :placeholder="t('product.assets.inventory.gatewaysPh')"
            style="width: 100%"
          >
            <ElOption
              v-for="g in gateways"
              :key="g.id"
              :label="`${g.code} · ${g.name}${g.fields?.macAddress ? ' · ' + g.fields.macAddress : ''}`"
              :value="g.id"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem :label="t('product.assets.inventory.formScope')">
          <ElRadioGroup v-model="scopeMode">
            <ElRadioButton value="ALL">{{ t('product.assets.inventory.scopeAll') }}</ElRadioButton>
            <ElRadioButton value="TYPES">{{ t('product.assets.inventory.scopeTypes') }}</ElRadioButton>
            <ElRadioButton value="ASSETS">{{ t('product.assets.inventory.scopeAssets') }}</ElRadioButton>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem v-if="scopeMode === 'TYPES'" :label="t('product.assets.types.title')">
          <ElSelect v-model="selectedTypeIds" multiple filterable collapse-tags collapse-tags-tooltip style="width: 100%">
            <ElOption v-for="t in assetTypes" :key="t.id" :label="`${t.code} · ${t.name}`" :value="t.id" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem v-if="scopeMode === 'ASSETS'" :label="t('product.overview.assets')">
          <ElSelect v-model="selectedAssetIds" multiple filterable collapse-tags collapse-tags-tooltip style="width: 100%">
            <ElOption v-for="a in assets" :key="a.id" :label="`${a.code} · ${a.name}`" :value="a.id" />
          </ElSelect>
        </ElFormItem>
        <p class="hint">{{ t('product.assets.inventory.formHint') }}</p>
      </ElForm>
      <template #footer>
        <ElButton @click="createOpen = false">{{ t('common.cancel') }}</ElButton>
        <ElButton type="primary" :loading="starting" @click="start">{{ t('common.start') }}</ElButton>
      </template>
    </ElDialog>

    <ElDrawer
      v-model="reportOpen"
      :title="t('product.assets.inventory.reportTitle', { name: localizeInventoryName(report?.name, t) })"
      size="720px"
    >
      <div v-if="report" class="report">
        <div class="report__summary">
          <div>
            <div class="report__kpi">{{ report.fields?.foundCount || 0 }}/{{ report.fields?.expectedCount || 0 }}</div>
            <div class="report__sub">{{ t('product.assets.inventory.reportSub', { pct: coveragePct }) }}</div>
          </div>
          <div class="report__meta">
            <div>{{ t('product.assets.inventory.reportStatus', { status: statusLabel(String(report.status || '')) }) }}</div>
            <div>{{ t('product.assets.inventory.reportScope', { scope: scopeLabel(report) }) }}</div>
            <div>{{ t('product.assets.inventory.reportGateway', { gateway: gatewayLabel(report) }) }}</div>
            <div>{{ t('product.assets.inventory.reportStarted', { time: formatDateTime(report.fields?.startedAt as string | null | undefined) }) }}</div>
            <div v-if="report.status === 'OPEN'">{{ t('product.assets.inventory.reportRemaining', { time: formatCountdown(Number(report.fields?.remainingSeconds || 0)) }) }}</div>
            <div v-else>{{ t('product.assets.inventory.reportClosed', { time: formatDateTime(report.fields?.closedAt as string | null | undefined) }) }}</div>
            <div>{{ t('product.assets.inventory.reportMissing', { missing: report.fields?.missingCount || 0, unbound: report.fields?.unboundCount || 0 }) }}</div>
          </div>
        </div>

        <ElRadioGroup v-model="reportFilter" style="margin-bottom: 12px">
          <ElRadioButton value="ALL">{{ t('common.all') }}</ElRadioButton>
          <ElRadioButton value="FOUND">{{ t('product.overview.found') }}</ElRadioButton>
          <ElRadioButton value="MISSING">{{ t('product.assets.inventory.missing') }}</ElRadioButton>
        </ElRadioGroup>

        <div style="display: flex; gap: 8px; margin-bottom: 12px">
          <ElButton type="primary" plain :disabled="!reportItems.length" @click="exportReport">{{ t('common.exportCsv') }}</ElButton>
          <ElButton type="danger" plain :disabled="!report" @click="report && removeSession(report)">{{ t('product.assets.inventory.deleteReport') }}</ElButton>
        </div>

        <ElTable :data="reportItems" stripe height="calc(100vh - 300px)">
          <ElTableColumn :label="t('product.overview.assets')" min-width="160">
            <template #default="{ row }">{{ row.assetCode }} · {{ row.assetName }}</template>
          </ElTableColumn>
          <ElTableColumn :label="t('common.type')" width="110">
            <template #default="{ row }">{{ row.assetTypeName || '-' }}</template>
          </ElTableColumn>
          <ElTableColumn :label="t('product.assets.inventory.csv.result')" width="90">
            <template #default="{ row }">
              <ElTag :type="row.found ? 'success' : 'danger'" effect="plain">
                {{ row.found ? t('product.assets.inventory.found') : row.bound === false ? t('product.assets.inventory.unbound') : t('product.assets.inventory.missing') }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="RSSI" width="90">
            <template #default="{ row }">{{ row.lastRssi == null ? '-' : `${row.lastRssi} dBm` }}</template>
          </ElTableColumn>
          <ElTableColumn :label="t('product.overview.gateways')" min-width="120">
            <template #default="{ row }">{{ row.gatewayName || row.gatewayMac || '-' }}</template>
          </ElTableColumn>
          <ElTableColumn :label="t('product.assets.inventory.csv.foundAt')" width="160">
            <template #default="{ row }">{{ formatDateTime(row.foundAt as string | null | undefined) }}</template>
          </ElTableColumn>
        </ElTable>
      </div>
    </ElDrawer>
  </div>
</template>

<style scoped>
.inventory-page {
  padding: 20px;
}
.inventory-page__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.inventory-page__head h2 {
  margin: 0;
}
.inventory-page__head p {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
}
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.filter-bar__left {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}
.filter-bar__count {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.live-panel {
  margin-bottom: 16px;
  padding: 16px;
  border: 1px solid color-mix(in srgb, var(--el-color-success) 35%, var(--el-border-color));
  background: color-mix(in srgb, var(--el-color-success) 8%, transparent);
  border-radius: 8px;
}
.live-panel__main {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.live-panel__label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.live-panel__countdown {
  font-size: 36px;
  font-weight: 700;
  letter-spacing: 0.04em;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}
.live-panel__meta {
  margin-top: 4px;
  color: var(--el-text-color-regular);
}
.live-panel__tip {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.live-panel__actions {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}
.hint {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.report__summary {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.report__kpi {
  font-size: 28px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.report__sub {
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
.report__meta {
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.7;
}
</style>
