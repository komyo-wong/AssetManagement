<script setup lang="ts">
  import { computed, onActivated, onUnmounted, ref, watch } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { useRoute } from 'vue-router'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { storeToRefs } from 'pinia'
  import { useTenantContextStore } from '@/store/modules/tenant-context'
  import {
    gatewaysApi,
    mapsApi,
    zonesApi,
    type NamedResource,
    type ResourceUpsert
  } from '@/api/asset-platform'
  import { formatDateTime } from '@/utils/datetime'
  import { queryText } from '@/utils/route-query'
  import PresencePill from '@/components/business/PresencePill.vue'
  import FloorPlanPicker from '@/components/business/FloorPlanPicker.vue'
  import TablePager from '@/components/business/TablePager.vue'
  import ProductTableHeader from '@/components/business/ProductTableHeader.vue'
  import { useClientPagination } from '@/composables/useClientPagination'
  import { useProductTable } from '@/composables/useProductTable'
  import { useTableColumns } from '@/hooks/core/useTableColumns'
  import type { ColumnOption } from '@/types/component'

  defineOptions({ name: 'GatewayManagement' })

  const { t } = useI18n()
  const route = useRoute()

  const { projectScope } = storeToRefs(useTenantContextStore())
  const { tableSize, isZebra, isBorder } = useProductTable()
  const loading = ref(false)
  const rows = ref<NamedResource[]>([])
  const zones = ref<NamedResource[]>([])
  const maps = ref<NamedResource[]>([])
  const keyword = ref('')
  const createVisible = ref(false)
  const editVisible = ref(false)
  const editing = ref<NamedResource | null>(null)
  const form = ref<ResourceUpsert>({
    code: '',
    name: '',
    description: '',
    fields: { zoneId: null, coordinateX: null, coordinateY: null, rssiAt1m: -61 }
  })
  const provisionVisible = ref(false)
  const provisionGateway = ref<NamedResource | null>(null)
  const provisionEntries = ref<Array<{ key: string; value: string }>>([])
  const provisionForm = ref({ username: '', password: '' })
  const provisionSaving = ref(false)
  let timer: ReturnType<typeof setInterval> | null = null

  const { columnChecks, resetColumns } = useTableColumns(() => {
    const cols: ColumnOption[] = [
      { prop: 'code', label: t('common.code'), checked: true },
      { prop: 'name', label: t('common.name'), checked: true },
      { prop: 'mac', label: 'MAC', checked: true },
      { prop: 'zone', label: t('product.assets.status.colZone'), checked: true },
      { prop: 'map', label: t('product.assets.status.colMap'), checked: true },
      { prop: 'install', label: t('product.devices.gateways.colInstall'), checked: true },
      { prop: 'status', label: t('common.status'), checked: true },
      { prop: 'lastSeenAt', label: t('product.assets.list.colLastSeen'), checked: true },
      { prop: '__actions', label: t('common.actions'), width: 180, fixed: 'right', checked: true, disabled: true }
    ]
    return cols
  })
  watch(() => t('common.actions'), () => resetColumns())
  function gwColVisible(prop: string) {
    return columnChecks.value.some((c) => c.prop === prop && (c.checked ?? c.visible ?? true))
  }

  const provisionKeysNative = [
    'brokerUri',
    'brokerHost',
    'brokerPort',
    'tlsEnabled',
    'clientId',
    'uplinkTopic',
    'downlinkTopic',
    'statusTopic',
    'keepaliveSeconds',
    'qos',
    'instructions'
  ] as const
  const provisionKeysHcbg = [
    'host',
    'port',
    'usr',
    'pw',
    'clientId',
    'pub',
    'sub',
    'qos',
    'dataMode',
    'instructions'
  ] as const

  const zoneOptions = computed(() =>
    zones.value.map((z) => ({
      label: z.fields?.mapName ? `${z.name} (${z.fields.mapName})` : z.name,
      value: z.id
    }))
  )

  const selectedMap = computed(() => {
    const zoneId = form.value.fields?.zoneId as string | null | undefined
    if (!zoneId) return null
    const zone = zones.value.find((z) => z.id === zoneId)
    const mapId = zone?.fields?.mapId as string | null | undefined
    if (!mapId) return null
    return maps.value.find((m) => m.id === mapId) || null
  })

  const filteredRows = computed(() => {
    const q = keyword.value.trim().toLowerCase()
    if (!q) return rows.value
    return rows.value.filter((row) => {
      const hay = [
        row.code,
        row.name,
        row.status,
        row.fields?.macAddress,
        row.fields?.clientId,
        row.fields?.vendor,
        row.fields?.zoneName,
        row.fields?.mapName
      ]
        .filter((v) => v != null && v !== '')
        .map((v) => String(v).toLowerCase())
      return hay.some((v) => v.includes(q))
    })
  })

  const {
    current: pageCurrent,
    size: pageSize,
    total: pageTotal,
    pagedRows,
    onPageChange,
    onSizeChange
  } = useClientPagination(filteredRows, { resetOn: keyword })

  async function loadZones() {
    if (!projectScope.value) {
      zones.value = []
      return
    }
    try {
      zones.value = await zonesApi.list(projectScope.value)
    } catch {
      zones.value = []
    }
  }

  async function loadMaps() {
    if (!projectScope.value) {
      maps.value = []
      return
    }
    try {
      maps.value = await mapsApi.list(projectScope.value)
    } catch {
      maps.value = []
    }
  }

  async function load(silent = false) {
    if (!projectScope.value) {
      rows.value = []
      return
    }
    if (!silent) loading.value = true
    try {
      rows.value = await gatewaysApi.list(projectScope.value)
    } catch {
      if (!silent) rows.value = []
    } finally {
      if (!silent) loading.value = false
    }
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
      void load(true)
    }, 5000)
  }

  function nextGatewayCodeFromRows() {
    const used = new Set<number>()
    for (const row of rows.value) {
      const raw = String(row.code || '').trim()
      if (!/^\d{1,4}$/.test(raw)) continue
      used.add(Number.parseInt(raw, 10))
    }
    for (let i = 1; i <= 9999; i += 1) {
      if (!used.has(i)) return String(i).padStart(4, '0')
    }
    return '0001'
  }

  async function nextGatewayCode(): Promise<string> {
    if (projectScope.value) {
      try {
        const data = await gatewaysApi.nextCode(projectScope.value)
        const code = String(data?.code || '').trim()
        if (/^\d{4}$/.test(code)) return code
      } catch {
        // fall back to the loaded list
      }
    }
    return nextGatewayCodeFromRows()
  }

  async function openCreate() {
    editing.value = null
    const code = await nextGatewayCode()
    form.value = {
      code,
      name: '',
      description: '',
      fields: { zoneId: null, coordinateX: null, coordinateY: null, rssiAt1m: -61, macAddress: '', clientId: '', vendor: 'NATIVE' }
    }
    void loadZones()
    void loadMaps()
    createVisible.value = true
  }

  function openEdit(row: NamedResource) {
    editing.value = row
    form.value = {
      code: row.code,
      name: row.name,
      description: '',
      fields: {
        macAddress: row.fields?.macAddress || '',
        clientId: row.fields?.clientId || '',
        zoneId: (row.fields?.zoneId as string | null | undefined) || null,
        coordinateX: row.fields?.coordinateX ?? null,
        coordinateY: row.fields?.coordinateY ?? null,
        rssiAt1m: row.fields?.rssiAt1m ?? -61,
        vendor: String(row.fields?.vendor || 'NATIVE')
      }
    }
    void loadZones()
    void loadMaps()
    editVisible.value = true
  }

  function onZoneChange(zoneId: string | null) {
    form.value.fields = {
      ...form.value.fields,
      zoneId: zoneId || null,
      // 换区域时清空旧坐标，避免落到错误地图
      coordinateX: null,
      coordinateY: null
    }
  }

  function isHcbgVendor(value: unknown) {
    return String(value || '').toUpperCase() === 'HCBG'
  }

  const hcbgChecked = computed({
    get: () => isHcbgVendor(form.value.fields?.vendor),
    set: (checked: boolean) => {
      form.value = {
        ...form.value,
        fields: {
          ...form.value.fields,
          vendor: checked ? 'HCBG' : 'NATIVE'
        }
      }
    }
  })

  function looksLikeMac(value: string) {
    return /^[0-9a-fA-F]{12}$/.test(value.replace(/[:\-.\s]/g, ''))
  }

  function showProvision(source: Record<string, unknown>, gateway: NamedResource | null) {
    provisionGateway.value = gateway
    provisionForm.value = {
      username: String(source.usr || source.username || ''),
      password: String(source.pw || source.password || '')
    }
    const keys = isHcbgVendor(source.vendor) ? provisionKeysHcbg : provisionKeysNative
    provisionEntries.value = keys
      .filter((key) => source[key] !== undefined && source[key] !== null && source[key] !== '')
      .map((key) => ({ key, value: String(source[key]) }))
    provisionVisible.value = true
  }

  async function createGateway() {
    if (!projectScope.value) return
    const code = form.value.code?.trim() || ''
    if (!/^\d{4}$/.test(code)) {
      ElMessage.warning(t('product.devices.gateways.codeInvalid'))
      return
    }
    const vendor = isHcbgVendor(form.value.fields?.vendor) ? 'HCBG' : 'NATIVE'
    const mac = String(form.value.fields?.macAddress || '').trim()
    if (vendor === 'HCBG' && !looksLikeMac(mac)) {
      ElMessage.warning(t('product.devices.gateways.needHcbgMac'))
      return
    }
    try {
      const created = await gatewaysApi.create(projectScope.value, {
        ...form.value,
        code,
        fields: {
          ...form.value.fields,
          vendor,
          hcbg: vendor === 'HCBG',
          macAddress: mac,
          zoneId: form.value.fields?.zoneId || null,
          coordinateX: form.value.fields?.coordinateX ?? null,
          coordinateY: form.value.fields?.coordinateY ?? null,
          rssiAt1m: form.value.fields?.rssiAt1m ?? -61
        }
      })
      createVisible.value = false
      ElMessage.success(t('product.devices.gateways.created'))
      showProvision({ ...(created.fields || {}), vendor }, { ...created, fields: { ...created.fields, vendor } })
      await load()
    } catch {
      // http util already toasts
    }
  }

  async function saveEdit() {
    if (!projectScope.value || !editing.value) return
    const name = form.value.name?.trim()
    if (!name) {
      ElMessage.warning(t('product.devices.gateways.needName'))
      return
    }
    const code = form.value.code?.trim() || ''
    if (code !== editing.value.code && !/^\d{4}$/.test(code)) {
      ElMessage.warning(t('product.devices.gateways.codeInvalid'))
      return
    }
    const vendor = isHcbgVendor(form.value.fields?.vendor) ? 'HCBG' : 'NATIVE'
    const mac = String(form.value.fields?.macAddress || editing.value.fields?.macAddress || '').trim()
    if (vendor === 'HCBG' && !looksLikeMac(mac)) {
      ElMessage.warning(t('product.devices.gateways.needHcbgMac'))
      return
    }
    try {
      await gatewaysApi.update(projectScope.value, editing.value.id, {
        code,
        name,
        fields: {
          vendor,
          hcbg: vendor === 'HCBG',
          macAddress: mac || null,
          clientId: form.value.fields?.clientId || editing.value.fields?.clientId || null,
          zoneId: form.value.fields?.zoneId || null,
          coordinateX: form.value.fields?.coordinateX ?? null,
          coordinateY: form.value.fields?.coordinateY ?? null,
          rssiAt1m: form.value.fields?.rssiAt1m ?? -61
        }
      })
      editVisible.value = false
      editing.value = null
      ElMessage.success(t('common.saveSuccess'))
      await load()
    } catch {
      // http util already toasts
    }
  }

  async function openProvision(row: NamedResource) {
    if (!projectScope.value) return
    try {
      const provision = await gatewaysApi.provision(projectScope.value, row.id)
      showProvision(provision, row)
    } catch {
      // http util already toasts
    }
  }

  function simpleCredentialValid(username: string, password: string) {
    return /^[A-Za-z0-9]{2,64}$/.test(username) && /^[A-Za-z0-9]{4,64}$/.test(password)
  }

  async function saveProvision() {
    if (!projectScope.value || !provisionGateway.value) return
    const username = provisionForm.value.username.trim()
    const password = provisionForm.value.password.trim()
    if (!simpleCredentialValid(username, password)) {
      ElMessage.warning(t('product.devices.gateways.simpleCredentialHint'))
      return
    }
    provisionSaving.value = true
    try {
      await gatewaysApi.update(projectScope.value, provisionGateway.value.id, {
        code: provisionGateway.value.code,
        name: provisionGateway.value.name,
        fields: {
          username,
          password
        }
      })
      provisionGateway.value = { ...provisionGateway.value, fields: { ...provisionGateway.value.fields, username } }
      ElMessage.success(t('common.saveSuccess'))
    } catch {
      // http util already toasts
    } finally {
      provisionSaving.value = false
    }
  }

  async function doDelete(row: NamedResource) {
    if (!projectScope.value) return
    try {
      await ElMessageBox.confirm(t('product.devices.gateways.deleteConfirm', { code: row.code, name: row.name }), t('common.deleteConfirm'), {
        type: 'warning',
        confirmButtonText: t('common.delete'),
        cancelButtonText: t('common.cancel')
      })
      await gatewaysApi.remove(projectScope.value, row.id)
      ElMessage.success(t('common.deleted'))
      await load()
    } catch {
      // cancelled
    }
  }

  function applyRouteFilters() {
    keyword.value = queryText(route.query, 'q')
  }

  const canCreate = computed(() => !!projectScope.value)

  watch(
    projectScope,
    () => {
      void load()
      void loadZones()
      void loadMaps()
      applyRouteFilters()
      setupPolling()
    },
    { immediate: true }
  )
  watch(
    () => route.query,
    () => applyRouteFilters()
  )
  onActivated(() => {
    applyRouteFilters()
    void load(true)
    void loadZones()
    void loadMaps()
  })
  onUnmounted(() => clearTimer())
</script>

<template>
  <div class="page-card art-card">
    <div class="header">
      <div>
        <h2>{{ t('product.devices.gateways.title') }}</h2>
      </div>
      <ElButton type="primary" :disabled="!canCreate" @click="openCreate">{{ t('product.devices.gateways.create') }}</ElButton>
    </div>

      <div class="toolbar art-table-card">
        <ProductTableHeader v-model:columns="columnChecks" :loading="loading" full-class="page-card" @refresh="() => load()">
          <template #left>
            <div class="toolbar-left">
              <ElInput
                v-model="keyword"
                clearable
                :placeholder="t('product.devices.gateways.searchPh')"
                style="max-width: 320px"
              />
              <span class="toolbar-count">{{ t('common.totalCount', { count: filteredRows.length }) }}</span>
            </div>
          </template>
        </ProductTableHeader>
      </div>
      <ElTable v-loading="loading" :data="pagedRows" :size="tableSize" :stripe="isZebra" :border="isBorder">
        <ElTableColumn v-if="gwColVisible('code')" prop="code" :label="t('common.code')" width="120" sortable show-overflow-tooltip />
        <ElTableColumn v-if="gwColVisible('name')" prop="name" :label="t('common.name')" sortable show-overflow-tooltip />
        <ElTableColumn
          v-if="gwColVisible('mac')"
          label="MAC"
          width="140"
          sortable
          :sort-method="
            (a: NamedResource, b: NamedResource) =>
              String(a.fields?.macAddress || '').localeCompare(String(b.fields?.macAddress || ''))
          "
          show-overflow-tooltip
        >
          <template #default="{ row }">{{ row.fields?.macAddress || '-' }}</template>
        </ElTableColumn>
        <ElTableColumn v-if="gwColVisible('zone')" :label="t('product.assets.status.colZone')" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.fields?.zoneName || '-' }}</template>
        </ElTableColumn>
        <ElTableColumn v-if="gwColVisible('map')" :label="t('product.assets.status.colMap')" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.fields?.mapName || '-' }}</template>
        </ElTableColumn>
        <ElTableColumn v-if="gwColVisible('install')" :label="t('product.devices.gateways.colInstall')" width="130">
          <template #default="{ row }">
            <span v-if="row.fields?.coordinateX != null && row.fields?.coordinateY != null">
              {{ row.fields.coordinateX }}, {{ row.fields.coordinateY }}
            </span>
            <span v-else>-</span>
          </template>
        </ElTableColumn>
        <ElTableColumn v-if="gwColVisible('status')" prop="status" :label="t('common.status')" width="100" sortable>
          <template #default="{ row }">
            <PresencePill :status="row.status" />
          </template>
        </ElTableColumn>
        <ElTableColumn v-if="gwColVisible('lastSeenAt')" :label="t('product.assets.list.colLastSeen')" width="170" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatDateTime(row.fields?.lastSeenAt as string | null | undefined) }}
          </template>
        </ElTableColumn>
        <ElTableColumn v-if="gwColVisible('__actions')" :label="t('common.actions')" width="180" fixed="right">
          <template #default="{ row }">
            <ElButton link type="primary" @click="openEdit(row)">{{ t('common.edit') }}</ElButton>
            <ElButton link type="primary" @click="openProvision(row)">{{ t('product.devices.gateways.provision') }}</ElButton>
            <ElButton link type="danger" @click="doDelete(row)">{{ t('common.delete') }}</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>

      <TablePager
        :total="pageTotal"
        :current="pageCurrent"
        :size="pageSize"
        @update:current="onPageChange"
        @update:size="onSizeChange"
      />

    <ElDrawer v-model="createVisible" :title="t('product.devices.gateways.create')" size="520px">
      <ElForm label-position="top">
        <ElFormItem :label="t('common.code')">
          <ElInput v-model="form.code" maxlength="4" :placeholder="t('product.devices.gateways.codePh')" />
          <div class="empty-hint">{{ t('product.devices.gateways.codeHint') }}</div>
        </ElFormItem>
        <ElFormItem :label="t('common.name')">
          <ElInput v-model="form.name" />
        </ElFormItem>
        <ElFormItem>
          <ElCheckbox v-model="hcbgChecked">
            {{ t('product.devices.gateways.hcbgLabel') }}
          </ElCheckbox>
          <div class="empty-hint">{{ t('product.devices.gateways.hcbgHint') }}</div>
        </ElFormItem>
        <ElFormItem :label="'MAC'" :required="isHcbgVendor(form.fields?.vendor)">
          <ElInput
            :model-value="String(form.fields?.macAddress ?? '')"
            :placeholder="isHcbgVendor(form.fields?.vendor) ? t('product.devices.gateways.hcbgMacPh') : ''"
            @update:model-value="(v: string) => (form.fields = { ...form.fields, macAddress: v })"
          />
        </ElFormItem>
        <ElFormItem :label="t('product.devices.gateways.zoneLabel')">
          <ElSelect
            :model-value="(form.fields?.zoneId as string | null | undefined) ?? ''"
            clearable
            filterable
            :placeholder="t('product.devices.gateways.zonePh')"
            style="width: 100%"
            @update:model-value="onZoneChange"
          >
            <ElOption
              v-for="opt in zoneOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem :label="t('product.devices.gateways.calRssi')">
          <ElInputNumber
            :model-value="form.fields?.rssiAt1m == null ? -61 : Number(form.fields.rssiAt1m)"
            :min="-100"
            :max="-30"
            :step="1"
            controls-position="right"
            style="width: 100%"
            @update:model-value="(v) => (form.fields = { ...form.fields, rssiAt1m: v ?? -61 })"
          />
          <div class="empty-hint">{{ t('product.devices.gateways.calHint') }}</div>
        </ElFormItem>
        <ElFormItem v-if="form.fields?.zoneId" :label="t('product.devices.gateways.installPoint')">
          <FloorPlanPicker
            v-if="selectedMap"
            :image-url="(selectedMap.fields?.imageUrl as string | null) || null"
            :width-meters="Number(selectedMap.fields?.widthMeters) || null"
            :height-meters="Number(selectedMap.fields?.heightMeters) || null"
            :x="form.fields?.coordinateX == null ? null : Number(form.fields.coordinateX)"
            :y="form.fields?.coordinateY == null ? null : Number(form.fields.coordinateY)"
            @update:x="(v) => (form.fields = { ...form.fields, coordinateX: v })"
            @update:y="(v) => (form.fields = { ...form.fields, coordinateY: v })"
          />
          <div v-else class="empty-hint">{{ t('product.devices.gateways.noMapForZone') }}</div>
        </ElFormItem>
        <ElFormItem :label="t('product.devices.gateways.clientIdOptional')">
          <ElInput
            :model-value="String(form.fields?.clientId ?? '')"
            placeholder="gw-{code}-{hex}"
            @update:model-value="(v: string) => (form.fields = { ...form.fields, clientId: v })"
          />
        </ElFormItem>
        <ElButton type="primary" @click="createGateway">{{ t('product.devices.gateways.createAndProvision') }}</ElButton>
      </ElForm>
    </ElDrawer>

    <ElDrawer v-model="editVisible" :title="t('product.devices.gateways.editTitle')" size="520px">
      <ElForm label-position="top">
        <ElFormItem :label="t('common.code')">
          <ElInput v-model="form.code" :placeholder="t('product.devices.gateways.codePh')" />
          <div class="empty-hint">{{ t('product.devices.gateways.codeHint') }}</div>
        </ElFormItem>
        <ElFormItem :label="t('common.name')" required>
          <ElInput v-model="form.name" maxlength="160" />
        </ElFormItem>
        <ElFormItem>
          <ElCheckbox v-model="hcbgChecked">
            {{ t('product.devices.gateways.hcbgLabel') }}
          </ElCheckbox>
          <div class="empty-hint">{{ t('product.devices.gateways.hcbgHint') }}</div>
        </ElFormItem>
        <ElFormItem :label="'MAC'" :required="isHcbgVendor(form.fields?.vendor)">
          <ElInput
            :model-value="String(form.fields?.macAddress ?? '')"
            :placeholder="isHcbgVendor(form.fields?.vendor) ? t('product.devices.gateways.hcbgMacPh') : ''"
            @update:model-value="(v: string) => (form.fields = { ...form.fields, macAddress: v })"
          />
        </ElFormItem>
        <ElFormItem :label="t('product.devices.gateways.zoneLabel')">
          <ElSelect
            :model-value="(form.fields?.zoneId as string | null | undefined) ?? ''"
            clearable
            filterable
            :placeholder="t('product.devices.gateways.zonePh')"
            style="width: 100%"
            @update:model-value="onZoneChange"
          >
            <ElOption
              v-for="opt in zoneOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem :label="t('product.devices.gateways.calRssi')">
          <ElInputNumber
            :model-value="form.fields?.rssiAt1m == null ? -61 : Number(form.fields.rssiAt1m)"
            :min="-100"
            :max="-30"
            :step="1"
            controls-position="right"
            style="width: 100%"
            @update:model-value="(v) => (form.fields = { ...form.fields, rssiAt1m: v ?? -61 })"
          />
          <div class="empty-hint">{{ t('product.devices.gateways.calHint') }}</div>
        </ElFormItem>
        <ElFormItem v-if="form.fields?.zoneId" :label="t('product.devices.gateways.installPoint')">
          <FloorPlanPicker
            v-if="selectedMap"
            :image-url="(selectedMap.fields?.imageUrl as string | null) || null"
            :width-meters="Number(selectedMap.fields?.widthMeters) || null"
            :height-meters="Number(selectedMap.fields?.heightMeters) || null"
            :x="form.fields?.coordinateX == null ? null : Number(form.fields.coordinateX)"
            :y="form.fields?.coordinateY == null ? null : Number(form.fields.coordinateY)"
            @update:x="(v) => (form.fields = { ...form.fields, coordinateX: v })"
            @update:y="(v) => (form.fields = { ...form.fields, coordinateY: v })"
          />
          <div v-else class="empty-hint">{{ t('product.devices.gateways.noMapForZone') }}</div>
        </ElFormItem>
        <ElButton type="primary" @click="saveEdit">{{ t('common.save') }}</ElButton>
      </ElForm>
    </ElDrawer>

    <ElDialog v-model="provisionVisible" :title="t('product.devices.gateways.provisionTitle')" width="560px">
      <p class="hint">
        {{
          isHcbgVendor(provisionGateway?.fields?.vendor)
            ? t('product.devices.gateways.provisionHintHcbg')
            : t('product.devices.gateways.provisionHint')
        }}
      </p>
      <ElForm label-position="top" class="provision-form">
        <ElFormItem
          :label="
            isHcbgVendor(provisionGateway?.fields?.vendor)
              ? t('product.devices.gateways.usr')
              : t('product.devices.gateways.username')
          "
        >
          <ElInput v-model="provisionForm.username" maxlength="64" />
        </ElFormItem>
        <ElFormItem
          :label="
            isHcbgVendor(provisionGateway?.fields?.vendor)
              ? t('product.devices.gateways.pw')
              : t('product.devices.gateways.password')
          "
        >
          <ElInput v-model="provisionForm.password" maxlength="64" show-password />
        </ElFormItem>
        <div class="empty-hint">{{ t('product.devices.gateways.simpleCredentialHint') }}</div>
      </ElForm>
      <div v-for="item in provisionEntries" :key="item.key" class="kv-row">
        <div class="kv-key">{{ item.key }}</div>
        <div class="kv-value">{{ item.value }}</div>
      </div>
      <template #footer>
        <ElButton @click="provisionVisible = false">{{ t('common.close') }}</ElButton>
        <ElButton type="primary" :loading="provisionSaving" @click="saveProvision">
          {{ t('product.devices.gateways.saveCredentials') }}
        </ElButton>
      </template>
    </ElDialog>
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
    margin-bottom: 16px;
  }
  .toolbar {
    margin-bottom: 12px;
  }
  .toolbar-left {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 12px;
  }
  .toolbar-count {
    font-size: 13px;
    color: var(--el-text-color-secondary);
    white-space: nowrap;
    flex-shrink: 0;
  }
  h2 {
    margin: 0 0 4px;
  }
  p {
    margin: 0;
    color: var(--el-text-color-secondary);
  }
  .hint {
    margin: 0 0 12px;
    color: var(--el-text-color-secondary);
  }
  .empty-hint {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }
  .kv-row {
    display: grid;
    grid-template-columns: 140px 1fr;
    gap: 8px;
    align-items: center;
    padding: 8px 0;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }
  .kv-key {
    font-weight: 600;
    color: var(--el-text-color-regular);
  }
  .kv-value {
    word-break: break-all;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 13px;
  }
  .provision-form {
    margin-bottom: 8px;
  }
</style>
