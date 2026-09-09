import { computed, ref, unref, watch, type ComputedRef, type Ref } from 'vue'

export const DEFAULT_PAGE_SIZE = 20
export const PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const

type SourceList<T> = Ref<T[]> | ComputedRef<T[]> | (() => T[])

/**
 * Client-side table pagination over an already-loaded (often filtered) list.
 */
export function useClientPagination<T>(
  source: SourceList<T>,
  options?: {
    defaultSize?: number
    /** When this value changes (e.g. search keyword), jump back to page 1 */
    resetOn?: Ref<unknown> | ComputedRef<unknown>
  }
) {
  const current = ref(1)
  const size = ref(options?.defaultSize ?? DEFAULT_PAGE_SIZE)

  const list = computed(() => {
    if (typeof source === 'function') return source()
    return unref(source) || []
  })

  const total = computed(() => list.value.length)

  const pagedRows = computed(() => {
    const start = (current.value - 1) * size.value
    return list.value.slice(start, start + size.value)
  })

  watch(total, (next) => {
    const maxPage = Math.max(1, Math.ceil(next / size.value) || 1)
    if (current.value > maxPage) current.value = maxPage
  })

  if (options?.resetOn) {
    watch(options.resetOn, () => {
      current.value = 1
    })
  }

  function onPageChange(page: number) {
    current.value = page
  }

  function onSizeChange(next: number) {
    size.value = next
    current.value = 1
  }

  function resetPage() {
    current.value = 1
  }

  return {
    current,
    size,
    total,
    pagedRows,
    pageSizes: [...PAGE_SIZE_OPTIONS],
    onPageChange,
    onSizeChange,
    resetPage
  }
}
