/** Format live-location / inventory labels that were stored in Chinese. */

type Translate = (key: string, params?: Record<string, unknown>) => string

export function formatApproxMeters(meters: unknown, t: Translate): string {
  const n = Number(meters)
  if (!Number.isFinite(n)) return ''
  const shown = Number.isInteger(n) ? String(n) : n.toFixed(1)
  return t('product.assets.status.approxMeters', { meters: shown })
}

export function formatLiveLocation(
  fields: Record<string, unknown> | undefined | null,
  t: Translate
): string {
  if (!fields) return ''
  const gateway = String(fields.lastGatewayName || '').trim()
  const place = String(fields.lastZoneName || fields.lastMapName || '').trim()
  const distance = formatApproxMeters(fields.estimatedDistanceMeters, t)
  if (gateway) {
    const parts = [t('product.assets.status.nearPrefix'), gateway]
    if (distance) parts.push(distance)
    let text = parts.join(' · ')
    if (place) text += ` (${place})`
    return text
  }
  const fallback = String(fields.liveLocationLabel || fields.locationLabel || '').trim()
  return localizeStoredLiveLocation(fallback, t)
}

function localizeStoredLiveLocation(raw: string, t: Translate): string {
  if (!raw) return ''
  return raw
    .replace(/^靠近\s*[·•-]\s*/u, `${t('product.assets.status.nearPrefix')} · `)
    .replace(/[·•]\s*约\s+/gu, ` · ${t('product.assets.status.approxPrefix')} `)
    .replace(/约\s+/gu, `${t('product.assets.status.approxPrefix')} `)
    .replace(/（/g, '(')
    .replace(/）/g, ')')
}

export function localizeInventoryName(name: unknown, t: Translate): string {
  const raw = String(name || '').trim()
  const matched = raw.match(/^(?:盘点|Inventory)\s+(.+)$/i)
  if (matched) {
    return t('product.assets.inventory.defaultName', { time: matched[1] })
  }
  return raw
}
