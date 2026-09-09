<script setup lang="ts">
  import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import {
    createPlatformOpsBackup,
    deletePlatformOpsBackup,
    downloadPlatformOpsBackup,
    executePlatformOpsCleanup,
    activatePlatformLicense,
    fetchPlatformLicense,
    fetchPlatformOps,
    fetchPlatformOpsCleanupStatus,
    updatePlatformOpsCleanupSchedule,
    previewPlatformOpsCleanup,
    restorePlatformOpsBackup,
    restorePlatformOpsUpload,
    restartPlatformOpsService
  } from '@/api/asset-platform'
  import { fetchGetUserInfo } from '@/api/auth'
  import { useUserStore } from '@/store/modules/user'
  import { formatDateTime } from '@/utils/datetime'

  defineOptions({ name: 'PlatformOps' })

  type HealthRow = { id: string; name: string; up: boolean; detail?: string }
  type CleanupTarget = { id: string; label: string; defaultDays: number; table?: string }
  type CleanupItem = CleanupTarget & {
    days: number
    count?: number
    deleted?: number
    approximate?: boolean
  }
  type CleanupJob = {
    status: string
    running?: boolean
    deleted?: number
    total?: number
    currentTable?: string
    error?: string
    items?: CleanupItem[]
  }
  type BackupRow = { fileName: string; sizeBytes: number; modifiedAt?: string }

  const { t } = useI18n()
  const loading = ref(false)
  const previewing = ref(false)
  const cleaning = ref(false)
  const backingUp = ref(false)
  const restoring = ref(false)
  const restarting = ref('')
  const selectedBackup = ref('')
  const selected = ref<string[]>([])
  const daysByTarget = reactive<Record<string, number>>({})
  const health = ref<HealthRow[]>([])
  const targets = ref<CleanupTarget[]>([])
  const previewItems = ref<CleanupItem[]>([])
  const previewTotal = ref(0)
  const backups = ref<BackupRow[]>([])
  const dockerAvailable = ref(false)
  const tools = ref({ pgDump: false, psql: false })
  const autoCleanup = reactive({ enabled: false, intervalDays: 1, lastRunAt: '', lastDeleted: 0 })
  const savingSchedule = ref(false)
  const cleanupJob = ref<CleanupJob>({ status: 'idle' })
  const previewApproximate = ref(false)
  const license = reactive({
    installId: '',
    activated: false,
    who: '',
    until: '',
    notify: false,
    loginCopyright: false,
    eink: false,
    buzz: false,
    maxBeacons: null as number | null,
    maxGateways: null as number | null
  })
  const licenseToken = ref('')
  const activating = ref(false)
  let pollTimer: ReturnType<typeof setInterval> | null = null

  function applyLicense(data: Record<string, unknown>) {
    license.installId = String(data.installId || '')
    license.activated = !!data.activated
    license.who = String(data.who || '')
    license.until = String(data.until || '')
    license.notify = !!data.notify
    license.loginCopyright = !!data.loginCopyright
    license.eink = !!data.eink
    license.buzz = !!data.buzz
    license.maxBeacons = typeof data.maxBeacons === 'number' ? data.maxBeacons : null
    license.maxGateways = typeof data.maxGateways === 'number' ? data.maxGateways : null
  }

  const dockerRestartable = new Set(['postgres', 'redis', 'mosquitto', 'web'])

  const canBackup = computed(() => tools.value.pgDump && tools.value.psql)

  function formatSize(bytes: number) {
    if (!bytes) return '0 B'
    const units = ['B', 'KB', 'MB', 'GB']
    let n = bytes
    let i = 0
    while (n >= 1024 && i < units.length - 1) {
      n /= 1024
      i += 1
    }
    return `${n.toFixed(i === 0 ? 0 : 1)} ${units[i]}`
  }

  function cleanupPayload() {
    const olderThanDays: Record<string, number> = {}
    for (const id of selected.value) {
      olderThanDays[id] = Number(daysByTarget[id] ?? 7)
    }
    return { targets: selected.value, olderThanDays }
  }

  async function load() {
    loading.value = true
    try {
      const data = await fetchPlatformOps()
      health.value = Array.isArray(data.services) ? (data.services as HealthRow[]) : []
      const docker = (data.docker || {}) as Record<string, unknown>
      dockerAvailable.value = !!docker.available
      const nextTargets = Array.isArray(data.cleanupTargets)
        ? (data.cleanupTargets as CleanupTarget[])
        : []
      targets.value = nextTargets
      const schedule = (data.autoCleanup || {}) as Record<string, unknown>
      autoCleanup.enabled = !!schedule.enabled
      autoCleanup.intervalDays = Number(schedule.intervalDays || schedule.days || 1)
      autoCleanup.lastRunAt = String(schedule.lastRunAt || '')
      autoCleanup.lastDeleted = Number(schedule.lastDeleted || 0)
      const savedRules = Array.isArray(schedule.rules)
        ? (schedule.rules as { id?: string; days?: number }[])
        : []
      if (savedRules.length) {
        selected.value = savedRules
          .map((row) => String(row.id || ''))
          .filter((id) => nextTargets.some((target) => target.id === id))
        for (const row of savedRules) {
          if (row.id) {
            daysByTarget[row.id] = Number(row.days ?? 7)
          }
        }
      } else if (selected.value.length === 0) {
        selected.value = nextTargets.map((row) => row.id)
      }
      for (const row of nextTargets) {
        if (daysByTarget[row.id] == null) {
          daysByTarget[row.id] = row.defaultDays
        }
      }
      backups.value = Array.isArray(data.backups) ? (data.backups as BackupRow[]) : []
      const toolInfo = (data.tools || {}) as Record<string, unknown>
      tools.value = { pgDump: !!toolInfo.pgDump, psql: !!toolInfo.psql }
      applyCleanupJob((data.cleanupJob || {}) as CleanupJob)
      try {
        applyLicense(await fetchPlatformLicense())
      } catch {
        /* 旧后端没有授权接口时忽略 */
      }
    } finally {
      loading.value = false
    }
  }

  function applyCleanupJob(job: CleanupJob) {
    cleanupJob.value = job
    cleaning.value = !!job.running || job.status === 'running'
    if (Array.isArray(job.items) && job.items.length) {
      previewItems.value = job.items
      previewTotal.value = Number(job.total || job.deleted || 0)
    }
    if (job.status === 'running') {
      startPolling()
    } else {
      stopPolling()
    }
  }

  function startPolling() {
    if (pollTimer) return
    pollTimer = setInterval(async () => {
      try {
        const previous = cleanupJob.value.status
        applyCleanupJob((await fetchPlatformOpsCleanupStatus()) as CleanupJob)
        if (previous === 'running' && cleanupJob.value.status === 'succeeded') {
          ElMessage.success(
            t('platformAdmin.ops.cleanupDone', { total: cleanupJob.value.total || 0 })
          )
          await load()
        } else if (previous === 'running' && cleanupJob.value.status === 'failed' && cleanupJob.value.error) {
          ElMessage.error(cleanupJob.value.error)
        }
      } catch {
        // keep polling until the page is closed
      }
    }, 2000)
  }

  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  async function preview() {
    previewing.value = true
    try {
      const data = await previewPlatformOpsCleanup(cleanupPayload())
      previewItems.value = Array.isArray(data.items) ? (data.items as CleanupItem[]) : []
      previewTotal.value = Number(data.total || 0)
      previewApproximate.value = !!data.approximate
    } finally {
      previewing.value = false
    }
  }

  async function cleanup() {
    if (!selected.value.length) return
    try {
      await ElMessageBox.confirm(
        t('platformAdmin.ops.cleanupConfirmBackground'),
        t('platformAdmin.ops.cleanupConfirmTitle'),
        { type: 'warning' }
      )
    } catch {
      return
    }
    cleaning.value = true
    try {
      const data = await executePlatformOpsCleanup(cleanupPayload())
      applyCleanupJob(data as CleanupJob)
      ElMessage.success(t('platformAdmin.ops.cleanupStarted'))
    } catch {
      cleaning.value = false
    }
  }

  async function saveSchedule() {
    if (autoCleanup.enabled && !selected.value.length) {
      ElMessage.warning(t('platformAdmin.ops.autoCleanupNeedRules'))
      return
    }
    savingSchedule.value = true
    try {
      const data = await updatePlatformOpsCleanupSchedule({
        enabled: autoCleanup.enabled,
        intervalDays: Number(autoCleanup.intervalDays || 1),
        rules: selected.value.map((id) => ({ id, days: Number(daysByTarget[id] ?? 7) }))
      })
      autoCleanup.enabled = !!data.enabled
      autoCleanup.intervalDays = Number(data.intervalDays || data.days || 1)
      autoCleanup.lastRunAt = String(data.lastRunAt || '')
      autoCleanup.lastDeleted = Number(data.lastDeleted || 0)
      ElMessage.success(t('platformAdmin.ops.autoCleanupSaved'))
    } finally {
      savingSchedule.value = false
    }
  }

  function targetLabel(row: { id: string; label: string }) {
    const key = `platformAdmin.ops.targets.${row.id}`
    const translated = t(key)
    return translated === key ? row.label : translated
  }

  async function backup() {
    backingUp.value = true
    try {
      await createPlatformOpsBackup()
      ElMessage.success(t('platformAdmin.ops.backupCreated'))
      await load()
    } finally {
      backingUp.value = false
    }
  }

  async function removeBackup(row: BackupRow) {
    try {
      await ElMessageBox.confirm(
        t('platformAdmin.ops.deleteBackupConfirm', { name: row.fileName }),
        t('platformAdmin.ops.delete'),
        { type: 'warning' }
      )
    } catch {
      return
    }
    await deletePlatformOpsBackup(row.fileName)
    if (selectedBackup.value === row.fileName) selectedBackup.value = ''
    ElMessage.success(t('platformAdmin.ops.backupDeleted'))
    await load()
  }

  async function promptToken(message: string, title: string, token: string, missingKey: string) {
    const { value } = await ElMessageBox.prompt(message, title, {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      inputPlaceholder: token,
      type: 'warning',
      inputValidator: (input: string) => (input === token ? true : t(missingKey))
    })
    return String(value || '')
  }

  async function restoreSelected() {
    const name = selectedBackup.value
    if (!name) {
      ElMessage.warning(t('platformAdmin.ops.pickBackup'))
      return
    }
    let confirm = ''
    try {
      confirm = await promptToken(
        t('platformAdmin.ops.restoreConfirm', { name }),
        t('platformAdmin.ops.restoreConfirmTitle'),
        'RESTORE',
        'platformAdmin.ops.restoreNeedToken'
      )
    } catch {
      return
    }
    restoring.value = true
    try {
      await restorePlatformOpsBackup(name, confirm)
      ElMessage.success(t('platformAdmin.ops.restoreAccepted'))
    } finally {
      restoring.value = false
    }
  }

  async function onUpload(file: File) {
    let confirm = ''
    try {
      confirm = await promptToken(
        t('platformAdmin.ops.restoreConfirm', { name: file.name }),
        t('platformAdmin.ops.restoreConfirmTitle'),
        'RESTORE',
        'platformAdmin.ops.restoreNeedToken'
      )
    } catch {
      return false
    }
    restoring.value = true
    try {
      await restorePlatformOpsUpload(file, confirm)
      ElMessage.success(t('platformAdmin.ops.restoreAccepted'))
    } finally {
      restoring.value = false
    }
    return false
  }

  function canRestart(id: string) {
    if (id === 'api' || id === 'worker') return true
    return dockerAvailable.value && dockerRestartable.has(id)
  }

  async function restart(row: HealthRow) {
    if (!canRestart(row.id)) return
    let confirm = ''
    try {
      confirm = await promptToken(
        t('platformAdmin.ops.restartConfirm', { name: row.name }),
        t('platformAdmin.ops.restartConfirmTitle'),
        'RESTART',
        'platformAdmin.ops.restartNeedToken'
      )
    } catch {
      return
    }
    restarting.value = row.id
    try {
      await restartPlatformOpsService(row.id, confirm)
      ElMessage.success(t('platformAdmin.ops.restarting'))
      if (row.id !== 'api') {
        setTimeout(() => void load(), 4000)
      }
    } finally {
      restarting.value = ''
    }
  }

  async function copyInstallId() {
    if (!license.installId) return
    await navigator.clipboard.writeText(license.installId)
    ElMessage.success(t('platformAdmin.ops.licenseCopied'))
  }

  async function activateLicense() {
    const token = licenseToken.value.trim()
    if (!token) {
      ElMessage.warning(t('platformAdmin.ops.licenseTokenPh'))
      return
    }
    activating.value = true
    try {
      applyLicense(await activatePlatformLicense(token))
      licenseToken.value = ''
      try {
        const me = await fetchGetUserInfo()
        useUserStore().setUserInfo(me)
      } catch {
        /* flags refresh is best-effort; API still enforces the license */
      }
      ElMessage.success(t('platformAdmin.ops.licenseActivated'))
    } finally {
      activating.value = false
    }
  }

  function formatCount(row: CleanupTarget) {
    const item = previewItems.value.find((entry) => entry.id === row.id)
    if (!item || item.count == null) return ''
    const value = Number(item.count)
    if (item.approximate || previewApproximate.value) {
      return t('platformAdmin.ops.countMany', { count: value })
    }
    return String(value)
  }

  onMounted(() => {
    void load()
  })

  onUnmounted(() => {
    stopPolling()
  })
</script>

<template>
  <div v-loading="loading" class="ops">
    <header class="ops__head">
      <div>
        <h1>{{ t('platformAdmin.ops.title') }}</h1>
        <p>{{ t('platformAdmin.ops.subtitle') }}</p>
      </div>
      <ElButton @click="load">{{ t('platformAdmin.ops.refresh') }}</ElButton>
    </header>

    <section class="card">
      <h3>{{ t('platformAdmin.ops.license') }}</h3>
      <p class="hint">{{ t('platformAdmin.ops.licenseHint') }}</p>
      <div class="license-row">
        <div>
          <strong>{{ t('platformAdmin.ops.licenseInstallId') }}</strong>
          <code class="install-id">{{ license.installId || '—' }}</code>
        </div>
        <ElButton size="small" :disabled="!license.installId" @click="copyInstallId">
          {{ t('platformAdmin.ops.licenseCopy') }}
        </ElButton>
      </div>
      <p class="hint">
        <template v-if="license.activated">
          {{ license.who ? `${t('platformAdmin.ops.licenseWho')}：${license.who}` : t('platformAdmin.ops.licenseActivated') }}
          <template v-if="license.until"> · {{ t('platformAdmin.ops.licenseUntil', { date: license.until }) }}</template>
        </template>
        <template v-else>{{ t('platformAdmin.ops.licenseNone') }}</template>
        · {{ t('platformAdmin.ops.licenseNotify') }}
        {{ license.notify ? t('platformAdmin.ops.licenseOn') : t('platformAdmin.ops.licenseOff') }}
        · {{ t('platformAdmin.ops.licenseCopyright') }}
        {{ license.loginCopyright ? t('platformAdmin.ops.licenseOn') : t('platformAdmin.ops.licenseOff') }}
        <template v-if="license.maxBeacons != null">
          · {{ t('platformAdmin.ops.licenseMaxBeacons', { count: license.maxBeacons }) }}
        </template>
        <template v-if="license.maxGateways != null">
          · {{ t('platformAdmin.ops.licenseMaxGateways', { count: license.maxGateways }) }}
        </template>
      </p>
      <div class="license-row">
        <ElInput
          v-model="licenseToken"
          type="textarea"
          :rows="2"
          :placeholder="t('platformAdmin.ops.licenseTokenPh')"
        />
        <ElButton type="primary" :loading="activating" @click="activateLicense">
          {{ t('platformAdmin.ops.licenseActivate') }}
        </ElButton>
      </div>
    </section>

    <section class="card">
      <h3>{{ t('platformAdmin.ops.health') }}</h3>
      <p class="hint">{{ t('platformAdmin.ops.healthHint') }}</p>
      <p v-if="!dockerAvailable" class="hint warn">{{ t('platformAdmin.ops.dockerOff') }}</p>
      <div class="health-grid">
        <article v-for="row in health" :key="row.id" class="health">
          <div>
            <strong>{{ row.name }}</strong>
            <span :class="row.up ? 'ok' : 'bad'">
              {{ row.up ? t('platformAdmin.ops.up') : t('platformAdmin.ops.down') }}
            </span>
            <p>{{ row.detail }}</p>
          </div>
          <ElButton
            v-if="canRestart(row.id)"
            size="small"
            type="warning"
            plain
            :loading="restarting === row.id"
            @click="restart(row)"
          >
            {{ t('platformAdmin.ops.restart') }}
          </ElButton>
        </article>
      </div>
    </section>

    <section class="card">
      <div class="card__head">
        <div>
          <h3>{{ t('platformAdmin.ops.cleanup') }}</h3>
          <p class="hint">{{ t('platformAdmin.ops.cleanupHint') }}</p>
          <p class="hint">{{ t('platformAdmin.ops.autoCleanupHint') }}</p>
        </div>
        <div class="actions">
          <ElButton :loading="previewing" @click="preview">{{ t('platformAdmin.ops.preview') }}</ElButton>
          <ElButton
            type="danger"
            :loading="cleaning"
            :disabled="!selected.length || cleanupJob.status === 'running'"
            @click="cleanup"
          >
            {{ t('platformAdmin.ops.executeCleanup') }}
          </ElButton>
          <ElButton type="primary" :loading="savingSchedule" @click="saveSchedule">
            {{ t('platformAdmin.ops.autoCleanupSave') }}
          </ElButton>
        </div>
      </div>
      <ElCheckboxGroup v-model="selected" class="targets">
        <div v-for="row in targets" :key="row.id" class="target">
          <ElCheckbox :value="row.id">{{ targetLabel(row) }}</ElCheckbox>
          <ElInputNumber v-model="daysByTarget[row.id]" :min="0" :max="3650" size="small" />
          <span class="muted">{{ t('platformAdmin.ops.days') }}</span>
          <span v-if="formatCount(row)" class="muted">
            {{ t('platformAdmin.ops.count') }}
            {{ formatCount(row) }}
          </span>
        </div>
      </ElCheckboxGroup>
      <p v-if="cleanupJob.status === 'running'" class="hint">
        {{
          t('platformAdmin.ops.cleanupProgress', {
            total: cleanupJob.deleted || 0,
            table: cleanupJob.currentTable || '—'
          })
        }}
      </p>
      <p v-else-if="cleanupJob.status === 'failed' && cleanupJob.error" class="hint warn">
        {{ cleanupJob.error }}
      </p>
      <div class="schedule">
        <span>{{ t('platformAdmin.ops.autoCleanupEnable') }}</span>
        <ElSwitch v-model="autoCleanup.enabled" />
        <span class="muted">{{ t('platformAdmin.ops.autoCleanupInterval') }}</span>
        <ElInputNumber v-model="autoCleanup.intervalDays" :min="1" :max="3650" size="small" />
        <p class="hint">
          {{
            autoCleanup.lastRunAt
              ? t('platformAdmin.ops.autoCleanupLastRun', {
                  total: autoCleanup.lastDeleted,
                  time: formatDateTime(autoCleanup.lastRunAt)
                })
              : t('platformAdmin.ops.autoCleanupNever')
          }}
        </p>
      </div>
    </section>

    <section class="card">
      <div class="card__head">
        <div>
          <h3>{{ t('platformAdmin.ops.backup') }}</h3>
          <p v-if="!canBackup" class="hint warn">{{ t('platformAdmin.ops.toolsMissing') }}</p>
        </div>
        <ElButton type="primary" :loading="backingUp" :disabled="!canBackup" @click="backup">
          {{ t('platformAdmin.ops.createBackup') }}
        </ElButton>
      </div>
      <ElTable
        :data="backups"
        stripe
        highlight-current-row
        empty-text="—"
        @current-change="(row: BackupRow | null) => (selectedBackup = row?.fileName || '')"
      >
        <ElTableColumn :label="t('platformAdmin.ops.fileName')" prop="fileName" min-width="280" />
        <ElTableColumn :label="t('platformAdmin.ops.size')" width="120">
          <template #default="{ row }">{{ formatSize(row.sizeBytes) }}</template>
        </ElTableColumn>
        <ElTableColumn :label="t('platformAdmin.ops.modifiedAt')" prop="modifiedAt" min-width="180" />
        <ElTableColumn width="200">
          <template #default="{ row }">
            <ElButton link type="primary" @click="downloadPlatformOpsBackup(row.fileName)">
              {{ t('platformAdmin.ops.download') }}
            </ElButton>
            <ElButton link type="danger" @click="removeBackup(row)">
              {{ t('platformAdmin.ops.delete') }}
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <p v-if="!backups.length" class="hint">{{ t('platformAdmin.ops.noBackups') }}</p>
    </section>

    <section class="card danger">
      <h3>{{ t('platformAdmin.ops.restore') }}</h3>
      <p class="hint">{{ t('platformAdmin.ops.restoreHint') }}</p>
      <div class="actions">
        <ElButton type="danger" :loading="restoring" :disabled="!canBackup" @click="restoreSelected">
          {{ t('platformAdmin.ops.restoreFromSelected') }}
        </ElButton>
        <ElUpload :show-file-list="false" accept=".sql,.sql.gz,.gz,.tar.gz,.tgz" :before-upload="onUpload" :disabled="restoring || !canBackup">
          <ElButton type="danger" plain :loading="restoring" :disabled="!canBackup">
            {{ t('platformAdmin.ops.restoreUpload') }}
          </ElButton>
        </ElUpload>
      </div>
    </section>
  </div>
</template>

<style scoped>
  .ops {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .ops__head {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    align-items: flex-start;
  }

  .ops__head h1,
  .card h3 {
    margin: 0;
  }

  .ops__head h1 {
    font-size: 22px;
    font-weight: 650;
  }

  .ops__head p,
  .hint {
    margin: 6px 0 0;
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }

  .license-row {
    display: flex;
    align-items: flex-start;
    gap: 12px;
    margin-top: 12px;
  }

  .license-row .el-textarea,
  .license-row .el-input {
    flex: 1;
  }

  .install-id {
    display: block;
    margin-top: 4px;
    font-size: 13px;
    word-break: break-all;
  }

  .card {
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color-light);
    border-radius: 12px;
    padding: 16px;
  }

  .card.danger {
    border-color: var(--el-color-danger-light-5);
  }

  .card__head {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    align-items: flex-start;
    margin-bottom: 12px;
  }

  .health-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    gap: 10px;
    margin-top: 12px;
  }

  .health {
    display: flex;
    justify-content: space-between;
    gap: 8px;
    align-items: flex-start;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 10px;
    padding: 10px 12px;
  }

  .health p {
    margin: 4px 0 0;
    color: var(--el-text-color-secondary);
    font-size: 12px;
    word-break: break-all;
  }

  .ok {
    color: var(--el-color-success);
    margin-left: 8px;
    font-size: 12px;
  }

  .bad,
  .warn {
    color: var(--el-color-danger);
  }

  .warn {
    margin-left: 0;
  }

  .targets {
    display: flex;
    flex-direction: column;
    gap: 8px;
    width: 100%;
  }

  .target {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  .muted {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }

  .actions {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
    align-items: center;
    margin-top: 12px;
  }

  .schedule {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
    margin-top: 16px;
    padding-top: 14px;
    border-top: 1px solid var(--el-border-color-lighter);
  }

  .schedule .hint {
    width: 100%;
    margin: 0;
  }

  .card__head .actions {
    margin-top: 0;
  }
</style>
