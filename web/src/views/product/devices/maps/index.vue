<script setup lang="ts">
  import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
  import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
  import { storeToRefs } from 'pinia'
  import { useTenantContextStore } from '@/store/modules/tenant-context'
  import { mapsApi, type NamedResource, type ResourceUpsert } from '@/api/asset-platform'
  import { formatDateTime } from '@/utils/datetime'
  import AuthBlobImage from '@/components/business/AuthBlobImage.vue'
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
  const keyword = ref('')
  const drawer = ref(false)
  const editing = ref<NamedResource | null>(null)
  const uploading = ref(false)
  const form = ref<ResourceUpsert>({
    code: '',
    name: '',
    description: '',
    fields: { imageUrl: null, widthMeters: null, heightMeters: null }
  })

  const { columnChecks, resetColumns } = useTableColumns(() => {
    const cols: ColumnOption[] = [
      { prop: 'code', label: t('common.code'), checked: true },
      { prop: 'name', label: t('common.name'), checked: true },
      { prop: 'size', label: t('product.devices.maps.colSize'), checked: true },
      { prop: 'image', label: t('product.devices.maps.colImage'), checked: true },
      { prop: 'zoneCount', label: t('product.devices.maps.colZoneCount'), checked: true },
      { prop: 'zones', label: t('product.devices.maps.colZones'), checked: true },
      { prop: 'updatedAt', label: t('common.updatedAt'), checked: true },
      { prop: '__actions', label: t('common.actions'), width: 120, fixed: 'right', checked: true, disabled: true }
    ]
    return cols
  })
  watch(() => t('common.actions'), () => resetColumns())
  function mapColVisible(prop: string) {
    return columnChecks.value.some((c) => c.prop === prop && (c.checked ?? c.visible ?? true))
  }

  const filteredRows = computed(() => {
    const q = keyword.value.trim().toLowerCase()
    if (!q) return rows.value
    return rows.value.filter((row) => {
      const hay = [row.code, row.name, row.status, row.fields?.zoneNames]
        .flatMap((v) => (Array.isArray(v) ? v : [v]))
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

  async function load() {
    if (!projectScope.value) {
      rows.value = []
      return
    }
    loading.value = true
    try {
      rows.value = await mapsApi.list(projectScope.value)
    } catch {
      rows.value = []
    } finally {
      loading.value = false
    }
  }

  function openCreate() {
    editing.value = null
    form.value = {
      code: '',
      name: '',
      description: '',
      fields: { imageUrl: null, widthMeters: null, heightMeters: null }
    }
    drawer.value = true
  }

  function openEdit(row: NamedResource) {
    editing.value = row
    form.value = {
      code: row.code,
      name: row.name,
      description: '',
      fields: {
        imageUrl: row.fields?.imageUrl ?? null,
        widthMeters: row.fields?.widthMeters ?? null,
        heightMeters: row.fields?.heightMeters ?? null
      }
    }
    drawer.value = true
  }

  async function onUpload(file: UploadFile) {
    if (!projectScope.value || !file.raw) return
    if (!file.raw.type.startsWith('image/')) {
      ElMessage.warning(t('product.devices.maps.needImageFile'))
      return
    }
    uploading.value = true
    try {
      const result = await mapsApi.uploadImage(projectScope.value, file.raw)
      form.value.fields = { ...form.value.fields, imageUrl: result.imageUrl }
      ElMessage.success(t('product.devices.maps.imageUploaded'))
    } catch {
      // toasted
    } finally {
      uploading.value = false
    }
  }

  async function save() {
    if (!projectScope.value) return
    const width = Number(form.value.fields?.widthMeters)
    const height = Number(form.value.fields?.heightMeters)
    if (!form.value.code?.trim() || !form.value.name?.trim()) {
      ElMessage.warning(t('product.devices.maps.needCodeName'))
      return
    }
    if (!(width > 0) || !(height > 0)) {
      ElMessage.warning(t('product.devices.maps.needSize'))
      return
    }
    try {
      const payload: ResourceUpsert = {
        code: form.value.code.trim(),
        name: form.value.name.trim(),
        fields: {
          imageUrl: form.value.fields?.imageUrl || null,
          widthMeters: width,
          heightMeters: height
        }
      }
      if (editing.value) {
        await mapsApi.update(projectScope.value, editing.value.id, payload)
      } else {
        await mapsApi.create(projectScope.value, payload)
      }
      drawer.value = false
      ElMessage.success(t('common.saveSuccess'))
      await load()
    } catch {
      // toasted
    }
  }

  async function doDelete(row: NamedResource) {
    if (!projectScope.value) return
    try {
      await ElMessageBox.confirm(t('product.devices.maps.deleteConfirm', { code: row.code, name: row.name }), t('common.deleteConfirm'), {
        type: 'warning',
        confirmButtonText: t('common.delete'),
        cancelButtonText: t('common.cancel')
      })
      await mapsApi.remove(projectScope.value, row.id)
      ElMessage.success(t('common.deleted'))
      await load()
    } catch {
      // cancelled
    }
  }

  function zoneNames(row: NamedResource) {
    const names = row.fields?.zoneNames
    if (Array.isArray(names) && names.length) return names.map(String).join(', ')
    return '-'
  }

  watch(projectScope, () => void load(), { immediate: true })
</script>

<template>
  <div class="page-card art-card">
    <div class="header">
      <div>
        <h2>{{ t('product.devices.maps.title') }}</h2>
        <p>{{ t('product.devices.maps.subtitle') }}</p>
      </div>
      <ElButton type="primary" :disabled="!projectScope" @click="openCreate">{{ t('product.devices.maps.create') }}</ElButton>
    </div>

      <div class="toolbar art-table-card">
        <ProductTableHeader v-model:columns="columnChecks" :loading="loading" full-class="page-card" @refresh="() => load()">
          <template #left>
            <div class="toolbar-left">
              <ElInput v-model="keyword" clearable :placeholder="t('common.searchPlaceholder')" style="max-width: 320px" />
              <span class="toolbar-count">{{ t('common.totalCount', { count: filteredRows.length }) }}</span>
            </div>
          </template>
        </ProductTableHeader>
      </div>

      <ElTable v-loading="loading" :data="pagedRows" :size="tableSize" :stripe="isZebra" :border="isBorder">
        <ElTableColumn v-if="mapColVisible('code')" prop="code" :label="t('common.code')" width="140" show-overflow-tooltip />
        <ElTableColumn v-if="mapColVisible('name')" prop="name" :label="t('common.name')" show-overflow-tooltip />
        <ElTableColumn v-if="mapColVisible('size')" :label="t('product.devices.maps.colSize')" width="140">
          <template #default="{ row }">
            {{ row.fields?.widthMeters ?? '-' }} × {{ row.fields?.heightMeters ?? '-' }}
          </template>
        </ElTableColumn>
        <ElTableColumn v-if="mapColVisible('image')" :label="t('product.devices.maps.colImage')" width="100">
          <template #default="{ row }">
            {{ row.fields?.imageUrl ? t('product.devices.maps.uploaded') : t('product.devices.maps.notUploaded') }}
          </template>
        </ElTableColumn>
        <ElTableColumn v-if="mapColVisible('zoneCount')" :label="t('product.devices.maps.colZoneCount')" width="110">
          <template #default="{ row }">{{ row.fields?.zoneCount ?? 0 }}</template>
        </ElTableColumn>
        <ElTableColumn v-if="mapColVisible('zones')" :label="t('product.devices.maps.colZones')" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ zoneNames(row) }}</template>
        </ElTableColumn>
        <ElTableColumn v-if="mapColVisible('updatedAt')" :label="t('common.updatedAt')" width="180">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </ElTableColumn>
        <ElTableColumn v-if="mapColVisible('__actions')" :label="t('common.actions')" width="120" fixed="right">
          <template #default="{ row }">
            <ElButton link type="primary" @click="openEdit(row)">{{ t('common.edit') }}</ElButton>
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
    

    <ElDrawer v-model="drawer" :title="editing ? t('product.devices.maps.drawerEdit') : t('product.devices.maps.drawerCreate')" size="480px">
      <ElForm label-position="top">
        <ElFormItem :label="t('common.code')" required>
          <ElInput v-model="form.code" :disabled="!!editing" />
        </ElFormItem>
        <ElFormItem :label="t('common.name')" required>
          <ElInput v-model="form.name" />
        </ElFormItem>
        <ElFormItem :label="t('product.devices.maps.width')" required>
          <ElInputNumber
            :model-value="form.fields?.widthMeters == null ? undefined : Number(form.fields.widthMeters)"
            :min="0.1"
            :step="0.1"
            controls-position="right"
            style="width: 100%"
            @update:model-value="(v) => (form.fields = { ...form.fields, widthMeters: v ?? null })"
          />
        </ElFormItem>
        <ElFormItem :label="t('product.devices.maps.height')" required>
          <ElInputNumber
            :model-value="form.fields?.heightMeters == null ? undefined : Number(form.fields.heightMeters)"
            :min="0.1"
            :step="0.1"
            controls-position="right"
            style="width: 100%"
            @update:model-value="(v) => (form.fields = { ...form.fields, heightMeters: v ?? null })"
          />
        </ElFormItem>
        <ElFormItem :label="t('product.devices.maps.colImage')">
          <ElUpload
            :auto-upload="false"
            :show-file-list="false"
            accept="image/*"
            :disabled="uploading"
            :on-change="onUpload"
          >
            <ElButton :loading="uploading">{{ t('product.devices.maps.uploadImage') }}</ElButton>
          </ElUpload>
          <div class="preview">
            <AuthBlobImage :src="(form.fields?.imageUrl as string | null) || null" :alt="t('product.floorPlan.previewAlt')" />
          </div>
        </ElFormItem>
        <ElFormItem v-if="editing" :label="t('product.devices.maps.colZones')">
          <div>{{ zoneNames(editing) === '-' ? t('product.devices.maps.noZones') : zoneNames(editing) }}</div>
        </ElFormItem>
        <ElButton type="primary" @click="save">{{ t('common.save') }}</ElButton>
      </ElForm>
    </ElDrawer>
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
  .preview {
    margin-top: 12px;
    height: 220px;
    border: 1px solid var(--el-border-color-lighter);
  }
</style>
