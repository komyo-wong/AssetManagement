<script setup lang="ts">
  import AuthBlobImage from '@/components/business/AuthBlobImage.vue'
  import AssetBuiltinPhoto from '@/components/business/AssetBuiltinPhoto.vue'
  import { isBuiltinPhoto } from '@/constants/asset-photos'

  defineProps<{
    src?: string | null
    alt?: string
  }>()
</script>

<template>
  <AssetBuiltinPhoto v-if="isBuiltinPhoto(src)" :src="src" />
  <AuthBlobImage v-else-if="src" :src="src" :alt="alt" fill />
  <div v-else class="asset-photo-empty" />
</template>

<style scoped>
  .asset-photo-empty {
    width: 100%;
    height: 100%;
    background: var(--el-fill-color-lighter);
  }
</style>
