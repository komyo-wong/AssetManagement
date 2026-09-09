<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useTenantContextStore } from '@/store/modules/tenant-context'
import { fetchAnalyticsSummary } from '@/api/asset-platform'
import { formatDateTime } from '@/utils/datetime'
import TablePager from '@/components/business/TablePager.vue'
import { useClientPagination } from '@/composables/useClientPagination'

const { t } = useI18n()
const { projectScope } = storeToRefs(useTenantContextStore())
const loading = ref(false)
const summary = ref<Record<string, unknown>>({})

const cards = computed(() => [
  { label: t('product.analytics.assetTotal'), value: summary.value.assetTotal ?? '-' },
  { label: t('product.analytics.beaconTotal'), value: summary.value.beaconTotal ?? '-' },
  { label: t('product.analytics.gatewayOnline'), value: summary.value.gatewayOnline ?? '-' },
  { label: t('product.analytics.openAlertsAlt'), value: summary.value.openAlerts ?? '-' },
  { label: t('product.analytics.scansHourAlt'), value: summary.value.scansLastHour ?? '-' }
])

const dailyScans = computed(() => {
  const rows = summary.value.dailyScans
  return Array.isArray(rows) ? (rows as Array<{ date?: string; scanCount?: number }>) : []
})

const {
  current: pageCurrent,
  size: pageSize,
  total: pageTotal,
  pagedRows,
  onPageChange,
  onSizeChange
} = useClientPagination(dailyScans)

const maxDaily = computed(() =>
  Math.max(1, ...dailyScans.value.map((row) => Number(row.scanCount || 0)))
)

watch(
  projectScope,
  async (scope) => {
    if (!scope) {
      summary.value = {}
      return
    }
    loading.value = true
    try {
      summary.value = await fetchAnalyticsSummary(scope)
    } finally {
      loading.value = false
    }
  },
  { immediate: true }
)
</script>
<template>
  <div class="page-card art-card" style="padding: 20px" v-loading="loading">
    <h2 style="margin: 0">{{ t('product.analytics.dailyTitle') }}</h2>
    <p style="margin: 4px 0 16px; color: var(--el-text-color-secondary)">
      {{ t('product.analytics.dailySubtitle') }}
    </p>

      <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 12px">
        <div
          v-for="card in cards"
          :key="card.label"
          style="padding: 14px; border: 1px solid var(--el-border-color); border-radius: 8px"
        >
          <div style="color: var(--el-text-color-secondary); font-size: 13px">{{ card.label }}</div>
          <div style="margin-top: 8px; font-size: 24px; font-weight: 600">{{ card.value }}</div>
        </div>
      </div>

      <h3 style="margin: 28px 0 12px">{{ t('product.analytics.dailyScans') }}</h3>
      <ElTable :data="pagedRows" stripe>
        <ElTableColumn prop="date" :label="t('product.analytics.date')" width="140" />
        <ElTableColumn prop="scanCount" :label="t('product.analytics.scanCount')" width="120" />
        <ElTableColumn :label="t('product.analytics.share')">
          <template #default="{ row }">
            <div style="height: 8px; background: var(--el-fill-color); border-radius: 999px; overflow: hidden">
              <div
                style="height: 100%; background: var(--el-color-primary)"
                :style="{ width: `${(Number(row.scanCount || 0) / maxDaily) * 100}%` }"
              />
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
      <p style="margin-top: 12px; color: var(--el-text-color-secondary); font-size: 12px">
        {{ t('product.analytics.generatedAtLabel', { time: formatDateTime(summary.generatedAt as string | null | undefined) }) }}
      </p>
    
  </div>
</template>
