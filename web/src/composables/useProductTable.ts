import { storeToRefs } from 'pinia'
import { useTableStore } from '@/store/modules/table'

/** Bind ElTable to Art Design table store (density / zebra / border). */
export function useProductTable() {
  const tableStore = useTableStore()
  const { tableSize, isZebra, isBorder } = storeToRefs(tableStore)
  return { tableStore, tableSize, isZebra, isBorder }
}
