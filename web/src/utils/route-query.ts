import type { LocationQuery } from 'vue-router'

export function queryText(query: LocationQuery, key: string): string {
  const raw = query[key]
  const value = Array.isArray(raw) ? raw[0] : raw
  return String(value || '').trim()
}
