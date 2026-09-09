<script setup lang="ts">
  import { computed } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { ElMessage } from 'element-plus'
  import {
    optionById,
    providerLabelKey,
    remember3d,
    rememberProvider,
    usableOption,
    type GeotagMapConfig
  } from './map-style'

  const props = defineProps<{
    config: GeotagMapConfig | null
    provider: string
    threeD: boolean
  }>()

  const emit = defineEmits<{
    'update:provider': [value: string]
    'update:threeD': [value: boolean]
  }>()

  const { t } = useI18n()

  const options = computed(() => props.config?.options || [])
  const current = computed(() => optionById(props.config, props.provider) || options.value[0])
  const can3d = computed(() => !!current.value?.supports3d && usableOption(current.value))

  function label(id: string) {
    const option = optionById(props.config, id)
    const name = t(providerLabelKey(id))
    if (option?.supports3d) return `${name} · 3D`
    return name
  }

  function onProvider(id: string) {
    const option = optionById(props.config, id)
    if (!usableOption(option)) {
      ElMessage.warning(t('product.geotag.mapKeyNeed', {
        provider: t(providerLabelKey(id)),
        url: option?.applyUrl || t('product.geotag.mapSettingsPath')
      }))
      emit('update:provider', props.provider)
      return
    }
    rememberProvider(id)
    emit('update:provider', id)
    if (!option?.supports3d && props.threeD) {
      remember3d(false)
      emit('update:threeD', false)
    }
  }

  function on3d(value: string | number | boolean) {
    const on = value === true
    remember3d(on)
    emit('update:threeD', on)
  }
</script>

<template>
  <div class="basemap-bar">
    <ElSelect :model-value="provider" size="small" class="provider" @change="onProvider">
      <ElOption v-for="option in options" :key="option.id" :label="label(option.id)" :value="option.id" />
    </ElSelect>
    <label v-if="can3d" class="three-d">
      <span>{{ t('product.geotag.map3d') }}</span>
      <ElSwitch :model-value="threeD" @change="on3d" />
    </label>
  </div>
</template>

<style scoped>
  .basemap-bar {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 8px;
    border-radius: 8px;
    background: var(--el-bg-color);
    box-shadow: 0 1px 6px rgb(0 0 0 / 12%);
  }

  .provider {
    width: 230px;
  }

  .three-d {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    color: var(--el-text-color-regular);
    white-space: nowrap;
  }
</style>
