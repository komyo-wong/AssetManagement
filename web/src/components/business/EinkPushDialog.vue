<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  fetchAssetEinkLast,
  gatewaysApi,
  pushAssetEinkJob,
  updateBeaconEinkSettings,
  type NamedResource
} from '@/api/asset-platform'
import {
  bytesToBase64,
  editorSize,
  EINK_PALETTE,
  encodeCanvasToPlanes,
  encodeCanvasToZa25Frame,
  fittedImageRect,
  paletteFor,
  quantizeImageData,
  type EinkColorName,
  type EinkFitMode,
  type EinkOrient,
  type EinkProfileId
} from '@/utils/eink-encode'
import {
  EINK_TEMPLATES,
  defaultFieldValues,
  getEinkTemplate,
  paintTemplate,
  type EinkFieldValues
} from '@/utils/eink-templates'
import {
  EINK_TEXT_WEIGHTS,
  clampEinkTextSize,
  clampEinkTextWeight,
  drawEinkText,
  measureEinkText,
  type EinkTextWeight
} from '@/utils/eink-text'
import {
  encodeEinkImage,
  loadEinkImage,
  newerEinkSnapshot,
  parseEinkSnapshot,
  readLocalEinkSnapshot,
  writeLocalEinkSnapshot,
  type EinkEditorSnapshot
} from '@/utils/eink-editor-state'

interface Stroke {
  color: EinkColorName
  width: number
  points: Array<{ x: number; y: number }>
}

interface ImageBox {
  x: number
  y: number
  w: number
  h: number
}

interface TextItem {
  id: string
  text: string
  color: EinkColorName
  size: number
  weight: EinkTextWeight
  x: number
  y: number
}

type EditorTab = 'template' | 'custom'
type CustomTool = 'move' | 'text' | 'draw'
type Selection = { kind: 'image' } | { kind: 'text'; id: string } | null

const props = defineProps<{
  modelValue: boolean
  asset: NamedResource | null
  projectScope: Api.AssetPlatform.ProjectScope | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  pushed: []
}>()

const { t } = useI18n()

const open = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v)
})

const capable = computed(() => !!props.asset?.fields?.einkCapable)
const detectedProfile = computed(() => {
  const raw = String(props.asset?.fields?.einkProfile || '').trim().toLowerCase()
  return raw === 'za25' || raw === 'elnk' ? (raw as EinkProfileId) : ''
})
const profile = ref<EinkProfileId | ''>('')
const activeProfile = computed<EinkProfileId>(() => (profile.value === 'za25' ? 'za25' : 'elnk'))
const canvasRef = ref<HTMLCanvasElement | null>(null)
const fileRef = ref<HTMLInputElement | null>(null)
const busy = ref(false)
const templateId = ref('price')
const orient = ref<EinkOrient>('landscape')
const fieldValues = ref<EinkFieldValues>({})
const passkey = ref('')
const preferredGatewayId = ref('')
const gatewayOptions = ref<NamedResource[]>([])
const imageEl = ref<HTMLImageElement | null>(null)
const imageName = ref('')
const imageBox = ref<ImageBox | null>(null)
const dither = ref(true)
const fitMode = ref<EinkFitMode>('contain')
const penColor = ref<EinkColorName>('black')
const penWidth = ref(5)
const eraser = ref(false)
const strokes = ref<Stroke[]>([])
const drawing = ref(false)
const editorTab = ref<EditorTab>('template')
const customTool = ref<CustomTool>('move')
const textItems = ref<TextItem[]>([])
const draftText = ref('')
const textColor = ref<EinkColorName>('black')
const textSize = ref(18)
const textWeight = ref<EinkTextWeight>(700)
const selected = ref<Selection>(null)
const dragging = ref(false)
const dragOrigin = ref<{ x: number; y: number; boxX: number; boxY: number } | null>(null)
const lastSavedHint = ref('')
let textSeq = 0
let restoring = false
let restored = false
let skipNextDraft = false

const activeTemplate = computed(() => getEinkTemplate(templateId.value))
const activeFields = computed(() => activeTemplate.value?.fields ?? [])
const palettes = computed(() => paletteFor(activeProfile.value))
const canvasCss = computed(() => {
  const { w, h } = editorSize(orient.value, activeProfile.value)
  const scale = w >= 250 ? 1.6 : 1.8
  return { width: `${Math.round(w * scale)}px`, height: `${Math.round(h * scale)}px` }
})
const canvasCursor = computed(() => {
  if (editorTab.value !== 'custom') return ''
  if (customTool.value === 'draw') return 'is-draw'
  if (customTool.value === 'text') return 'is-text'
  return dragging.value ? 'is-move is-dragging' : 'is-move'
})

const previewLabel = computed(() => {
  const { w, h } = editorSize(orient.value, activeProfile.value)
  const model = activeProfile.value === 'za25' ? t('product.assets.list.einkProfileZa25') : t('product.assets.list.einkProfileElnk')
  return t(
    orient.value === 'portrait' ? 'product.assets.list.einkPreviewSizedPortrait' : 'product.assets.list.einkPreviewSizedLandscape',
    { w, h, model }
  )
})

function isMultilineField(key: string, sample: string) {
  return key === 'body' || String(sample).includes('\n')
}

function fieldText(key: string): string {
  return String(fieldValues.value[key] ?? '')
}

function fieldSize(key: string, fallback = 14): number {
  const v = Number(fieldValues.value[`${key}Size`])
  return Number.isFinite(v) && v >= 8 ? Math.round(v) : fallback
}

function setFieldText(key: string, value: string) {
  fieldValues.value = { ...fieldValues.value, [key]: value }
  refreshPreview()
}

function setFieldSize(key: string, value: number | undefined) {
  const n = Number(value)
  fieldValues.value = {
    ...fieldValues.value,
    [`${key}Size`]: Number.isFinite(n) ? Math.max(8, Math.min(72, Math.round(n))) : 14
  }
  refreshPreview()
}

function selectTemplate(id: string) {
  templateId.value = id
  const meta = getEinkTemplate(id)
  fieldValues.value = defaultFieldValues(id)
  if (meta?.orient) {
    orient.value = meta.orient
  }
  void nextTick(() => refreshPreview())
}

function setOrient(next: EinkOrient) {
  orient.value = next
  if (imageEl.value) layoutImage()
  void nextTick(() => refreshPreview())
}

function clampColor(name: EinkColorName): EinkColorName {
  return palettes.value.includes(name) ? name : 'black'
}

function setProfile(next: EinkProfileId) {
  profile.value = next
  if (next === 'elnk') {
    if (penColor.value === 'yellow') penColor.value = 'black'
    if (textColor.value === 'yellow') textColor.value = 'black'
    textItems.value = textItems.value.map((item) =>
      item.color === 'yellow' ? { ...item, color: 'black' } : item
    )
  }
  if (imageEl.value) layoutImage()
  void nextTick(() => refreshPreview())
}

function cssColor(name: EinkColorName) {
  const [r, g, b] = EINK_PALETTE[name]
  return `rgb(${r},${g},${b})`
}

function colorLabel(name: EinkColorName) {
  if (name === 'white') return t('product.assets.list.einkColorWhite')
  if (name === 'yellow') return t('product.assets.list.einkColorYellow')
  if (name === 'red') return t('product.assets.list.einkColorRed')
  return t('product.assets.list.einkColorBlack')
}

function pickPen(name: EinkColorName) {
  eraser.value = false
  penColor.value = name
  customTool.value = 'draw'
}

function pickTextColor(name: EinkColorName) {
  textColor.value = name
  const item = selectedText()
  if (item) {
    item.color = name
    refreshPreview()
  }
}

function setEditorTab(tab: EditorTab) {
  editorTab.value = tab
}

function setCustomTool(tool: CustomTool) {
  editorTab.value = 'custom'
  customTool.value = tool
  drawing.value = false
  if (tool === 'draw') selected.value = null
  void nextTick(() => refreshPreview())
}

function ensureCanvasSize(ctx: CanvasRenderingContext2D) {
  const { w, h } = editorSize(orient.value, activeProfile.value)
  if (ctx.canvas.width !== w || ctx.canvas.height !== h) {
    ctx.canvas.width = w
    ctx.canvas.height = h
  }
  ctx.imageSmoothingEnabled = false
  return { w, h }
}

function fillBlank(ctx: CanvasRenderingContext2D) {
  const { w, h } = ensureCanvasSize(ctx)
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, w, h)
  return { w, h }
}

function layoutImage() {
  const img = imageEl.value
  if (!img) {
    imageBox.value = null
    return
  }
  const { w, h } = editorSize(orient.value, activeProfile.value)
  imageBox.value = fittedImageRect(
    img.naturalWidth || img.width,
    img.naturalHeight || img.height,
    w,
    h,
    fitMode.value
  )
}

function measureTextBox(ctx: CanvasRenderingContext2D, item: TextItem) {
  const box = measureEinkText(ctx, item.text, item.size, item.weight)
  return { x: item.x, y: item.y, w: box.w, h: box.h }
}

function drawTextItem(ctx: CanvasRenderingContext2D, item: TextItem) {
  drawEinkText(ctx, item.text, item.x, item.y, item.size, item.color, 'left', item.weight)
}

function drawSelection(ctx: CanvasRenderingContext2D, box: ImageBox) {
  ctx.save()
  ctx.strokeStyle = '#2563eb'
  ctx.lineWidth = 1
  ctx.setLineDash([3, 2])
  ctx.strokeRect(Math.round(box.x) + 0.5, Math.round(box.y) + 0.5, Math.round(box.w), Math.round(box.h))
  ctx.restore()
}

function refreshPreview(opts?: { chrome?: boolean }) {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d', { willReadFrequently: true })
  if (!ctx) return
  const chrome = opts?.chrome !== false
  if (editorTab.value === 'template') {
    paintTemplate(ctx, templateId.value, fieldValues.value, orient.value, activeProfile.value)
    return
  }
  fillBlank(ctx)
  const img = imageEl.value
  const box = imageBox.value
  if (img && box) {
    ctx.imageSmoothingEnabled = true
    ctx.drawImage(img, box.x, box.y, box.w, box.h)
    const raw = ctx.getImageData(0, 0, canvas.width, canvas.height)
    ctx.putImageData(quantizeImageData(raw, activeProfile.value, dither.value && !dragging.value), 0, 0)
  }
  for (const item of textItems.value) {
    if (item.text.trim()) drawTextItem(ctx, item)
  }
  for (const stroke of strokes.value) {
    drawStroke(ctx, stroke)
  }
  if (!chrome) return
  if (selected.value?.kind === 'image' && imageBox.value) {
    drawSelection(ctx, imageBox.value)
  }
  if (selected.value?.kind === 'text') {
    const item = selectedText()
    if (item) drawSelection(ctx, measureTextBox(ctx, item))
  }
}

function stampDisk(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  radius: number,
  color: EinkColorName
) {
  const [r, g, b] = EINK_PALETTE[color]
  const rad = Math.max(1, Math.round(radius))
  const cx = Math.round(x)
  const cy = Math.round(y)
  ctx.fillStyle = `rgb(${r},${g},${b})`
  const r2 = rad * rad
  for (let dy = -rad; dy <= rad; dy++) {
    for (let dx = -rad; dx <= rad; dx++) {
      if (dx * dx + dy * dy <= r2) {
        ctx.fillRect(cx + dx, cy + dy, 1, 1)
      }
    }
  }
}

function stampSegment(
  ctx: CanvasRenderingContext2D,
  from: { x: number; y: number },
  to: { x: number; y: number },
  radius: number,
  color: EinkColorName
) {
  const dist = Math.hypot(to.x - from.x, to.y - from.y)
  const steps = Math.max(1, Math.ceil(dist))
  for (let s = 0; s <= steps; s++) {
    const t = s / steps
    stampDisk(ctx, from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t, radius, color)
  }
}

function drawStroke(ctx: CanvasRenderingContext2D, stroke: Stroke) {
  if (!stroke.points.length) return
  const radius = Math.max(1, stroke.width / 2)
  let prev = stroke.points[0]
  stampDisk(ctx, prev.x, prev.y, radius, stroke.color)
  for (let i = 1; i < stroke.points.length; i++) {
    stampSegment(ctx, prev, stroke.points[i], radius, stroke.color)
    prev = stroke.points[i]
  }
}

function canvasPoint(ev: PointerEvent) {
  const canvas = canvasRef.value
  if (!canvas) return null
  const r = canvas.getBoundingClientRect()
  if (r.width <= 0 || r.height <= 0) return null
  return {
    x: ((ev.clientX - r.left) * canvas.width) / r.width,
    y: ((ev.clientY - r.top) * canvas.height) / r.height
  }
}

function hitBox(p: { x: number; y: number }, box: ImageBox, pad = 2) {
  return p.x >= box.x - pad && p.x <= box.x + box.w + pad && p.y >= box.y - pad && p.y <= box.y + box.h + pad
}

function selectedText() {
  if (selected.value?.kind !== 'text') return null
  const id = selected.value.id
  return textItems.value.find((item) => item.id === id) || null
}

function hitTest(p: { x: number; y: number }, ctx: CanvasRenderingContext2D): Selection {
  for (let i = textItems.value.length - 1; i >= 0; i--) {
    const item = textItems.value[i]
    if (hitBox(p, measureTextBox(ctx, item))) return { kind: 'text', id: item.id }
  }
  if (imageBox.value && hitBox(p, imageBox.value)) return { kind: 'image' }
  return null
}

function beginDrag(p: { x: number; y: number }) {
  if (selected.value?.kind === 'image' && imageBox.value) {
    dragging.value = true
    dragOrigin.value = { x: p.x, y: p.y, boxX: imageBox.value.x, boxY: imageBox.value.y }
    return
  }
  const item = selectedText()
  if (item) {
    dragging.value = true
    dragOrigin.value = { x: p.x, y: p.y, boxX: item.x, boxY: item.y }
  }
}

function onPointerDown(ev: PointerEvent) {
  if (editorTab.value !== 'custom') return
  const canvas = canvasRef.value
  const p = canvasPoint(ev)
  if (!canvas || !p) return
  ev.preventDefault()
  canvas.setPointerCapture(ev.pointerId)

  if (customTool.value === 'draw') {
    drawing.value = true
    strokes.value.push({
      color: eraser.value ? 'white' : penColor.value,
      width: eraser.value ? Math.max(8, penWidth.value * 2) : penWidth.value,
      points: [p]
    })
    const ctx = canvas.getContext('2d', { willReadFrequently: true })
    if (ctx) {
      const last = strokes.value[strokes.value.length - 1]
      stampDisk(ctx, p.x, p.y, Math.max(1, last.width / 2), last.color)
    }
    return
  }

  const ctx = canvas.getContext('2d', { willReadFrequently: true })
  if (!ctx) return
  const hit = hitTest(p, ctx)
  if (hit) {
    selected.value = hit
    if (hit.kind === 'text') {
      const item = textItems.value.find((row) => row.id === hit.id)
      if (item) {
        draftText.value = item.text
        textColor.value = item.color
        textSize.value = clampEinkTextSize(item.size)
        textWeight.value = clampEinkTextWeight(item.weight)
      }
    }
    beginDrag(p)
    refreshPreview()
    return
  }

  if (customTool.value === 'text' && draftText.value.trim()) {
    placeText(p.x, p.y)
    beginDrag(p)
    return
  }

  selected.value = null
  refreshPreview()
}

function onPointerMove(ev: PointerEvent) {
  if (editorTab.value !== 'custom') return
  const p = canvasPoint(ev)
  if (!p) return

  if (drawing.value && customTool.value === 'draw') {
    const last = strokes.value[strokes.value.length - 1]
    if (!last) return
    const prev = last.points[last.points.length - 1]
    last.points.push(p)
    const canvas = canvasRef.value
    const ctx = canvas?.getContext('2d', { willReadFrequently: true })
    if (ctx) stampSegment(ctx, prev, p, Math.max(1, last.width / 2), last.color)
    return
  }

  if (!dragging.value || !dragOrigin.value) return
  const dx = p.x - dragOrigin.value.x
  const dy = p.y - dragOrigin.value.y
  if (selected.value?.kind === 'image' && imageBox.value) {
    imageBox.value = {
      ...imageBox.value,
      x: dragOrigin.value.boxX + dx,
      y: dragOrigin.value.boxY + dy
    }
    refreshPreview()
    return
  }
  const item = selectedText()
  if (item) {
    item.x = dragOrigin.value.boxX + dx
    item.y = dragOrigin.value.boxY + dy
    refreshPreview()
  }
}

function onPointerUp(ev: PointerEvent) {
  const wasDragging = dragging.value
  drawing.value = false
  dragging.value = false
  dragOrigin.value = null
  try {
    canvasRef.value?.releasePointerCapture(ev.pointerId)
  } catch {
    /* ignore */
  }
  if (wasDragging) refreshPreview()
}

function undoStroke() {
  strokes.value = strokes.value.slice(0, -1)
  refreshPreview()
}

function clearInk() {
  strokes.value = []
  refreshPreview()
}

function clearImage() {
  imageEl.value = null
  imageName.value = ''
  imageBox.value = null
  if (selected.value?.kind === 'image') selected.value = null
  refreshPreview()
}

function onPickImage(ev: Event) {
  const input = ev.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  editorTab.value = 'custom'
  customTool.value = 'move'
  const url = URL.createObjectURL(file)
  const img = new Image()
  img.onload = () => {
    URL.revokeObjectURL(url)
    imageEl.value = img
    imageName.value = file.name
    layoutImage()
    selected.value = { kind: 'image' }
    refreshPreview()
  }
  img.onerror = () => {
    URL.revokeObjectURL(url)
    ElMessage.error(t('product.assets.list.einkImageFail'))
  }
  img.src = url
}

function placeText(x?: number, y?: number) {
  const text = draftText.value.trim()
  if (!text) {
    ElMessage.warning(t('product.assets.list.einkTextNeed'))
    return
  }
  editorTab.value = 'custom'
  const { w, h } = editorSize(orient.value, activeProfile.value)
  const id = `t${++textSeq}`
  const item: TextItem = {
    id,
    text,
    color: clampColor(textColor.value),
    size: clampEinkTextSize(textSize.value),
    weight: clampEinkTextWeight(textWeight.value),
    x: Math.round(x ?? 8),
    y: Math.round(y ?? Math.max(8, h / 2 - textSize.value / 2))
  }
  if (x == null) {
    const canvas = canvasRef.value
    const ctx = canvas?.getContext('2d')
    if (ctx) {
      const box = measureTextBox(ctx, item)
      item.x = Math.max(4, Math.round((w - box.w) / 2))
    }
  }
  textItems.value = [...textItems.value, item]
  selected.value = { kind: 'text', id }
  customTool.value = 'move'
  refreshPreview()
}

function removeText(id: string) {
  textItems.value = textItems.value.filter((item) => item.id !== id)
  if (selected.value?.kind === 'text' && selected.value.id === id) selected.value = null
  refreshPreview()
}

function selectTextRow(item: TextItem) {
  selected.value = { kind: 'text', id: item.id }
  draftText.value = item.text
  textColor.value = item.color
  textSize.value = clampEinkTextSize(item.size)
  textWeight.value = clampEinkTextWeight(item.weight)
  customTool.value = 'move'
  refreshPreview()
}

function setDraftText(value: string) {
  draftText.value = value
  const item = selectedText()
  if (item) {
    item.text = value
    refreshPreview()
  }
}

function setTextSize(value: number | undefined) {
  const size = clampEinkTextSize(value ?? 18)
  textSize.value = size
  const item = selectedText()
  if (item) {
    item.size = size
    refreshPreview()
  }
}

function setTextWeight(value: EinkTextWeight | number) {
  const w = clampEinkTextWeight(Number(value))
  textWeight.value = w
  const item = selectedText()
  if (item) {
    item.weight = w
    refreshPreview()
  }
}

async function loadGateways() {
  if (!props.projectScope) {
    gatewayOptions.value = []
    return
  }
  try {
    gatewayOptions.value = (await gatewaysApi.list(props.projectScope)).filter(
      (gw) => String(gw.fields?.vendor || '').toUpperCase() !== 'HCBG'
    )
  } catch {
    gatewayOptions.value = []
  }
}

function resetFromAsset() {
  const asset = props.asset
  preferredGatewayId.value = String(asset?.fields?.preferredGatewayId || '')
  passkey.value = ''
  profile.value = detectedProfile.value
  imageEl.value = null
  imageName.value = ''
  imageBox.value = null
  strokes.value = []
  textItems.value = []
  draftText.value = ''
  textColor.value = 'black'
  textSize.value = 18
  textWeight.value = 700
  selected.value = null
  customTool.value = 'move'
  editorTab.value = 'template'
  dither.value = true
  fitMode.value = 'contain'
  eraser.value = false
  penColor.value = 'black'
  lastSavedHint.value = ''
  const name = String(asset?.name || asset?.code || '').trim()
  selectTemplate('price')
  if (name) {
    const vals = { ...fieldValues.value }
    if ('name' in vals) vals.name = name
    else if ('title' in vals) vals.title = name
    fieldValues.value = vals
  }
}

function snapshotColor(name: unknown): EinkColorName {
  if (name === 'white' || name === 'yellow' || name === 'red' || name === 'black') {
    return clampColor(name)
  }
  return 'black'
}

function captureEditorState(source: 'push' | 'draft'): EinkEditorSnapshot {
  return {
    v: 1,
    tab: editorTab.value,
    templateId: templateId.value,
    fieldValues: { ...fieldValues.value },
    orient: orient.value,
    profile: profile.value,
    texts: textItems.value.map((item) => ({ ...item })),
    strokes: strokes.value.map((stroke) => ({
      color: stroke.color,
      width: stroke.width,
      points: stroke.points.map((p) => ({ ...p }))
    })),
    image: imageEl.value
      ? {
          name: imageName.value,
          dataUrl: encodeEinkImage(imageEl.value),
          box: imageBox.value ? { ...imageBox.value } : null,
          dither: dither.value,
          fitMode: fitMode.value
        }
      : null,
    savedAt: new Date().toISOString(),
    source
  }
}

function persistDraftIfNeeded() {
  const assetId = props.asset?.id
  if (!assetId || restoring || !restored || skipNextDraft) {
    skipNextDraft = false
    return
  }
  writeLocalEinkSnapshot(assetId, captureEditorState('draft'))
}

function formatSavedAt(iso: string) {
  const t = Date.parse(iso)
  if (!Number.isFinite(t)) return ''
  return new Date(t).toLocaleString()
}

async function applySnapshot(snap: EinkEditorSnapshot) {
  restoring = true
  try {
    editorTab.value = snap.tab === 'custom' ? 'custom' : 'template'
    if (snap.profile === 'za25' || snap.profile === 'elnk') {
      profile.value = snap.profile
    }
    if (snap.orient === 'portrait' || snap.orient === 'landscape') {
      orient.value = snap.orient
    }
    if (snap.templateId) {
      templateId.value = snap.templateId
      fieldValues.value = { ...defaultFieldValues(snap.templateId), ...(snap.fieldValues || {}) }
    }
    textItems.value = (Array.isArray(snap.texts) ? snap.texts : [])
      .map((item, i) => ({
        id: String(item.id || `t${i + 1}`),
        text: String(item.text || ''),
        color: snapshotColor(item.color),
        size: clampEinkTextSize(item.size),
        weight: clampEinkTextWeight(item.weight),
        x: Math.round(Number(item.x) || 0),
        y: Math.round(Number(item.y) || 0)
      }))
      .filter((item) => item.text)
    textSeq = textItems.value.reduce((max, item) => {
      const n = Number(String(item.id).replace(/^t/i, ''))
      return Number.isFinite(n) && n > max ? n : max
    }, 0)
    strokes.value = (Array.isArray(snap.strokes) ? snap.strokes : [])
      .map((stroke) => ({
        color: snapshotColor(stroke.color),
        width: Math.max(1, Math.min(24, Number(stroke.width) || 5)),
        points: Array.isArray(stroke.points)
          ? stroke.points.map((p) => ({ x: Number(p.x) || 0, y: Number(p.y) || 0 }))
          : []
      }))
      .filter((stroke) => stroke.points.length)
    dither.value = snap.image?.dither !== false
    fitMode.value = snap.image?.fitMode === 'cover' ? 'cover' : 'contain'
    imageName.value = snap.image?.name || ''
    const box = snap.image?.box
    imageBox.value =
      box && Number.isFinite(box.w) && Number.isFinite(box.h)
        ? {
            x: Math.round(Number(box.x) || 0),
            y: Math.round(Number(box.y) || 0),
            w: Math.round(box.w),
            h: Math.round(box.h)
          }
        : null
    if (snap.image?.dataUrl) {
      try {
        imageEl.value = await loadEinkImage(snap.image.dataUrl)
        if (!imageBox.value) layoutImage()
      } catch {
        imageEl.value = null
        imageBox.value = null
        imageName.value = ''
      }
    } else {
      imageEl.value = null
    }
    const time = formatSavedAt(snap.savedAt)
    lastSavedHint.value = time
      ? t(snap.source === 'draft' ? 'product.assets.list.einkLastSavedDraft' : 'product.assets.list.einkLastSavedPush', {
          time
        })
      : ''
  } finally {
    restoring = false
  }
}

async function restoreLastEditor() {
  lastSavedHint.value = ''
  const assetId = props.asset?.id
  if (!assetId || !props.projectScope) return
  const local = readLocalEinkSnapshot(assetId)
  let remote: EinkEditorSnapshot | null = null
  try {
    const res = await fetchAssetEinkLast(props.projectScope, assetId)
    remote = parseEinkSnapshot(res?.editor)
    if (remote) {
      remote = {
        ...remote,
        savedAt: remote.savedAt || res?.lastEinkAt || '',
        source: remote.source === 'draft' ? 'draft' : 'push'
      }
    }
  } catch {
    /* 接口未升级时仍可用本机草稿 */
  }
  const snap = newerEinkSnapshot(local, remote)
  if (snap) await applySnapshot(snap)
}

watch(
  () => props.modelValue,
  async (visible) => {
    if (!visible) {
      persistDraftIfNeeded()
      return
    }
    restored = false
    resetFromAsset()
    await loadGateways()
    await restoreLastEditor()
    restored = true
    await nextTick()
    refreshPreview()
  }
)

watch(
  () => props.asset?.id,
  async () => {
    if (!props.modelValue) return
    restored = false
    resetFromAsset()
    await restoreLastEditor()
    restored = true
    await nextTick()
    refreshPreview()
  }
)

watch(dither, () => refreshPreview())

watch(fitMode, () => {
  if (imageEl.value) layoutImage()
  refreshPreview()
})

watch(editorTab, () => {
  drawing.value = false
  dragging.value = false
  void nextTick(() => refreshPreview())
})

async function confirmPush() {
  if (!props.projectScope || !props.asset) return
  if (!capable.value) {
    ElMessage.warning(t('product.assets.list.einkNotCapable'))
    return
  }
  if (!profile.value) {
    ElMessage.warning(t('product.assets.list.einkNeedProfile'))
    return
  }

  const pk = passkey.value.trim()
  if (pk && !/^\d{4,6}$/.test(pk)) {
    ElMessage.warning(t('product.assets.list.einkPasskeyPh'))
    return
  }

  refreshPreview({ chrome: false })
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height)

  const beaconId = String(props.asset.fields?.boundBeaconId || '').trim()
  const firstText = textItems.value.find((item) => item.text.trim())?.text.trim() || ''
  const titleHint = String(
    firstText || fieldValues.value.name || fieldValues.value.title || fieldValues.value.phone || props.asset.name || ''
  ).trim()

  busy.value = true
  try {
    if (beaconId && (pk || preferredGatewayId.value || profile.value)) {
      const payload: {
        preferredGatewayId: string | null
        einkPasskey?: string
        einkProfile?: string
      } = {
        preferredGatewayId: preferredGatewayId.value || null,
        einkProfile: profile.value
      }
      if (pk) payload.einkPasskey = pk
      await updateBeaconEinkSettings(props.projectScope, beaconId, payload)
    }

    const editor = captureEditorState('push')
    const body: {
      orient: EinkOrient
      gatewayId: string | null
      templateId: string
      title: string | null
      profile: EinkProfileId
      editor: Record<string, unknown>
      bw?: string
      red?: string
      frame?: string
    } = {
      orient: orient.value,
      gatewayId: preferredGatewayId.value || null,
      templateId: editorTab.value === 'custom' ? 'custom' : templateId.value,
      title: titleHint || null,
      profile: profile.value,
      editor: editor as unknown as Record<string, unknown>
    }
    if (profile.value === 'za25') {
      body.frame = bytesToBase64(encodeCanvasToZa25Frame(imageData, orient.value))
    } else {
      const { bw, red } = encodeCanvasToPlanes(imageData, orient.value)
      body.bw = bytesToBase64(bw)
      body.red = bytesToBase64(red)
    }

    const result = await pushAssetEinkJob(props.projectScope, props.asset.id, body)
    writeLocalEinkSnapshot(props.asset.id, editor)
    skipNextDraft = true
    ElMessage.success(t('product.assets.list.einkQueued', { jobId: result.jobId }))
    open.value = false
    emit('pushed')
  } finally {
    busy.value = false
    refreshPreview()
  }
}
</script>

<template>
  <ElDialog
    v-model="open"
    :title="t('product.assets.list.einkTitle')"
    width="1100px"
    top="3vh"
    destroy-on-close
    class="eink-push-dialog"
    @opened="refreshPreview"
  >
    <template v-if="!capable">
      <ElAlert type="warning" show-icon :closable="false" :title="t('product.assets.list.einkNotCapable')" />
    </template>

    <div v-else class="eink-editor">
      <input ref="fileRef" type="file" accept="image/*" class="hidden-file" @change="onPickImage" />
      <p class="eink-hint">{{ t('product.assets.list.einkHint') }}</p>
      <p v-if="lastSavedHint" class="eink-restore">{{ lastSavedHint }}</p>

      <div class="profile-row">
        <span class="profile-label">{{ t('product.assets.list.einkProfile') }}</span>
        <div class="orient-seg">
          <button type="button" :class="{ active: profile === 'elnk' }" @click="setProfile('elnk')">
            {{ t('product.assets.list.einkProfileElnk') }}
          </button>
          <button type="button" :class="{ active: profile === 'za25' }" @click="setProfile('za25')">
            {{ t('product.assets.list.einkProfileZa25') }}
          </button>
        </div>
        <span v-if="!profile" class="profile-warn">{{ t('product.assets.list.einkNeedProfile') }}</span>
        <span v-else-if="detectedProfile && detectedProfile !== profile" class="profile-meta">
          {{ t('product.assets.list.einkProfileOverride') }}
        </span>
      </div>

      <div class="mode-row">
        <div class="orient-seg mode-tabs">
          <button type="button" :class="{ active: editorTab === 'template' }" @click="setEditorTab('template')">
            {{ t('product.assets.list.einkTabTemplate') }}
          </button>
          <button type="button" :class="{ active: editorTab === 'custom' }" @click="setEditorTab('custom')">
            {{ t('product.assets.list.einkTabCustom') }}
          </button>
        </div>
        <div class="orient-seg">
          <button type="button" :class="{ active: orient === 'landscape' }" @click="setOrient('landscape')">
            {{ t('product.assets.list.einkLandscape') }}
          </button>
          <button type="button" :class="{ active: orient === 'portrait' }" @click="setOrient('portrait')">
            {{ t('product.assets.list.einkPortrait') }}
          </button>
        </div>
      </div>

      <div class="eink-layout">
        <section class="eink-panel eink-tools">
          <template v-if="editorTab === 'template'">
            <h3>{{ t('product.assets.list.einkTemplates') }}</h3>
            <div class="tpl-grid">
              <button
                v-for="tpl in EINK_TEMPLATES"
                :key="tpl.id"
                type="button"
                class="tpl-card"
                :class="{ active: templateId === tpl.id }"
                @click="selectTemplate(tpl.id)"
              >
                <span class="tpl-card__name">{{ tpl.name }}</span>
                <span class="tpl-card__desc">{{ tpl.desc }}</span>
              </button>
            </div>
            <h3>{{ t('product.assets.list.einkFields') }}</h3>
            <div v-if="!activeFields.length" class="eink-empty-fields">
              {{ t('product.assets.list.einkNoFields') }}
            </div>
            <ElForm v-else label-position="top" class="field-form" @submit.prevent>
              <div v-for="field in activeFields" :key="field.key" class="field-row">
                <ElFormItem :label="field.label" class="field-row__text">
                  <ElInput
                    v-if="isMultilineField(field.key, field.value)"
                    :model-value="fieldText(field.key)"
                    type="textarea"
                    :rows="3"
                    @update:model-value="(v: string) => setFieldText(field.key, v)"
                  />
                  <ElInput
                    v-else
                    :model-value="fieldText(field.key)"
                    @update:model-value="(v: string) => setFieldText(field.key, v)"
                  />
                </ElFormItem>
                <ElFormItem :label="t('product.assets.list.einkFontSize')" class="field-row__size">
                  <ElInputNumber
                    :model-value="fieldSize(field.key, field.size ?? 14)"
                    :min="8"
                    :max="72"
                    controls-position="right"
                    @update:model-value="(v: number | undefined) => setFieldSize(field.key, v)"
                  />
                </ElFormItem>
              </div>
            </ElForm>
          </template>

          <template v-else>
            <p class="tab-hint">{{ t('product.assets.list.einkCustomHint') }}</p>
            <div class="orient-seg tool-seg">
              <button type="button" :class="{ active: customTool === 'move' }" @click="setCustomTool('move')">
                {{ t('product.assets.list.einkToolMove') }}
              </button>
              <button type="button" :class="{ active: customTool === 'text' }" @click="setCustomTool('text')">
                {{ t('product.assets.list.einkToolText') }}
              </button>
              <button type="button" :class="{ active: customTool === 'draw' }" @click="setCustomTool('draw')">
                {{ t('product.assets.list.einkToolDraw') }}
              </button>
            </div>

            <h3>{{ t('product.assets.list.einkImage') }}</h3>
            <div class="tool-row">
              <ElButton type="primary" @click="fileRef?.click()">{{ t('product.assets.list.einkImagePick') }}</ElButton>
              <ElButton :disabled="!imageEl" @click="clearImage">{{ t('product.assets.list.einkImageClear') }}</ElButton>
            </div>
            <p v-if="imageName" class="preview-meta">{{ imageName }} · {{ t('product.assets.list.einkImageMoveHint') }}</p>
            <div class="tool-row">
              <ElCheckbox v-model="dither">{{ t('product.assets.list.einkDither') }}</ElCheckbox>
              <ElRadioGroup v-model="fitMode" size="small">
                <ElRadioButton value="contain">{{ t('product.assets.list.einkFitContain') }}</ElRadioButton>
                <ElRadioButton value="cover">{{ t('product.assets.list.einkFitCover') }}</ElRadioButton>
              </ElRadioGroup>
            </div>

            <h3>{{ t('product.assets.list.einkText') }}</h3>
            <ElInput
              :model-value="draftText"
              type="textarea"
              :rows="2"
              :placeholder="t('product.assets.list.einkTextPh')"
              @update:model-value="setDraftText"
            />
            <div class="swatches">
              <button
                v-for="c in palettes"
                :key="'text-' + c"
                type="button"
                class="swatch"
                :class="[`swatch--${c}`, { active: textColor === c }]"
                @click="pickTextColor(c)"
              >
                <span class="swatch__dot" :style="{ background: cssColor(c) }" />
                <span class="swatch__name">{{ colorLabel(c) }}</span>
              </button>
            </div>
            <div class="tool-row">
              <span class="pen-label">{{ t('product.assets.list.einkFontSize') }}</span>
              <ElInputNumber
                :model-value="textSize"
                :min="8"
                :max="72"
                controls-position="right"
                @update:model-value="setTextSize"
              />
              <ElButton type="primary" @click="placeText()">{{ t('product.assets.list.einkTextPlace') }}</ElButton>
            </div>
            <div class="tool-row">
              <span class="pen-label">{{ t('product.assets.list.einkTextWeight') }}</span>
              <ElRadioGroup :model-value="textWeight" size="small" @update:model-value="setTextWeight">
                <ElRadioButton v-for="w in EINK_TEXT_WEIGHTS" :key="w" :value="w">
                  {{ t(`product.assets.list.einkWeight${w}`) }}
                </ElRadioButton>
              </ElRadioGroup>
            </div>
            <ul v-if="textItems.length" class="text-list">
              <li
                v-for="item in textItems"
                :key="item.id"
                :class="{ active: selected?.kind === 'text' && selected.id === item.id }"
                @click="selectTextRow(item)"
              >
                <span class="text-list__swatch" :style="{ background: cssColor(item.color) }" />
                <span class="text-list__label">{{ item.text }}</span>
                <button type="button" class="text-list__del" @click.stop="removeText(item.id)">
                  {{ t('common.delete') }}
                </button>
              </li>
            </ul>

            <h3>{{ t('product.assets.list.einkDraw') }}</h3>
            <div class="swatches">
              <button
                v-for="c in palettes"
                :key="'pen-' + c"
                type="button"
                class="swatch"
                :class="[`swatch--${c}`, { active: !eraser && penColor === c }]"
                @click="pickPen(c)"
              >
                <span class="swatch__dot" :style="{ background: cssColor(c) }" />
                <span class="swatch__name">{{ colorLabel(c) }}</span>
              </button>
              <button type="button" class="swatch eraser" :class="{ active: eraser }" @click="eraser = true; customTool = 'draw'">
                {{ t('product.assets.list.einkEraser') }}
              </button>
            </div>
            <div class="tool-row">
              <span class="pen-label">{{ t('product.assets.list.einkPenWidth') }}</span>
              <ElSlider v-model="penWidth" :min="1" :max="16" style="flex: 1" />
            </div>
            <div class="tool-row">
              <ElButton size="small" :disabled="!strokes.length" @click="undoStroke">{{ t('product.assets.list.einkUndo') }}</ElButton>
              <ElButton size="small" :disabled="!strokes.length" @click="clearInk">{{ t('product.assets.list.einkClearInk') }}</ElButton>
            </div>
          </template>

          <ElDivider />
          <ElForm label-position="top" @submit.prevent>
            <ElFormItem :label="profile === 'za25' ? t('product.assets.list.einkUnlock') : t('product.assets.list.einkPasskey')">
              <ElInput
                v-model="passkey"
                maxlength="6"
                clearable
                :placeholder="
                  asset?.fields?.einkPasskeyConfigured
                    ? t('product.assets.list.einkPasskeyConfigured')
                    : profile === 'za25'
                      ? t('product.assets.list.einkUnlockPh')
                      : t('product.assets.list.einkPasskeyPh')
                "
              />
            </ElFormItem>
            <ElFormItem :label="t('product.assets.list.einkPreferredGateway')">
              <ElSelect
                v-model="preferredGatewayId"
                clearable
                filterable
                style="width: 100%"
                :placeholder="t('product.assets.list.einkPreferredGatewayPh')"
              >
                <ElOption
                  v-for="gw in gatewayOptions"
                  :key="gw.id"
                  :label="`${gw.name}${gw.fields?.macAddress ? ' · ' + gw.fields.macAddress : ''}`"
                  :value="gw.id"
                />
              </ElSelect>
            </ElFormItem>
          </ElForm>
        </section>

        <section class="eink-panel eink-preview-panel">
          <h3>{{ previewLabel }}</h3>
          <div class="preview-toolbar">
            <ElButton size="small" @click="setCustomTool('move'); fileRef?.click()">
              {{ t('product.assets.list.einkImagePick') }}
            </ElButton>
            <ElButton size="small" @click="setCustomTool('text')">
              {{ t('product.assets.list.einkToolText') }}
            </ElButton>
            <ElButton size="small" @click="setCustomTool('draw')">
              {{ t('product.assets.list.einkToolDraw') }}
            </ElButton>
          </div>
          <div class="preview-frame" :data-orient="orient">
            <canvas
              ref="canvasRef"
              class="eink-canvas"
              :class="canvasCursor"
              :width="editorSize(orient, activeProfile).w"
              :height="editorSize(orient, activeProfile).h"
              :style="canvasCss"
              @pointerdown="onPointerDown"
              @pointermove="onPointerMove"
              @pointerup="onPointerUp"
              @pointercancel="onPointerUp"
            />
          </div>
          <p class="preview-meta">
            {{ asset?.name || asset?.code }}
            <template v-if="asset?.fields?.boundBeaconMac">
              · {{ asset.fields.boundBeaconMac }}
            </template>
          </p>
        </section>
      </div>
    </div>

    <template #footer>
      <ElButton @click="open = false">{{ t('common.cancel') }}</ElButton>
      <ElButton type="primary" :loading="busy" :disabled="!capable || !profile" @click="confirmPush">
        {{ t('product.assets.list.einkSubmit') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.eink-hint {
  margin: 0 0 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.45;
}

.eink-restore {
  margin: -6px 0 12px;
  color: var(--el-color-primary);
  font-size: 13px;
  line-height: 1.4;
}

.profile-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.profile-label {
  font-size: 13px;
  font-weight: 600;
}

.profile-warn {
  color: var(--el-color-warning);
  font-size: 12px;
}

.profile-meta {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.mode-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}

.mode-tabs {
  flex: 1;
  min-width: 220px;
}

.tab-hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.45;
}

.eink-layout {
  display: grid;
  grid-template-columns: minmax(280px, 1fr) auto;
  gap: 12px;
  align-items: start;
}

.eink-panel {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 10px 12px;
  background: var(--el-fill-color-blank);
  min-height: 0;
}

.eink-panel h3 {
  margin: 12px 0 8px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--el-text-color-secondary);
}

.eink-panel h3:first-child {
  margin-top: 0;
}

.orient-seg {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px;
  padding: 3px;
  margin-bottom: 8px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.tool-seg {
  grid-template-columns: 1fr 1fr 1fr;
}

.orient-seg button {
  appearance: none;
  border: none;
  background: transparent;
  border-radius: 6px;
  padding: 7px 6px;
  font: inherit;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  cursor: pointer;
}

.orient-seg button.active {
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.tpl-grid {
  display: flex;
  flex-direction: column;
  gap: 5px;
  max-height: 420px;
  overflow: auto;
}

.tpl-card {
  appearance: none;
  border: 1px solid var(--el-border-color);
  background: var(--el-bg-color);
  border-radius: 8px;
  padding: 8px 10px;
  text-align: left;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.tpl-card:hover {
  border-color: var(--el-color-primary-light-5);
}

.tpl-card.active {
  border-color: var(--el-color-primary);
  color: #fff;
  background: var(--el-color-primary);
}

.tpl-card__name {
  font-size: 13px;
  font-weight: 600;
}

.tpl-card__desc {
  font-size: 11px;
  opacity: 0.75;
}

.eink-empty-fields {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  padding: 8px 0 12px;
}

.eink-tools {
  max-height: calc(100vh - 280px);
  overflow: auto;
}

.field-form {
  max-height: 220px;
  overflow: auto;
  padding-right: 4px;
}

.preview-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.field-row {
  display: grid;
  grid-template-columns: 1fr 88px;
  gap: 8px;
  align-items: start;
}

.field-row__size :deep(.el-input-number) {
  width: 100%;
}

.tool-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.hidden-file {
  display: none;
}

.swatches {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 8px 0 10px;
}

.swatch {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 36px;
  border-radius: 8px;
  border: 1px solid #c8c2b6;
  background: var(--el-bg-color);
  cursor: pointer;
  padding: 4px 10px 4px 6px;
  font: inherit;
  color: var(--el-text-color-primary);
}

.swatch__dot {
  width: 22px;
  height: 22px;
  border-radius: 5px;
  border: 1px solid #c8c2b6;
  flex: 0 0 22px;
}

.swatch--white .swatch__dot {
  box-shadow: inset 0 0 0 1px #d8d2c8;
}

.swatch__name {
  font-size: 13px;
  font-weight: 600;
}

.swatch.active {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 1px;
  border-color: var(--el-color-primary);
}

.swatch.eraser {
  padding: 4px 10px;
  font-size: 13px;
  font-weight: 600;
  background: #f3f0ea;
}

.pen-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.text-list {
  list-style: none;
  margin: 0 0 12px;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.text-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  cursor: pointer;
}

.text-list li.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.text-list__swatch {
  width: 14px;
  height: 14px;
  border-radius: 3px;
  border: 1px solid #c8c2b6;
  flex: 0 0 14px;
}

.text-list__label {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.text-list__del {
  appearance: none;
  border: none;
  background: transparent;
  color: var(--el-color-danger);
  cursor: pointer;
  font-size: 12px;
  padding: 0;
}

.eink-preview-panel {
  min-width: 300px;
}

.preview-frame {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 16px;
  background: linear-gradient(160deg, #f7f4ee 0%, #ebe6db 100%);
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
  touch-action: none;
}

.eink-canvas {
  border: 1px solid #c8c2b6;
  image-rendering: pixelated;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  cursor: default;
  touch-action: none;
}

.eink-canvas.is-draw {
  cursor: crosshair;
}

.eink-canvas.is-text {
  cursor: text;
}

.eink-canvas.is-move {
  cursor: grab;
}

.eink-canvas.is-dragging {
  cursor: grabbing;
}

.preview-meta {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  word-break: break-all;
}

@media (max-width: 900px) {
  .eink-layout {
    grid-template-columns: 1fr;
  }

  .tpl-grid {
    flex-direction: row;
    flex-wrap: wrap;
    max-height: none;
  }

  .tpl-card {
    min-width: 110px;
    flex: 1 1 110px;
  }
}
</style>
