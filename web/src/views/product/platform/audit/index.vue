<script setup lang="ts">
  import { onMounted, ref, watch } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { fetchPlatformAudit, fetchPlatformUsers } from '@/api/asset-platform'
  import { formatDateTime } from '@/utils/datetime'
  import {
    auditActionLabel,
    auditActorLabel,
    auditChangeSummary
  } from '@/utils/audit-display'

  defineOptions({ name: 'PlatformAudit' })

  const { t } = useI18n()
  const rows = ref<Record<string, unknown>[]>([])
  const loading = ref(false)
  const current = ref(1)
  const size = ref(20)
  const total = ref(0)
  const actorFilter = ref('')
  const includeSessionRefresh = ref(false)
  const userOptions = ref<{ label: string; value: string }[]>([])

  async function loadUsers() {
    try {
      const list = await fetchPlatformUsers()
      userOptions.value = (Array.isArray(list) ? list : []).map((u) => {
        const username = String(u.username || '')
        const displayName = String(u.displayName || '')
        return {
          value: username,
          label: displayName && displayName !== username ? `${displayName}（${username}）` : username
        }
      }).filter((o) => o.value)
    } catch {
      userOptions.value = []
    }
  }

  async function load() {
    loading.value = true
    try {
      const page = await fetchPlatformAudit({
        current: current.value,
        size: size.value,
        actor: actorFilter.value.trim() || undefined,
        includeSessionRefresh: includeSessionRefresh.value
      })
      rows.value = page?.records || []
      total.value = Number(page?.total || 0)
    } finally {
      loading.value = false
    }
  }

  function onPageChange(page: number) {
    current.value = page
    void load()
  }

  function onSizeChange(next: number) {
    size.value = next
    current.value = 1
    void load()
  }

  function resetFilters() {
    actorFilter.value = ''
    includeSessionRefresh.value = false
    current.value = 1
    void load()
  }

  function search() {
    current.value = 1
    void load()
  }

  watch(includeSessionRefresh, () => {
    current.value = 1
    void load()
  })

  onMounted(async () => {
    await Promise.all([loadUsers(), load()])
  })
</script>

<template>
  <div class="page-card art-card platform-page" v-loading="loading">
    <header class="platform-page__head">
      <div>
        <h2>{{ t('platformAdmin.audit.title') }}</h2>
        <p>{{ t('platformAdmin.audit.subtitle') }}</p>
      </div>
      <ElButton @click="load">{{ t('platformAdmin.audit.refresh') }}</ElButton>
    </header>

    <div class="filters">
      <ElSelect
        v-model="actorFilter"
        filterable
        clearable
        allow-create
        default-first-option
        :placeholder="t('platformAdmin.audit.filterActorPh')"
        style="width: 260px"
      >
        <ElOption
          v-for="opt in userOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </ElSelect>
      <ElCheckbox v-model="includeSessionRefresh">
        {{ t('platformAdmin.audit.showSessionRefresh') }}
      </ElCheckbox>
      <ElButton type="primary" @click="search">{{ t('platformAdmin.audit.search') }}</ElButton>
      <ElButton @click="resetFilters">{{ t('platformAdmin.audit.reset') }}</ElButton>
    </div>

    <ElTable :data="rows" stripe>
      <ElTableColumn :label="t('platformAdmin.audit.colWho')" min-width="160">
        <template #default="{ row }">
          <div class="who">
            <strong>{{ auditActorLabel(row, t) }}</strong>
          </div>
        </template>
      </ElTableColumn>
      <ElTableColumn :label="t('platformAdmin.audit.colWhat')" min-width="180">
        <template #default="{ row }">
          {{ auditActionLabel(row.action, t) }}
        </template>
      </ElTableColumn>
      <ElTableColumn :label="t('platformAdmin.audit.colChange')" min-width="280" show-overflow-tooltip>
        <template #default="{ row }">
          {{ auditChangeSummary(row, t) }}
        </template>
      </ElTableColumn>
      <ElTableColumn :label="t('platformAdmin.audit.colResult')" width="100">
        <template #default="{ row }">
          <span :class="row.successful ? 'ok' : 'fail'">
            {{
              row.successful
                ? t('platformAdmin.audit.success')
                : t('platformAdmin.audit.failed')
            }}
          </span>
        </template>
      </ElTableColumn>
      <ElTableColumn :label="t('platformAdmin.audit.colWhen')" width="190">
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt as string | null | undefined) }}
        </template>
      </ElTableColumn>
      <template #empty>
        <span class="muted">{{ t('platformAdmin.audit.empty') }}</span>
      </template>
    </ElTable>

    <div class="pager">
      <ElPagination
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :current-page="current"
        :page-size="size"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>
  </div>
</template>

<style scoped>
  .platform-page {
    padding: 20px;
  }

  .platform-page__head {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 16px;
  }

  .platform-page__head h2 {
    margin: 0;
    font-size: 20px;
    font-weight: 650;
  }

  .platform-page__head p {
    margin: 4px 0 0;
    color: var(--el-text-color-secondary);
    font-size: 13px;
    max-width: 640px;
  }

  .filters {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 12px;
    margin-bottom: 16px;
  }

  .who strong {
    font-weight: 600;
  }

  .pager {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }

  .ok {
    color: var(--el-color-success);
  }

  .fail {
    color: var(--el-color-danger);
  }

  .muted {
    color: var(--el-text-color-secondary);
  }
</style>
