<script setup lang="ts">
  import { computed, onMounted, ref } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { fetchPlatformPermissions } from '@/api/asset-platform'
  import {
    isPlatformAdminAssignablePermission,
    permissionDescription,
    permissionModuleLabel,
    permissionName
  } from '@/utils/permission-i18n'
  import TablePager from '@/components/business/TablePager.vue'
  import { useClientPagination } from '@/composables/useClientPagination'

  defineOptions({ name: 'PlatformPermissions' })

  const { t, locale } = useI18n()
  const rows = ref<Record<string, unknown>[]>([])
  const loading = ref(false)
  const keyword = ref('')
  const moduleFilter = ref('')

  const visibleRows = computed(() =>
    rows.value.filter((row) => isPlatformAdminAssignablePermission(String(row.code || '')))
  )

  const modules = computed(() => {
    const set = new Set<string>()
    for (const row of visibleRows.value) set.add(String(row.module || ''))
    return [...set].filter(Boolean).sort()
  })

  const filtered = computed(() => {
    // locale in deps so labels refresh when language switches
    void locale.value
    const q = keyword.value.trim().toLowerCase()
    return visibleRows.value
      .filter((row) => {
        if (moduleFilter.value && String(row.module) !== moduleFilter.value) return false
        if (!q) return true
        const code = String(row.code || '').toLowerCase()
        const name = permissionName(String(row.code), String(row.name || '')).toLowerCase()
        const desc = permissionDescription(
          String(row.code),
          String(row.description || '')
        ).toLowerCase()
        return code.includes(q) || name.includes(q) || desc.includes(q)
      })
      .sort((a, b) => {
        const ma = String(a.module || '')
        const mb = String(b.module || '')
        if (ma !== mb) return ma.localeCompare(mb)
        return String(a.code).localeCompare(String(b.code))
      })
  })

  const {
    current: pageCurrent,
    size: pageSize,
    total: pageTotal,
    pagedRows,
    onPageChange,
    onSizeChange
  } = useClientPagination(filtered, { resetOn: keyword })

  onMounted(async () => {
    loading.value = true
    try {
      rows.value = (await fetchPlatformPermissions()) || []
    } finally {
      loading.value = false
    }
  })
</script>

<template>
  <div class="page-card art-card platform-page" v-loading="loading">
    <header class="platform-page__head">
      <div>
        <h2>{{ t('platformAdmin.permissions.title') }}</h2>
        <p>{{ t('platformAdmin.permissions.subtitle') }}</p>
      </div>
    </header>

    <div class="filters">
      <ElInput
        v-model="keyword"
        clearable
        :placeholder="t('platformAdmin.permissions.search')"
        style="max-width: 280px"
      />
      <ElSelect
        v-model="moduleFilter"
        clearable
        :placeholder="t('platformAdmin.permissions.allModules')"
        style="width: 180px"
      >
        <ElOption
          v-for="m in modules"
          :key="m"
          :label="permissionModuleLabel(m)"
          :value="m"
        />
      </ElSelect>
    </div>

    <ElTable :data="pagedRows" stripe empty-text="">
      <ElTableColumn prop="code" :label="t('platformAdmin.permissions.code')" min-width="220" />
      <ElTableColumn :label="t('platformAdmin.permissions.name')" min-width="160">
        <template #default="{ row }">
          {{ permissionName(String(row.code), String(row.name || '')) }}
        </template>
      </ElTableColumn>
      <ElTableColumn :label="t('platformAdmin.permissions.module')" width="140">
        <template #default="{ row }">
          {{ permissionModuleLabel(String(row.module || ''), String(row.module || '')) }}
        </template>
      </ElTableColumn>
      <ElTableColumn
        :label="t('platformAdmin.permissions.description')"
        min-width="240"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ permissionDescription(String(row.code), String(row.description || '')) }}
        </template>
      </ElTableColumn>
      <template #empty>
        <span class="muted">{{ t('platformAdmin.permissions.empty') }}</span>
      </template>
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

<style scoped>
  .platform-page {
    padding: 20px;
  }

  .platform-page__head {
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
    max-width: 720px;
  }

  .filters {
    display: flex;
    gap: 10px;
    flex-wrap: wrap;
    margin-bottom: 12px;
  }

  .muted {
    color: var(--el-text-color-secondary);
  }
</style>
