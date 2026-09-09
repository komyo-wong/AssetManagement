<script setup lang="ts">
  import { onMounted, ref } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { fetchPlatformGateways } from '@/api/asset-platform'
  import { formatDateTime } from '@/utils/datetime'
  import PresencePill from '@/components/business/PresencePill.vue'
  import TablePager from '@/components/business/TablePager.vue'
  import { useClientPagination } from '@/composables/useClientPagination'

  const { t } = useI18n()
  const rows = ref<Record<string, unknown>[]>([])
  const {
    current: pageCurrent,
    size: pageSize,
    total: pageTotal,
    pagedRows,
    onPageChange,
    onSizeChange
  } = useClientPagination(rows)
  const loading = ref(false)

  onMounted(async () => {
    loading.value = true
    try {
      rows.value = await fetchPlatformGateways()
    } catch {
      rows.value = []
    } finally {
      loading.value = false
    }
  })
</script>

<template>
  <div class="page-card art-card" style="padding: 20px">
    <h2>{{ t('product.platformGateways.title') }}</h2>
    <p style="color: var(--el-text-color-secondary); margin: 4px 0 16px">{{ t('product.platformGateways.subtitle') }}</p>
    <ElTable v-loading="loading" :data="pagedRows" stripe>
      <ElTableColumn prop="tenantCode" :label="t('product.platformGateways.tenant')" width="120" show-overflow-tooltip />
      <ElTableColumn prop="projectCode" :label="t('product.platformGateways.project')" width="120" show-overflow-tooltip />
      <ElTableColumn prop="code" :label="t('common.code')" width="140" show-overflow-tooltip />
      <ElTableColumn prop="name" :label="t('common.name')" show-overflow-tooltip />
      <ElTableColumn :label="t('common.status')" width="100">
        <template #default="{ row }">
          <PresencePill :status="String(row.status || '')" />
        </template>
      </ElTableColumn>
      <ElTableColumn prop="clientId" label="Client ID" width="180" show-overflow-tooltip />
      <ElTableColumn :label="t('product.platformGateways.lastSeen')" width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ formatDateTime(row.lastSeenAt as string | null | undefined) }}</template>
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
