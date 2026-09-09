<script setup lang="ts">
  import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
  import { storeToRefs } from 'pinia'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { useTenantContextStore } from '@/store/modules/tenant-context'
  import {
    createNotificationChannel,
    createNotificationSubscription,
    deleteNotificationChannel,
    deleteNotificationSubscription,
    fetchNotificationOverview,
    fetchNotificationDeliveries,
    updateNotificationChannel,
    updateNotificationSubscription
  } from '@/api/notifications'
  import { formatDateTime } from '@/utils/datetime'
  import { localizeAlertMessage, localizeAlertTitle } from '@/utils/alert-i18n'
  import TablePager from '@/components/business/TablePager.vue'
  import { useClientPagination } from '@/composables/useClientPagination'

  const { t } = useI18n()

  defineOptions({ name: 'MyNotifications' })

  const { projectScope } = storeToRefs(useTenantContextStore())
  const loading = ref(false)
  const accountEmail = ref('')
  const licensed = ref(false)
  const channels = ref<Record<string, unknown>[]>([])
  const subscriptions = ref<Record<string, unknown>[]>([])
  const deliveries = ref<Record<string, unknown>[]>([])

  const {
    current: pageCurrent,
    size: pageSize,
    total: pageTotal,
    pagedRows,
    onPageChange,
    onSizeChange
  } = useClientPagination(deliveries)

  const {
    current: channelsPageCurrent,
    size: channelsPageSize,
    total: channelsPageTotal,
    pagedRows: pagedChannels,
    onPageChange: onChannelsPageChange,
    onSizeChange: onChannelsSizeChange
  } = useClientPagination(channels)

  const {
    current: subsPageCurrent,
    size: subsPageSize,
    total: subsPageTotal,
    pagedRows: pagedSubscriptions,
    onPageChange: onSubsPageChange,
    onSizeChange: onSubsSizeChange
  } = useClientPagination(subscriptions)
  let pollTimer: ReturnType<typeof setInterval> | null = null

  const channelForm = reactive({
    channelType: 'EMAIL',
    address: '',
    displayName: '',
    enabled: true
  })

  const INTERVAL_OPTIONS = computed(() => [
    { label: t('product.alerts.notifications.intervalOnce'), value: 0 },
    { label: t('product.alerts.notifications.interval5m'), value: 300 },
    { label: t('product.alerts.notifications.interval15m'), value: 900 },
    { label: t('product.alerts.notifications.interval30m'), value: 1800 },
    { label: t('product.alerts.notifications.interval1h'), value: 3600 },
    { label: t('product.alerts.notifications.interval6h'), value: 21600 }
  ])

  const subForm = reactive({
    channelId: '',
    onAlert: true,
    onRecovery: true,
    minSeverity: 'WARNING',
    enabled: true,
    throttleSeconds: 0,
    quietHoursEnabled: false,
    quietStart: '22:00',
    quietEnd: '08:00',
    allowCritical: true,
    ruleTypesCsv: ''
  })

  function intervalLabel(seconds: unknown) {
    const n = Number(seconds)
    if (!n || n <= 0) return t('product.alerts.notifications.onceShort')
    const hit = INTERVAL_OPTIONS.value.find((o) => o.value === n)
    return hit ? hit.label : t('product.alerts.notifications.everyNSec', { n })
  }

  function kindLabel(kind: unknown) {
    const k = String(kind || '').toUpperCase()
    if (k === 'ALERT') return t('product.alerts.notifications.kindAlert')
    if (k === 'RECOVERY') return t('product.alerts.notifications.kindRecovery')
    return String(kind || '—')
  }

  function statusLabel(status: unknown) {
    const s = String(status || '').toUpperCase()
    if (s === 'SENT') return t('product.alerts.notifications.statusSent')
    if (s === 'FAILED') return t('product.alerts.notifications.statusFailed')
    if (s === 'PENDING') return t('product.alerts.notifications.statusPending')
    if (s === 'SKIPPED') return t('product.alerts.notifications.statusSkipped')
    return String(status || '—')
  }

  function statusTagType(status: unknown) {
    const s = String(status || '').toUpperCase()
    if (s === 'SENT') return 'success'
    if (s === 'FAILED') return 'danger'
    if (s === 'SKIPPED') return 'info'
    return 'warning'
  }

  const channelOptions = computed(() =>
    channels.value.map((c) => ({
      id: String(c.id),
      label: `${c.channelType} · ${c.displayName || c.address}`
    }))
  )

  async function load(silent = false) {
    if (!silent) loading.value = true
    try {
      const overview = await fetchNotificationOverview()
      accountEmail.value = String(overview.accountEmail || '')
      licensed.value = overview.licensed === true
      channels.value = (overview.channels as Record<string, unknown>[]) || []
      subscriptions.value = (overview.subscriptions as Record<string, unknown>[]) || []
      if (!channelForm.address && accountEmail.value) {
        channelForm.address = accountEmail.value
      }
    } catch {
      if (!silent) {
        channels.value = []
        subscriptions.value = []
      }
    }
    try {
      const page = await fetchNotificationDeliveries({ current: 1, size: 50 })
      const records = (page as { records?: Record<string, unknown>[] } | null)?.records
      deliveries.value = Array.isArray(records) ? records : []
    } catch {
      if (!silent) {
        deliveries.value = []
        ElMessage.error(t('product.alerts.notifications.loadDeliveriesFailed'))
      }
    } finally {
      if (!silent) loading.value = false
    }
  }

  async function addChannel() {
    if (!channelForm.address.trim()) {
      ElMessage.warning(t('product.alerts.notifications.needAddress'))
      return
    }
    await createNotificationChannel({
      channelType: channelForm.channelType,
      address: channelForm.address.trim(),
      displayName: channelForm.displayName.trim() || undefined,
      enabled: channelForm.enabled
    })
    ElMessage.success(t('product.alerts.notifications.channelAdded'))
    channelForm.displayName = ''
    if (channelForm.channelType === 'WEBHOOK') channelForm.address = ''
    await load()
  }

  async function setChannelEnabled(row: Record<string, unknown>, enabled: string | number | boolean) {
    await updateNotificationChannel(String(row.id), { enabled: !!enabled })
    await load()
  }

  async function removeChannel(row: Record<string, unknown>) {
    await ElMessageBox.confirm(t('product.alerts.notifications.deleteChannelConfirm'), t('product.alerts.notifications.deleteChannelTitle'))
    await deleteNotificationChannel(String(row.id))
    await load()
  }

  async function addSubscription() {
    if (!projectScope.value) {
      ElMessage.warning(t('product.alerts.notifications.workspaceNotReady'))
      return
    }
    if (!subForm.channelId) {
      ElMessage.warning(t('product.alerts.notifications.needChannel'))
      return
    }
    const quietHoursJson = subForm.quietHoursEnabled
      ? JSON.stringify({
          tz: 'Asia/Shanghai',
          start: subForm.quietStart,
          end: subForm.quietEnd,
          allowCritical: subForm.allowCritical
        })
      : null
    await createNotificationSubscription({
      tenantId: projectScope.value.tenantId,
      projectId: projectScope.value.projectId,
      channelId: subForm.channelId,
      onAlert: subForm.onAlert,
      onRecovery: subForm.onRecovery,
      minSeverity: subForm.minSeverity,
      enabled: subForm.enabled,
      throttleSeconds: subForm.throttleSeconds > 0 ? subForm.throttleSeconds : 0,
      quietHoursJson,
      ruleTypesCsv: subForm.ruleTypesCsv.trim() || null
    })
    ElMessage.success(t('product.alerts.notifications.subscriptionCreated'))
    await load()
  }

  async function setSubscriptionField(
    row: Record<string, unknown>,
    field: 'onAlert' | 'onRecovery' | 'enabled',
    value: string | number | boolean
  ) {
    await updateNotificationSubscription(String(row.id), { [field]: !!value })
    await load()
  }

  async function setSubscriptionInterval(row: Record<string, unknown>, value: number) {
    await updateNotificationSubscription(String(row.id), { throttleSeconds: value })
    await load()
  }

  async function removeSubscription(row: Record<string, unknown>) {
    await ElMessageBox.confirm(t('product.alerts.notifications.deleteSubConfirm'), t('product.alerts.notifications.deleteSubTitle'))
    await deleteNotificationSubscription(String(row.id))
    await load()
  }

  onMounted(() => {
    void load(false)
    pollTimer = setInterval(() => {
      if (document.visibilityState === 'hidden') return
      void load(true)
    }, 10000)
  })

  onUnmounted(() => {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  })
</script>

<template>
  <div class="page-card art-card notify-page" v-loading="loading">
    <header class="notify-page__head">
      <div>
        <h2>{{ t('product.alerts.notifications.title') }}</h2>
        <p>
          {{ t('product.alerts.notifications.subtitle') }}
        </p>
      </div>
      <ElButton @click="() => load()">{{ t('common.refresh') }}</ElButton>
    </header>

    <ElAlert
      v-if="!licensed"
      type="warning"
      show-icon
      :closable="false"
      :title="t('product.alerts.notifications.needLicense')"
      style="margin-bottom: 16px"
    />

    <section class="panel">
      <h3>{{ t('product.alerts.notifications.channels') }}</h3>
      <div class="form-row">
        <ElSelect v-model="channelForm.channelType" style="width: 140px">
          <ElOption :label="t('product.alerts.notifications.email')" value="EMAIL" />
          <ElOption label="Webhook" value="WEBHOOK" />
        </ElSelect>
        <ElInput
          v-model="channelForm.address"
          :placeholder="channelForm.channelType === 'EMAIL' ? t('product.alerts.notifications.emailPh') : 'https://example.com/hook'"
          style="flex: 1"
        />
        <ElInput v-model="channelForm.displayName" :placeholder="t('product.alerts.notifications.remarkPh')" style="width: 160px" />
        <ElButton type="primary" :disabled="!licensed" @click="addChannel">{{ t('common.add') }}</ElButton>
      </div>
      <p class="hint">{{ t('product.alerts.notifications.accountEmail', { email: accountEmail || '—' }) }}</p>
      <ElTable :data="pagedChannels" stripe style="margin-top: 12px">
        <ElTableColumn prop="channelType" :label="t('common.type')" width="100" />
        <ElTableColumn prop="address" :label="t('common.address')" min-width="220" show-overflow-tooltip />
        <ElTableColumn prop="displayName" :label="t('common.remark')" width="140" />
        <ElTableColumn :label="t('common.enable')" width="90">
          <template #default="{ row }">
            <ElSwitch :model-value="!!row.enabled" @change="(v) => setChannelEnabled(row, v)" />
          </template>
        </ElTableColumn>
        <ElTableColumn :label="t('common.actions')" width="100">
          <template #default="{ row }">
            <ElButton link type="danger" @click="removeChannel(row)">{{ t('common.delete') }}</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <TablePager
        :total="channelsPageTotal"
        :current="channelsPageCurrent"
        :size="channelsPageSize"
        @update:current="onChannelsPageChange"
        @update:size="onChannelsSizeChange"
      />
    </section>

    <section class="panel">
      <h3>{{ t('product.alerts.notifications.subscriptions') }}</h3>
      <div class="form-grid">
        <ElFormItem :label="t('product.alerts.notifications.channel')">
          <ElSelect v-model="subForm.channelId" :placeholder="t('product.alerts.notifications.selectChannel')" style="width: 100%">
            <ElOption v-for="opt in channelOptions" :key="opt.id" :label="opt.label" :value="opt.id" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem :label="t('product.alerts.notifications.minSeverity')">
          <ElSelect v-model="subForm.minSeverity" style="width: 100%">
            <ElOption label="INFO" value="INFO" />
            <ElOption label="WARNING" value="WARNING" />
            <ElOption label="CRITICAL" value="CRITICAL" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem :label="t('product.alerts.notifications.events')">
          <div class="checks">
            <ElCheckbox v-model="subForm.onAlert">{{ t('product.alerts.notifications.onAlert') }}</ElCheckbox>
            <ElCheckbox v-model="subForm.onRecovery">{{ t('product.alerts.notifications.onRecovery') }}</ElCheckbox>
          </div>
        </ElFormItem>
        <ElFormItem :label="t('product.alerts.notifications.interval')">
          <ElSelect v-model="subForm.throttleSeconds" style="width: 100%">
            <ElOption
              v-for="opt in INTERVAL_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
          <div class="hint">{{ t('product.alerts.notifications.intervalHint') }}</div>
        </ElFormItem>
        <ElFormItem :label="t('product.alerts.notifications.ruleTypes')">
          <ElInput
            v-model="subForm.ruleTypesCsv"
            :placeholder="t('product.alerts.notifications.ruleTypesPh')"
          />
        </ElFormItem>
        <ElFormItem :label="t('product.alerts.notifications.quietHours')">
          <div class="checks">
            <ElCheckbox v-model="subForm.quietHoursEnabled">{{ t('common.enable') }}</ElCheckbox>
            <ElTimeSelect
              v-model="subForm.quietStart"
              start="00:00"
              step="00:30"
              end="23:30"
              :disabled="!subForm.quietHoursEnabled"
              style="width: 110px"
            />
            <span>{{ t('common.to') }}</span>
            <ElTimeSelect
              v-model="subForm.quietEnd"
              start="00:00"
              step="00:30"
              end="23:30"
              :disabled="!subForm.quietHoursEnabled"
              style="width: 110px"
            />
            <ElCheckbox v-model="subForm.allowCritical" :disabled="!subForm.quietHoursEnabled">
              {{ t('product.alerts.notifications.criticalBypass') }}
            </ElCheckbox>
          </div>
        </ElFormItem>
      </div>
      <ElButton type="primary" :disabled="!licensed" @click="addSubscription">{{ t('product.alerts.notifications.addSubscription') }}</ElButton>

      <ElTable :data="pagedSubscriptions" stripe style="margin-top: 16px">
        <ElTableColumn :label="t('product.alerts.notifications.channel')" min-width="180">
          <template #default="{ row }">{{ row.channelType }} · {{ row.channelAddress }}</template>
        </ElTableColumn>
        <ElTableColumn :label="t('product.toaster.alertFallback')" width="90">
          <template #default="{ row }">
            <ElSwitch :model-value="!!row.onAlert" @change="(v) => setSubscriptionField(row, 'onAlert', v)" />
          </template>
        </ElTableColumn>
        <ElTableColumn :label="t('product.alerts.notifications.colRecovery')" width="90">
          <template #default="{ row }">
            <ElSwitch :model-value="!!row.onRecovery" @change="(v) => setSubscriptionField(row, 'onRecovery', v)" />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="minSeverity" :label="t('product.alerts.notifications.minSeverity')" width="110" />
        <ElTableColumn :label="t('product.alerts.notifications.interval')" width="160">
          <template #default="{ row }">
            <ElSelect
              :model-value="Number(row.throttleSeconds) || 0"
              size="small"
              style="width: 140px"
              @change="(v: number) => setSubscriptionInterval(row, v)"
            >
              <ElOption
                v-for="opt in INTERVAL_OPTIONS"
                :key="opt.value"
                :label="intervalLabel(opt.value)"
                :value="opt.value"
              />
            </ElSelect>
          </template>
        </ElTableColumn>
        <ElTableColumn :label="t('common.enable')" width="90">
          <template #default="{ row }">
            <ElSwitch :model-value="!!row.enabled" @change="(v) => setSubscriptionField(row, 'enabled', v)" />
          </template>
        </ElTableColumn>
        <ElTableColumn :label="t('product.alerts.notifications.colAdvanced')" min-width="160">
          <template #default="{ row }">
            <span class="muted">
              {{ row.ruleTypesCsv || t('product.alerts.notifications.allRules') }}
              <template v-if="row.quietHoursJson"> · {{ t('product.alerts.notifications.hasQuiet') }}</template>
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn :label="t('common.actions')" width="100">
          <template #default="{ row }">
            <ElButton link type="danger" @click="removeSubscription(row)">{{ t('common.delete') }}</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <TablePager
        :total="subsPageTotal"
        :current="subsPageCurrent"
        :size="subsPageSize"
        @update:current="onSubsPageChange"
        @update:size="onSubsSizeChange"
      />
    </section>

    <section class="panel">
      <h3>{{ t('product.alerts.notifications.deliveries') }}</h3>
      <p class="hint" style="margin-top: 0">
        {{ t('product.alerts.notifications.deliveriesHint', { count: deliveries.length }) }}
      </p>
      <ElTable :data="pagedRows" stripe :empty-text="t('product.alerts.notifications.emptyDeliveries')">
        <ElTableColumn :label="t('common.title')" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ localizeAlertTitle(row.title, t) }}</template>
        </ElTableColumn>
        <ElTableColumn :label="t('common.message')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{
            row.message ? localizeAlertMessage(row.message, t) : row.lastError || '—'
          }}</template>
        </ElTableColumn>
        <ElTableColumn :label="t('common.type')" width="90">
          <template #default="{ row }">{{ kindLabel(row.eventKind) }}</template>
        </ElTableColumn>
        <ElTableColumn :label="t('common.status')" width="100">
          <template #default="{ row }">
            <ElTag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="attempts" :label="t('product.alerts.notifications.colAttempts')" width="70" />
        <ElTableColumn :label="t('common.time')" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt as string) }}</template>
        </ElTableColumn>
      </ElTable>
      <TablePager
        :total="pageTotal"
        :current="pageCurrent"
        :size="pageSize"
        @update:current="onPageChange"
        @update:size="onSizeChange"
      />
    </section>
  </div>
</template>

<style scoped>
  .notify-page {
    padding: 20px;
    display: flex;
    flex-direction: column;
    gap: 16px;
  }
  .notify-page__head {
    display: flex;
    justify-content: space-between;
    gap: 12px;
  }
  .notify-page__head h2 {
    margin: 0;
  }
  .notify-page__head p {
    margin: 6px 0 0;
    color: var(--el-text-color-secondary);
  }
  .panel {
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 12px;
    padding: 16px;
  }
  .panel h3 {
    margin: 0 0 12px;
    font-size: 15px;
  }
  .form-row {
    display: flex;
    gap: 8px;
    align-items: center;
  }
  .form-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px 16px;
    margin-bottom: 12px;
  }
  .checks {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    align-items: center;
  }
  .hint,
  .muted {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
  .hint {
    margin: 8px 0 0;
  }
  @media (max-width: 900px) {
    .form-row,
    .form-grid {
      grid-template-columns: 1fr;
      flex-direction: column;
      align-items: stretch;
    }
  }
</style>
