<script setup lang="ts">
  import { computed, nextTick, onActivated, onMounted, onUnmounted, ref, watch } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { useRoute } from 'vue-router'
  import { storeToRefs } from 'pinia'
  import type { TableInstance } from 'element-plus'
  import maplibregl, { type MapMouseEvent } from 'maplibre-gl'
  import 'maplibre-gl/dist/maplibre-gl.css'
  import { fetchGeotagDevices, fetchGeotagTracks } from '@/api/asset-platform'
  import TablePager from '@/components/business/TablePager.vue'
  import { useClientPagination } from '@/composables/useClientPagination'
  import { useTenantContextStore } from '@/store/modules/tenant-context'
  import { useTableStore } from '@/store/modules/table'
  import { formatDateTime } from '@/utils/datetime'
  import { queryText } from '@/utils/route-query'
  import {
    applyTerrain,
    geotagMapStyle,
    locationLabel,
    optionById,
    remembered3d,
    rememberedProvider,
    styleKeyOf,
    usableOption,
    type GeotagMapConfig
  } from '../map-style'
  import BasemapBar from '../basemap-bar.vue'

  defineOptions({ name: 'GeotagTrack' })

  const { t } = useI18n()
  const route = useRoute()
  const { projectScope } = storeToRefs(useTenantContextStore())
  const tableStore = useTableStore()
  const { tableSize, isZebra, isBorder } = storeToRefs(tableStore)
  const loading = ref(false)
  const devices = ref<Array<{ sn: string; assetName?: string }>>([])
  const sn = ref('')
  const range = ref<[Date, Date]>([
    new Date(Date.now() - 3 * 24 * 60 * 60 * 1000),
    new Date()
  ])
  let enterInFlight = false
  let skipSnQuery = false
  const points = ref<Record<string, unknown>[]>([])
  const mapConfig = ref<GeotagMapConfig | null>(null)
  const provider = ref('osm')
  const threeD = ref(false)
  const mapEl = ref<HTMLElement | null>(null)
  const bodyEl = ref<HTMLElement | null>(null)
  const tableRef = ref<TableInstance>()
  const selectedKey = ref('')
  const LIST_WIDTH_KEY = 'geotag-track-list-width'
  const listWidth = ref(readListWidth())
  const dragging = ref(false)
  const POINT_LAYERS = ['track-points-hit', 'track-points', 'track-selected']
  let map: maplibregl.Map | null = null
  let styleKey = ''
  let pulseMarker: maplibregl.Marker | null = null
  let infoPopup: maplibregl.Popup | null = null
  let resizeObserver: ResizeObserver | null = null

  const newestFirst = computed(() => [...points.value].reverse())
  const {
    current: pageCurrent,
    size: pageSize,
    total: pageTotal,
    pagedRows,
    onPageChange,
    onSizeChange
  } = useClientPagination(newestFirst, {
    defaultSize: 20,
    resetOn: computed(() => `${sn.value}|${points.value.length}`)
  })
  const trackPageSizes = [8, 10, 20, 50]

  function readListWidth() {
    try {
      const n = Number(localStorage.getItem(LIST_WIDTH_KEY))
      if (Number.isFinite(n) && n >= 280) return n
    } catch {
      /* ignore */
    }
    return 460
  }

  function clampListWidth(next: number) {
    const max = Math.max(280, (bodyEl.value?.clientWidth || 960) - 280)
    return Math.min(max, Math.max(280, Math.round(next)))
  }

  function startResize(event: MouseEvent) {
    dragging.value = true
    const startX = event.clientX
    const startW = listWidth.value
    const onMove = (move: MouseEvent) => {
      listWidth.value = clampListWidth(startW + (startX - move.clientX))
    }
    const onUp = () => {
      dragging.value = false
      try {
        localStorage.setItem(LIST_WIDTH_KEY, String(listWidth.value))
      } catch {
        /* ignore */
      }
      map?.resize()
      window.removeEventListener('mousemove', onMove)
      window.removeEventListener('mouseup', onUp)
    }
    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseup', onUp)
    event.preventDefault()
  }

  function formatTrackTime(value: unknown) {
    if (value == null || value === '') return '—'
    const date = value instanceof Date ? value : new Date(String(value))
    if (Number.isNaN(date.getTime())) return formatDateTime(value as string)
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    const hour = String(date.getHours()).padStart(2, '0')
    const minute = String(date.getMinutes()).padStart(2, '0')
    const second = String(date.getSeconds()).padStart(2, '0')
    return `${month}-${day} ${hour}:${minute}:${second}`
  }

  function asCoord(value: unknown) {
    const n = typeof value === 'number' ? value : Number(value)
    return Number.isFinite(n) ? n : null
  }

  function lngLatOf(row: Record<string, unknown>): [number, number] | null {
    const lng = asCoord(row.lng)
    const lat = asCoord(row.lat)
    if (lng == null || lat == null) return null
    return [lng, lat]
  }

  function rowKey(row: Record<string, unknown>) {
    if (row.id != null && String(row.id)) return String(row.id)
    const pair = lngLatOf(row)
    return `${row.locationTime || ''}|${pair?.[1] ?? ''}|${pair?.[0] ?? ''}`
  }

  function rowByKey(key: string) {
    return points.value.find((item) => rowKey(item) === key)
  }

  function addressKey(row: Record<string, unknown>) {
    const raw = typeof row.address === 'string' ? row.address.trim() : ''
    if (raw) return `a:${raw}`
    const pair = lngLatOf(row)
    if (!pair) return ''
    return `g:${pair[1].toFixed(5)},${pair[0].toFixed(5)}`
  }

  function latestByAddress() {
    const byKey = new Map<string, Record<string, unknown>>()
    for (const row of points.value) {
      if (!lngLatOf(row)) continue
      const key = addressKey(row)
      if (!key) continue
      byKey.set(key, row)
    }
    return byKey
  }

  function rowByAddressKey(key: string) {
    return latestByAddress().get(key)
  }

  function mapPointRows() {
    return [...latestByAddress().values()]
  }

  function activeOption() {
    return optionById(mapConfig.value, provider.value) || mapConfig.value
  }

  function locatedPoints() {
    return points.value.filter((item) => lngLatOf(item))
  }

  function coords() {
    const pairs: [number, number][] = []
    let lastKey = ''
    for (const row of locatedPoints()) {
      const pair = lngLatOf(row)
      const key = addressKey(row)
      if (!pair || !key || key === lastKey) continue
      lastKey = key
      pairs.push(pair)
    }
    return pairs
  }

  function trackGeoJson() {
    const rows = mapPointRows()
    const pairs = coords()
    return {
      type: 'FeatureCollection' as const,
      features: [
        ...rows.map((item) => ({
          type: 'Feature' as const,
          properties: { pointId: addressKey(item) },
          geometry: { type: 'Point' as const, coordinates: lngLatOf(item)! }
        })),
        ...(pairs.length >= 2
          ? [
              {
                type: 'Feature' as const,
                properties: {},
                geometry: { type: 'LineString' as const, coordinates: pairs }
              }
            ]
          : [])
      ]
    }
  }

  function escapeHtml(value: unknown) {
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
  }

  function popupHtml(row: Record<string, unknown>) {
    const device = devices.value.find((item) => item.sn === sn.value)
    const title = device?.assetName || String(row.sn || sn.value || '')
    const pair = lngLatOf(row)
    const battery = row.battery == null || row.battery === '' ? '—' : `${row.battery}%`
    const address =
      locationLabel({
        address: typeof row.address === 'string' ? row.address : undefined,
        lat: pair?.[1],
        lng: pair?.[0]
      }) || '—'
    return `<div class="geotag-track-popup">
      <strong>${escapeHtml(title)}</strong>
      <p>${escapeHtml(t('product.geotag.sn'))}：${escapeHtml(row.sn || sn.value)}</p>
      <p>${escapeHtml(t('product.geotag.locationTime'))}：${escapeHtml(formatDateTime(row.locationTime as string))}</p>
      <p>${escapeHtml(t('product.geotag.address'))}：${escapeHtml(address)}</p>
      <p>${escapeHtml(t('product.geotag.battery'))}：${escapeHtml(battery)}</p>
      <p>${escapeHtml(t('product.geotag.lat'))}：${pair ? pair[1] : '—'}</p>
      <p>${escapeHtml(t('product.geotag.lng'))}：${pair ? pair[0] : '—'}</p>
    </div>`
  }

  function applyBasemap() {
    if (!map) return
    const option = activeOption()
    const key = styleKeyOf(option)
    if (key !== styleKey) {
      styleKey = key
      map.setStyle(geotagMapStyle(option))
      map.once('style.load', () => {
        map.once('idle', () => {
          applyTerrain(map, option, threeD.value)
          drawTrack()
        })
      })
      return
    }
    applyTerrain(map, option, threeD.value)
  }

  function ingestMap(config: GeotagMapConfig | null, apply = true) {
    mapConfig.value = config
    const fallback = String(config?.provider || 'osm')
    const remembered = rememberedProvider(fallback)
    const option = optionById(config, remembered)
    provider.value = usableOption(option) ? remembered : fallback
    threeD.value = remembered3d() && !!optionById(config, provider.value)?.supports3d
    if (apply) applyBasemap()
  }

  function clearTrackLayers() {
    if (!map) return
    for (const id of ['track-points-hit', 'track-selected', 'track-points', 'track-line']) {
      if (map.getLayer(id)) map.removeLayer(id)
    }
    if (map.getSource('track')) map.removeSource('track')
  }

  function drawTrack() {
    if (!map || !map.isStyleLoaded()) return
    applyTerrain(map, activeOption(), threeD.value)
    const data = trackGeoJson()
    clearTrackLayers()
    map.addSource('track', { type: 'geojson', data })
    map.addLayer({
      id: 'track-line',
      type: 'line',
      source: 'track',
      filter: ['==', ['geometry-type'], 'LineString'],
      paint: { 'line-color': '#2563eb', 'line-width': 3 }
    })
    map.addLayer({
      id: 'track-points',
      type: 'circle',
      source: 'track',
      filter: ['==', ['geometry-type'], 'Point'],
      paint: {
        'circle-radius': 7,
        'circle-color': '#2563eb',
        'circle-stroke-width': 2,
        'circle-stroke-color': '#ffffff'
      }
    })
    map.addLayer({
      id: 'track-selected',
      type: 'circle',
      source: 'track',
      filter: ['==', ['get', 'pointId'], '__none__'],
      paint: {
        'circle-radius': 11,
        'circle-color': '#f59e0b',
        'circle-stroke-width': 3,
        'circle-stroke-color': '#ffffff'
      }
    })
    map.addLayer({
      id: 'track-points-hit',
      type: 'circle',
      source: 'track',
      filter: ['==', ['geometry-type'], 'Point'],
      paint: {
        'circle-radius': 18,
        'circle-color': '#2563eb',
        'circle-opacity': 0
      }
    })
    applySelectedFilter()
    if (selectedKey.value) {
      const row = rowByKey(selectedKey.value)
      if (row) {
        placePulse(row, false)
        showPointPopup(row)
      }
    }
  }

  function applySelectedFilter() {
    if (!map?.getLayer('track-selected')) return
    const row = rowByKey(selectedKey.value)
    const id = row ? addressKey(row) : '__none__'
    map.setFilter('track-selected', ['==', ['get', 'pointId'], id || '__none__'])
  }

  function clearPopup() {
    infoPopup?.remove()
    infoPopup = null
  }

  function showPointPopup(row: Record<string, unknown>) {
    if (!map) return
    const pair = lngLatOf(row)
    if (!pair) return
    if (!infoPopup) {
      infoPopup = new maplibregl.Popup({
        offset: 18,
        closeButton: true,
        maxWidth: '300px',
        className: 'geotag-track-popup-wrap'
      })
    }
    infoPopup.setLngLat(pair).setHTML(popupHtml(row)).addTo(map)
  }

  function clearPulse() {
    pulseMarker?.remove()
    pulseMarker = null
  }

  function placePulse(row: Record<string, unknown>, fly: boolean) {
    if (!map) return
    const lngLat = lngLatOf(row)
    if (!lngLat) return
    if (!pulseMarker) {
      const wrap = document.createElement('div')
      wrap.className = 'geotag-pulse-wrap'
      const inner = document.createElement('div')
      inner.className = 'geotag-pulse'
      wrap.appendChild(inner)
      pulseMarker = new maplibregl.Marker({ element: wrap, anchor: 'center' }).setLngLat(lngLat).addTo(map)
    } else {
      pulseMarker.setLngLat(lngLat)
    }
    const inner = pulseMarker.getElement().querySelector('.geotag-pulse')
    if (inner) {
      inner.classList.remove('geotag-pulse--beat')
      void (inner as HTMLElement).offsetWidth
      inner.classList.add('geotag-pulse--beat')
    }
    if (fly) {
      map.flyTo({
        center: lngLat,
        zoom: Math.max(map.getZoom(), 16.5),
        speed: 1.2,
        curve: 1.35,
        essential: true
      })
    }
  }

  function revealRow(row: Record<string, unknown>) {
    const index = newestFirst.value.findIndex((item) => rowKey(item) === rowKey(row))
    if (index >= 0) {
      const page = Math.floor(index / pageSize.value) + 1
      if (page !== pageCurrent.value) onPageChange(page)
    }
    void nextTick(() => {
      void nextTick(() => {
        tableRef.value?.setCurrentRow(row)
        const current = tableRef.value?.$el?.querySelector(
          '.el-table__body tr.current-row'
        ) as HTMLElement | undefined
        current?.scrollIntoView({ block: 'nearest' })
      })
    })
  }

  function selectPoint(row: Record<string, unknown>, fly: boolean) {
    if (!lngLatOf(row)) return
    selectedKey.value = rowKey(row)
    applySelectedFilter()
    revealRow(row)
    placePulse(row, fly)
    showPointPopup(row)
  }

  function focusRow(row: Record<string, unknown>) {
    selectPoint(row, true)
  }

  function onMapClick(event: MapMouseEvent) {
    if (!map) return
    const layers = POINT_LAYERS.filter((id) => map!.getLayer(id))
    if (!layers.length) return
    const feature = map.queryRenderedFeatures(event.point, { layers }).find((item) => item.properties?.pointId)
    if (!feature) return
    const row = rowByAddressKey(String(feature.properties?.pointId || ''))
    if (!row) return
    selectPoint(row, false)
  }

  function onMapMouseMove(event: MapMouseEvent) {
    if (!map) return
    const layers = POINT_LAYERS.filter((id) => map!.getLayer(id))
    if (!layers.length) {
      map.getCanvas().style.cursor = ''
      return
    }
    const hit = map.queryRenderedFeatures(event.point, { layers }).some((item) => item.properties?.pointId)
    map.getCanvas().style.cursor = hit ? 'pointer' : ''
  }

  function fitTrack() {
    const pairs = coords()
    if (!map || !pairs.length) return
    const bounds = pairs.reduce(
      (box, pair) => box.extend(pair),
      new maplibregl.LngLatBounds(pairs[0], pairs[0])
    )
    map.fitBounds(bounds, { padding: 48, maxZoom: 16, duration: 400 })
  }

  function applySnFromRoute() {
    const wanted = queryText(route.query, 'sn')
    if (wanted) {
      sn.value = wanted
    }
  }

  async function loadDevices() {
    if (!projectScope.value) return
    const data = await fetchGeotagDevices(projectScope.value)
    devices.value = (Array.isArray(data.items) ? data.items : []) as Array<{ sn: string; assetName?: string }>
    ingestMap((data.map as GeotagMapConfig) || null, true)
    applySnFromRoute()
    if (!sn.value && devices.value[0]) {
      sn.value = devices.value[0].sn
    }
  }

  async function query() {
    if (!projectScope.value || !sn.value) return
    loading.value = true
    try {
      const data = await fetchGeotagTracks(projectScope.value, {
        sn: sn.value,
        from: range.value[0].toISOString(),
        to: range.value[1].toISOString()
      })
      points.value = Array.isArray(data.items) ? (data.items as Record<string, unknown>[]) : []
      if (!rowByKey(selectedKey.value)) {
        selectedKey.value = ''
        clearPulse()
        clearPopup()
      }
      if (data.map) ingestMap(data.map as GeotagMapConfig, false)
      await nextTick()
      drawTrack()
      if (!selectedKey.value) fitTrack()
    } finally {
      loading.value = false
    }
  }

  async function enterPage() {
    if (!projectScope.value || enterInFlight) return
    enterInFlight = true
    skipSnQuery = true
    try {
      await loadDevices()
      if (sn.value) {
        await query()
      }
      map?.resize()
    } finally {
      skipSnQuery = false
      enterInFlight = false
    }
  }

  function initMap() {
    if (map || !mapEl.value) return
    map = new maplibregl.Map({
      container: mapEl.value,
      style: geotagMapStyle(),
      center: [114.0579, 22.5431],
      zoom: 11,
      maxPitch: 85
    })
    styleKey = styleKeyOf(null)
    map.addControl(new maplibregl.NavigationControl({ visualizePitch: true }), 'top-right')
    map.on('load', drawTrack)
    map.on('style.load', () => {
      void nextTick(() => drawTrack())
    })
    map.on('click', onMapClick)
    map.on('mousemove', onMapMouseMove)
    resizeObserver = new ResizeObserver(() => map?.resize())
    resizeObserver.observe(mapEl.value)
  }

  onMounted(() => {
    initMap()
    void enterPage()
  })

  onActivated(() => {
    initMap()
    void enterPage()
  })

  watch(projectScope, () => {
    void enterPage()
  })

  watch([provider, threeD], () => {
    applyBasemap()
  })

  watch(sn, () => {
    selectedKey.value = ''
    clearPulse()
    clearPopup()
    if (skipSnQuery || !sn.value) return
    void query()
  })

  watch(
    () => queryText(route.query, 'sn'),
    (wanted) => {
      if (wanted && wanted !== sn.value) {
        sn.value = wanted
        void query()
      }
    }
  )

  onUnmounted(() => {
    resizeObserver?.disconnect()
    resizeObserver = null
    map?.off('click', onMapClick)
    map?.off('mousemove', onMapMouseMove)
    clearPopup()
    clearPulse()
    map?.remove()
    map = null
  })
</script>

<template>
  <div class="geotag-track">
    <div class="toolbar page-card art-card">
      <ElSelect v-model="sn" :placeholder="t('product.geotag.selectDevice')" filterable style="width: 280px">
        <ElOption
          v-for="item in devices"
          :key="item.sn"
          :label="`${item.assetName || item.sn} · ${item.sn}`"
          :value="item.sn"
        />
      </ElSelect>
      <ElDatePicker
        v-model="range"
        type="datetimerange"
        :start-placeholder="t('product.geotag.from')"
        :end-placeholder="t('product.geotag.to')"
      />
      <ElButton type="primary" :loading="loading" @click="query">{{ t('product.geotag.query') }}</ElButton>
      <span class="count">{{ t('product.geotag.points', { count: points.length }) }}</span>
    </div>
    <div ref="bodyEl" class="body" :class="{ dragging }">
      <div class="map-wrap">
        <BasemapBar v-model:provider="provider" v-model:three-d="threeD" :config="mapConfig" class="basemap" />
        <div ref="mapEl" class="map" />
      </div>
      <button type="button" class="gutter" aria-label="resize" @mousedown="startResize" />
      <div class="table-pane page-card art-card" :style="{ width: `${listWidth}px` }">
        <div class="table-scroll">
          <ElTable
            ref="tableRef"
            v-loading="loading"
            :data="pagedRows"
            :row-key="rowKey"
            :current-row-key="selectedKey || undefined"
            highlight-current-row
            :size="tableSize"
            :stripe="isZebra"
            :border="isBorder"
            class="table"
            @row-click="focusRow"
          >
            <ElTableColumn :label="t('product.geotag.locationTime')" width="148" class-name="track-time-col">
              <template #default="{ row }">{{ formatTrackTime(row.locationTime) }}</template>
            </ElTableColumn>
            <ElTableColumn :label="t('product.geotag.battery')" width="68">
              <template #default="{ row }">{{ row.battery == null ? '—' : `${row.battery}%` }}</template>
            </ElTableColumn>
            <ElTableColumn :label="t('product.geotag.address')" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                {{ locationLabel(row as { address?: string; lat?: number; lng?: number }) }}
              </template>
            </ElTableColumn>
            <template #empty>{{ t('product.geotag.noPoint') }}</template>
          </ElTable>
        </div>
        <div class="pager-wrap">
          <TablePager
            :total="pageTotal"
            :current="pageCurrent"
            :size="pageSize"
            :page-sizes="trackPageSizes"
            compact
            @update:current="onPageChange"
            @update:size="onSizeChange"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
  .geotag-track {
    display: flex;
    flex-direction: column;
    gap: 12px;
    height: var(--art-full-height, calc(100vh - 160px));
    max-height: var(--art-full-height, calc(100vh - 160px));
    min-height: 0;
    padding: 12px;
    overflow: hidden;
  }

  .toolbar {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    align-items: center;
    padding: 12px 16px;
  }

  .count {
    color: var(--el-text-color-secondary);
  }

  .body {
    display: flex;
    min-height: 0;
    flex: 1;
    overflow: hidden;
  }

  .body.dragging {
    cursor: col-resize;
    user-select: none;
  }

  .gutter {
    width: 10px;
    flex-shrink: 0;
    padding: 0;
    border: 0;
    background: transparent;
    cursor: col-resize;
  }

  .gutter::after {
    content: '';
    display: block;
    width: 3px;
    height: 48px;
    margin: calc(50% - 24px) auto 0;
    border-radius: 999px;
    background: var(--el-border-color);
  }

  .gutter:hover::after,
  .body.dragging .gutter::after {
    background: var(--el-color-primary);
  }

  .map-wrap {
    position: relative;
    min-width: 240px;
    min-height: 0;
    flex: 1;
    height: 100%;
    border-radius: 8px;
    overflow: hidden;
  }

  .basemap {
    position: absolute;
    z-index: 2;
    top: 12px;
    left: 12px;
  }

  .table-pane {
    display: flex;
    flex-direction: column;
    flex-shrink: 0;
    min-height: 0;
    height: 100%;
    min-width: 0;
    padding: 12px;
    overflow: hidden;
  }

  .table-scroll {
    flex: 1 1 0%;
    height: 0;
    min-height: 0;
    overflow: auto;
    overscroll-behavior: contain;
  }

  .table-scroll :deep(.el-table__header-wrapper) {
    position: sticky;
    top: 0;
    z-index: 2;
  }

  .pager-wrap {
    flex: 0 0 auto;
    min-height: 40px;
  }

  .map,
  .table {
    min-height: 0;
  }

  .map {
    width: 100%;
    height: 100%;
  }

  :deep(.table-pager) {
    margin-top: 8px;
  }

  :deep(.el-table__body tr) {
    cursor: pointer;
  }

  :deep(.track-time-col .cell) {
    overflow: visible;
    white-space: nowrap;
  }
</style>

<style>
  .geotag-pulse-wrap {
    width: 18px;
    height: 18px;
    pointer-events: none;
  }

  .geotag-pulse {
    position: relative;
    width: 18px;
    height: 18px;
    border-radius: 50%;
    background: #2563eb;
    box-shadow: 0 0 0 3px #fff;
    pointer-events: none;
  }

  .geotag-pulse--beat {
    animation: geotag-pulse-beat 1.15s ease-out 2;
  }

  .geotag-pulse--beat::after {
    content: '';
    position: absolute;
    inset: -4px;
    border-radius: 50%;
    border: 3px solid #2563eb;
    animation: geotag-pulse-ring 1.15s ease-out 2;
  }

  @keyframes geotag-pulse-beat {
    0% {
      transform: scale(1);
    }
    40% {
      transform: scale(1.55);
    }
    100% {
      transform: scale(1);
    }
  }

  .geotag-track-popup {
    font-size: 13px;
    line-height: 1.5;
    color: #1f2937;
  }

  .geotag-track-popup strong {
    display: block;
    margin-bottom: 6px;
    font-size: 14px;
  }

  .geotag-track-popup p {
    margin: 0 0 4px;
  }

  @keyframes geotag-pulse-ring {
    0% {
      transform: scale(0.7);
      opacity: 0.9;
    }
    100% {
      transform: scale(2.4);
      opacity: 0;
    }
  }
</style>
