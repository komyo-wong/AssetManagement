<script setup lang="ts">
  import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
  import { useI18n } from 'vue-i18n'
  import AuthBlobImage from '@/components/business/AuthBlobImage.vue'

  const { t } = useI18n()

  const props = withDefaults(
    defineProps<{
      imageUrl?: string | null
      widthMeters?: number | null
      heightMeters?: number | null
      x?: number | null
      y?: number | null
      editable?: boolean
    }>(),
    {
      editable: true
    }
  )

  const emit = defineEmits<{
    'update:x': [value: number | null]
    'update:y': [value: number | null]
    pick: [payload: { x: number; y: number }]
  }>()

  const frameRef = ref<HTMLElement | null>(null)
  const imageBox = ref<{ left: number; top: number; width: number; height: number } | null>(null)
  let resizeObserver: ResizeObserver | null = null

  const canPick = computed(
    () =>
      props.editable &&
      !!props.imageUrl &&
      Number(props.widthMeters) > 0 &&
      Number(props.heightMeters) > 0
  )

  function measureImageBox() {
    const frame = frameRef.value
    if (!frame) {
      imageBox.value = null
      return
    }
    const img = frame.querySelector('img')
    if (!img || img.clientWidth <= 0 || img.clientHeight <= 0) {
      imageBox.value = null
      return
    }
    const frameRect = frame.getBoundingClientRect()
    const imgRect = img.getBoundingClientRect()
    imageBox.value = {
      left: imgRect.left - frameRect.left,
      top: imgRect.top - frameRect.top,
      width: imgRect.width,
      height: imgRect.height
    }
  }

  const markerStyle = computed(() => {
    const w = Number(props.widthMeters)
    const h = Number(props.heightMeters)
    const box = imageBox.value
    if (!(w > 0 && h > 0) || props.x == null || props.y == null || !box) return null
    const left = box.left + (Math.min(w, Math.max(0, Number(props.x))) / w) * box.width
    const top = box.top + (Math.min(h, Math.max(0, Number(props.y))) / h) * box.height
    return { left: `${left}px`, top: `${top}px` }
  })

  function onClick(event: MouseEvent) {
    if (!canPick.value || !frameRef.value) return
    const img = frameRef.value.querySelector('img')
    if (!img) return
    const rect = img.getBoundingClientRect()
    if (rect.width <= 0 || rect.height <= 0) return
    const px = event.clientX - rect.left
    const py = event.clientY - rect.top
    if (px < 0 || py < 0 || px > rect.width || py > rect.height) return
    const x = (px / rect.width) * Number(props.widthMeters)
    const y = (py / rect.height) * Number(props.heightMeters)
    const roundedX = Math.round(x * 100) / 100
    const roundedY = Math.round(y * 100) / 100
    emit('update:x', roundedX)
    emit('update:y', roundedY)
    emit('pick', { x: roundedX, y: roundedY })
  }

  onMounted(() => {
    measureImageBox()
    if (frameRef.value && typeof ResizeObserver !== 'undefined') {
      resizeObserver = new ResizeObserver(() => measureImageBox())
      resizeObserver.observe(frameRef.value)
    }
    window.addEventListener('resize', measureImageBox)
  })

  onUnmounted(() => {
    resizeObserver?.disconnect()
    window.removeEventListener('resize', measureImageBox)
  })

  watch(
    () => [props.imageUrl, props.widthMeters, props.heightMeters, props.x, props.y],
    () => void nextTick(() => measureImageBox())
  )
</script>

<template>
  <div class="floor-plan-picker">
    <div
      ref="frameRef"
      class="frame"
      :class="{ pickable: canPick }"
      @click="onClick"
    >
      <AuthBlobImage :src="imageUrl" :alt="t('product.floorPlan.alt')" @load="() => measureImageBox()" />
      <div v-if="markerStyle" class="marker" :style="markerStyle" :title="t('product.floorPlan.installPoint')" />
    </div>
    <p class="hint">
      <template v-if="!imageUrl">{{ t('product.floorPlan.needUpload') }}</template>
      <template v-else-if="!(Number(widthMeters) > 0 && Number(heightMeters) > 0)">
        {{ t('product.floorPlan.needSize') }}
      </template>
      <template v-else-if="editable">{{ t('product.floorPlan.clickPick') }}</template>
      <template v-else>{{ t('product.floorPlan.coordsReadonly', { x: x ?? '-', y: y ?? '-' }) }}</template>
    </p>
    <p v-if="x != null && y != null" class="coords">{{ t('product.floorPlan.currentPoint', { x, y }) }}</p>
  </div>
</template>

<style scoped>
  .floor-plan-picker {
    width: 100%;
  }
  .frame {
    position: relative;
    width: 100%;
    min-height: 220px;
    max-height: 360px;
    border: 1px solid var(--el-border-color);
    background: var(--el-fill-color-blank);
    overflow: hidden;
  }
  .frame.pickable {
    cursor: crosshair;
  }
  .frame :deep(.auth-blob-image) {
    min-height: 220px;
    max-height: 360px;
  }
  .marker {
    position: absolute;
    width: 14px;
    height: 14px;
    transform: translate(-50%, -50%);
    border-radius: 50%;
    background: #e11d48;
    border: 2px solid #fff;
    box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.25);
    pointer-events: none;
    z-index: 2;
  }
  .hint,
  .coords {
    margin: 8px 0 0;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
  .coords {
    color: var(--el-text-color-regular);
  }
</style>
