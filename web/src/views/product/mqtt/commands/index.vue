<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import { useTenantContextStore } from '@/store/modules/tenant-context'
import { fetchMqttCommands, fetchMqttConnections, publishMqttCommand } from '@/api/asset-platform'
import { formatDateTime } from '@/utils/datetime'
import TablePager from '@/components/business/TablePager.vue'
import { useClientPagination } from '@/composables/useClientPagination'

const { t } = useI18n()
const { projectScope } = storeToRefs(useTenantContextStore())
const rows = ref<Api.AssetPlatform.MqttCommandItem[]>([])
const {
  current: pageCurrent,
  size: pageSize,
  total: pageTotal,
  pagedRows,
  onPageChange,
  onSizeChange
} = useClientPagination(rows)
const connections = ref<Api.AssetPlatform.MqttConnection[]>([])
const form = ref({
  topic: 'SrvData',
  payload: '',
  qos: 0,
  retained: false,
  connectionId: '' as string
})
let timer: ReturnType<typeof setInterval> | null = null

async function load() {
  if (!projectScope.value) {
    rows.value = []
    connections.value = []
    return
  }
  const [page, conns] = await Promise.all([
    fetchMqttCommands(projectScope.value, { current: 1, size: 50 }),
    fetchMqttConnections(projectScope.value).catch(() => [] as Api.AssetPlatform.MqttConnection[])
  ])
  rows.value = page.records || []
  connections.value = conns || []
  if (!form.value.connectionId && connections.value.length > 0) {
    const primary = connections.value.find((c) => c.role === 'primary' && c.enabled) || connections.value[0]
    form.value.connectionId = primary?.id || ''
  }
}

async function send() {
  if (!projectScope.value) return
  await publishMqttCommand(projectScope.value, {
    topic: form.value.topic,
    payload: form.value.payload,
    qos: form.value.qos,
    retained: form.value.retained,
    connectionId: form.value.connectionId || undefined
  })
  ElMessage.success(t('product.mqtt.commands.queued'))
  form.value.payload = ''
  await load()
}

function setup() {
  if (timer) clearInterval(timer)
  void load()
  timer = setInterval(() => {
    if (document.visibilityState === 'hidden') return
    void load()
  }, 4000)
}

function connectionLabel(c: Api.AssetPlatform.MqttConnection) {
  return `${c.name} · ${c.role}${c.enabled ? '' : t('product.mqtt.commands.disabledSuffix')}`
}

watch(projectScope, setup, { immediate: true })
onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>
<template>
  <div class="page-card art-card" style="padding: 20px">
    <h2>{{ t('product.mqtt.commands.title') }}</h2>
    <p style="color: var(--el-text-color-secondary)">
      {{ t('product.mqtt.commands.subtitle') }}
    </p>
    <ElForm label-position="top" style="max-width: 640px; margin-top: 12px">
      <ElFormItem :label="t('product.mqtt.commands.connection')">
        <ElSelect v-model="form.connectionId" clearable :placeholder="t('product.mqtt.commands.connectionPh')" style="width: 100%">
          <ElOption
            v-for="c in connections"
            :key="c.id"
            :label="connectionLabel(c)"
            :value="c.id"
            :disabled="!c.enabled"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="Topic">
        <ElInput v-model="form.topic" />
      </ElFormItem>
      <ElFormItem label="Payload">
        <ElInput v-model="form.payload" type="textarea" :rows="4" />
      </ElFormItem>
      <div style="display: flex; gap: 16px">
        <ElFormItem label="QoS" style="flex: 1">
          <ElSelect v-model="form.qos" style="width: 100%">
            <ElOption :value="0" label="0" />
            <ElOption :value="1" label="1" />
            <ElOption :value="2" label="2" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="Retained" style="flex: 1">
          <ElSwitch v-model="form.retained" />
        </ElFormItem>
      </div>
      <ElButton type="primary" :disabled="!form.payload" @click="send">{{ t('product.mqtt.commands.publish') }}</ElButton>
    </ElForm>

    <ElTable :data="pagedRows" stripe style="margin-top: 24px">
      <ElTableColumn prop="topic" label="Topic" width="140" />
      <ElTableColumn prop="payload" label="Payload" show-overflow-tooltip />
      <ElTableColumn prop="qos" label="QoS" width="70" />
      <ElTableColumn prop="status" :label="t('common.status')" width="100" />
      <ElTableColumn :label="t('common.createdAt')" width="180">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </ElTableColumn>
      <ElTableColumn :label="t('product.mqtt.commands.sentAt')" width="180">
        <template #default="{ row }">{{ formatDateTime(row.sentAt) }}</template>
      </ElTableColumn>
      <ElTableColumn prop="errorMessage" :label="t('common.error')" width="180" show-overflow-tooltip />
    </ElTable>
    <TablePager
      :total="pageTotal"
      :current="pageCurrent"
      :size="pageSize"
      @update:current="onPageChange"
      @update:size="onSizeChange"
    />
  </div>
</template>
