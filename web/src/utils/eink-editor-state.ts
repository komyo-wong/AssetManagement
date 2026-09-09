import type { EinkColorName, EinkFitMode, EinkOrient, EinkProfileId } from './eink-encode'
import type { EinkTextWeight } from './eink-text'
import type { EinkFieldValues } from './eink-templates'

export interface EinkEditorImageBox {
  x: number
  y: number
  w: number
  h: number
}

export interface EinkEditorTextItem {
  id: string
  text: string
  color: EinkColorName
  size: number
  weight: EinkTextWeight
  x: number
  y: number
}

export interface EinkEditorStroke {
  color: EinkColorName
  width: number
  points: Array<{ x: number; y: number }>
}

export interface EinkEditorSnapshot {
  v: 1
  tab: 'template' | 'custom'
  templateId: string
  fieldValues: EinkFieldValues
  orient: EinkOrient
  profile: EinkProfileId | ''
  texts: EinkEditorTextItem[]
  strokes: EinkEditorStroke[]
  image: {
    name: string
    dataUrl: string
    box: EinkEditorImageBox | null
    dither: boolean
    fitMode: EinkFitMode
  } | null
  savedAt: string
  source?: 'push' | 'draft'
}

const STORAGE_PREFIX = 'eink-editor:'

export function einkEditorStorageKey(assetId: string) {
  return `${STORAGE_PREFIX}${assetId}`
}

export function encodeEinkImage(img: HTMLImageElement): string {
  const max = 640
  const sw = img.naturalWidth || img.width || 1
  const sh = img.naturalHeight || img.height || 1
  const scale = Math.min(1, max / Math.max(sw, sh))
  const w = Math.max(1, Math.round(sw * scale))
  const h = Math.max(1, Math.round(sh * scale))
  const canvas = document.createElement('canvas')
  canvas.width = w
  canvas.height = h
  const ctx = canvas.getContext('2d')
  if (!ctx) return ''
  ctx.drawImage(img, 0, 0, w, h)
  return canvas.toDataURL('image/jpeg', 0.82)
}

export function loadEinkImage(dataUrl: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error('eink image'))
    img.src = dataUrl
  })
}

export function readLocalEinkSnapshot(assetId: string): EinkEditorSnapshot | null {
  if (!assetId || typeof localStorage === 'undefined') return null
  try {
    const raw = localStorage.getItem(einkEditorStorageKey(assetId))
    return parseEinkSnapshot(raw)
  } catch {
    return null
  }
}

export function writeLocalEinkSnapshot(assetId: string, snap: EinkEditorSnapshot) {
  if (!assetId || typeof localStorage === 'undefined') return
  try {
    localStorage.setItem(einkEditorStorageKey(assetId), JSON.stringify(snap))
  } catch {
    /* quota */
  }
}

export function parseEinkSnapshot(raw: unknown): EinkEditorSnapshot | null {
  if (!raw) return null
  try {
    const data = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (!data || typeof data !== 'object') return null
    const tab = data.tab === 'custom' ? 'custom' : data.tab === 'template' ? 'template' : ''
    if (!tab) return null
    return data as EinkEditorSnapshot
  } catch {
    return null
  }
}

export function newerEinkSnapshot(
  a: EinkEditorSnapshot | null,
  b: EinkEditorSnapshot | null
): EinkEditorSnapshot | null {
  if (!a) return b
  if (!b) return a
  const ta = Date.parse(a.savedAt || '') || 0
  const tb = Date.parse(b.savedAt || '') || 0
  return tb > ta ? b : a
}
