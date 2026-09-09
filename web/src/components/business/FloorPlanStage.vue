<script setup lang="ts">
  import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
  import { useI18n } from 'vue-i18n'
  import AuthBlobImage from '@/components/business/AuthBlobImage.vue'

  const { t } = useI18n()

  export type FloorMarker = {
    id: string
    kind: 'gateway' | 'asset'
    label: string
    subLabel?: string
    x: number
    y: number
    status?: string
  }

  const props = defineProps<{
    imageUrl?: string | null
    widthMeters?: number | null
    heightMeters?: number | null
    markers?: FloorMarker[]
    selectedId?: string | null
  }>()

  const emit = defineEmits<{
    select: [id: string]
  }>()

  const frameRef = ref<HTMLElement | null>(null)
  const imageBox = ref<{ left: number; top: number; width: number; height: number } | null>(null)
  const imageAspect = ref<number | null>(null)
  let resizeObserver: ResizeObserver | null = null

  const ready = computed(
    () => !!props.imageUrl && Number(props.widthMeters) > 0 && Number(props.heightMeters) > 0
  )

  /** 锁定图片比例后铺满；米制仅用于坐标换算，避免轮询时比例被清空导致跳动 */
  const frameStyle = computed(() => {
    if (imageAspect.value && imageAspect.value > 0) {
      return { aspectRatio: String(imageAspect.value) }
    }
    const w = Number(props.widthMeters)
    const h = Number(props.heightMeters)
    if (w > 0 && h > 0) return { aspectRatio: `${w} / ${h}` }
    return undefined
  })

  function measureImageBox() {
    const frame = frameRef.value
    if (!frame) return
    const img = frame.querySelector('img')
    if (!img || img.clientWidth <= 0 || img.clientHeight <= 0) {
      // 布局瞬态：保留上次测量，避免标记跳到错误位置
      return
    }
    if (!imageAspect.value && img.naturalWidth > 0 && img.naturalHeight > 0) {
      imageAspect.value = img.naturalWidth / img.naturalHeight
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

  function onImageReady() {
    void nextTick(() => {
      measureImageBox()
      // 比例刚锁定后高度会变，再测一次
      requestAnimationFrame(() => measureImageBox())
    })
  }

  /** 同坐标点用像素扇形微错开，避免按米错开在图上看起来隔很远 */
  const placed = computed(() => {
    const w = Number(props.widthMeters)
    const h = Number(props.heightMeters)
    const box = imageBox.value
    if (!(w > 0 && h > 0) || !box || box.width <= 0 || box.height <= 0) return []

    const buckets = new Map<string, FloorMarker[]>()
    for (const m of props.markers || []) {
      const key = `${Number(m.x).toFixed(2)},${Number(m.y).toFixed(2)}`
      const list = buckets.get(key) || []
      list.push(m)
      buckets.set(key, list)
    }

    const out: Array<FloorMarker & { left: string; top: string }> = []
    for (const group of buckets.values()) {
      group.forEach((m, idx) => {
        const baseX = box.left + (Math.min(w, Math.max(0, m.x)) / w) * box.width
        const baseY = box.top + (Math.min(h, Math.max(0, m.y)) / h) * box.height
        let dx = 0
        let dy = 0
        if (group.length > 1) {
          const angle = (idx / group.length) * Math.PI * 2 - Math.PI / 2
          const radius = 10 + Math.floor(idx / 6) * 6
          dx = Math.cos(angle) * radius
          dy = Math.sin(angle) * radius
        }
        out.push({
          ...m,
          left: `${baseX + dx}px`,
          top: `${baseY + dy}px`
        })
      })
    }
    return out
  })

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
    () => [props.imageUrl, props.widthMeters, props.heightMeters],
    () => {
      imageAspect.value = null
      imageBox.value = null
      void nextTick(() => measureImageBox())
    }
  )

  watch(
    () => props.markers,
    () => void nextTick(() => measureImageBox()),
    { deep: false }
  )
</script>

<template>
  <div class="floor-stage">
    <div v-if="!ready" class="empty">{{ t('product.floorPlan.stageEmpty') }}</div>
    <div v-else ref="frameRef" class="frame" :style="frameStyle">
      <AuthBlobImage :src="imageUrl" :alt="t('product.floorPlan.alt')" fill @load="onImageReady" />
      <button
        v-for="m in placed"
        :key="m.id"
        type="button"
        class="marker"
        :class="[m.kind, String(m.status || '').toLowerCase(), { selected: m.id === selectedId }]"
        :style="{ left: m.left, top: m.top }"
        :title="m.subLabel ? `${m.label} · ${m.subLabel}` : m.label"
        @click.stop="emit('select', m.id)"
      >
        <span class="dot" />
        <span class="label">
          {{ m.label }}
          <small v-if="m.subLabel">{{ m.subLabel }}</small>
        </span>
      </button>
    </div>
  </div>
</template>

<style scoped>
  .floor-stage {
    width: 100%;
  }
  .empty {
    min-height: 280px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--el-text-color-secondary);
    background: var(--el-fill-color-lighter);
    border: 1px solid var(--el-border-color);
  }
  .frame {
    position: relative;
    width: 100%;
    height: auto;
    border: 1px solid var(--el-border-color);
    background: var(--el-fill-color-blank);
    overflow: hidden;
  }
  .frame :deep(.auth-blob-image) {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    min-height: 0;
  }
  .marker {
    position: absolute;
    transform: translate(-50%, -50%);
    border: 0;
    background: transparent;
    padding: 0;
    cursor: pointer;
    z-index: 2;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
  }
  .marker .dot {
    width: 12px;
    height: 12px;
    border-radius: 50%;
    border: 2px solid #fff;
    box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.2);
  }
  .marker.gateway .dot {
    background: #2563eb;
  }
  .marker.asset .dot {
    background: #059669;
  }
  .marker.asset.offline .dot {
    background: #94a3b8;
  }
  .marker.selected .dot {
    width: 16px;
    height: 16px;
    box-shadow: 0 0 0 2px #f59e0b;
  }
  .marker .label {
    max-width: 110px;
    padding: 1px 4px;
    border-radius: 2px;
    background: rgba(255, 255, 255, 0.88);
    color: #0f172a;
    font-size: 11px;
    line-height: 1.2;
    text-align: center;
    display: flex;
    flex-direction: column;
    align-items: center;
  }
  .marker .label small {
    font-size: 10px;
    color: #475569;
    white-space: nowrap;
  }
</style>
