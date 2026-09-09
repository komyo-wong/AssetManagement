<script setup lang="ts">
/**
 * Shared Art Design table chrome: refresh / density / fullscreen / columns / settings.
 * Bind your ElTable with :size :stripe :border from the same useProductTable() hook.
 */
import { storeToRefs } from 'pinia'
import { useTableStore } from '@/store/modules/table'
import type { ColumnOption } from '@/types/component'

const props = withDefaults(
  defineProps<{
    columns: ColumnOption[]
    loading?: boolean
    fullClass?: string
    layout?: string
  }>(),
  {
    loading: false,
    fullClass: 'art-table-card',
    layout: 'refresh,size,fullscreen,columns,settings'
  }
)

const emit = defineEmits<{ refresh: [] }>()

const columnsModel = defineModel<ColumnOption[]>('columns', { required: true })

const tableStore = useTableStore()
const { tableSize, isZebra, isBorder } = storeToRefs(tableStore)

defineExpose({ tableSize, isZebra, isBorder })
</script>

<template>
  <ArtTableHeader
    v-model:columns="columnsModel"
    :loading="loading"
    :layout="layout"
    :full-class="fullClass"
    @refresh="emit('refresh')"
  >
    <template v-if="$slots.left" #left>
      <slot name="left" />
    </template>
    <template v-if="$slots.right" #right>
      <slot name="right" />
    </template>
  </ArtTableHeader>
</template>
