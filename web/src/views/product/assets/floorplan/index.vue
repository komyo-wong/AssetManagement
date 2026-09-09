<script setup lang="ts">
  import { computed, onActivated, onUnmounted, ref, watch } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { useRoute } from 'vue-router'
  import { storeToRefs } from 'pinia'
  import { useTenantContextStore } from '@/store/modules/tenant-context'
  import { assetsApi, gatewaysApi, mapsApi, type NamedResource } from '@/api/asset-platform'
  import { formatDateTime } from '@/utils/datetime'
  import { formatApproxMeters, formatLiveLocation } from '@/utils/locale-labels'
  import { queryText } from '@/utils/route-query'
  import PresencePill from '@/components/business/PresencePill.vue'
  import FloorPlanStage, { type FloorMarker } from '@/components/business/FloorPlanStage.vue'

  defineOptions({ name: 'AssetFloorplan' })

  const { t } = useI18n()
  const route = useRoute()
  const { projectScope } = storeToRefs(useTenantContextStore())
  const loading = ref(false)
  const maps = ref<NamedResource[]>([])
  const gateways = ref<NamedResource[]>([])
  const assets = ref<NamedResource[]>([])
  const selectedMapId = ref('')
  const selectedId = ref<string | null>(null)
  const lastRefreshedAt = ref<Date | null>(null)
  let timer: ReturnType<typeof setInterval> | null = null

  const selectedMap = computed(() => maps.value.find((m) => m.id === selectedMapId.value) || null)

  const mapGateways = computed(() =>
    gateways.value.filter(
      (g) =>
        String(g.fields?.mapId || '') === selectedMapId.value &&
        g.fields?.coordinateX != null &&
        g.fields?.coordinateY != null
    )
  )

  const mapAssets = computed(() =>
    assets.value.filter((a) => {
      if (String(a.fields?.lastMapId || '') !== selectedMapId.value) return false
      if (a.fields?.lastGatewayCoordinateX == null || a.fields?.lastGatewayCoordinateY == null) return false
      return a.status === 'ONLINE' || a.status === 'OFFLINE'
    })
  )

  const unlocatedAssets = computed(() =>
    assets.value.filter((a) => {
      if (a.status === 'UNBOUND' || a.status === 'ARCHIVED') return false
      if (!selectedMapId.value) return false
      const onMap = String(a.fields?.lastMapId || '') === selectedMapId.value
      if (!onMap) return false
      return a.fields?.lastGatewayCoordinateX == null || a.fields?.lastGatewayCoordinateY == null
    })
  )

  const markers = computed<FloorMarker[]>(() => {
    const gwMarkers: FloorMarker[] = mapGateways.value.map((g) => ({
      id: `gw:${g.id}`,
      kind: 'gateway',
      label: g.name,
      x: Number(g.fields?.coordinateX),
      y: Number(g.fields?.coordinateY),
      status: g.status
    }))

    // 资产落在最近网关坐标；重叠点由 FloorPlanStage 做像素级微错开
    const assetMarkers: FloorMarker[] = mapAssets.value.map((a) => {
      const distanceLabel = formatApproxMeters(a.fields?.estimatedDistanceMeters, t)
      return {
        id: `asset:${a.id}`,
        kind: 'asset' as const,
        label: a.name,
        subLabel: distanceLabel || undefined,
        x: Number(a.fields?.lastGatewayCoordinateX),
        y: Number(a.fields?.lastGatewayCoordinateY),
        status: a.status
      }
    })
    return [...gwMarkers, ...assetMarkers]
  })

  const selectedAsset = computed(() => {
    if (!selectedId.value?.startsWith('asset:')) return null
    const id = selectedId.value.slice('asset:'.length)
    return assets.value.find((a) => a.id === id) || null
  })

  const selectedGateway = computed(() => {
    if (!selectedId.value?.startsWith('gw:')) return null
    const id = selectedId.value.slice('gw:'.length)
    return gateways.value.find((g) => g.id === id) || null
  })

  async function load(silent = false) {
    if (!projectScope.value) {
      maps.value = []
      gateways.value = []
      assets.value = []
      return
    }
    if (!silent) loading.value = true
    try {
      const [mapRows, gwRows, assetRows] = await Promise.all([
        mapsApi.list(projectScope.value),
        gatewaysApi.list(projectScope.value),
        assetsApi.list(projectScope.value)
      ])
      maps.value = mapRows
      gateways.value = gwRows
      assets.value = assetRows
      if (!selectedMapId.value && mapRows.length) {
        selectedMapId.value = mapRows[0].id
      } else if (selectedMapId.value && !mapRows.some((m) => m.id === selectedMapId.value)) {
        selectedMapId.value = mapRows[0]?.id || ''
      }
      lastRefreshedAt.value = new Date()
      if (!silent) applySelection()
    } catch {
      if (!silent) {
        maps.value = []
        gateways.value = []
        assets.value = []
      }
    } finally {
      if (!silent) loading.value = false
    }
  }

  function setupPolling() {
    if (timer) clearInterval(timer)
    timer = setInterval(() => {
      if (document.visibilityState === 'hidden') return
      void load(true)
    }, 5000)
  }

  function onSelectMarker(id: string) {
    selectedId.value = id
  }

  function applySelection() {
    const id = queryText(route.query, 'id')
    if (!id) {
      selectedId.value = null
      return
    }
    const kind = queryText(route.query, 'kind') === 'gateway' ? 'gateway' : 'asset'
    if (kind === 'gateway') {
      const gw = gateways.value.find((g) => g.id === id)
      if (!gw) return
      const mapId = String(gw.fields?.mapId || '')
      if (mapId) selectedMapId.value = mapId
      selectedId.value = `gw:${id}`
      return
    }
    const asset = assets.value.find((a) => a.id === id)
    if (!asset) return
    const mapId = String(asset.fields?.lastMapId || '')
    if (mapId) selectedMapId.value = mapId
    selectedId.value = `asset:${id}`
  }

  watch(
    projectScope,
    () => {
      selectedMapId.value = ''
      selectedId.value = null
      void load()
      setupPolling()
    },
    { immediate: true }
  )
  watch(
    () => route.query,
    () => applySelection()
  )
  onActivated(() => applySelection())
  onUnmounted(() => {
    if (timer) clearInterval(timer)
  })
</script>

<template>
  <div class="page-card art-card" v-loading="loading">
    <div class="header">
      <div>
        <h2>{{ t('product.assets.floorplan.title') }}</h2>
        <p>
          {{ t('product.assets.floorplan.subtitle') }}
          <span v-if="lastRefreshedAt"> · {{ t('product.assets.floorplan.lastRefresh', { time: formatDateTime(lastRefreshedAt) }) }}</span>
        </p>
      </div>
      <div class="header-actions">
        <ElSelect
          v-model="selectedMapId"
          :placeholder="t('product.assets.floorplan.selectMap')"
          style="width: 260px"
          :disabled="!maps.length"
          filterable
        >
          <ElOption
            v-for="m in maps"
            :key="m.id"
            :label="`${m.name} (${m.code})`"
            :value="m.id"
          />
        </ElSelect>
        <ElButton @click="load()">{{ t('common.refresh') }}</ElButton>
      </div>
    </div>
    <ElAlert
      v-if="!maps.length"
      type="info"
      :closable="false"
      :title="t('product.assets.floorplan.noMap')"
    />

    <div v-else class="layout">
      <div class="canvas">
        <FloorPlanStage
          :image-url="(selectedMap?.fields?.imageUrl as string | null) || null"
          :width-meters="Number(selectedMap?.fields?.widthMeters) || null"
          :height-meters="Number(selectedMap?.fields?.heightMeters) || null"
          :markers="markers"
          :selected-id="selectedId"
          @select="onSelectMarker"
        />
        <div class="legend">
          <span><i class="gw" />{{ t('product.assets.floorplan.legendGateway') }}</span>
          <span><i class="asset" />{{ t('product.assets.floorplan.legendAsset') }}</span>
        </div>
      </div>

      <aside class="side">
        <section v-if="selectedAsset" class="panel">
          <h3>{{ t('product.assets.floorplan.asset') }}</h3>
          <div class="kv"><span>{{ t('common.name') }}</span><b>{{ selectedAsset.name }}</b></div>
          <div class="kv"><span>{{ t('common.code') }}</span><b>{{ selectedAsset.code }}</b></div>
          <div class="kv">
            <span>{{ t('common.status') }}</span>
            <PresencePill :status="selectedAsset.status" />
          </div>
          <div class="kv"><span>{{ t('product.assets.floorplan.liveLocation') }}</span><b>{{ formatLiveLocation(selectedAsset.fields, t) || '-' }}</b></div>
          <div class="kv"><span>{{ t('product.assets.floorplan.distance') }}</span><b>{{ formatApproxMeters(selectedAsset.fields?.estimatedDistanceMeters, t) || '-' }}</b></div>
          <div class="kv"><span>{{ t('product.assets.floorplan.signal') }}</span><b>{{ selectedAsset.fields?.lastRssi != null ? `${selectedAsset.fields.lastRssi} dBm` : '-' }}</b></div>
          <div class="kv"><span>{{ t('product.assets.floorplan.zone') }}</span><b>{{ selectedAsset.fields?.lastZoneName || '-' }}</b></div>
          <div class="kv"><span>{{ t('product.assets.floorplan.lastGateway') }}</span><b>{{ selectedAsset.fields?.lastGatewayName || '-' }}</b></div>
          <div class="kv">
            <span>{{ t('product.assets.floorplan.lastSeen') }}</span>
            <b>{{ formatDateTime(selectedAsset.fields?.lastSeenAt as string | null | undefined) }}</b>
          </div>
        </section>

        <section v-else-if="selectedGateway" class="panel">
          <h3>{{ t('product.assets.floorplan.gateway') }}</h3>
          <div class="kv"><span>{{ t('common.name') }}</span><b>{{ selectedGateway.name }}</b></div>
          <div class="kv"><span>{{ t('common.code') }}</span><b>{{ selectedGateway.code }}</b></div>
          <div class="kv">
            <span>{{ t('common.status') }}</span>
            <PresencePill :status="selectedGateway.status" />
          </div>
          <div class="kv"><span>{{ t('product.assets.floorplan.zone') }}</span><b>{{ selectedGateway.fields?.zoneName || '-' }}</b></div>
          <div class="kv">
            <span>{{ t('product.assets.floorplan.installPoint') }}</span>
            <b>
              {{ selectedGateway.fields?.coordinateX }}, {{ selectedGateway.fields?.coordinateY }} m
            </b>
          </div>
        </section>

        <section v-else class="panel muted">
          <h3>{{ t('product.assets.floorplan.detail') }}</h3>
          <p>{{ t('product.assets.floorplan.detailHint') }}</p>
        </section>

        <section class="panel">
          <h3>{{ t('product.assets.floorplan.mapAssets', { count: mapAssets.length }) }}</h3>
          <ElTable :data="mapAssets" size="small" max-height="280" @row-click="(row) => (selectedId = `asset:${row.id}`)">
            <ElTableColumn prop="name" :label="t('common.name')" min-width="90" show-overflow-tooltip />
            <ElTableColumn :label="t('common.status')" width="72">
              <template #default="{ row }">
                <PresencePill :status="row.status" />
              </template>
            </ElTableColumn>
            <ElTableColumn :label="t('product.assets.floorplan.distance')" width="78">
              <template #default="{ row }">{{ formatApproxMeters(row.fields?.estimatedDistanceMeters, t) || '-' }}</template>
            </ElTableColumn>
            <ElTableColumn :label="t('product.assets.floorplan.gateway')" min-width="80" show-overflow-tooltip>
              <template #default="{ row }">{{ row.fields?.lastGatewayName || '-' }}</template>
            </ElTableColumn>
          </ElTable>
        </section>

        <section v-if="unlocatedAssets.length" class="panel">
          <h3>{{ t('product.assets.floorplan.unlocated', { count: unlocatedAssets.length }) }}</h3>
          <p class="hint">{{ t('product.assets.floorplan.unlocatedHint') }}</p>
          <ul class="list">
            <li v-for="a in unlocatedAssets" :key="a.id">
              {{ a.name }} · {{ a.fields?.lastGatewayName || t('product.assets.floorplan.noGateway') }}
            </li>
          </ul>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
  .page-card {
    padding: 20px;
  }
  .header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 16px;
    margin-bottom: 16px;
  }
  .header-actions {
    display: flex;
    gap: 8px;
    align-items: center;
  }
  h2 {
    margin: 0 0 4px;
  }
  p {
    margin: 0;
    color: var(--el-text-color-secondary);
  }
  .layout {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 320px;
    gap: 16px;
    min-height: 520px;
  }
  .canvas {
    min-width: 0;
  }
  .legend {
    display: flex;
    gap: 16px;
    margin-top: 8px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
  .legend i {
    display: inline-block;
    width: 10px;
    height: 10px;
    border-radius: 50%;
    margin-right: 6px;
  }
  .legend .gw {
    background: #2563eb;
  }
  .legend .asset {
    background: #059669;
  }
  .side {
    display: flex;
    flex-direction: column;
    gap: 12px;
    min-width: 0;
  }
  .panel {
    border: 1px solid var(--el-border-color-lighter);
    padding: 12px;
    background: var(--el-bg-color);
  }
  .panel.muted p {
    margin-top: 8px;
  }
  .panel h3 {
    margin: 0 0 10px;
    font-size: 14px;
  }
  .kv {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    padding: 6px 0;
    font-size: 13px;
    border-bottom: 1px solid var(--el-border-color-extra-light);
  }
  .kv span {
    color: var(--el-text-color-secondary);
    flex-shrink: 0;
  }
  .kv b {
    font-weight: 500;
    text-align: right;
    word-break: break-all;
  }
  .hint {
    margin: 0 0 8px;
    font-size: 12px;
  }
  .list {
    margin: 0;
    padding-left: 18px;
    font-size: 13px;
    color: var(--el-text-color-regular);
  }
  @media (max-width: 960px) {
    .layout {
      grid-template-columns: 1fr;
    }
  }
</style>
