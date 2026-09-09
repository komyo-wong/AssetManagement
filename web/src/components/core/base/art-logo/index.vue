<!-- 系统logo -->
<template>
  <div class="flex-cc">
    <img :style="logoStyle" :src="logoSrc" alt="logo" class="w-full h-full object-contain" />
  </div>
</template>

<script setup lang="ts">
  import defaultLogo from '@imgs/common/logo.webp'
  import { useBrandingStore } from '@/store/modules/branding'

  defineOptions({ name: 'ArtLogo' })

  interface Props {
    /** logo 大小 */
    size?: number | string
    /** 覆盖品牌配置的自定义地址 */
    src?: string
  }

  const props = withDefaults(defineProps<Props>(), {
    size: 36,
    src: ''
  })

  const brandingStore = useBrandingStore()
  const logoSrc = computed(() => props.src || brandingStore.titleLogo || defaultLogo)
  const logoStyle = computed(() => ({ width: `${props.size}px` }))
</script>
