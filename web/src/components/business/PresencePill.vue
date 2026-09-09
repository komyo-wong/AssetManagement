<template>
  <span class="presence-pill" :class="tone">{{ label }}</span>
</template>

<script setup lang="ts">
  import { computed } from 'vue'
  import { useI18n } from 'vue-i18n'
  import { presenceLabel } from '@/utils/permission-i18n'

  const props = defineProps<{
    status?: string | null
  }>()

  const { locale } = useI18n()

  const normalized = computed(() => String(props.status || 'UNKNOWN').toUpperCase())

  const tone = computed(() => {
    if (normalized.value === 'ONLINE') return 'online'
    if (normalized.value === 'OFFLINE') return 'offline'
    if (normalized.value === 'UNBOUND' || normalized.value === 'UNKNOWN') return 'neutral'
    if (normalized.value === 'ARCHIVED') return 'neutral'
    return 'neutral'
  })

  const label = computed(() => {
    void locale.value
    return presenceLabel(normalized.value)
  })
</script>

<style scoped>
  .presence-pill {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 52px;
    padding: 2px 10px;
    border-radius: 4px;
    font-size: 12px;
    font-weight: 600;
    line-height: 1.6;
  }

  .presence-pill.online {
    color: #0b6b2f;
    background: #d8f5e2;
  }

  .presence-pill.offline {
    color: #9b1c1c;
    background: #fde2e2;
  }

  .presence-pill.neutral {
    color: #4b5563;
    background: #e5e7eb;
  }
</style>
