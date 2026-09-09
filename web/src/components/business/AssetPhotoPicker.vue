<script setup lang="ts">
  import { computed, ref } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { ElMessage } from 'element-plus'
  import type { UploadFile } from 'element-plus'
  import { assetsApi } from '@/api/asset-platform'
  import AssetPhoto from '@/components/business/AssetPhoto.vue'
  import AssetBuiltinPhoto from '@/components/business/AssetBuiltinPhoto.vue'
  import {
    ASSET_PHOTO_GROUPS,
    ASSET_PHOTO_MAX_BYTES,
    builtinPhotoUrl
  } from '@/constants/asset-photos'

  const props = defineProps<{
    modelValue?: string | null
    projectScope: Api.AssetPlatform.ProjectScope | null
  }>()

  const emit = defineEmits<{
    'update:modelValue': [value: string]
  }>()

  const { t } = useI18n()
  const libraryOpen = ref(false)
  const uploading = ref(false)
  const keyword = ref('')

  const current = computed(() => props.modelValue?.trim() || '')

  const filteredGroups = computed(() => {
    const q = keyword.value.trim().toLowerCase()
    return ASSET_PHOTO_GROUPS.map((group) => {
      const ids = group.ids.filter((id) => {
        if (!q) return true
        const label = t(`product.assets.photos.${id}`).toLowerCase()
        return id.includes(q) || label.includes(q)
      })
      return { id: group.id, ids }
    }).filter((group) => group.ids.length > 0)
  })

  function pick(id: string) {
    emit('update:modelValue', builtinPhotoUrl(id))
    libraryOpen.value = false
  }

  function clear() {
    emit('update:modelValue', '')
  }

  async function onUpload(file: UploadFile) {
    const raw = file.raw
    if (!props.projectScope || !raw) return
    if (!raw.type.startsWith('image/')) {
      ElMessage.warning(t('product.assets.list.photoNeedFile'))
      return
    }
    if (raw.size > ASSET_PHOTO_MAX_BYTES) {
      ElMessage.warning(t('product.assets.list.photoTooLarge'))
      return
    }
    uploading.value = true
    try {
      const result = await assetsApi.uploadImage(props.projectScope, raw)
      emit('update:modelValue', result.imageUrl)
      ElMessage.success(t('product.assets.list.photoUploaded'))
    } catch {
      // toasted
    } finally {
      uploading.value = false
    }
  }
</script>

<template>
  <div class="asset-photo-picker">
    <div class="preview">
      <AssetPhoto v-if="current" :src="current" :alt="t('product.assets.list.photo')" />
      <span v-else class="empty">{{ t('product.assets.list.photoEmpty') }}</span>
    </div>
    <div class="actions">
      <ElButton size="small" @click="libraryOpen = true">{{ t('product.assets.list.photoLibrary') }}</ElButton>
      <ElUpload
        :show-file-list="false"
        accept="image/jpeg,image/png,image/webp,image/gif"
        :auto-upload="false"
        :disabled="!projectScope || uploading"
        @change="onUpload"
      >
        <ElButton size="small" :loading="uploading">{{ t('product.assets.list.photoUpload') }}</ElButton>
      </ElUpload>
      <ElButton size="small" :disabled="!current" @click="clear">{{ t('product.assets.list.photoClear') }}</ElButton>
      <p class="hint">{{ t('product.assets.list.photoHint') }}</p>
      <p class="hint">{{ t('product.assets.list.photoShareHint') }}</p>
    </div>
  </div>

  <ElDialog
    v-model="libraryOpen"
    :title="t('product.assets.list.photoLibrary')"
    width="640px"
    append-to-body
    @open="keyword = ''"
  >
    <ElInput
      v-model="keyword"
      clearable
      :placeholder="t('product.assets.list.photoSearch')"
      style="margin-bottom: 12px"
    />
    <div class="library">
      <section v-for="group in filteredGroups" :key="group.id" class="library-group">
        <h4>{{ t(`product.assets.photoGroups.${group.id}`) }}</h4>
        <div class="library-grid">
          <button
            v-for="id in group.ids"
            :key="id"
            type="button"
            class="library-item"
            :class="{ active: current === builtinPhotoUrl(id) }"
            @click="pick(id)"
          >
            <AssetBuiltinPhoto :src="builtinPhotoUrl(id)" />
            <span>{{ t(`product.assets.photos.${id}`) }}</span>
          </button>
        </div>
      </section>
      <p v-if="!filteredGroups.length" class="empty">{{ t('product.assets.list.photoSearchEmpty') }}</p>
    </div>
  </ElDialog>
</template>

<style scoped>
  .asset-photo-picker {
    display: flex;
    gap: 12px;
    align-items: flex-start;
  }
  .preview {
    width: 88px;
    height: 88px;
    border-radius: 12px;
    overflow: hidden;
    background: var(--el-fill-color-lighter);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }
  .empty {
    color: var(--el-text-color-secondary);
    font-size: 12px;
    padding: 8px;
    text-align: center;
  }
  .actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
    min-width: 0;
  }
  .hint {
    width: 100%;
    margin: 0;
    color: var(--el-text-color-secondary);
    font-size: 12px;
    line-height: 1.4;
  }
  .library {
    max-height: 62vh;
    overflow: auto;
    padding-right: 4px;
  }
  .library-group {
    margin-bottom: 14px;
  }
  .library-group h4 {
    margin: 0 0 8px;
    font-size: 13px;
    font-weight: 600;
    color: var(--el-text-color-regular);
  }
  .library-grid {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 8px;
  }
  .library-item {
    border: 1px solid var(--el-border-color);
    background: var(--el-bg-color);
    border-radius: 10px;
    padding: 8px 4px 6px;
    cursor: pointer;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
  }
  .library-item :deep(img) {
    width: 56px;
    height: 56px;
    border-radius: 8px;
  }
  .library-item span {
    font-size: 12px;
    color: var(--el-text-color-regular);
    text-align: center;
    line-height: 1.2;
  }
  .library-item.active,
  .library-item:hover {
    border-color: var(--el-color-primary);
  }
</style>
