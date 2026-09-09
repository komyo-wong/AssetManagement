<script setup lang="ts">
  import { computed, onUnmounted, ref, watch } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { storeToRefs } from 'pinia'
  import { useRouter } from 'vue-router'
  import { useTenantContextStore } from '@/store/modules/tenant-context'
  import {
    assetsApi,
    beaconsApi,
    fetchAlerts,
    fetchAnalyticsSummary,
    fetchInventorySessions,
    gatewaysApi,
    type NamedResource
  } from '@/api/asset-platform'
  import { formatDateTime } from '@/utils/datetime'
  import { formatApproxMeters, formatLiveLocation, localizeInventoryName } from '@/utils/locale-labels'
  import { localizeAlertMessage, localizeAlertTitle } from '@/utils/alert-i18n'
  import { asAssetStatusRow, isGatewayAsset } from '@/utils/gateway-as-asset'
  import PresencePill from '@/components/business/PresencePill.vue'

  const { t } = useI18n()

  defineOptions({ name: 'AssetOverview' })

  const router = useRouter()
  const { projectScope } = storeToRefs(useTenantContextStore())

  const loading = ref(false)
  const loadError = ref('')
  const lastRefreshedAt = ref<Date | null>(null)

  const assets = ref<NamedResource[]>([])
  const beacons = ref<NamedResource[]>([])
  const gateways = ref<NamedResource[]>([])
  const alerts = ref<NamedResource[]>([])
  const inventorySessions = ref<NamedResource[]>([])
  const analytics = ref<Record<string, unknown>>({})

  let timer: ReturnType<typeof setInterval> | null = null
  let loadInFlight = false

  const taggedAssets = computed(() => assets.value.filter((a) => a.status !== 'ARCHIVED'))
  const activeGateways = computed(() => gateways.value.filter((g) => g.status !== 'ARCHIVED'))
  const statusAssets = computed(() => [
    ...taggedAssets.value,
    ...activeGateways.value.map((g) => asAssetStatusRow(g, t('product.assets.status.gatewayType')))
  ])
  const ledgerTotal = computed(() => taggedAssets.value.length)
  const fieldBeacons = computed(() => beacons.value.filter((b) => b.status !== 'ARCHIVED'))
  const fieldGateways = computed(() => activeGateways.value)
  const fieldTotal = computed(() => fieldBeacons.value.length + fieldGateways.value.length)
  const beaconOnline = computed(() => fieldBeacons.value.filter((b) => b.status === 'ONLINE').length)
  const gatewayFieldOnline = computed(() => fieldGateways.value.filter((g) => g.status === 'ONLINE').length)
  const fieldOnline = computed(() => beaconOnline.value + gatewayFieldOnline.value)
  const offlineAssets = computed(() => taggedAssets.value.filter((a) => a.status === 'OFFLINE').length)
  const unboundAssets = computed(() => taggedAssets.value.filter((a) => a.status === 'UNBOUND').length)
  const unboundBeacons = computed(() => fieldBeacons.value.filter((b) => !b.fields?.boundAssetId).length)
  const onlineGateways = computed(() => activeGateways.value.filter((g) => g.status === 'ONLINE').length)
  const offlineGateways = computed(() => gateways.value.filter((g) => g.status === 'OFFLINE').length)
  const openAlertTotal = computed(() => {
    const fromList = alerts.value.filter((a) => String(a.status || '').toUpperCase() === 'OPEN').length
    if (alerts.value.length) return fromList
    const n = analytics.value.openAlerts
    return typeof n === 'number' ? n : fromList
  })

  const recentInventories = computed(() => {
    return [...inventorySessions.value]
      .sort((a, b) => {
        const ta = new Date(String(a.fields?.startedAt || a.createdAt || 0)).getTime()
        const tb = new Date(String(b.fields?.startedAt || b.createdAt || 0)).getTime()
        return tb - ta
      })
      .slice(0, 6)
  })

  const offlineAssetsList = computed(() =>
    assets.value
      .filter((a) => a.status === 'OFFLINE')
      .sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))
      .slice(0, 80)
  )

  const offlineGatewaysList = computed(() =>
    gateways.value
      .filter((g) => g.status === 'OFFLINE')
      .sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))
      .slice(0, 80)
  )

  const offlineDeviceTotal = computed(() => offlineAssets.value + offlineGateways.value)

  const attentionAssets = computed(() => {
    const rows = statusAssets.value
      .map((a) => {
        const reasons: string[] = []
        if (a.status === 'OFFLINE') reasons.push(t('product.overview.reasonOffline'))
        if (a.status === 'UNBOUND') reasons.push(t('product.overview.reasonUnbound'))
        const pct = a.fields?.batteryPercent
        if (typeof pct === 'number' && pct <= 20) reasons.push(t('product.overview.reasonBatteryPct', { pct }))
        const label = String(a.fields?.batteryLabel || '')
        if (!reasons.some((r) => /电量|Battery|battery/i.test(r)) && /low|低/i.test(label)) {
          reasons.push(t('product.overview.reasonBatteryLow'))
        }
        return { asset: a, reasons }
      })
      .filter((row) => row.reasons.length > 0)
      .sort((a, b) => {
        const rank = (s?: string) => (s === 'OFFLINE' ? 0 : s === 'UNBOUND' ? 1 : 2)
        return rank(a.asset.status) - rank(b.asset.status)
      })
    return rows.slice(0, 50)
  })

  const openAlerts = computed(() =>
    alerts.value.filter((a) => String(a.status || '').toUpperCase() === 'OPEN').slice(0, 30)
  )

  type TodoKind = 'offline' | 'unbound' | 'battery' | 'alert'
  const todos = computed(() => {
    const items: Array<{
      key: string
      kind: TodoKind
      title: string
      subtitle: string
      status?: string
      reason: string
      to: string
      query?: Record<string, string>
    }> = []

    for (const row of attentionAssets.value) {
      const a = row.asset
      const isGw = isGatewayAsset(a)
      const primary: TodoKind =
        a.status === 'OFFLINE' ? 'offline' : a.status === 'UNBOUND' ? 'unbound' : 'battery'
      let to = 'AssetStatus'
      let query: Record<string, string> = { q: a.code || a.name }
      if (primary === 'offline') {
        if (isGw) {
          to = 'GatewayManagement'
          query = { q: a.name }
        } else {
          query = { presence: 'OFFLINE', q: a.code || a.name }
        }
      } else if (primary === 'unbound') {
        to = 'AssetList'
        query = { bound: 'UNBOUND', q: a.code || a.name }
      } else {
        query = { battery: 'low', q: a.code || a.name }
      }
      items.push({
        key: `asset-${a.id}`,
        kind: primary,
        title: a.name,
        subtitle: `${a.code} · ${formatLiveLocation(a.fields, t) || display(a.fields?.locationLabel)}`,
        status: a.status,
        reason: row.reasons.join(' · '),
        to,
        query
      })
    }

    for (const alert of openAlerts.value) {
      items.push({
        key: `alert-${alert.id}`,
        kind: 'alert',
        title: localizeAlertTitle(alert.name || alert.code, t),
        subtitle: String(localizeAlertMessage(alert.fields?.message || alert.fields?.ruleName, t) || ''),
        reason: t('product.overview.openAlertsTitle'),
        to: 'AlertEvents'
      })
    }

    const rank = (k: TodoKind) => (k === 'offline' ? 0 : k === 'unbound' ? 1 : k === 'battery' ? 2 : 3)
    return items.sort((a, b) => rank(a.kind) - rank(b.kind)).slice(0, 50)
  })

  const recentActivity = computed(() => {
    return [...statusAssets.value]
      .filter((a) => a.fields?.lastSeenAt)
      .sort((a, b) => {
        const ta = new Date(String(a.fields?.lastSeenAt || 0)).getTime()
        const tb = new Date(String(b.fields?.lastSeenAt || 0)).getTime()
        return tb - ta
      })
      .slice(0, 6)
  })

  const gatewayHealth = computed(() =>
    [...activeGateways.value].sort((a, b) => {
      const rank = (s?: string) => (s === 'OFFLINE' ? 0 : s === 'ONLINE' ? 2 : 1)
      const d = rank(a.status) - rank(b.status)
      if (d !== 0) return d
      return a.name.localeCompare(b.name, 'zh-CN')
    }).slice(0, 6)
  )

  const kpis = computed(() => [
    {
      key: 'devices',
      label: t('product.overview.metricDeviceOverview'),
      value: fieldTotal.value ? `${fieldOnline.value}/${fieldTotal.value}` : '0',
      hint: fieldTotal.value
        ? t('product.overview.fieldSplit', {
            beacons: `${beaconOnline.value}/${fieldBeacons.value.length}`,
            gateways: `${gatewayFieldOnline.value}/${fieldGateways.value.length}`
          })
        : t('product.overview.noAssets'),
      icon: 'ri:stack-line',
      tone: fieldOnline.value && fieldOnline.value === fieldTotal.value ? 'green' : fieldOnline.value ? 'orange' : 'slate',
      to: 'AssetStatus'
    },
    {
      key: 'ledger',
      label: t('product.overview.metricLedger'),
      value: String(ledgerTotal.value),
      hint: ledgerTotal.value
        ? unboundAssets.value
          ? t('product.overview.ledgerHint', { unbound: unboundAssets.value })
          : t('product.overview.ledgerAllBound')
        : t('product.overview.noAssets'),
      icon: 'ri:archive-line',
      tone: 'blue',
      to: 'AssetList'
    },
    {
      key: 'gateways',
      label: t('product.overview.metricGateways'),
      value: activeGateways.value.length ? `${onlineGateways.value}/${activeGateways.value.length}` : '0',
      hint: activeGateways.value.length
        ? offlineGateways.value
          ? t('product.overview.gatewaysOffline', { count: offlineGateways.value })
          : t('product.overview.gatewayAllOnline')
        : t('product.overview.noGateways'),
      icon: 'ri:base-station-line',
      tone: offlineGateways.value ? 'orange' : activeGateways.value.length ? 'teal' : 'slate',
      to: 'GatewayManagement'
    },
    {
      key: 'offline',
      label: t('product.overview.metricOfflineDevices'),
      value: String(offlineDeviceTotal.value),
      hint: offlineDeviceTotal.value
        ? t('product.overview.offlineBreakdown', { assets: offlineAssets.value, gateways: offlineGateways.value })
        : t('product.overview.noOffline'),
      icon: 'ri:wifi-off-line',
      tone: offlineDeviceTotal.value ? 'orange' : 'slate',
      to: 'AssetStatus',
      query: { presence: 'OFFLINE' }
    },
    {
      key: 'unbound',
      label: t('product.overview.metricUnbound'),
      value: String(unboundBeacons.value),
      hint: fieldBeacons.value.length
        ? unboundBeacons.value
          ? t('product.overview.unboundHint', { bound: fieldBeacons.value.length - unboundBeacons.value })
          : t('product.overview.allBound')
        : t('product.overview.noBeacons'),
      icon: 'ri:link-unlink-m',
      tone: unboundBeacons.value ? 'orange' : 'slate',
      to: 'BeaconManagement',
      query: { bound: 'UNBOUND' }
    },
    {
      key: 'alerts',
      label: t('product.overview.metricOpenAlerts'),
      value: String(openAlertTotal.value),
      hint: openAlertTotal.value ? t('product.overview.needAttention') : t('product.overview.noTodos'),
      icon: 'ri:alarm-warning-line',
      tone: openAlertTotal.value ? 'red' : 'slate',
      to: 'AlertEvents'
    }
  ])

  function display(value: unknown) {
    if (value == null || value === '') return '—'
    return String(value)
  }

  function inventoryCoverage(session: NamedResource | null) {
    if (!session) return null
    const expected = Number(session.fields?.expectedCount || 0)
    const found = Number(session.fields?.foundCount || 0)
    if (expected <= 0) return null
    return Math.round((found / expected) * 100)
  }

  function inventoryStatusLabel(status?: string) {
    const value = String(status || '').toUpperCase()
    if (value === 'OPEN') return t('product.assets.inventory.statusOpen')
    if (value === 'CLOSED') return t('product.assets.inventory.statusClosed')
    return display(status)
  }

  async function load(silent = false) {
    if (!projectScope.value) {
      assets.value = []
      beacons.value = []
      gateways.value = []
      alerts.value = []
      inventorySessions.value = []
      analytics.value = {}
      return
    }
    if (loadInFlight) return
    loadInFlight = true
    if (!silent) loading.value = true
    loadError.value = ''
    const scope = projectScope.value
    try {
      const [assetRows, beaconRows, gatewayRows, alertPage, sessions, summary] = await Promise.all([
        assetsApi.list(scope),
        beaconsApi.list(scope).catch(() => [] as NamedResource[]),
        gatewaysApi.list(scope),
        fetchAlerts(scope, { current: 1, size: 20 }),
        fetchInventorySessions(scope),
        fetchAnalyticsSummary(scope).catch(() => ({}))
      ])
      assets.value = assetRows
      beacons.value = beaconRows
      gateways.value = gatewayRows
      alerts.value = alertPage?.records || []
      inventorySessions.value = sessions
      analytics.value = summary || {}
      lastRefreshedAt.value = new Date()
    } catch (error) {
      loadError.value = error instanceof Error ? error.message : t('product.overview.loadFailed')
    } finally {
      loading.value = false
      loadInFlight = false
    }
  }

  function go(name: string, query?: Record<string, string>) {
    router.push({ name, query: { _: String(Date.now()), ...(query || {}) } }).catch(() => {})
  }

  function goRecent(row: NamedResource) {
    go('AssetFloorplan', {
      id: row.id,
      kind: isGatewayAsset(row) ? 'gateway' : 'asset'
    })
  }

  watch(
    projectScope,
    () => {
      void load()
      if (timer) clearInterval(timer)
      timer = setInterval(() => void load(true), 8000)
    },
    { immediate: true }
  )

  onUnmounted(() => {
    if (timer) clearInterval(timer)
  })
</script>

<template>
  <div class="overview" v-loading="loading">
    <header class="overview__head">
      <div>
        <h1>{{ t('product.overview.title') }}</h1>
        <p>
          {{ t('product.overview.subtitle') }}
          <span v-if="lastRefreshedAt"> · {{ t('product.overview.lastRefresh', { time: formatDateTime(lastRefreshedAt) }) }}</span>
        </p>
      </div>
      <div class="overview__actions">
        <ElButton @click="go('AssetList')">{{ t('menus.asset.assets') }}</ElButton>
        <ElButton @click="go('AssetStatus')">{{ t('product.overview.assetStatus') }}</ElButton>
        <ElButton @click="go('GatewayManagement')">{{ t('menus.device.gateways') }}</ElButton>
        <ElButton @click="go('AssetFloorplan')">{{ t('product.overview.floorplan') }}</ElButton>
        <ElButton @click="go('AssetInventory')">{{ t('product.overview.inventory') }}</ElButton>
        <ElButton type="primary" @click="load()">{{ t('common.refresh') }}</ElButton>
      </div>
    </header>

    <ElAlert
      v-if="loadError"
      type="error"
      :closable="false"
      show-icon
      :title="loadError"
      style="margin-bottom: 16px"
    />

    <section class="kpi-grid">
      <button
        v-for="kpi in kpis"
        :key="kpi.key"
        type="button"
        class="kpi"
        :class="kpi.tone"
        @click="go(kpi.to, kpi.query)"
      >
        <div class="kpi__icon">
          <ArtSvgIcon :icon="kpi.icon" />
        </div>
        <div class="kpi__body">
          <span class="kpi__label">{{ kpi.label }}</span>
          <strong class="kpi__value">{{ kpi.value }}</strong>
          <small class="kpi__hint">{{ kpi.hint }}</small>
        </div>
      </button>
    </section>

    <section class="main-grid">
      <article class="panel">
        <div class="panel__head">
          <div>
            <span class="eyebrow">{{ t('product.overview.pending') }}</span>
            <h2>{{ t('product.overview.todosTitle') }}</h2>
          </div>
        </div>
        <div class="panel__body">
          <div v-if="!todos.length" class="empty">
            <ArtSvgIcon icon="ri:checkbox-circle-line" />
            <div>
              <strong>{{ t('product.overview.noTodosMerged') }}</strong>
              <p>{{ t('product.overview.todosOk') }}</p>
            </div>
          </div>
          <ul v-else class="row-list">
            <li v-for="row in todos" :key="row.key" @click="go(row.to, row.query)">
              <div class="row-list__main">
                <strong>{{ row.title }}</strong>
                <small>{{ row.subtitle }}</small>
              </div>
              <div class="row-list__meta">
                <PresencePill v-if="row.status" :status="row.status" />
                <ElTag v-else size="small" type="danger" effect="plain">OPEN</ElTag>
                <span class="reason">{{ row.reason }}</span>
              </div>
            </li>
          </ul>
        </div>
      </article>

      <article class="panel offline-panel" :class="{ 'is-alert': offlineDeviceTotal > 0 }">
        <div class="panel__head">
          <div>
            <span class="eyebrow">{{ t('product.overview.deviceHealth') }}</span>
            <h2>{{ t('product.overview.offlineDevicesTitle') }}</h2>
          </div>
          <ElTag v-if="offlineDeviceTotal" type="warning" effect="dark" round>
            {{ t('product.overview.offlineCount', { count: offlineDeviceTotal }) }}
          </ElTag>
        </div>
        <div class="panel__body">
        <div v-if="!offlineDeviceTotal" class="empty compact">
          <ArtSvgIcon icon="ri:checkbox-circle-line" />
          <div>
            <strong>{{ t('product.overview.noOfflineDevices') }}</strong>
            <p>{{ t('product.overview.allOnline') }}</p>
          </div>
        </div>
        <div v-else class="offline-grid">
          <div>
            <button type="button" class="split-block__title is-link" @click="go('AssetStatus', { presence: 'OFFLINE' })">
              {{ t('product.overview.offlineAssets') }}
              <small>{{ t('product.overview.units', { count: offlineAssets }) }}</small>
            </button>
            <div v-if="!offlineAssetsList.length" class="empty compact">
              <div>
                <strong>{{ t('product.overview.noOfflineAssets') }}</strong>
              </div>
            </div>
            <ul v-else class="row-list dense">
              <li
                v-for="asset in offlineAssetsList"
                :key="asset.id"
                @click="go('AssetStatus', { presence: 'OFFLINE', q: asset.code || asset.name })"
              >
                <div class="row-list__main">
                  <strong>{{ asset.name }}</strong>
                  <small>
                    {{ formatLiveLocation(asset.fields, t) || display(asset.fields?.locationLabel || asset.code) }}
                  </small>
                </div>
                <div class="row-list__meta">
                  <PresencePill status="OFFLINE" />
                  <span class="reason">{{ formatDateTime(asset.fields?.lastSeenAt as string) }}</span>
                </div>
              </li>
            </ul>
          </div>
          <div>
            <button type="button" class="split-block__title is-link" @click="go('GatewayManagement')">
              {{ t('product.overview.offlineGateways') }}
              <small>{{ t('product.overview.units', { count: offlineGateways }) }}</small>
            </button>
            <div v-if="!offlineGatewaysList.length" class="empty compact">
              <div>
                <strong>{{ t('product.overview.noOfflineGateways') }}</strong>
              </div>
            </div>
            <ul v-else class="row-list dense">
              <li
                v-for="gw in offlineGatewaysList"
                :key="gw.id"
                @click="go('GatewayManagement', { q: gw.name })"
              >
                <div class="row-list__main">
                  <strong>{{ gw.name }}</strong>
                  <small>{{ display(gw.fields?.zoneName || gw.fields?.mapName || gw.code) }}</small>
                </div>
                <div class="row-list__meta">
                  <PresencePill status="OFFLINE" />
                  <span class="reason">{{ formatDateTime(gw.fields?.lastSeenAt as string) }}</span>
                </div>
              </li>
            </ul>
          </div>
        </div>
        </div>
      </article>
    </section>

    <section class="bottom-grid">
      <article class="panel">
        <div class="panel__head">
          <div>
            <span class="eyebrow">{{ t('product.overview.activity') }}</span>
            <h2>{{ t('product.overview.recentPresence') }}</h2>
          </div>
          <ElButton text type="primary" @click="go('AssetFloorplan')">{{ t('product.overview.floorplan') }}</ElButton>
        </div>
        <div class="panel__body">
          <div v-if="!recentActivity.length" class="empty compact">
            <div>
              <strong>{{ t('product.overview.noScans') }}</strong>
              <p>{{ t('product.overview.scansHint') }}</p>
            </div>
          </div>
          <ul v-else class="row-list dense">
            <li v-for="asset in recentActivity" :key="asset.id" @click="goRecent(asset)">
              <div class="row-list__main">
                <strong>{{ asset.name }}</strong>
                <small>
                  {{ display(asset.fields?.lastGatewayName) }}
                  <template v-if="asset.fields?.estimatedDistanceMeters != null">
                    · {{ formatApproxMeters(asset.fields.estimatedDistanceMeters, t) }}
                  </template>
                </small>
              </div>
              <div class="row-list__meta">
                <PresencePill :status="asset.status" />
                <span class="reason">{{ formatDateTime(asset.fields?.lastSeenAt as string) }}</span>
              </div>
            </li>
          </ul>
        </div>
      </article>

      <article class="panel">
        <div class="panel__head">
          <div>
            <span class="eyebrow">{{ t('product.overview.gateways') }}</span>
            <h2>{{ t('product.overview.gatewayStatusTitle') }}</h2>
          </div>
          <ElButton text type="primary" @click="go('GatewayManagement')">{{ t('menus.device.gateways') }}</ElButton>
        </div>
        <div class="panel__body">
          <div v-if="!gatewayHealth.length" class="empty compact">
            <div>
              <strong>{{ t('product.overview.noGateways') }}</strong>
              <p>{{ t('product.overview.registerGateway') }}</p>
            </div>
          </div>
          <ul v-else class="row-list dense">
            <li
              v-for="gw in gatewayHealth"
              :key="gw.id"
              @click="go('GatewayManagement', { q: gw.name })"
            >
              <div class="row-list__main">
                <strong>{{ gw.name }}</strong>
                <small>{{ display(gw.fields?.zoneName || gw.fields?.mapName || gw.code) }}</small>
              </div>
              <div class="row-list__meta">
                <PresencePill :status="gw.status" />
                <span class="reason">{{ formatDateTime(gw.fields?.lastSeenAt as string) }}</span>
              </div>
            </li>
          </ul>
        </div>
      </article>

      <article class="panel inventory-panel">
        <div class="panel__head">
          <div>
            <span class="eyebrow">{{ t('product.overview.inventory') }}</span>
            <h2>{{ t('product.overview.latestInventory') }}</h2>
          </div>
          <ElButton text type="primary" @click="go('AssetInventory')">{{ t('product.overview.inventory') }}</ElButton>
        </div>
        <div class="panel__body">
          <div v-if="!recentInventories.length" class="empty compact">
            <div>
              <strong>{{ t('product.overview.noInventory') }}</strong>
              <p>{{ t('product.overview.inventoryHint') }}</p>
            </div>
          </div>
          <ul v-else class="row-list dense">
            <li
              v-for="session in recentInventories"
              :key="session.id"
              @click="go('AssetInventory')"
            >
              <div class="row-list__main">
                <strong>{{ localizeInventoryName(session.name, t) }}</strong>
                <small>
                  {{ t('product.overview.startedAt', { time: formatDateTime(session.fields?.startedAt as string) }) }}
                  · {{ inventoryStatusLabel(session.status) }}
                </small>
              </div>
              <div class="row-list__meta">
                <strong>
                  {{ display(session.fields?.foundCount) }} / {{ display(session.fields?.expectedCount) }}
                </strong>
                <span class="reason">
                  {{ inventoryCoverage(session) == null ? '—' : `${inventoryCoverage(session)}%` }}
                  {{ t('product.overview.coverage') }}
                </span>
              </div>
            </li>
          </ul>
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped>
  .overview {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .overview__head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    padding: 4px 2px;
  }

  .overview__head h1 {
    margin: 0;
    font-size: 24px;
    font-weight: 650;
    letter-spacing: -0.02em;
  }

  .overview__head p {
    margin: 6px 0 0;
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }

  .overview__actions {
    display: flex;
    gap: 8px;
    flex-shrink: 0;
    flex-wrap: wrap;
    justify-content: flex-end;
  }

  .kpi-grid {
    display: grid;
    grid-template-columns: repeat(6, minmax(0, 1fr));
    gap: 12px;
  }

  .offline-panel.is-alert {
    border-color: color-mix(in srgb, var(--el-color-warning) 45%, var(--el-border-color-lighter));
  }

  .offline-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px;
  }

  .kpi {
    appearance: none;
    border: 1px solid var(--el-border-color-lighter);
    background: var(--el-bg-color);
    border-radius: 14px;
    padding: 14px;
    display: flex;
    gap: 12px;
    text-align: left;
    cursor: pointer;
    transition: border-color 0.15s ease, transform 0.15s ease;
  }

  .kpi:hover {
    border-color: var(--el-color-primary-light-5);
    transform: translateY(-1px);
  }

  .kpi__icon {
    width: 38px;
    height: 38px;
    border-radius: 10px;
    display: grid;
    place-items: center;
    font-size: 18px;
    flex-shrink: 0;
  }

  .kpi.blue .kpi__icon { background: #eff6ff; color: #2563eb; }
  .kpi.green .kpi__icon { background: #ecfdf5; color: #059669; }
  .kpi.orange .kpi__icon { background: #fff7ed; color: #ea580c; }
  .kpi.teal .kpi__icon { background: #f0fdfa; color: #0d9488; }
  .kpi.red .kpi__icon { background: #fef2f2; color: #dc2626; }
  .kpi.slate .kpi__icon { background: #f8fafc; color: #64748b; }

  .kpi__body {
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .kpi__label {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .kpi__value {
    font-size: 22px;
    font-weight: 700;
    line-height: 1.1;
    font-variant-numeric: tabular-nums;
  }

  .kpi__hint {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    line-height: 1.35;
    white-space: normal;
  }

  .main-grid {
    display: grid;
    gap: 12px;
    grid-template-columns: 1.2fr 1fr;
  }

  .bottom-grid {
    display: grid;
    gap: 12px;
    grid-template-columns: 1fr 1fr 1fr;
  }

  .split-block__title {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 8px;
    font-size: 13px;
    font-weight: 600;
    margin-bottom: 4px;
    width: 100%;
    appearance: none;
    border: 0;
    background: transparent;
    padding: 0;
    color: inherit;
    text-align: left;
  }

  .split-block__title small {
    font-weight: 400;
    color: var(--el-text-color-secondary);
  }

  .split-block__title.is-link {
    cursor: pointer;
  }

  .split-block__title.is-link:hover {
    color: var(--el-color-primary);
  }

  .panel {
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 16px;
    padding: 16px;
    min-width: 0;
    display: flex;
    flex-direction: column;
    min-height: 0;
  }

  .main-grid .panel__body {
    max-height: 168px;
  }

  .bottom-grid .panel__body {
    max-height: 264px;
  }

  .panel__head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 12px;
    flex-shrink: 0;
  }

  .panel__body {
    flex: 1 1 auto;
    min-height: 0;
    overflow: auto;
    scrollbar-width: thin;
  }

  .panel__head h2 {
    margin: 2px 0 0;
    font-size: 16px;
    font-weight: 650;
  }

  .eyebrow {
    display: inline-block;
    font-size: 11px;
    letter-spacing: 0.04em;
    text-transform: uppercase;
    color: var(--el-text-color-secondary);
  }

  .row-list {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
  }

  .row-list li {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 10px 4px;
    border-top: 1px solid var(--el-border-color-extra-light);
    cursor: pointer;
  }

  .row-list li:first-child {
    border-top: 0;
  }

  .row-list li:hover {
    background: rgb(15 23 42 / 2%);
  }

  .row-list.dense li {
    padding: 8px 4px;
  }

  .row-list__main {
    min-width: 0;
  }

  .row-list__main strong {
    display: block;
    font-size: 13px;
    font-weight: 600;
  }

  .row-list__main small,
  .reason {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }

  .row-list__main small {
    display: block;
    margin-top: 2px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .row-list__meta {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 4px;
    flex-shrink: 0;
  }

  .empty {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px 8px;
    color: var(--el-text-color-secondary);
  }

  .empty.compact {
    padding: 8px 4px;
    justify-content: space-between;
  }

  .empty :deep(svg) {
    font-size: 28px;
    color: var(--el-color-success);
  }

  .empty strong {
    display: block;
    color: var(--el-text-color-primary);
    margin-bottom: 2px;
  }

  .empty p {
    margin: 0;
    font-size: 13px;
  }

  .inventory-panel .row-list__meta strong {
    font-variant-numeric: tabular-nums;
  }

  @media (max-width: 1280px) {
    .kpi-grid {
      grid-template-columns: repeat(3, minmax(0, 1fr));
    }
  }

  @media (max-width: 960px) {
    .overview__head,
    .main-grid,
    .bottom-grid {
      grid-template-columns: 1fr;
      flex-direction: column;
    }

    .overview__head {
      display: flex;
    }

    .kpi-grid {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .offline-grid {
      grid-template-columns: 1fr;
    }
  }
</style>
