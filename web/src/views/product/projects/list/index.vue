<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useTenantContextStore } from '@/store/modules/tenant-context'
import { archiveProject, createProject, fetchProjects, updateProject } from '@/api/asset-platform'
import { formatDateTime } from '@/utils/datetime'
import TablePager from '@/components/business/TablePager.vue'
import ProductTableHeader from '@/components/business/ProductTableHeader.vue'
import { useClientPagination } from '@/composables/useClientPagination'
import { useProductTable } from '@/composables/useProductTable'
import { useTableColumns } from '@/hooks/core/useTableColumns'
import type { ColumnOption } from '@/types/component'

const { t } = useI18n()
const { currentTenantId } = storeToRefs(useTenantContextStore())
const { tableSize, isZebra, isBorder } = useProductTable()
const rows = ref<Api.AssetPlatform.ProjectItem[]>([])
const {
  current: pageCurrent,
  size: pageSize,
  total: pageTotal,
  pagedRows,
  onPageChange,
  onSizeChange
} = useClientPagination(rows)
const drawer = ref(false)
const editing = ref<Api.AssetPlatform.ProjectItem | null>(null)
const form = ref({ code: '', name: '', description: '', defaultLocale: 'zh-CN' })

const { columnChecks, resetColumns } = useTableColumns(() => {
  const cols: ColumnOption[] = [
    { prop: 'code', label: t('common.code'), checked: true },
    { prop: 'name', label: t('common.name'), checked: true },
    { prop: 'status', label: t('common.status'), checked: true },
    { prop: 'updatedAt', label: t('common.updatedAt'), checked: true },
    { prop: '__actions', label: t('common.actions'), width: 120, fixed: 'right', checked: true, disabled: true }
  ]
  return cols
})
watch(() => t('common.actions'), () => resetColumns())
function projColVisible(prop: string) {
  return columnChecks.value.some((c) => c.prop === prop && (c.checked ?? c.visible ?? true))
}

async function load() {
  if (!currentTenantId.value) {
    rows.value = []
    return
  }
  rows.value = await fetchProjects(currentTenantId.value)
}

function openCreate() {
  editing.value = null
  form.value = { code: '', name: '', description: '', defaultLocale: 'zh-CN' }
  drawer.value = true
}

function openEdit(row: Api.AssetPlatform.ProjectItem) {
  editing.value = row
  form.value = {
    code: row.code,
    name: row.name,
    description: row.description || '',
    defaultLocale: row.defaultLocale
  }
  drawer.value = true
}

async function save() {
  if (!currentTenantId.value) return
  if (editing.value) await updateProject(currentTenantId.value, editing.value.id, form.value)
  else await createProject(currentTenantId.value, form.value)
  drawer.value = false
  ElMessage.success(t('common.saveSuccess'))
  await load()
}

async function archive(row: Api.AssetPlatform.ProjectItem) {
  if (!currentTenantId.value) return
  if (String(row.status).toLowerCase() === 'archived') {
    ElMessage.info(t('product.projects.list.archiveInfo'))
    return
  }
  await ElMessageBox.confirm(
    t('product.projects.list.archiveConfirm', { name: row.name }),
    t('product.projects.list.archiveTitle'),
    { type: 'warning' }
  )
  await archiveProject(currentTenantId.value, row.id)
  ElMessage.success(t('product.projects.list.archived'))
  await load()
}

watch(currentTenantId, load, { immediate: true })
onMounted(load)
</script>
<template>
  <div class="page-card art-card" style="padding: 20px">
    <div style="display: flex; justify-content: space-between; margin-bottom: 16px">
      <div>
        <h2 style="margin: 0">{{ t('product.projects.list.title') }}</h2>
        <p style="margin: 4px 0 0; color: var(--el-text-color-secondary)">
          {{ t('product.projects.list.subtitle') }}
        </p>
      </div>
      <ElButton type="primary" :disabled="!currentTenantId" @click="openCreate">{{ t('product.projects.list.create') }}</ElButton>
    </div>
    <ElAlert v-if="!currentTenantId" type="warning" :closable="false" :title="t('common.selectTenant')" />
    <template v-else>
      <div class="toolbar art-table-card">
        <ProductTableHeader v-model:columns="columnChecks" full-class="page-card" @refresh="() => load()" />
      </div>
      <ElTable :data="pagedRows" :size="tableSize" :stripe="isZebra" :border="isBorder">
        <ElTableColumn v-if="projColVisible('code')" prop="code" :label="t('common.code')" width="140" />
        <ElTableColumn v-if="projColVisible('name')" prop="name" :label="t('common.name')" />
        <ElTableColumn v-if="projColVisible('status')" prop="status" :label="t('common.status')" width="120" />
        <ElTableColumn v-if="projColVisible('updatedAt')" :label="t('common.updatedAt')" width="200">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </ElTableColumn>
        <ElTableColumn v-if="projColVisible('__actions')" :label="t('common.actions')" width="120" fixed="right">
          <template #default="{ row }">
            <ElButton link type="primary" @click="openEdit(row)">{{ t('common.edit') }}</ElButton>
            <ElButton
              link
              type="danger"
              :disabled="String(row.status).toLowerCase() === 'archived'"
              @click="archive(row)"
            >
              {{ t('product.projects.list.archive') }}
            </ElButton>
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
    </template>
    <ElDrawer v-model="drawer" :title="t('product.projects.list.drawerTitle')" size="420px">
      <ElForm label-position="top">
        <ElFormItem :label="t('common.code')"><ElInput v-model="form.code" :disabled="!!editing" /></ElFormItem>
        <ElFormItem :label="t('common.name')"><ElInput v-model="form.name" /></ElFormItem>
        <ElFormItem :label="t('common.description')"><ElInput v-model="form.description" type="textarea" /></ElFormItem>
        <ElButton type="primary" @click="save">{{ t('common.save') }}</ElButton>
      </ElForm>
    </ElDrawer>
  </div>
</template>
