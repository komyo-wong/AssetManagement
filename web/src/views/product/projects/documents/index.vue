<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { storeToRefs } from 'pinia'
import { documentsApi, type NamedResource } from '@/api/asset-platform'
import { useTenantContextStore } from '@/store/modules/tenant-context'
import { formatDateTime } from '@/utils/datetime'
import TablePager from '@/components/business/TablePager.vue'
import ProductTableHeader from '@/components/business/ProductTableHeader.vue'
import { useClientPagination } from '@/composables/useClientPagination'
import { useProductTable } from '@/composables/useProductTable'
import { useTableColumns } from '@/hooks/core/useTableColumns'
import type { ColumnOption } from '@/types/component'

const { t } = useI18n()
const { projectScope } = storeToRefs(useTenantContextStore())
const { tableSize, isZebra, isBorder } = useProductTable()
const loading = ref(false)
const rows = ref<NamedResource[]>([])
const {
  current: pageCurrent,
  size: pageSize,
  total: pageTotal,
  pagedRows,
  onPageChange,
  onSizeChange
} = useClientPagination(rows)
const uploadOpen = ref(false)
const title = ref('')
const fileList = ref<File[]>([])
const uploading = ref(false)

const { columnChecks, resetColumns } = useTableColumns(() => {
  const cols: ColumnOption[] = [
    { prop: 'code', label: t('common.code'), checked: true },
    { prop: 'name', label: t('common.title'), checked: true },
    { prop: 'fileName', label: t('common.fileName'), checked: true },
    { prop: 'contentType', label: t('common.type'), checked: true },
    { prop: 'size', label: t('common.size'), checked: true },
    { prop: 'updatedAt', label: t('common.updatedAt'), checked: true },
    { prop: '__actions', label: t('common.actions'), width: 120, fixed: 'right', checked: true, disabled: true }
  ]
  return cols
})
watch(() => t('common.actions'), () => resetColumns())
function docColVisible(prop: string) {
  return columnChecks.value.some((c) => c.prop === prop && (c.checked ?? c.visible ?? true))
}

async function load() {
  if (!projectScope.value) {
    rows.value = []
    return
  }
  loading.value = true
  try {
    rows.value = (await documentsApi.list(projectScope.value)) || []
  } finally {
    loading.value = false
  }
}

function openUpload() {
  title.value = ''
  fileList.value = []
  uploadOpen.value = true
}

function onFileChange(file: { raw?: File }) {
  fileList.value = file.raw ? [file.raw] : []
}

async function submitUpload() {
  if (!projectScope.value) return
  const file = fileList.value[0]
  if (!file) {
    ElMessage.warning(t('product.projects.documents.needFile'))
    return
  }
  uploading.value = true
  try {
    await documentsApi.upload(projectScope.value, file, title.value || undefined)
    ElMessage.success(t('product.projects.documents.uploaded'))
    uploadOpen.value = false
    await load()
  } finally {
    uploading.value = false
  }
}

async function download(row: NamedResource) {
  if (!projectScope.value) return
  const name = String(row.fields?.fileName || row.name || 'download')
  await documentsApi.download(projectScope.value, row.id, name)
}

async function remove(row: NamedResource) {
  if (!projectScope.value) return
  await ElMessageBox.confirm(
    t('product.projects.documents.deleteConfirm', { name: row.name }),
    t('common.deleteConfirm'),
    { type: 'warning' }
  )
  await documentsApi.remove(projectScope.value, row.id)
  ElMessage.success(t('common.deleted'))
  await load()
}

function field(row: NamedResource, key: string) {
  return row.fields?.[key] ?? '-'
}

watch(projectScope, () => void load(), { immediate: true })
onMounted(load)
</script>

<template>
  <div class="page-card art-card" style="padding: 20px" v-loading="loading">
    <div style="display: flex; justify-content: space-between; margin-bottom: 16px">
      <div>
        <h2 style="margin: 0">{{ t('product.projects.documents.title') }}</h2>
        <p style="margin: 4px 0 0; color: var(--el-text-color-secondary)">
          {{ t('product.projects.documents.subtitle') }}
        </p>
      </div>
      <ElButton type="primary" :disabled="!projectScope" @click="openUpload">{{ t('product.projects.documents.upload') }}</ElButton>
    </div>

    <ElEmpty v-if="!projectScope" :description="t('product.projects.documents.emptyScope')" />

    <template v-else>
      <div class="toolbar art-table-card">
        <ProductTableHeader v-model:columns="columnChecks" :loading="loading" full-class="page-card" @refresh="() => load()" />
      </div>
      <ElTable :data="pagedRows" :size="tableSize" :stripe="isZebra" :border="isBorder">
        <ElTableColumn v-if="docColVisible('code')" prop="code" :label="t('common.code')" width="160" />
        <ElTableColumn v-if="docColVisible('name')" prop="name" :label="t('common.title')" min-width="160" />
        <ElTableColumn v-if="docColVisible('fileName')" :label="t('common.fileName')" min-width="180">
          <template #default="{ row }">{{ field(row, 'fileName') }}</template>
        </ElTableColumn>
        <ElTableColumn v-if="docColVisible('contentType')" :label="t('common.type')" width="140">
          <template #default="{ row }">{{ field(row, 'contentType') }}</template>
        </ElTableColumn>
        <ElTableColumn v-if="docColVisible('size')" :label="t('common.size')" width="120">
          <template #default="{ row }">
            {{
              typeof row.fields?.sizeBytes === 'number'
                ? `${Math.max(1, Math.round(Number(row.fields.sizeBytes) / 1024))} KB`
                : '-'
            }}
          </template>
        </ElTableColumn>
        <ElTableColumn v-if="docColVisible('updatedAt')" :label="t('common.updatedAt')" width="180">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </ElTableColumn>
        <ElTableColumn v-if="docColVisible('__actions')" :label="t('common.actions')" width="120" fixed="right">
          <template #default="{ row }">
            <ElButton link type="primary" @click="download(row)">{{ t('common.download') }}</ElButton>
            <ElButton link type="danger" @click="remove(row)">{{ t('common.delete') }}</ElButton>
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

    <ElDialog v-model="uploadOpen" :title="t('product.projects.documents.uploadTitle')" width="480px">
      <ElForm label-position="top">
        <ElFormItem :label="t('product.projects.documents.titleOptional')">
          <ElInput v-model="title" :placeholder="t('product.projects.documents.titlePh')" />
        </ElFormItem>
        <ElFormItem :label="t('common.file')" required>
          <ElUpload :auto-upload="false" :limit="1" :on-change="onFileChange" :on-remove="() => (fileList = [])">
            <ElButton>{{ t('product.projects.documents.selectFile') }}</ElButton>
          </ElUpload>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="uploadOpen = false">{{ t('common.cancel') }}</ElButton>
        <ElButton type="primary" :loading="uploading" @click="submitUpload">{{ t('common.upload') }}</ElButton>
      </template>
    </ElDialog>
  </div>
</template>
