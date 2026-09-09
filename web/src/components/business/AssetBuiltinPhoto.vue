<script setup lang="ts">
  import { computed } from 'vue'
  import { builtinPhotoId, builtinPhotoSrc } from '@/constants/asset-photos'

  const props = defineProps<{ src?: string | null }>()
  const id = computed(() => builtinPhotoId(props.src) || 'generic')
  const href = computed(() => builtinPhotoSrc(id.value))
</script>

<template>
  <img class="asset-builtin" :src="href" alt="" />
</template>

<style scoped>
  .asset-builtin {
    width: 100%;
    height: 100%;
    display: block;
    object-fit: cover;
  }
</style>
