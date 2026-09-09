/**
 * E-ink text: Noto/PingFang gothic, hard-threshold blit (the look that reads on
 * 128–300px 3/4-color panels). Size is exact; weight uses font-weight plus
 * optional 1px dilation for extra bold.
 */
import { EINK_PALETTE, type EinkColorName } from './eink-encode'

export type EinkTextWeight = 400 | 500 | 700 | 900
export const EINK_TEXT_WEIGHTS: EinkTextWeight[] = [400, 500, 700, 900]

export function clampEinkTextSize(size: number): number {
  const n = Math.round(Number(size) || 14)
  return Math.max(8, Math.min(72, n))
}

export function clampEinkTextWeight(weight: number): EinkTextWeight {
  if (weight <= 400) return 400
  if (weight <= 500) return 500
  if (weight <= 700) return 700
  return 900
}

function fontCss(size: number, weight: EinkTextWeight) {
  return `${weight} ${size}px "Noto Sans SC", "Noto Sans CJK SC", "Noto Sans", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif`
}

function dilateMask(mask: Uint8Array, w: number, h: number) {
  const next = new Uint8Array(mask.length)
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      let on = 0
      for (let dy = -1; dy <= 1 && !on; dy++) {
        for (let dx = -1; dx <= 1 && !on; dx++) {
          const nx = x + dx
          const ny = y + dy
          if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
          if (mask[ny * w + nx]) on = 1
        }
      }
      next[y * w + x] = on
    }
  }
  mask.set(next)
}

function blitLine(
  str: string,
  size: number,
  weight: EinkTextWeight,
  color: EinkColorName
): HTMLCanvasElement | null {
  const off = document.createElement('canvas')
  const c = off.getContext('2d', { willReadFrequently: true })
  if (!c) return null
  const font = fontCss(size, weight)
  c.font = font
  const tw = Math.ceil(Math.max(c.measureText(str).width, 1))
  const th = Math.ceil(size * 1.25)
  const pad = weight >= 900 ? 2 : 1
  off.width = tw + pad * 2
  off.height = th + pad * 2
  c.font = font
  c.textBaseline = 'top'
  c.textAlign = 'left'
  c.imageSmoothingEnabled = false
  c.fillStyle = '#ffffff'
  c.fillRect(0, 0, off.width, off.height)
  c.fillStyle = '#000000'
  c.fillText(str, pad, pad)

  const src = c.getImageData(0, 0, off.width, off.height)
  const pixels = off.width * off.height
  const mask = new Uint8Array(pixels)
  const cut = size <= 18 ? 100 : 140
  for (let i = 0, p = 0; i < src.data.length; i += 4, p++) {
    const lum = 0.299 * src.data[i] + 0.587 * src.data[i + 1] + 0.114 * src.data[i + 2]
    mask[p] = lum < cut ? 1 : 0
  }
  if (weight >= 900) dilateMask(mask, off.width, off.height)

  const out = c.createImageData(off.width, off.height)
  const ink = EINK_PALETTE[color]
  for (let p = 0, i = 0; p < pixels; p++, i += 4) {
    if (!mask[p]) continue
    out.data[i] = ink[0]
    out.data[i + 1] = ink[1]
    out.data[i + 2] = ink[2]
    out.data[i + 3] = 255
  }
  c.putImageData(out, 0, 0)
  return off
}

export function measureEinkText(
  ctx: CanvasRenderingContext2D,
  text: string,
  size: number,
  weight: EinkTextWeight = 700
): { w: number; h: number } {
  size = clampEinkTextSize(size)
  weight = clampEinkTextWeight(weight)
  const lines = String(text || '').split('\n')
  ctx.save()
  ctx.font = fontCss(size, weight)
  let w = 0
  for (const line of lines) {
    w = Math.max(w, Math.ceil(ctx.measureText(line).width))
  }
  ctx.restore()
  const pad = weight >= 900 ? 2 : 0
  return {
    w: Math.ceil(w) + pad,
    h: Math.ceil(size * 1.25) * Math.max(lines.length, 1)
  }
}

export function drawEinkText(
  ctx: CanvasRenderingContext2D,
  text: unknown,
  x: number,
  y: number,
  size: number,
  color: EinkColorName,
  align: CanvasTextAlign = 'left',
  weight: EinkTextWeight = 700
) {
  const str = String(text ?? '')
  if (!str) return
  size = clampEinkTextSize(size)
  weight = clampEinkTextWeight(weight)
  x = Math.round(x)
  y = Math.round(y)
  const lineH = Math.ceil(size * 1.25)
  str.split('\n').forEach((line, i) => {
    if (!line) return
    const glyph = blitLine(line, size, weight, color)
    if (!glyph) return
    const pad = weight >= 900 ? 2 : 1
    const tw = glyph.width - pad * 2
    let dx = x - pad
    if (align === 'center') dx = x - Math.round(tw / 2) - pad
    if (align === 'right') dx = x - tw - pad
    ctx.imageSmoothingEnabled = false
    ctx.drawImage(glyph, dx, y + i * lineH - pad)
  })
}
