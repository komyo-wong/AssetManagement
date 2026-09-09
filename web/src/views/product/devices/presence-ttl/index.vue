<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import { useTenantContextStore } from '@/store/modules/tenant-context'
import { fetchPresenceSettings, updatePresenceSettings } from '@/api/asset-platform'

const { t } = useI18n()
const { projectScope } = storeToRefs(useTenantContextStore())
const loading = ref(false)
const saving = ref(false)
const gatewaySeconds = ref(90)
const beaconSeconds = ref(300)

const gatewayHint = computed(() => {
  const s = Number(gatewaySeconds.value) || 0
  if (s < 60) return t('product.devices.presenceTtl.sec', { n: s })
  const m = Math.floor(s / 60)
  const r = s % 60
  return r
    ? t('product.devices.presenceTtl.aboutMinSec', { m, s: r })
    : t('product.devices.presenceTtl.aboutMin', { m })
})

const beaconHint = computed(() => {
  const s = Number(beaconSeconds.value) || 0
  if (s < 60) return t('product.devices.presenceTtl.sec', { n: s })
  const m = (s / 60).toFixed(s % 60 === 0 ? 0 : 1)
  return t('product.devices.presenceTtl.aboutMin', { m })
})

async function load() {
  if (!projectScope.value) return
  loading.value = true
  try {
    const settings = await fetchPresenceSettings(projectScope.value)
    gatewaySeconds.value = settings.gatewayOnlineTtlSeconds
    beaconSeconds.value = settings.beaconOnlineTtlSeconds
  } catch {
    gatewaySeconds.value = 90
    beaconSeconds.value = 300
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!projectScope.value) return
  const gw = Number(gatewaySeconds.value)
  const bc = Number(beaconSeconds.value)
  if (!Number.isFinite(gw) || gw < 30 || gw > 3600) {
    ElMessage.warning(t('product.devices.presenceTtl.gatewayRange'))
    return
  }
  if (!Number.isFinite(bc) || bc < 60 || bc > 86400) {
    ElMessage.warning(t('product.devices.presenceTtl.beaconRange'))
    return
  }
  saving.value = true
  try {
    const saved = await updatePresenceSettings(projectScope.value, {
      gatewayOnlineTtlSeconds: Math.round(gw),
      beaconOnlineTtlSeconds: Math.round(bc)
    })
    gatewaySeconds.value = saved.gatewayOnlineTtlSeconds
    beaconSeconds.value = saved.beaconOnlineTtlSeconds
    ElMessage.success(t('product.devices.presenceTtl.saved'))
  } finally {
    saving.value = false
  }
}

watch(projectScope, load, { immediate: true })
</script>

<template>
  <div class="page-card art-card" style="padding: 20px" v-loading="loading">
    <div style="margin-bottom: 16px">
      <h2 style="margin: 0">{{ t('product.devices.presenceTtl.title') }}</h2>
      <p style="margin: 4px 0 0; color: var(--el-text-color-secondary)">
        {{ t('product.devices.presenceTtl.subtitle') }}
      </p>
    </div>
    <ElForm label-position="top" style="max-width: 420px">
      <ElFormItem :label="t('product.devices.presenceTtl.gatewayLabel', { hint: gatewayHint })">
        <ElInputNumber v-model="gatewaySeconds" :min="30" :max="3600" :step="10" controls-position="right" style="width: 100%" />
        <p class="hint">{{ t('product.devices.presenceTtl.gatewayHint') }}</p>
      </ElFormItem>
      <ElFormItem :label="t('product.devices.presenceTtl.beaconLabel', { hint: beaconHint })">
        <ElInputNumber v-model="beaconSeconds" :min="60" :max="86400" :step="60" controls-position="right" style="width: 100%" />
        <p class="hint">{{ t('product.devices.presenceTtl.beaconHint') }}</p>
      </ElFormItem>
      <ElButton type="primary" :loading="saving" @click="save">{{ t('common.save') }}</ElButton>
    </ElForm>
  </div>
</template>

<style scoped>
.hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
}
</style>
