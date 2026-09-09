/**
 * Localize alert event title/message stored by the backend.
 * Supports legacy Chinese templates and new English templates.
 */

type Translate = (key: string, params?: Record<string, unknown>) => string

const TITLE_RULES: Array<{
  re: RegExp
  key: string
}> = [
  { re: /^(?:资产离线|Asset offline)\s*[·•-]\s*(.+)$/i, key: 'alerts.content.title.assetOffline' },
  { re: /^(?:网关离线|Gateway offline)\s*[·•-]\s*(.+)$/i, key: 'alerts.content.title.gatewayOffline' },
  { re: /^(?:信标离线|Beacon offline)\s*[·•-]\s*(.+)$/i, key: 'alerts.content.title.beaconOffline' },
  { re: /^(?:电量过低|Low battery)\s*[·•-]\s*(.+)$/i, key: 'alerts.content.title.lowBattery' },
  { re: /^(?:信号过弱|Weak signal)\s*[·•-]\s*(.+)$/i, key: 'alerts.content.title.weakSignal' },
  { re: /^(?:盘点结果|Inventory result)\s*[·•-]\s*(.+)$/i, key: 'alerts.content.title.inventoryResult' }
]

type MessageRule = {
  re: RegExp
  key: string
  params: (m: RegExpMatchArray) => Record<string, unknown>
}

const MESSAGE_RULES: MessageRule[] = [
  {
    re: /^(?:绑定信标超过|Bound beacon not scanned for over)\s+(\d+)\s*(?:秒未见扫描|s)[：:]\s*(.+)$/i,
    key: 'alerts.content.message.assetOffline',
    params: (m) => ({ seconds: m[1], detail: m[2] })
  },
  {
    re: /^(?:超过|No heartbeat\/report for over)\s+(\d+)\s*(?:秒无心跳\/上报[：:]MAC|s[：:]\s*MAC)\s*(.+)$/i,
    key: 'alerts.content.message.gatewayOffline',
    params: (m) => ({ seconds: m[1], mac: m[2] })
  },
  {
    re: /^(?:超过|Not scanned for over)\s+(\d+)\s*(?:秒未扫描[：:]MAC|s[：:]\s*MAC)\s*(.+)$/i,
    key: 'alerts.content.message.beaconOffline',
    params: (m) => ({ seconds: m[1], mac: m[2] })
  },
  {
    re: /^(?:电量约|Battery about)\s+(\d+)%\s*(?:（阈值\s*≤|\(threshold\s*≤)\s*(\d+)%[）)]\s*[：:]?\s*MAC\s*(.+)$/i,
    key: 'alerts.content.message.lowBattery',
    params: (m) => ({ percent: m[1], threshold: m[2], mac: m[3] })
  },
  {
    re: /^RSSI\s+(-?\d+)\s*dBm\s*(?:低于阈值|below threshold)\s+(-?\d+)\s*dBm\s*[：:]?\s*MAC\s*(.+)$/i,
    key: 'alerts.content.message.weakSignal',
    params: (m) => ({ rssi: m[1], threshold: m[2], mac: m[3] })
  }
]

const INVENTORY_RE =
  /^(?:盘点完成|Inventory complete)[：:]\s*(\d+)\s*\/\s*(\d+)\s*(?:（覆盖率|\(coverage)\s*(\d+)%[）)]([\s\S]*?)(?:规则阈值[：:]覆盖率\s*≥|Rule threshold[：:]\s*coverage\s*≥)\s*(\d+)%\s*$/i

const INVENTORY_MISSING_RE =
  /(?:缺失示例|缺失|Missing sample|Missing)[：:]\s*(.+?)(?:\s*(?:等|etc\.?))?(?:[。.]|$)/i

export function localizeAlertTitle(raw: unknown, t: Translate): string {
  const title = String(raw ?? '').trim()
  if (!title) return '—'
  for (const rule of TITLE_RULES) {
    const m = title.match(rule.re)
    if (m) return t(rule.key, { name: m[1].trim() })
  }
  return title
}

export function localizeAlertMessage(raw: unknown, t: Translate): string {
  const message = String(raw ?? '').trim()
  if (!message) return '—'

  for (const rule of MESSAGE_RULES) {
    const m = message.match(rule.re)
    if (m) return t(rule.key, rule.params(m))
  }

  const inv = message.match(INVENTORY_RE)
  if (inv) {
    const found = inv[1]
    const expected = inv[2]
    const coverage = inv[3]
    const mid = inv[4] || ''
    const minCoverage = inv[5]
    const missingMatch = mid.match(/(?:未点到|missing)\s*(\d+)/i)
    const allFound = /全部点到|all found/i.test(mid)
    const missingCount = missingMatch ? missingMatch[1] : '0'
    const hintMatch = mid.match(INVENTORY_MISSING_RE)
    const missingList = hintMatch ? hintMatch[1].trim() : ''

    if (allFound || missingCount === '0') {
      return t('alerts.content.message.inventoryAllFound', {
        found,
        expected,
        coverage,
        minCoverage
      })
    }
    if (missingList) {
      return t('alerts.content.message.inventoryMissing', {
        found,
        expected,
        coverage,
        missing: missingCount,
        missingList,
        minCoverage
      })
    }
    return t('alerts.content.message.inventoryPartial', {
      found,
      expected,
      coverage,
      missing: missingCount,
      minCoverage
    })
  }

  return message
}
