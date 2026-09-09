import { ref } from 'vue'

type ColumnLike = {
  property?: string
  prop?: string
  columnKey?: string
  id?: string
  label?: string
}

export type FitTextWidthOptions = {
  /** 表头文字，参与取最长 */
  header?: string
  min?: number
  max?: number
  /** 左右内边距 + 排序图标等 */
  padding?: number
  font?: string
}

function storageKey(tableKey: string) {
  return `am:table-col-widths:v2:${tableKey}`
}

function readStored(tableKey: string): Record<string, number> {
  try {
    const raw = localStorage.getItem(storageKey(tableKey))
    if (!raw) return {}
    const parsed = JSON.parse(raw) as Record<string, unknown>
    const out: Record<string, number> = {}
    for (const [k, v] of Object.entries(parsed || {})) {
      const n = Number(v)
      if (Number.isFinite(n) && n >= 48) out[k] = Math.round(n)
    }
    return out
  } catch {
    return {}
  }
}

function columnKeyOf(column: ColumnLike) {
  return String(column.property || column.prop || column.columnKey || column.id || column.label || '').trim()
}

let measureCtx: CanvasRenderingContext2D | null | undefined

function getMeasureCtx() {
  if (measureCtx !== undefined) return measureCtx
  if (typeof document === 'undefined') {
    measureCtx = null
    return null
  }
  const canvas = document.createElement('canvas')
  measureCtx = canvas.getContext('2d')
  return measureCtx
}

function roughTextWidth(text: string) {
  let w = 0
  for (const ch of text) {
    w += ch.charCodeAt(0) > 255 ? 13 : 7.5
  }
  return w
}

/**
 * 按当前数据最长文本估算列宽（含表头）
 */
export function estimateTextColumnWidth(
  values: Iterable<unknown>,
  opts: FitTextWidthOptions = {}
): number {
  const min = opts.min ?? 88
  const max = opts.max ?? 200
  const padding = opts.padding ?? 40
  const font = opts.font || '14px system-ui, -apple-system, "Segoe UI", sans-serif'
  const samples: string[] = []
  if (opts.header) samples.push(opts.header)
  for (const v of values) {
    if (v == null || v === '') continue
    samples.push(String(v))
  }
  if (!samples.length) return min

  const ctx = getMeasureCtx()
  let content = 0
  if (ctx) {
    ctx.font = font
    for (const s of samples) content = Math.max(content, ctx.measureText(s).width)
  } else {
    for (const s of samples) content = Math.max(content, roughTextWidth(s))
  }
  return Math.min(max, Math.max(min, Math.ceil(content + padding)))
}

/**
 * 表格列宽拖动记忆（仅保存用户拖过的列；未拖动的可用动态估算）
 */
export function usePersistedColumnWidths(
  tableKey: string,
  defaults: Record<string, number> = {}
) {
  const userWidths = ref<Record<string, number>>(readStored(tableKey))

  function colWidth(key: string, fallback?: number) {
    return userWidths.value[key] ?? fallback ?? defaults[key]
  }

  function fitWidth(key: string, values: Iterable<unknown>, opts?: FitTextWidthOptions) {
    return colWidth(key, estimateTextColumnWidth(values, opts))
  }

  function onHeaderDragEnd(newWidth: number, _oldWidth: number, column: ColumnLike) {
    const key = columnKeyOf(column)
    if (!key || !Number.isFinite(newWidth) || newWidth < 48) return
    userWidths.value = { ...userWidths.value, [key]: Math.round(newWidth) }
    try {
      localStorage.setItem(storageKey(tableKey), JSON.stringify(userWidths.value))
    } catch {
      // ignore quota / private mode
    }
  }

  return { widths: userWidths, colWidth, fitWidth, onHeaderDragEnd }
}
