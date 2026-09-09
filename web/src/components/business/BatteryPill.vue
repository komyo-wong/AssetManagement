<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  protocolType?: string | null
  label?: string | null
  percent?: number | null
  level?: number | null
}>()

const { t } = useI18n()
const protocol = computed(() => String(props.protocolType || '').toUpperCase())
const hasReading = computed(() =>
  props.label != null
  || props.level != null
  || props.percent != null
)
const text = computed(() => {
  if (props.label) return String(props.label)
  if (props.level != null) {
    const map = [
      t('product.battery.full'),
      t('product.battery.medium'),
      t('product.battery.low'),
      t('product.battery.critical')
    ]
    return map[Number(props.level)] || t('product.battery.unknown')
  }
  if (props.percent != null) return `${props.percent}%`
  if (protocol.value === 'FINDMY') return t('product.battery.noOfFrame')
  if (protocol.value === 'BEACON') return t('product.battery.noBattery')
  if (protocol.value === 'AUTO') return t('product.battery.detecting')
  return props.protocolType ? '—' : '-'
})
const color = computed(() => {
  if (!hasReading.value) return 'var(--el-text-color-placeholder)'
  const level = props.level == null ? -1 : Number(props.level)
  if (level === 0) return '#16a34a'
  if (level === 1) return '#ca8a04'
  if (level === 2) return '#ea580c'
  if (level === 3) return '#dc2626'
  return 'var(--el-text-color-secondary)'
})
const width = computed(() => {
  if (props.percent != null) return Math.max(0, Math.min(100, Number(props.percent)))
  if (props.level == null) return 0
  return [100, 66, 33, 10][Number(props.level)] ?? 0
})
const title = computed(() => {
  if (protocol.value === 'FINDMY') return `Find My · ${text.value}`
  if (protocol.value === 'BEACON') return `Beacon · ${text.value}`
  if (protocol.value === 'AUTO') return t('product.battery.autoTitle', { text: text.value })
  return text.value
})
</script>

<template>
  <div class="battery" :title="title">
    <span class="battery__bar" aria-hidden="true">
      <span class="battery__fill" :style="{ width: `${hasReading ? width : 0}%`, background: color }" />
    </span>
    <span class="battery__text" :style="{ color }">{{ text }}</span>
  </div>
</template>

<style scoped>
.battery {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 88px;
}
.battery__bar {
  width: 36px;
  height: 10px;
  border: 1px solid color-mix(in srgb, var(--el-border-color) 80%, transparent);
  border-radius: 2px;
  overflow: hidden;
  background: color-mix(in srgb, var(--el-fill-color) 70%, transparent);
  position: relative;
}
.battery__bar::after {
  content: '';
  position: absolute;
  right: -3px;
  top: 2px;
  width: 2px;
  height: 4px;
  border-radius: 0 1px 1px 0;
  background: color-mix(in srgb, var(--el-border-color) 80%, transparent);
}
.battery__fill {
  display: block;
  height: 100%;
  transition: width 0.2s ease;
}
.battery__text {
  font-size: 12px;
  line-height: 1;
  white-space: nowrap;
}
</style>
