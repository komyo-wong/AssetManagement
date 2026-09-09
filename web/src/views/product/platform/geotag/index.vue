<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import {
    fetchPlatformGeotagSettings,
    rotatePlatformGeotagKeys,
    simulateGeotagPoint,
    testPlatformGeotag,
    testPlatformGeotagWebhook,
    updatePlatformGeotagSettings
  } from '@/api/asset-platform'
  import { formatDateTime } from '@/utils/datetime'

  defineOptions({ name: 'PlatformGeotagSettings' })

  const { t } = useI18n()
  const loading = ref(false)
  const saving = ref(false)
  const testing = ref(false)
  const testingWebhook = ref(false)
  const rotating = ref(false)
  const simulating = ref(false)
  const enabled = ref(false)
  const ready = ref(false)

  const form = reactive({
    enabled: false,
    apiBaseUrl: '',
    businessNo: '',
    platformPublicKey: '',
    ourPublicKey: '',
    webhookEnabled: false,
    webhookHost: '',
    webhookPort: 80,
    webhookHttps: false,
    webhookUrl: '',
    detectedPublicIp: '',
    lastWebhookAt: '',
    lastWebhookProbeOk: false,
    lastWebhookProbeAt: '',
    lastWebhookProbeMessage: '',
    lastTestOk: false,
    lastTestAt: '',
    lastTestMessage: '',
    mock: false,
    mapProvider: 'osm',
    mapCartoKey: '',
    mapMaptilerKey: ''
  })

  const simulate = reactive({
    sn: '',
    lat: 22.5431,
    lng: 114.0579,
    battery: 100
  })

  async function load() {
    loading.value = true
    try {
      const data = await fetchPlatformGeotagSettings()
      apply(data)
    } finally {
      loading.value = false
    }
  }

  function apply(data: Record<string, unknown>) {
    form.enabled = !!data.enabled
    enabled.value = !!data.enabled
    ready.value = !!data.ready
    form.apiBaseUrl = String(data.apiBaseUrl || '')
    form.businessNo = String(data.businessNo || '')
    form.platformPublicKey = String(data.platformPublicKey || '')
    form.ourPublicKey = String(data.ourPublicKey || '')
    form.webhookEnabled = !!data.webhookEnabled
    form.webhookHost = String(data.webhookHost || data.detectedPublicIp || '')
    form.webhookPort = Number(data.webhookPort || 80)
    form.webhookHttps = !!data.webhookHttps
    form.webhookUrl = String(data.webhookUrl || '')
    form.detectedPublicIp = String(data.detectedPublicIp || '')
    form.lastWebhookAt = data.lastWebhookAt ? String(data.lastWebhookAt) : ''
    form.lastWebhookProbeOk = !!data.lastWebhookProbeOk
    form.lastWebhookProbeAt = data.lastWebhookProbeAt ? String(data.lastWebhookProbeAt) : ''
    form.lastWebhookProbeMessage = String(data.lastWebhookProbeMessage || '')
    form.lastTestOk = !!data.lastTestOk
    form.lastTestAt = data.lastTestAt ? String(data.lastTestAt) : ''
    form.lastTestMessage = String(data.lastTestMessage || '')
    form.mock = !!data.mock
    form.mapProvider = String(data.mapProvider || 'osm')
    form.mapCartoKey = String(data.mapCartoKey || '')
    form.mapMaptilerKey = String(data.mapMaptilerKey || '')
  }

  async function save() {
    saving.value = true
    try {
      if ((form.mapProvider === 'carto' && !form.mapCartoKey.trim())
        || (form.mapProvider === 'maptiler' && !form.mapMaptilerKey.trim())) {
        ElMessage.warning(t('product.geotag.mapKeyNeed', {
          provider: t(form.mapProvider === 'carto' ? 'platformAdmin.geotag.mapCarto' : 'platformAdmin.geotag.mapMaptiler'),
          url: form.mapProvider === 'carto' ? 'https://carto.com/basemaps/' : 'https://cloud.maptiler.com/account/keys/'
        }))
      }
      const data = await updatePlatformGeotagSettings({
        enabled: form.enabled,
        apiBaseUrl: form.apiBaseUrl.trim() || null,
        businessNo: form.businessNo.trim() || null,
        platformPublicKey: form.platformPublicKey.trim() || null,
        webhookEnabled: form.webhookEnabled,
        webhookHost: form.webhookHost.trim() || null,
        webhookPort: form.webhookPort,
        webhookHttps: form.webhookHttps,
        mapProvider: form.mapProvider,
        mapCartoKey: form.mapCartoKey.trim() || null,
        mapMaptilerKey: form.mapMaptilerKey.trim() || null
      })
      const wasEnabled = enabled.value
      apply(data)
      ElMessage.success(t('platformAdmin.geotag.saved'))
      if (wasEnabled !== form.enabled) {
        window.location.reload()
      }
    } finally {
      saving.value = false
    }
  }

  async function test() {
    testing.value = true
    try {
      const data = await testPlatformGeotag()
      ElMessage.success(t('platformAdmin.geotag.tested', { count: Number(data.deviceCount || 0) }))
      await load()
    } finally {
      testing.value = false
    }
  }

  const previewWebhookUrl = computed(() => buildWebhookUrl(
    form.webhookHttps,
    form.webhookHost.trim() || form.detectedPublicIp.trim(),
    form.webhookPort
  ))

  function buildWebhookUrl(https: boolean, host: string, port: number) {
    let trimmed = (host || '').trim()
    if (!trimmed) return ''
    const colon = trimmed.indexOf(':')
    if (colon > 0 && trimmed.indexOf(']') < 0) {
      trimmed = trimmed.slice(0, colon)
    }
    const scheme = https ? 'https' : 'http'
    const effective = !port || port <= 0 ? (https ? 443 : 80) : port
    const omit = (https && effective === 443) || (!https && effective === 80)
    return `${scheme}://${omit ? trimmed : `${trimmed}:${effective}`}/api/v1/public/geotag/webhook`
  }

  async function testWebhook() {
    testingWebhook.value = true
    try {
      const data = await testPlatformGeotagWebhook({
        webhookEnabled: true,
        webhookHost: form.webhookHost.trim() || form.detectedPublicIp.trim() || null,
        webhookPort: form.webhookPort,
        webhookHttps: form.webhookHttps
      })
      ElMessage.success(String(data.message || t('platformAdmin.geotag.webhookOk')))
      await load()
    } finally {
      testingWebhook.value = false
    }
  }

  function onWebhookToggle(value: string | number | boolean) {
    if (value && !form.webhookHost.trim() && form.detectedPublicIp) {
      form.webhookHost = form.detectedPublicIp
    }
  }

  async function rotate() {
    try {
      await ElMessageBox.confirm(t('platformAdmin.geotag.rotateConfirm'), t('platformAdmin.geotag.rotateKeys'), {
        type: 'warning'
      })
    } catch {
      return
    }
    rotating.value = true
    try {
      const data = await rotatePlatformGeotagKeys()
      apply(data)
      ElMessage.success(t('platformAdmin.geotag.rotated'))
    } finally {
      rotating.value = false
    }
  }

  async function copy(text: string) {
    if (!text) return
    await navigator.clipboard.writeText(text)
    ElMessage.success(t('platformAdmin.geotag.copied'))
  }

  async function writePoint() {
    if (!simulate.sn.trim()) {
      ElMessage.warning(t('platformAdmin.geotag.simulateSnPh'))
      return
    }
    simulating.value = true
    try {
      await simulateGeotagPoint({
        sn: simulate.sn.trim(),
        lat: simulate.lat,
        lng: simulate.lng,
        battery: simulate.battery,
        locationTime: Date.now()
      })
      ElMessage.success(t('platformAdmin.geotag.simulated'))
    } finally {
      simulating.value = false
    }
  }

  onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page-card art-card geotag-page">
    <h2>{{ t('platformAdmin.geotag.title') }}</h2>
    <p class="subtitle">
      <span :class="enabled ? 'ok' : 'warn'">
        {{ enabled ? t('platformAdmin.geotag.ready') : t('platformAdmin.geotag.notReady') }}
      </span>
    </p>

    <ElForm label-width="140px">
      <ElFormItem :label="t('platformAdmin.geotag.enabled')">
        <ElSwitch v-model="form.enabled" />
      </ElFormItem>
      <ElFormItem :label="t('platformAdmin.geotag.apiBaseUrl')">
        <ElInput v-model="form.apiBaseUrl" :placeholder="t('platformAdmin.geotag.apiBaseUrlPh')" />
      </ElFormItem>
      <ElFormItem :label="t('platformAdmin.geotag.businessNo')">
        <ElInput v-model="form.businessNo" :placeholder="t('platformAdmin.geotag.businessNoPh')" />
      </ElFormItem>
      <ElFormItem :label="t('platformAdmin.geotag.platformPublicKey')">
        <ElInput
          v-model="form.platformPublicKey"
          type="textarea"
          :rows="3"
          :placeholder="t('platformAdmin.geotag.platformPublicKeyPh')"
        />
      </ElFormItem>
      <ElFormItem :label="t('platformAdmin.geotag.ourPublicKey')">
        <ElInput v-model="form.ourPublicKey" type="textarea" :rows="3" readonly />
        <div class="row-actions">
          <ElButton @click="copy(form.ourPublicKey)">{{ t('platformAdmin.geotag.copy') }}</ElButton>
          <ElButton :loading="rotating" @click="rotate">{{ t('platformAdmin.geotag.rotateKeys') }}</ElButton>
        </div>
        <p class="field-hint block">{{ t('platformAdmin.geotag.ourPublicKeyHint') }}</p>
      </ElFormItem>
      <ElFormItem :label="t('platformAdmin.geotag.webhookEnabled')">
        <ElSwitch v-model="form.webhookEnabled" @change="onWebhookToggle" />
        <p class="field-hint block">
          {{ form.webhookEnabled ? t('platformAdmin.geotag.webhookOnHint') : t('platformAdmin.geotag.webhookOffHint') }}
        </p>
      </ElFormItem>
      <template v-if="form.webhookEnabled">
        <ElFormItem :label="t('platformAdmin.geotag.webhookHost')">
          <ElInput v-model="form.webhookHost" :placeholder="form.detectedPublicIp || t('platformAdmin.geotag.webhookHostPh')" />
        </ElFormItem>
        <ElFormItem :label="t('platformAdmin.geotag.webhookPort')">
          <ElInputNumber v-model="form.webhookPort" :min="1" :max="65535" :step="1" />
        </ElFormItem>
        <ElFormItem :label="t('platformAdmin.geotag.webhookHttps')">
          <ElSwitch v-model="form.webhookHttps" />
        </ElFormItem>
        <ElFormItem :label="t('platformAdmin.geotag.webhookUrl')">
          <ElInput :model-value="previewWebhookUrl" readonly />
          <div class="row-actions">
            <ElButton @click="copy(previewWebhookUrl)">{{ t('platformAdmin.geotag.copy') }}</ElButton>
            <ElButton type="success" :loading="testingWebhook" @click="testWebhook">
              {{ t('platformAdmin.geotag.webhookTest') }}
            </ElButton>
          </div>
          <p class="field-hint block">{{ t('platformAdmin.geotag.webhookPortHint') }}</p>
          <p class="field-hint block">{{ t('platformAdmin.geotag.webhookNatHint') }}</p>
          <p v-if="form.lastWebhookProbeMessage" class="field-hint block" :class="form.lastWebhookProbeOk ? 'ok' : 'warn'">
            {{ form.lastWebhookProbeMessage }}
            <template v-if="form.lastWebhookProbeAt"> · {{ formatDateTime(form.lastWebhookProbeAt) }}</template>
          </p>
          <p v-if="form.lastWebhookAt" class="field-hint block">
            {{ t('platformAdmin.geotag.lastWebhookAt', { time: formatDateTime(form.lastWebhookAt) }) }}
          </p>
        </ElFormItem>
      </template>
      <ElFormItem :label="t('platformAdmin.geotag.mapProvider')">
        <ElSelect v-model="form.mapProvider" style="width: 420px">
          <ElOption :label="t('platformAdmin.geotag.mapOsm')" value="osm" />
          <ElOption :label="t('platformAdmin.geotag.mapEsri')" value="esri" />
          <ElOption :label="t('platformAdmin.geotag.mapCarto')" value="carto" />
          <ElOption :label="t('platformAdmin.geotag.mapMaptiler')" value="maptiler" />
        </ElSelect>
        <p class="field-hint block">{{ t('platformAdmin.geotag.mapSettingsHint') }}</p>
      </ElFormItem>
      <ElFormItem :label="t('platformAdmin.geotag.mapCartoKey')">
        <ElInput v-model="form.mapCartoKey" :placeholder="t('platformAdmin.geotag.mapApiKeyPh')" />
        <p class="field-hint block">
          {{ t('platformAdmin.geotag.mapCartoHint') }}
          <a href="https://carto.com/basemaps/" target="_blank" rel="noreferrer">https://carto.com/basemaps/</a>
        </p>
      </ElFormItem>
      <ElFormItem :label="t('platformAdmin.geotag.mapMaptilerKey')">
        <ElInput v-model="form.mapMaptilerKey" :placeholder="t('platformAdmin.geotag.mapApiKeyPh')" />
        <p class="field-hint block">
          {{ t('platformAdmin.geotag.mapMaptilerHint') }}
          <a href="https://cloud.maptiler.com/account/keys/" target="_blank" rel="noreferrer">
            https://cloud.maptiler.com/account/keys/
          </a>
        </p>
      </ElFormItem>
      <ElFormItem :label="t('platformAdmin.geotag.lastTest')" v-if="form.lastTestMessage">
        <span :class="form.lastTestOk ? 'ok' : 'warn'">
          {{ form.lastTestMessage }}
          <template v-if="form.lastTestAt"> · {{ formatDateTime(form.lastTestAt) }}</template>
        </span>
      </ElFormItem>
      <ElFormItem>
        <ElButton type="primary" :loading="saving" @click="save">{{ t('platformAdmin.geotag.save') }}</ElButton>
        <ElButton type="success" :loading="testing" @click="test">{{ t('platformAdmin.geotag.test') }}</ElButton>
      </ElFormItem>
    </ElForm>

    <ElDivider />
    <h3 class="section-title">{{ t('platformAdmin.geotag.simulateTitle') }}</h3>
    <p class="subtitle">{{ t('platformAdmin.geotag.simulateHint') }}</p>
    <ElForm label-width="140px">
      <ElFormItem :label="t('platformAdmin.geotag.simulateSn')">
        <ElInput v-model="simulate.sn" :placeholder="t('platformAdmin.geotag.simulateSnPh')" />
      </ElFormItem>
      <ElFormItem :label="t('platformAdmin.geotag.lat')">
        <ElInputNumber v-model="simulate.lat" :precision="6" :step="0.001" />
      </ElFormItem>
      <ElFormItem :label="t('platformAdmin.geotag.lng')">
        <ElInputNumber v-model="simulate.lng" :precision="6" :step="0.001" />
      </ElFormItem>
      <ElFormItem :label="t('platformAdmin.geotag.battery')">
        <ElInputNumber v-model="simulate.battery" :min="0" :max="100" />
      </ElFormItem>
      <ElFormItem>
        <ElButton type="primary" :loading="simulating" @click="writePoint">
          {{ t('platformAdmin.geotag.simulate') }}
        </ElButton>
      </ElFormItem>
    </ElForm>
  </div>
</template>

<style scoped>
  .geotag-page {
    padding: 20px;
    max-width: 820px;
  }

  .subtitle {
    color: var(--el-text-color-secondary);
    margin: 4px 0 16px;
  }

  .ok {
    color: var(--el-color-success);
  }

  .warn {
    color: var(--el-color-warning);
  }

  .field-hint {
    margin-left: 12px;
    color: var(--el-text-color-secondary);
  }

  .field-hint.block {
    margin: 8px 0 0;
    line-height: 1.5;
  }

  .row-actions {
    display: flex;
    gap: 8px;
    margin-top: 8px;
  }

  .section-title {
    margin-bottom: 8px;
  }
</style>
