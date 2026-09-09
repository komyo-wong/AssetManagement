<script setup lang="ts">
  import { onUnmounted, ref, watch } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { storeToRefs } from 'pinia'
  import request from '@/utils/http'
  import { useUserStore } from '@/store/modules/user'

  const { t } = useI18n()

  const props = withDefaults(
    defineProps<{
      src?: string | null
      alt?: string
      /** 铺满容器高度；图片仍按 contain 完整显示 */
      fill?: boolean
    }>(),
    { fill: false }
  )

  const emit = defineEmits<{
    load: []
    error: []
  }>()

  const { accessToken, getUserInfo } = storeToRefs(useUserStore())
  const blobUrl = ref('')
  const loading = ref(false)
  const failed = ref(false)

  async function load() {
    failed.value = false
    if (blobUrl.value) {
      URL.revokeObjectURL(blobUrl.value)
      blobUrl.value = ''
    }
    const src = props.src?.trim()
    if (!src) return

    // 外链与本站静态图可直接展示；项目内 /api 文档需要带鉴权拉 blob
    if (!src.includes('/api/v1/')) {
      blobUrl.value = src
      return
    }

    loading.value = true
    try {
      const blob = await request.get<Blob>({
        url: src,
        responseType: 'blob',
        rawBlob: true,
        showErrorMessage: false
      })
      blobUrl.value = URL.createObjectURL(blob)
    } catch {
      failed.value = true
      emit('error')
    } finally {
      loading.value = false
    }
  }

  function onImgLoad() {
    emit('load')
  }

  watch(
    () => [props.src, accessToken.value, getUserInfo.value.userId] as const,
    () => void load(),
    { immediate: true }
  )
  onUnmounted(() => {
    if (blobUrl.value && blobUrl.value.startsWith('blob:')) {
      URL.revokeObjectURL(blobUrl.value)
    }
  })
</script>

<template>
  <div class="auth-blob-image" :class="{ loading, failed, fill }">
    <img
      v-if="blobUrl && !failed"
      :src="blobUrl"
      :alt="alt || 'image'"
      @load="onImgLoad"
    />
    <div v-else-if="failed" class="placeholder">{{ t('product.floorPlan.loadFailed') }}</div>
    <div v-else-if="!src" class="placeholder">{{ t('product.floorPlan.noImage') }}</div>
    <div v-else class="placeholder">{{ t('product.floorPlan.loading') }}</div>
  </div>
</template>

<style scoped>
  .auth-blob-image {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--el-fill-color-lighter);
    overflow: hidden;
  }
  .auth-blob-image img {
    max-width: 100%;
    max-height: 100%;
    object-fit: contain;
    display: block;
  }
  .auth-blob-image.fill img {
    width: 100%;
    height: 100%;
    max-width: none;
    max-height: none;
    object-fit: contain;
  }
  .placeholder {
    color: var(--el-text-color-secondary);
    font-size: 13px;
    padding: 16px;
  }
</style>
