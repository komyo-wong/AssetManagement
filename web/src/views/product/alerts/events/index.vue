<script setup lang="ts">
  import { computed, onUnmounted, ref, watch } from 'vue'
  import { storeToRefs } from 'pinia'
  import { useI18n } from 'vue-i18n'
  import { ElMessage } from 'element-plus'
  import { useTenantContextStore } from '@/store/modules/tenant-context'
  import { acknowledgeAlert, fetchAlerts, resolveAlert, type NamedResource } from '@/api/asset-platform'
  import { formatDateTime } from '@/utils/datetime'
  import { localizeAlertMessage, localizeAlertTitle } from '@/utils/alert-i18n'
  import TablePager from '@/components/business/TablePager.vue'
  import ProductTableHeader from '@/components/business/ProductTableHeader.vue'
  import { useClientPagination } from '@/composables/useClientPagination'
  import { useProductTable } from '@/composables/useProductTable'
  import { useTableColumns } from '@/hooks/core/useTableColumns'
  import type { ColumnOption } from '@/types/component'

  defineOptions({ name: 'AlertEvents' })

  const { t } = useI18n()
  const { projectScope } = storeToRefs(useTenantContextStore())
  const { tableSize, isZebra, isBorder } = useProductTable()
  const emptyHint = computed(() => t('alerts.events.empty'))
  const loading = ref(false)
  const rows = ref<NamedResource[]>([])
  const keyword = ref('')
  const statusFilter = ref('')
  const severityFilter = ref('')
  let pollTimer: ReturnType<typeof setInterval> | null = null

  const { columnChecks, resetColumns } = useTableColumns(() => {
    const cols: ColumnOption[] = [
      { prop: 'title', label: t('alerts.events.colTitle'), checked: true },
      { prop: 'status', label: t('alerts.events.colStatus'), checked: true },
      { prop: 'severity', label: t('alerts.events.colSeverity'), checked: true },
      { prop: 'message', label: t('alerts.events.colMessage'), checked: true },
      { prop: 'openedAt', label: t('alerts.events.colOpenedAt'), checked: true },
      { prop: '__actions', label: t('alerts.events.colActions'), width: 140, fixed: 'right', checked: true, disabled: true }
    ]
    return cols
  })
  watch(() => t('alerts.events.colActions'), () => resetColumns())
  function evtColVisible(prop: string) {
    return columnChecks.value.some((c) => c.prop === prop && (c.checked ?? c.visible ?? true))
  }

  const filteredRows = computed(() => {
    const q = keyword.value.trim().toLowerCase()
    const status = statusFilter.value.toUpperCase()
    const severity = severityFilter.value.toUpperCase()
    return rows.value.filter((row) => {
      if (status && String(row.status || '').toUpperCase() !== status) return false
      const sev = String(row.fields?.severity || '').toUpperCase()
      if (severity && sev !== severity) return false
      if (!q) return true
      const rawTitle = String(row.name || '')
      const rawMessage = String(row.fields?.message || '')
      const title = localizeAlertTitle(rawTitle, t).toLowerCase()
      const message = localizeAlertMessage(rawMessage, t).toLowerCase()
      const code = String(row.code || '').toLowerCase()
      const resource = String(row.fields?.resourceId || row.fields?.resourceType || '').toLowerCase()
      return (
        title.includes(q) ||
        message.includes(q) ||
        rawTitle.toLowerCase().includes(q) ||
        rawMessage.toLowerCase().includes(q) ||
        code.includes(q) ||
        resource.includes(q)
      )
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

  function displayTitle(row: NamedResource) {
    return localizeAlertTitle(row.name, t)
  }

  function displayMessage(row: NamedResource) {
    return localizeAlertMessage(row.fields?.message, t)
  }

  function labelStatus(status?: string) {
    const key = String(status || '').toUpperCase()
    const map: Record<string, string> = {
      OPEN: 'alerts.events.statusOpen',
      ACKNOWLEDGED: 'alerts.events.statusAcknowledged',
      RESOLVED: 'alerts.events.statusResolved'
    }
    return map[key] ? t(map[key]) : status || '—'
  }

  function labelSeverity(severity?: string) {
    const key = String(severity || '').toUpperCase()
    const map: Record<string, string> = {
      INFO: 'alerts.events.severityInfo',
      WARNING: 'alerts.events.severityWarning',
      CRITICAL: 'alerts.events.severityCritical'
    }
    return map[key] ? t(map[key]) : severity || '—'
  }

  function statusTagType(status?: string) {
    const key = String(status || '').toUpperCase()
    if (key === 'OPEN') return 'danger'
    if (key === 'ACKNOWLEDGED') return 'warning'
    if (key === 'RESOLVED') return 'success'
    return 'info'
  }

  function severityTagType(severity?: string) {
    const key = String(severity || '').toUpperCase()
    if (key === 'CRITICAL') return 'danger'
    if (key === 'WARNING') return 'warning'
    return 'info'
  }

  async function load(silent = false) {
    if (!projectScope.value) {
      rows.value = []
      return
    }
    if (!silent) loading.value = true
    try {
      const page = await fetchAlerts(projectScope.value, { current: 1, size: 200 })
      rows.value = Array.isArray(page?.records) ? page.records : []
    } catch {
      if (!silent) {
        rows.value = []
        ElMessage.error(t('alerts.events.loadFailed'))
      }
    } finally {
      if (!silent) loading.value = false
    }
  }

  function resetFilters() {
    keyword.value = ''
    statusFilter.value = ''
    severityFilter.value = ''
  }

  function setupPolling() {
    if (pollTimer) clearInterval(pollTimer)
    pollTimer = setInterval(() => {
      if (document.visibilityState === 'hidden') return
      void load(true)
    }, 5000)
  }

  async function ack(row: NamedResource) {
    if (!projectScope.value) return
    await acknowledgeAlert(projectScope.value, row.id)
    ElMessage.success(t('alerts.events.ackSuccess'))
    await load(true)
  }

  async function resolve(row: NamedResource) {
    if (!projectScope.value) return
    await resolveAlert(projectScope.value, row.id)
    ElMessage.success(t('alerts.events.resolveSuccess'))
    await load(true)
  }

  function canAck(row: NamedResource) {
    return String(row.status || '').toUpperCase() === 'OPEN'
  }

  function canResolve(row: NamedResource) {
    const s = String(row.status || '').toUpperCase()
    return s === 'OPEN' || s === 'ACKNOWLEDGED'
  }

  watch(
    projectScope,
    () => {
      void load(false)
      setupPolling()
    },
    { immediate: true }
  )

  onUnmounted(() => {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  })
</script>

<template>
  <div class="page-card art-card alert-events" v-loading="loading">
    <div class="head">
      <div>
        <h2>{{ t('alerts.events.title') }}</h2>
        <p>
          {{ t('alerts.events.subtitle', { count: rows.length }) }}
          <span v-if="keyword || statusFilter || severityFilter" class="filtered">
            · {{ t('alerts.events.filteredCount', { shown: filteredRows.length, total: rows.length }) }}
          </span>
        </p>
      </div>
    </div>

    <div class="filters art-table-card">
      <ProductTableHeader v-model:columns="columnChecks" :loading="loading" full-class="page-card" @refresh="() => load(false)">
        <template #left>
          <div class="filters-left">
            <ElInput
              v-model="keyword"
              clearable
              :placeholder="t('alerts.events.filterKeyword')"
            />
            <ElSelect
              v-model="statusFilter"
              clearable
              :placeholder="t('alerts.events.filterStatus')"
            >
              <ElOption :label="t('alerts.events.filterAll')" value="" />
              <ElOption :label="t('alerts.events.statusOpen')" value="OPEN" />
              <ElOption :label="t('alerts.events.statusAcknowledged')" value="ACKNOWLEDGED" />
              <ElOption :label="t('alerts.events.statusResolved')" value="RESOLVED" />
            </ElSelect>
            <ElSelect
              v-model="severityFilter"
              clearable
              :placeholder="t('alerts.events.filterSeverity')"
            >
              <ElOption :label="t('alerts.events.filterAll')" value="" />
              <ElOption :label="t('alerts.events.severityInfo')" value="INFO" />
              <ElOption :label="t('alerts.events.severityWarning')" value="WARNING" />
              <ElOption :label="t('alerts.events.severityCritical')" value="CRITICAL" />
            </ElSelect>
            <ElButton @click="resetFilters">{{ t('alerts.events.filterReset') }}</ElButton>
          </div>
        </template>
      </ProductTableHeader>
    </div>

    <ElTable :data="pagedRows" :size="tableSize" :stripe="isZebra" :border="isBorder" :empty-text="emptyHint">
      <ElTableColumn v-if="evtColVisible('title')" :label="t('alerts.events.colTitle')" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ displayTitle(row) }}</template>
      </ElTableColumn>
      <ElTableColumn v-if="evtColVisible('status')" :label="t('alerts.events.colStatus')" width="120">
        <template #default="{ row }">
          <ElTag :type="statusTagType(row.status)" size="small">{{ labelStatus(row.status) }}</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn v-if="evtColVisible('severity')" :label="t('alerts.events.colSeverity')" width="110">
        <template #default="{ row }">
          <ElTag :type="severityTagType(row.fields?.severity as string)" size="small" effect="plain">
            {{ labelSeverity(row.fields?.severity as string) }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn v-if="evtColVisible('message')" :label="t('alerts.events.colMessage')" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">{{ displayMessage(row) }}</template>
      </ElTableColumn>
      <ElTableColumn v-if="evtColVisible('openedAt')" :label="t('alerts.events.colOpenedAt')" width="180">
        <template #default="{ row }">{{ formatDateTime(row.fields?.openedAt as string) }}</template>
      </ElTableColumn>
      <ElTableColumn v-if="evtColVisible('__actions')" :label="t('alerts.events.colActions')" width="140" fixed="right">
        <template #default="{ row }">
          <ElTooltip v-if="canAck(row)" :content="t('alerts.events.ackHint')" placement="top">
            <ElButton link type="primary" @click="ack(row)">{{ t('alerts.events.ack') }}</ElButton>
          </ElTooltip>
          <ElTooltip v-if="canResolve(row)" :content="t('alerts.events.resolveHint')" placement="top">
            <ElButton link type="success" @click="resolve(row)">{{ t('alerts.events.resolve') }}</ElButton>
          </ElTooltip>
          <span v-if="!canAck(row) && !canResolve(row)" class="muted">
            {{ t('alerts.events.noActions') }}
          </span>
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
  </div>
</template>

<style scoped>
  .alert-events {
    padding: 20px;
  }

  .head {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 12px;
  }

  .head h2 {
    margin: 0;
  }

  .head p {
    margin: 6px 0 0;
    color: var(--el-text-color-secondary);
  }

  .filtered {
    color: var(--el-color-primary);
  }

  .filters {
    margin: 16px 0 12px;
  }

  .filters-left {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    align-items: center;
  }

  .filters-left :deep(.el-input) {
    width: 220px;
  }

  .filters-left :deep(.el-select) {
    width: 140px;
  }

  .muted {
    color: var(--el-text-color-secondary);
  }
</style>
