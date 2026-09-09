/**
 * Display helpers for API timestamps (UTC Instant / ISO-8601).
 * Always uses the browser's local timezone — never force Asia/Shanghai or UTC.
 */

const DEFAULT_OPTIONS: Intl.DateTimeFormatOptions = {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit'
}

export function formatDateTime(
  value?: string | number | Date | null,
  options?: Intl.DateTimeFormatOptions
): string {
  if (value === null || value === undefined || value === '') {
    return '—'
  }
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '—'
  }
  return new Intl.DateTimeFormat(undefined, { ...DEFAULT_OPTIONS, ...options }).format(date)
}

/** Column props that usually hold Instant / ISO timestamps from the API. */
export function isInstantFieldProp(prop: string): boolean {
  return /(?:At|Time|Timestamp)$/i.test(prop)
}
