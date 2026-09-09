<script setup lang="ts">
  import { PAGE_SIZE_OPTIONS } from '@/composables/useClientPagination'

  defineProps<{
    total: number
    current: number
    size: number
    pageSizes?: number[]
    small?: boolean
    compact?: boolean
  }>()

  const emit = defineEmits<{
    'update:current': [value: number]
    'update:size': [value: number]
  }>()
</script>

<template>
  <div v-if="total > 0" class="table-pager" :class="{ compact }">
    <ElPagination
      background
      :layout="compact ? 'sizes, prev, pager, next' : 'total, sizes, prev, pager, next'"
      :pager-count="compact ? 5 : 7"
      :size="small || compact ? 'small' : 'default'"
      :hide-on-single-page="false"
      :total="total"
      :current-page="current"
      :page-size="size"
      :page-sizes="pageSizes || [...PAGE_SIZE_OPTIONS]"
      @current-change="emit('update:current', $event)"
      @size-change="emit('update:size', $event)"
    />
  </div>
</template>

<style scoped>
  .table-pager {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }

  .table-pager.compact {
    margin-top: 8px;
    justify-content: stretch;
  }

  .table-pager.compact :deep(.el-pagination) {
    flex-wrap: wrap;
    width: 100%;
    justify-content: flex-end;
    row-gap: 6px;
  }

  .table-pager.compact :deep(.el-pagination__sizes) {
    margin-left: 0;
    margin-right: auto;
  }
</style>
