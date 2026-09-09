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
    <h2 style="margin: 0">{{ t('product.analytics.assetsTitle') }}</h2>
    <p style="margin: 4px 0 16px; color: var(--el-text-color-secondary)">{{ t('product.analytics.assetsSubtitle') }}</p>

      <ElDescriptions :column="2" border>
        <ElDescriptionsItem :label="t('product.analytics.assetCount')">{{ summary.assetTotal }}</ElDescriptionsItem>
        <ElDescriptionsItem :label="t('product.analytics.beaconCount')">{{ summary.beaconTotal }}</ElDescriptionsItem>
        <ElDescriptionsItem :label="t('product.analytics.gatewayOnline')">{{ summary.gatewayOnline }}</ElDescriptionsItem>
        <ElDescriptionsItem :label="t('product.analytics.openAlerts')">{{ summary.openAlerts }}</ElDescriptionsItem>
        <ElDescriptionsItem :label="t('product.analytics.scansLastHour')">{{ summary.scansLastHour }}</ElDescriptionsItem>
        <ElDescriptionsItem :label="t('product.analytics.generatedAt')">{{
          formatDateTime(summary.generatedAt as string | null | undefined)
        }}</ElDescriptionsItem>
      </ElDescriptions>
      <h3 style="margin: 24px 0 12px">{{ t('product.analytics.dailyScans') }}</h3>
      <ElTable :data="pagedRows" stripe>
        <ElTableColumn prop="date" :label="t('product.analytics.date')" width="160" />
        <ElTableColumn prop="scanCount" :label="t('product.analytics.scanCount')" />
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
