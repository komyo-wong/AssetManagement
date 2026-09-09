<script setup lang="ts">
  import { computed, onUnmounted, ref, useSlots, watch } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { storeToRefs } from 'pinia'
  import { useTenantContextStore } from '@/store/modules/tenant-context'
  import { useTableStore } from '@/store/modules/table'
  import { usePersistedColumnWidths } from '@/hooks/core/usePersistedColumnWidths'
  import { useTableColumns } from '@/hooks/core/useTableColumns'
  import type { ColumnOption } from '@/types/component'
  import type { NamedResource, ResourceUpsert } from '@/api/asset-platform'
  import { formatDateTime, isInstantFieldProp } from '@/utils/datetime'
  import PresencePill from '@/components/business/PresencePill.vue'
  import TablePager from '@/components/business/TablePager.vue'
  import { useClientPagination } from '@/composables/useClientPagination'

  const props = withDefaults(
    defineProps<{
      title: string
      subtitle?: string
      /** 列宽本地记忆 key；默认用 title */
      tableKey?: string
      columns?: Array<{ prop: string; label: string; width?: number; sortable?: boolean | 'custom' }>
      list: (scope: Api.AssetPlatform.ProjectScope) => Promise<NamedResource[]>
      create?: (scope: Api.AssetPlatform.ProjectScope, input: ResourceUpsert) => Promise<NamedResource>
      update?: (scope: Api.AssetPlatform.ProjectScope, id: string, input: ResourceUpsert) => Promise<NamedResource>
      remove?: (scope: Api.AssetPlatform.ProjectScope, id: string) => Promise<unknown>
      extraFields?: Array<{
        key: string
        label: string
        placeholder?: string
        type?: 'text' | 'select'
        clearable?: boolean
        defaultValue?: string | number | null
        options?: Array<{ label: string; value: string }>
      }>
      createLabel?: string
      pollIntervalMs?: number
      actionWidth?: number
      searchPlaceholder?: string
      searchKeys?: string[]
      enableSelection?: boolean
      /** 隐藏「编码」表单项（编码由 MAC 等字段自动生成） */
      hideCode?: boolean
      /** 隐藏「描述」表单项 */
      hideDescription?: boolean
      /** 隐藏「名称」表单项（如信标仅需 MAC） */
      hideName?: boolean
      /** 行操作为「详情」只读抽屉（数据只读展示，不可保存） */
      enableDetail?: boolean
      /** 额外行过滤（与搜索关键词叠加） */
      rowFilter?: (row: NamedResource) => boolean
      /** 过滤条件变化时重置到第 1 页（建议传筛选状态的拼接 key） */
      filterKey?: string | number
    }>(),
    {
      pollIntervalMs: 0,
      actionWidth: 168,
      searchPlaceholder: undefined,
      searchKeys: () => ['code', 'name', 'status'],
      enableSelection: false,
      hideCode: false,
      hideDescription: false,
      hideName: false,
      enableDetail: false,
      rowFilter: undefined,
      filterKey: ''
    }
  )

  const { t } = useI18n()
  const slots = useSlots()
  const { projectScope } = storeToRefs(useTenantContextStore())
  const tableStore = useTableStore()
  const { tableSize, isZebra, isBorder } = storeToRefs(tableStore)
  const loading = ref(false)
  const rows = ref<NamedResource[]>([])
  const keyword = ref('')
  const drawer = ref(false)
  const editing = ref<NamedResource | null>(null)
  const form = ref<ResourceUpsert>({ code: '', name: '', description: '', fields: {} })
  const selectedRows = ref<NamedResource[]>([])
  let timer: ReturnType<typeof setInterval> | null = null
  let loadInFlight = false

  const resolvedSearchPlaceholder = computed(
    () => props.searchPlaceholder || t('common.searchPlaceholder')
  )

  /** 打开已有记录时是否只读（详情模式，或未提供 update） */
  const formReadOnly = computed(() => !!editing.value && (props.enableDetail || !props.update))

  const drawerTitle = computed(() => {
    if (!editing.value) return t('product.crud.drawerCreate')
    if (formReadOnly.value) return t('product.crud.drawerDetail')
    return t('product.crud.drawerEdit')
  })

  const defaultColumns = computed(() => [
    { prop: 'code', label: t('common.code'), width: 140, sortable: true },
    { prop: 'name', label: t('common.name'), sortable: true },
    { prop: 'status', label: t('common.status'), width: 120, sortable: true },
    { prop: 'updatedAt', label: t('common.updatedAt'), width: 180, sortable: true }
  ])

  const displayColumns = computed(() => props.columns || defaultColumns.value)

  const { columns: visibleArtColumns, columnChecks, resetColumns } = useTableColumns(() => {
    const cols: ColumnOption<NamedResource>[] = []
    if (props.enableSelection) {
      cols.push({ type: 'selection', width: 48, fixed: true })
    }
    for (const col of displayColumns.value) {
      cols.push({
        prop: col.prop,
        label: col.label,
        width: col.width,
        sortable: col.sortable === false ? false : true,
        checked: true
      })
    }
    if (props.update || props.enableDetail || props.remove || !!slots.actions) {
      cols.push({
        prop: '__actions',
        label: t('common.actions'),
        width: props.actionWidth,
        fixed: 'right',
        checked: true,
        disabled: true
      })
    }
    return cols
  })

  watch(
    () =>
      displayColumns.value
        .map((c) => `${c.prop}:${c.label}:${c.width ?? ''}`)
        .concat([
          String(props.enableSelection),
          String(props.actionWidth),
          t('common.actions')
        ])
        .join('|'),
    () => resetColumns()
  )

  const tableBodyColumns = computed(() =>
    visibleArtColumns.value.filter((c) => c.prop && c.prop !== '__actions' && c.type !== 'selection')
  )
  const showActionsColumn = computed(() =>
    visibleArtColumns.value.some((c) => c.prop === '__actions')
  )
  const showSelectionColumn = computed(() =>
    visibleArtColumns.value.some((c) => c.type === 'selection')
  )

  const resolvedTableKey = computed(() => props.tableKey || `resource:${props.title}`)
  const columnDefaults = computed(() => {
    const map: Record<string, number> = { __actions: props.actionWidth }
    for (const col of displayColumns.value) {
      if (col.width) map[col.prop] = col.width
    }
    return map
  })
  const { colWidth, fitWidth, onHeaderDragEnd } = usePersistedColumnWidths(
    resolvedTableKey.value,
    columnDefaults.value
  )

  const filteredRows = computed(() => {
    const q = keyword.value.trim().toLowerCase()
    const keys = props.searchKeys || ['code', 'name', 'status']
    return rows.value.filter((row) => {
      if (props.rowFilter && !props.rowFilter(row)) return false
      if (!q) return true
      for (const key of keys) {
        const top = (row as Record<string, unknown>)[key]
        if (top != null && String(top).toLowerCase().includes(q)) return true
        const field = row.fields?.[key]
        if (field != null && String(field).toLowerCase().includes(q)) return true
      }
      return false
    })
  })

  const paginationResetToken = computed(() => `${keyword.value}::${props.filterKey ?? ''}`)

  const {
    current: pageCurrent,
    size: pageSize,
    total: pageTotal,
    pagedRows,
    onPageChange,
    onSizeChange
  } = useClientPagination(filteredRows, { resetOn: paginationResetToken })

  /** 无固定 width 的列（含 name）用 min-width，撑满整行剩余宽度 */
  function isFlexColumn(col: { prop: string; width?: number }) {
    return col.prop === 'name' || col.width == null
  }

  function resolveColWidth(col: { prop: string; label: string; width?: number }) {
    if (col.prop === 'name') {
      return fitWidth(
        'name',
        rows.value.map((r) => r.name),
        { header: col.label || t('common.name'), min: 120, max: 280 }
      )
    }
    return colWidth(col.prop, col.width) ?? 120
  }

  async function load(silent = false) {
    if (!projectScope.value) {
      rows.value = []
      return
    }
    if (loadInFlight) return
    loadInFlight = true
    if (!silent) loading.value = true
    try {
      rows.value = await props.list(projectScope.value)
      if (!silent) selectedRows.value = []
    } catch {
      if (!silent) rows.value = []
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

  function isPresenceStatus(value: unknown) {
    const status = String(value || '').toUpperCase()
    return status === 'ONLINE' || status === 'OFFLINE' || status === 'UNBOUND' || status === 'ARCHIVED'
  }

  function displayFieldValue(value: unknown) {
    if (value == null || value === '') return '-'
    if (Array.isArray(value)) return value.length ? value.map(String).join(', ') : '-'
    return String(value)
  }

  function setupPolling() {
    clearTimer()
    if (!props.pollIntervalMs || props.pollIntervalMs <= 0) return
    timer = setInterval(() => {
      if (document.visibilityState === 'hidden') return
      void load(true)
    }, props.pollIntervalMs)
  }

  function openCreate() {
    editing.value = null
    form.value = { code: '', name: '', description: '', fields: {} }
    for (const field of props.extraFields || []) {
      if (field.defaultValue !== undefined) {
        form.value.fields = { ...form.value.fields, [field.key]: field.defaultValue }
      }
    }
    drawer.value = true
  }

  function openEdit(row: NamedResource) {
    editing.value = row
    form.value = {
      code: row.code,
      name: row.name,
      description: String(row.fields?.description ?? ''),
      status: row.status,
      fields: { ...(row.fields || {}) }
    }
    drawer.value = true
  }

  async function save() {
    if (!projectScope.value || !props.create) return
    try {
      if (editing.value && props.update) {
        await props.update(projectScope.value, editing.value.id, form.value)
      } else {
        await props.create(projectScope.value, form.value)
      }
      drawer.value = false
      ElMessage.success(t('common.saveSuccess'))
      await load()
    } catch {
      // http util already toasts
    }
  }

  async function doDelete(row: NamedResource) {
    if (!projectScope.value || !props.remove) return
    try {
      await ElMessageBox.confirm(
        t('product.crud.deleteConfirm', { code: row.code, name: row.name }),
        t('common.deleteConfirm'),
        {
          type: 'warning',
          confirmButtonText: t('common.delete'),
          cancelButtonText: t('common.cancel')
        }
      )
      await props.remove(projectScope.value, row.id)
      ElMessage.success(t('common.deleted'))
      await load()
    } catch {
      // cancelled or http toast
    }
  }

  function onSelectionChange(selection: NamedResource[]) {
    selectedRows.value = selection
  }

  function clearSelection() {
    selectedRows.value = []
  }

  watch(
    projectScope,
    () => {
      void load()
      setupPolling()
    },
    { immediate: true }
  )
  watch(
    () => props.pollIntervalMs,
    () => setupPolling()
  )
  onUnmounted(() => clearTimer())

  defineExpose({
    reload: () => load(),
    clearSelection,
    getSelected: () => selectedRows.value
  })
</script>

<template>
  <div class="page-card art-card">
    <div class="header">
      <div>
        <h2>{{ title }}</h2>
        <p v-if="subtitle">{{ subtitle }}</p>
      </div>
      <div class="header-actions">
        <slot
          name="toolbar"
          :reload="() => load()"
          :selected="selectedRows"
          :clear-selection="clearSelection"
        />
        <ElButton v-if="create" type="primary" :disabled="!projectScope" @click="openCreate">
          {{ createLabel || t('common.add') }}
        </ElButton>
      </div>
    </div>

      <div class="toolbar art-table-card">
        <ArtTableHeader
          v-model:columns="columnChecks"
          :loading="loading"
          layout="refresh,size,fullscreen,columns,settings"
          full-class="art-table-card"
          @refresh="() => load()"
        >
          <template #left>
            <div class="toolbar-left">
              <ElInput
                v-model="keyword"
                clearable
                :placeholder="resolvedSearchPlaceholder"
              />
              <slot name="filters" :rows="rows" :filtered-count="filteredRows.length" />
              <span class="toolbar-count">
                {{
                  filteredRows.length === rows.length
                    ? t('common.totalCount', { count: filteredRows.length })
                    : t('product.crud.filterCount', { filtered: filteredRows.length, total: rows.length })
                }}
              </span>
              <span v-if="enableSelection && selectedRows.length" class="toolbar-count">
                {{ t('common.selectedCount', { count: selectedRows.length }) }}
              </span>
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
        row-key="id"
        style="width: 100%"
        @selection-change="onSelectionChange"
        @header-dragend="onHeaderDragEnd"
      >
        <ElTableColumn v-if="showSelectionColumn" type="selection" width="48" reserve-selection />
        <ElTableColumn
          v-for="col in tableBodyColumns"
          :key="String(col.prop)"
          :prop="col.prop"
          :label="col.label"
          :width="isFlexColumn(col as any) ? undefined : resolveColWidth(col as any)"
          :min-width="isFlexColumn(col as any) ? resolveColWidth(col as any) : undefined"
          :sortable="col.sortable === false ? false : true"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <slot :name="`cell-${col.prop}`" :row="row" :col="col">
              <PresencePill
                v-if="col.prop === 'status' && isPresenceStatus(row.status || row.fields?.status)"
                :status="String(row.status || row.fields?.status || '')"
              />
              <span v-else-if="col.prop && col.prop in row">{{
                isInstantFieldProp(col.prop)
                  ? formatDateTime((row as any)[col.prop] as string | null | undefined)
                  : displayFieldValue((row as any)[col.prop])
              }}</span>
              <span v-else>{{
                col.prop && isInstantFieldProp(col.prop)
                  ? formatDateTime(row.fields?.[col.prop] as string | null | undefined)
                  : displayFieldValue(col.prop ? row.fields?.[col.prop] : null)
              }}</span>
            </slot>
          </template>
        </ElTableColumn>
        <ElTableColumn
          v-if="showActionsColumn"
          column-key="__actions"
          :label="t('common.actions')"
          :width="colWidth('__actions', actionWidth)"
          fixed="right"
        >
          <template #default="{ row }">
            <ElButton v-if="enableDetail" link type="primary" @click="openEdit(row)">{{ t('common.detail') }}</ElButton>
            <ElButton v-else-if="update" link type="primary" @click="openEdit(row)">{{ t('common.edit') }}</ElButton>
            <slot name="actions" :row="row" :reload="() => load()" />
            <ElButton v-if="remove" link type="danger" @click="doDelete(row)">{{ t('common.delete') }}</ElButton>
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


    <ElDrawer v-model="drawer" :title="drawerTitle" size="420px">
      <ElForm label-position="top">
        <ElFormItem v-if="!hideCode" :label="t('common.code')">
          <ElInput v-model="form.code" :disabled="!!editing || formReadOnly" />
        </ElFormItem>
        <ElFormItem v-if="!hideName" :label="t('common.name')">
          <ElInput v-model="form.name" :disabled="formReadOnly" />
        </ElFormItem>
        <ElFormItem v-if="!hideDescription" :label="t('common.description')">
          <ElInput v-model="form.description" type="textarea" :disabled="formReadOnly" />
        </ElFormItem>
        <ElFormItem v-for="field in extraFields || []" :key="field.key" :label="field.label">
          <ElSelect
            v-if="field.type === 'select'"
            :model-value="(form.fields?.[field.key] as string | null | undefined) ?? ''"
            :placeholder="field.placeholder || t('product.crud.selectField', { label: field.label })"
            :clearable="field.clearable !== false"
            :disabled="formReadOnly"
            style="width: 100%"
            @update:model-value="(v: string | null) => (form.fields = { ...form.fields, [field.key]: v || null })"
          >
            <ElOption
              v-for="opt in field.options || []"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
          <ElInput
            v-else
            :model-value="String(form.fields?.[field.key] ?? '')"
            :placeholder="field.placeholder"
            :disabled="formReadOnly"
            @update:model-value="(v: string) => (form.fields = { ...form.fields, [field.key]: v })"
          />
        </ElFormItem>
        <slot name="form-extra" :form="form" :editing="editing" :read-only="formReadOnly" />
        <ElButton v-if="formReadOnly" @click="drawer = false">{{ t('common.close') }}</ElButton>
        <ElButton v-else type="primary" @click="save">{{ t('common.save') }}</ElButton>
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
  .header-actions {
    display: flex;
    gap: 8px;
    align-items: center;
  }
  .toolbar {
    margin-bottom: 12px;
  }
  .toolbar-left {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
    width: 100%;
  }
  .toolbar-left :deep(.el-input) {
    width: 200px;
  }
  .toolbar-left :deep(.el-select) {
    width: 120px;
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
</style>
