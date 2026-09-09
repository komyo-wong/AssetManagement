<script setup lang="ts">
  import { computed, onMounted, reactive, ref, watch } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { storeToRefs } from 'pinia'
  import { ElMessage } from 'element-plus'
  import { useTenantContextStore } from '@/store/modules/tenant-context'
  import { alertRulesApi, type NamedResource } from '@/api/asset-platform'
  import { formatDateTime } from '@/utils/datetime'
  import TablePager from '@/components/business/TablePager.vue'
  import ProductTableHeader from '@/components/business/ProductTableHeader.vue'
  import { useClientPagination } from '@/composables/useClientPagination'
  import { useProductTable } from '@/composables/useProductTable'
  import { useTableColumns } from '@/hooks/core/useTableColumns'
  import type { ColumnOption } from '@/types/component'

  defineOptions({ name: 'AlertRules' })

  type RuleType =
    | 'BEACON_OFFLINE'
    | 'GATEWAY_OFFLINE'
    | 'ASSET_OFFLINE'
    | 'BATTERY_LOW'
    | 'RSSI_THRESHOLD'
    | 'INVENTORY_RESULT'

  const { t } = useI18n()

  const CONDITIONS = computed(() =>
    (
      [
        { value: 'BEACON_OFFLINE' as RuleType, defaultThreshold: 300, codePrefix: 'offline', unitKey: 'unitSec' },
        { value: 'GATEWAY_OFFLINE' as RuleType, defaultThreshold: 90, codePrefix: 'gw-offline', unitKey: 'unitSec' },
        { value: 'ASSET_OFFLINE' as RuleType, defaultThreshold: 300, codePrefix: 'asset-offline', unitKey: 'unitSec' },
        { value: 'BATTERY_LOW' as RuleType, defaultThreshold: 20, codePrefix: 'battery', unitKey: 'unitPct' },
        { value: 'RSSI_THRESHOLD' as RuleType, defaultThreshold: -85, codePrefix: 'rssi', unitKey: 'unitDbm' },
        { value: 'INVENTORY_RESULT' as RuleType, defaultThreshold: 100, codePrefix: 'inventory', unitKey: 'unitPct' }
      ] as const
    ).map((c) => ({
      ...c,
      label: t(`product.alerts.rules.${c.value}.label`),
      unit: t(`product.alerts.rules.${c.unitKey}`),
      hint: t(`product.alerts.rules.${c.value}.hint`)
    }))
  )

  const { projectScope } = storeToRefs(useTenantContextStore())
  const { tableSize, isZebra, isBorder } = useProductTable()
  const loading = ref(false)
  const rows = ref<NamedResource[]>([])
  const drawer = ref(false)
  const editing = ref<NamedResource | null>(null)

  const { columnChecks, resetColumns } = useTableColumns(() => {
    const cols: ColumnOption[] = [
      { prop: 'name', label: t('product.alerts.rules.colName'), checked: true },
      { prop: 'condition', label: t('product.alerts.rules.colCondition'), checked: true },
      { prop: 'threshold', label: t('product.alerts.rules.colThreshold'), checked: true },
      { prop: 'enabled', label: t('common.enable'), checked: true },
      { prop: 'updatedAt', label: t('common.updatedAt'), checked: true },
      { prop: '__actions', label: t('common.actions'), width: 80, fixed: 'right', checked: true, disabled: true }
    ]
    return cols
  })
  watch(() => t('common.actions'), () => resetColumns())
  function ruleColVisible(prop: string) {
    return columnChecks.value.some((c) => c.prop === prop && (c.checked ?? c.visible ?? true))
  }

  const {
    current: pageCurrent,
    size: pageSize,
    total: pageTotal,
    pagedRows,
    onPageChange,
    onSizeChange
  } = useClientPagination(rows)

  const form = reactive({
    name: '',
    ruleType: 'BEACON_OFFLINE' as RuleType,
    thresholdValue: 300,
    enabled: true
  })

  const conditionMeta = computed(() => CONDITIONS.value.find((c) => c.value === form.ruleType) || CONDITIONS.value[0])

  watch(
    () => form.ruleType,
    (type) => {
      const meta = CONDITIONS.value.find((c) => c.value === type)
      if (meta && !editing.value) {
        form.thresholdValue = meta.defaultThreshold
      }
    }
  )

  function conditionLabel(type: unknown) {
    return CONDITIONS.value.find((c) => c.value === type)?.label || String(type || '-')
  }

  function thresholdText(row: NamedResource) {
    const type = String(row.fields?.ruleType || '')
    const value = row.fields?.thresholdValue
    if (value == null || value === '') return '-'
    const key = `product.alerts.rules.summary.${type}`
    if (['BEACON_OFFLINE', 'GATEWAY_OFFLINE', 'ASSET_OFFLINE', 'BATTERY_LOW', 'RSSI_THRESHOLD', 'INVENTORY_RESULT'].includes(type)) {
      return t(key, { value })
    }
    return String(value)
  }

  function autoCode() {
    const prefix = conditionMeta.value.codePrefix || 'rule'
    const stamp = Date.now().toString(36).slice(-6)
    return `${prefix}-${Math.abs(form.thresholdValue)}-${stamp}`
  }

  async function load() {
    if (!projectScope.value) {
      rows.value = []
      return
    }
    loading.value = true
    try {
      rows.value = await alertRulesApi.list(projectScope.value)
    } catch {
      rows.value = []
    } finally {
      loading.value = false
    }
  }

  function openCreate() {
    editing.value = null
    form.name = ''
    form.ruleType = 'BEACON_OFFLINE'
    form.thresholdValue = 300
    form.enabled = true
    drawer.value = true
  }

  function openEdit(row: NamedResource) {
    editing.value = row
    form.name = row.name
    form.ruleType = (String(row.fields?.ruleType || 'BEACON_OFFLINE') as RuleType)
    form.thresholdValue = Number(row.fields?.thresholdValue ?? 300)
    form.enabled = row.fields?.enabled !== false
    drawer.value = true
  }

  async function save() {
    if (!projectScope.value) return
    if (!form.name.trim()) {
      ElMessage.warning(t('product.alerts.rules.needName'))
      return
    }
    const fields = {
      ruleType: form.ruleType,
      thresholdValue: form.thresholdValue,
      enabled: form.enabled
    }
    if (editing.value) {
      await alertRulesApi.update(projectScope.value, editing.value.id, {
        code: editing.value.code,
        name: form.name.trim(),
        description: '',
        fields
      })
      ElMessage.success(t('product.alerts.rules.updated'))
    } else {
      await alertRulesApi.create(projectScope.value, {
        code: autoCode(),
        name: form.name.trim(),
        description: '',
        fields
      })
      ElMessage.success(t('product.alerts.rules.created'))
    }
    drawer.value = false
    await load()
  }

  async function toggleEnabled(row: NamedResource, enabled: string | number | boolean) {
    if (!projectScope.value) return
    await alertRulesApi.update(projectScope.value, row.id, {
      code: row.code,
      name: row.name,
      description: String((row as { description?: string }).description || ''),
      fields: {
        ruleType: row.fields?.ruleType,
        thresholdValue: row.fields?.thresholdValue,
        enabled: !!enabled
      }
    })
    await load()
  }

  onMounted(load)
  watch(projectScope, () => void load())
</script>

<template>
  <div class="page-card art-card" style="padding: 20px">
    <div style="display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; margin-bottom: 16px">
      <div>
        <h2 style="margin: 0">{{ t('product.alerts.rules.title') }}</h2>
        <p style="color: var(--el-text-color-secondary); margin: 6px 0 0">
          {{ t('product.alerts.rules.subtitle') }}
        </p>
      </div>
      <ElButton type="primary" @click="openCreate">{{ t('product.alerts.rules.create') }}</ElButton>
    </div>

    <div class="toolbar art-table-card">
      <ProductTableHeader v-model:columns="columnChecks" :loading="loading" full-class="page-card" @refresh="() => load()" />
    </div>

    <ElTable v-loading="loading" :data="pagedRows" :size="tableSize" :stripe="isZebra" :border="isBorder">
      <ElTableColumn v-if="ruleColVisible('name')" prop="name" :label="t('product.alerts.rules.colName')" min-width="160" show-overflow-tooltip />
      <ElTableColumn v-if="ruleColVisible('condition')" :label="t('product.alerts.rules.colCondition')" width="160">
        <template #default="{ row }">{{ conditionLabel(row.fields?.ruleType) }}</template>
      </ElTableColumn>
      <ElTableColumn v-if="ruleColVisible('threshold')" :label="t('product.alerts.rules.colThreshold')" min-width="180">
        <template #default="{ row }">{{ thresholdText(row) }}</template>
      </ElTableColumn>
      <ElTableColumn v-if="ruleColVisible('enabled')" :label="t('common.enable')" width="90">
        <template #default="{ row }">
          <ElSwitch
            :model-value="row.fields?.enabled !== false"
            @change="(v) => toggleEnabled(row, v)"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn v-if="ruleColVisible('updatedAt')" :label="t('common.updatedAt')" width="180">
        <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
      </ElTableColumn>
      <ElTableColumn v-if="ruleColVisible('__actions')" :label="t('common.actions')" width="80" fixed="right">
        <template #default="{ row }">
          <ElButton link type="primary" @click="openEdit(row)">{{ t('common.edit') }}</ElButton>
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
      v-model="drawer"
      :title="editing ? t('product.alerts.rules.drawerEdit') : t('product.alerts.rules.drawerCreate')"
      size="420px"
    >
      <ElForm label-position="top">
        <ElFormItem :label="t('product.alerts.rules.colName')" required>
          <ElInput v-model="form.name" :placeholder="t('product.alerts.rules.namePh')" />
        </ElFormItem>
        <ElFormItem :label="t('product.alerts.rules.colCondition')" required>
          <ElSelect v-model="form.ruleType" style="width: 100%">
            <ElOption v-for="c in CONDITIONS" :key="c.value" :label="c.label" :value="c.value" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem
          :label="
            form.ruleType === 'INVENTORY_RESULT'
              ? t('product.alerts.rules.minCoverage')
              : t('product.alerts.rules.thresholdLabel', { unit: conditionMeta.unit })
          "
          required
        >
          <ElInputNumber
            v-model="form.thresholdValue"
            style="width: 100%"
            :min="form.ruleType === 'INVENTORY_RESULT' ? 0 : undefined"
            :max="form.ruleType === 'INVENTORY_RESULT' || form.ruleType === 'BATTERY_LOW' ? 100 : undefined"
          />
          <div style="margin-top: 6px; color: var(--el-text-color-secondary); font-size: 12px">
            {{ conditionMeta.hint }}
          </div>
        </ElFormItem>
        <ElFormItem :label="t('common.enable')">
          <ElSwitch v-model="form.enabled" />
        </ElFormItem>
        <ElButton type="primary" @click="save">{{ t('common.save') }}</ElButton>
      </ElForm>
    </ElDrawer>
  </div>
</template>
