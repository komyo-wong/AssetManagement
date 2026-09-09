<script setup lang="ts">
  import { computed, nextTick, onActivated, onDeactivated, onMounted, onUnmounted, ref, watch } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { storeToRefs } from 'pinia'
  import maplibregl from 'maplibre-gl'
  import 'maplibre-gl/dist/maplibre-gl.css'
  import { fetchGeotagPositions } from '@/api/asset-platform'
  import { useTenantContextStore } from '@/store/modules/tenant-context'
  import { formatDateTime } from '@/utils/datetime'
  import {
    applyTerrain,
    geotagMapStyle,
    locationLabel,
    optionById,
    providerLabelKey,
    remembered3d,
    rememberedProvider,
    styleKeyOf,
    usableOption,
    type GeotagDevicePoint,
    type GeotagMapConfig
  } from '../map-style'
  import BasemapBar from '../basemap-bar.vue'

  defineOptions({ name: 'GeotagMap' })

  const { t } = useI18n()
  const { projectScope } = storeToRefs(useTenantContextStore())
  const loading = ref(false)
  const items = ref<GeotagDevicePoint[]>([])
  const selected = ref('')
  const typeFilter = ref('')
  const mock = ref(false)
  const cloudSyncError = ref('')
  const mapConfig = ref<GeotagMapConfig | null>(null)
  const provider = ref('osm')
  const threeD = ref(false)
  const mapEl = ref<HTMLElement | null>(null)
  const listEl = ref<HTMLElement | null>(null)
  let map: maplibregl.Map | null = null
  let markers: maplibregl.Marker[] = []
  const markerBySn = new Map<string, maplibregl.Marker>()
  let infoPopup: maplibregl.Popup | null = null
  let timer: ReturnType<typeof setInterval> | null = null
  let fitted = false
  let styleKey = ''
  let loadInFlight = false

  function escapeHtml(value: unknown) {
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
  }

  function popupHtml(item: GeotagDevicePoint) {
    const battery = item.battery == null ? '—' : `${item.battery}%`
    return `<div class="geotag-map-popup">
      <strong>${escapeHtml(item.assetName || item.sn)}</strong>
      <p>${escapeHtml(t('product.geotag.sn'))}：${escapeHtml(item.sn)}</p>
      <p>${escapeHtml(t('product.geotag.assetType'))}：${escapeHtml(item.assetTypeName || t('product.geotag.unclassified'))}</p>
      <p>${escapeHtml(t('product.geotag.locationTime'))}：${escapeHtml(formatDateTime(item.locationTime))}</p>
      <p>${escapeHtml(t('product.geotag.address'))}：${escapeHtml(locationLabel(item) || '—')}</p>
      <p>${escapeHtml(t('product.geotag.battery'))}：${escapeHtml(battery)}</p>
    </div>`
  }

  const typeOptions = computed(() => {
    const names = new Map<string, string>()
    for (const item of items.value) {
      const id = item.assetTypeId || '__none__'
      names.set(id, item.assetTypeName || t('product.geotag.unclassified'))
    }
    return [...names.entries()].map(([id, name]) => ({ id, name }))
  })

  const filtered = computed(() => {
    if (!typeFilter.value) return items.value
    if (typeFilter.value === '__none__') {
      return items.value.filter((item) => !item.assetTypeId)
    }
    return items.value.filter((item) => item.assetTypeId === typeFilter.value)
  })

  function hasPoint(item: GeotagDevicePoint) {
    return typeof item.lat === 'number' && typeof item.lng === 'number'
  }

  function activeOption() {
    return optionById(mapConfig.value, provider.value) || mapConfig.value
  }

  function applyBasemap() {
    if (!map) return
    const option = activeOption()
    const key = styleKeyOf(option)
    if (key !== styleKey) {
      styleKey = key
      map.setStyle(geotagMapStyle(option))
      map.once('style.load', () => {
        applyTerrain(map, option, threeD.value)
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

  function clearPopup() {
    infoPopup?.remove()
    infoPopup = null
  }

  function clearSelection() {
    selected.value = ''
    paintSelection()
    clearPopup()
  }

  function showPopup(item: GeotagDevicePoint) {
    if (!map || !hasPoint(item)) {
      clearPopup()
      return
    }
    if (!infoPopup) {
      infoPopup = new maplibregl.Popup({
        offset: 16,
        closeButton: true,
        maxWidth: '280px',
        className: 'geotag-map-popup-wrap'
      })
    }
    infoPopup.setLngLat([item.lng as number, item.lat as number]).setHTML(popupHtml(item)).addTo(map)
  }

  function paintSelection() {
    markerBySn.forEach((marker, sn) => {
      marker.getElement().classList.toggle('is-selected', sn === selected.value)
    })
  }

  function scrollToRow(sn: string) {
    void nextTick(() => {
      const root = listEl.value
      if (!root || !sn) return
      const row = root.querySelector(`[data-sn="${CSS.escape(sn)}"]`) as HTMLElement | null
      row?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
    })
  }

  function selectItem(item: GeotagDevicePoint, fly: boolean) {
    selected.value = item.sn
    paintSelection()
    if (hasPoint(item)) {
      showPopup(item)
      if (fly && map) {
        map.flyTo({
          center: [item.lng as number, item.lat as number],
          zoom: Math.max(map.getZoom(), 15),
          speed: 1.2,
          essential: true
        })
      }
    } else {
      clearPopup()
    }
    scrollToRow(item.sn)
  }

  function renderMarkers(fit = false) {
    markers.forEach((marker) => marker.remove())
    markers = []
    markerBySn.clear()
    if (!map) return
    const bounds = new maplibregl.LngLatBounds()
    let any = false
    for (const item of filtered.value) {
      if (!hasPoint(item)) continue
      const marker = new maplibregl.Marker({ color: '#0f766e' })
        .setLngLat([item.lng as number, item.lat as number])
        .addTo(map)
      marker.getElement().addEventListener('click', (event) => {
        event.stopPropagation()
        selectItem(item, false)
      })
      markers.push(marker)
      markerBySn.set(item.sn, marker)
      bounds.extend([item.lng as number, item.lat as number])
      any = true
    }
    paintSelection()
    const current = filtered.value.find((item) => item.sn === selected.value)
    if (current && hasPoint(current)) {
      showPopup(current)
    } else if (!current) {
      clearPopup()
    }
    if (any && (fit || !fitted)) {
      map.fitBounds(bounds, { padding: 64, maxZoom: 15, duration: 400 })
      fitted = true
    }
  }

  async function load(fit = false) {
    if (!projectScope.value || loadInFlight) return
    loadInFlight = true
    loading.value = true
    try {
      const data = await fetchGeotagPositions(projectScope.value)
      items.value = (Array.isArray(data.items) ? data.items : []) as GeotagDevicePoint[]
      mock.value = !!data.mock
      cloudSyncError.value = String(data.cloudSyncError || '')
      const first = !mapConfig.value
      ingestMap((data.map as GeotagMapConfig) || null, first)
      if (selected.value && !filtered.value.some((item) => item.sn === selected.value)) {
        selected.value = ''
      }
      renderMarkers(fit)
    } finally {
      loadInFlight = false
      loading.value = false
    }
  }

  function focus(item: GeotagDevicePoint) {
    selectItem(item, true)
  }

  function clearTimer() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  function setupPolling() {
    clearTimer()
    timer = setInterval(() => {
      if (document.visibilityState === 'hidden') return
      void load(false)
    }, 15000)
  }

  onMounted(() => {
    if (mapEl.value) {
      map = new maplibregl.Map({
        container: mapEl.value,
        style: geotagMapStyle(),
        center: [114.0579, 22.5431],
        zoom: 11,
        maxPitch: 85
      })
      styleKey = styleKeyOf(null)
      map.addControl(new maplibregl.NavigationControl({ visualizePitch: true }), 'top-right')
    }
    void load(true)
  })

  onActivated(() => {
    clearSelection()
    map?.resize()
    void load(true)
    setupPolling()
  })

  watch(projectScope, () => {
    void load(true)
  })

  watch(typeFilter, () => {
    if (selected.value && !filtered.value.some((item) => item.sn === selected.value)) {
      selected.value = ''
    }
    fitted = false
    renderMarkers(true)
  })

  watch([provider, threeD], () => {
    applyBasemap()
  })

  onDeactivated(() => {
    clearTimer()
  })

  onUnmounted(() => {
    clearTimer()
    markers.forEach((marker) => marker.remove())
    markerBySn.clear()
    clearPopup()
    map?.remove()
    map = null
  })
</script>

<template>
  <div class="geotag-map-page">
    <aside ref="listEl" class="list">
      <div class="list-head">
        <h2>{{ t('menus.geotag.map') }}</h2>
        <ElButton size="small" :loading="loading" @click="load(false)">{{ t('product.geotag.refresh') }}</ElButton>
      </div>
      <ElSelect v-model="typeFilter" clearable :placeholder="t('product.geotag.allTypes')" class="type-filter">
        <ElOption v-for="option in typeOptions" :key="option.id" :label="option.name" :value="option.id" />
      </ElSelect>
      <p v-if="mapConfig?.fallback && mapConfig.needsKey" class="warn">
        {{ t('product.geotag.mapKeyMissing', { provider: t(providerLabelKey(mapConfig.requestedProvider)) }) }}
        <a v-if="mapConfig.applyUrl" :href="mapConfig.applyUrl" target="_blank" rel="noreferrer">
          {{ t('product.geotag.mapKeyApply') }}
        </a>
      </p>
      <p v-if="mock" class="hint">{{ t('product.geotag.mock') }}</p>
      <p v-if="cloudSyncError" class="warn">{{ t('product.geotag.cloudSyncError') }}</p>
      <p v-if="!filtered.length" class="hint">{{ t('product.geotag.empty') }}</p>
      <button
        v-for="item in filtered"
        :key="item.sn"
        :data-sn="item.sn"
        type="button"
        class="row"
        :class="{ active: selected === item.sn }"
        @click="focus(item)"
      >
        <strong>{{ item.assetName || item.sn }}</strong>
        <span>{{ item.sn }}</span>
        <span v-if="item.assetTypeName">{{ item.assetTypeName }}</span>
        <span v-if="hasPoint(item)">
          {{ formatDateTime(item.locationTime) }} · {{ item.battery ?? '—' }}%
        </span>
        <span v-if="hasPoint(item)" class="addr">{{ locationLabel(item) }}</span>
        <span v-else>{{ t('product.geotag.noPoint') }}</span>
      </button>
    </aside>
    <div class="map-wrap">
      <BasemapBar v-model:provider="provider" v-model:three-d="threeD" :config="mapConfig" class="basemap" />
      <div ref="mapEl" class="map" />
    </div>
  </div>
</template>

<style scoped>
  .geotag-map-page {
    display: flex;
    height: calc(100vh - 120px);
    min-height: 480px;
    background: var(--el-bg-color);
  }

  .list {
    width: 320px;
    flex-shrink: 0;
    overflow: auto;
    padding: 16px;
    border-right: 1px solid var(--el-border-color);
  }

  .list-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
  }

  .list-head h2 {
    margin: 0;
    font-size: 16px;
  }

  .type-filter {
    width: 100%;
    margin-bottom: 12px;
  }

  .hint,
  .warn {
    color: var(--el-text-color-secondary);
    font-size: 13px;
    margin: 0 0 12px;
    line-height: 1.5;
  }

  .warn {
    color: var(--el-color-warning);
  }

  .warn a {
    color: var(--el-color-primary);
    margin-left: 4px;
  }

  .row {
    display: flex;
    flex-direction: column;
    gap: 2px;
    width: 100%;
    text-align: left;
    padding: 10px;
    margin-bottom: 8px;
    border: 1px solid var(--el-border-color);
    border-radius: 8px;
    background: transparent;
    cursor: pointer;
  }

  .row.active {
    border-color: var(--el-color-primary);
  }

  .row span {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }

  .row .addr {
    color: var(--el-text-color-regular);
    line-height: 1.4;
  }

  .map-wrap {
    position: relative;
    flex: 1;
    min-width: 0;
  }

  .basemap {
    position: absolute;
    z-index: 2;
    top: 12px;
    left: 12px;
  }

  .map {
    width: 100%;
    height: 100%;
  }

  :deep(.maplibregl-marker.is-selected) {
    z-index: 2;
  }

  :deep(.maplibregl-marker.is-selected svg) {
    filter: drop-shadow(0 0 6px rgba(37, 99, 235, 0.7));
    transform: scale(1.18);
    transform-origin: bottom center;
  }
</style>
