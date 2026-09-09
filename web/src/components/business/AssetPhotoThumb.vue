<script setup lang="ts">
  import { computed } from 'vue'
  import { useI18n } from 'vue-i18n'
  import AssetPhoto from '@/components/business/AssetPhoto.vue'

  const { t } = useI18n()
  const props = defineProps<{
    src?: string | null
    name?: string
  }>()

  const hasPhoto = computed(() => !!props.src?.trim())
</script>

<template>
  <ElPopover
    v-if="hasPhoto"
    trigger="hover"
    placement="right"
    :show-after="160"
    :width="220"
    popper-class="asset-photo-zoom-popper"
  >
    <template #reference>
      <div class="asset-photo-thumb" :title="name">
        <AssetPhoto :src="src" :alt="name" />
      </div>
    </template>
    <div class="asset-photo-zoom">
      <AssetPhoto :src="src" :alt="name" />
    </div>
  </ElPopover>
  <div v-else class="asset-photo-thumb is-empty" :title="t('product.assets.list.photoEmpty')">
    <span />
  </div>
</template>

<style scoped>
  .asset-photo-thumb {
    width: 36px;
    height: 36px;
    border-radius: 8px;
    overflow: hidden;
    background: var(--el-fill-color-lighter);
    cursor: default;
  }
  .asset-photo-thumb.is-empty {
    border: 1px dashed var(--el-border-color);
  }
  .asset-photo-zoom {
    width: 196px;
    height: 196px;
    border-radius: 12px;
    overflow: hidden;
  }
</style>
