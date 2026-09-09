/**
 * GeoTag e-ink encode:
 *  - elnk: 128×250 BWR, two 4000-byte planes
 *  - za25: 200×300 BWRY 2bpp, one 15000-byte frame
 *    (panel Y origin is inverted; keep X as-is — horizontal mirror flips the badge text)
 */

export type EinkOrient = 'landscape' | 'portrait'
export type EinkProfileId = 'elnk' | 'za25'
export type EinkColorName = 'black' | 'white' | 'red' | 'yellow'
export type EinkFitMode = 'contain' | 'cover'

export const EINK_DEV_W = 128
export const EINK_DEV_H = 250
export const EINK_PLANE_LEN = 4000
export const EINK_ROW_BYTES = EINK_DEV_W / 8

export const ZA25_DEV_W = 200
export const ZA25_DEV_H = 300
export const ZA25_FRAME_LEN = 15000
export const ZA25_ROW_BYTES = ZA25_DEV_W / 4

export const PIX_BLACK = 0
export const PIX_WHITE = 1
export const PIX_YELLOW = 2
export const PIX_RED = 3

export const EINK_PALETTE: Record<EinkColorName, [number, number, number]> = {
  black: [0, 0, 0],
  white: [255, 255, 255],
  red: [196, 60, 40],
  yellow: [230, 190, 40]
}

export function editorSize(orient: EinkOrient, profile: EinkProfileId = 'elnk'): { w: number; h: number } {
  if (profile === 'za25') {
    return orient === 'portrait' ? { w: ZA25_DEV_W, h: ZA25_DEV_H } : { w: ZA25_DEV_H, h: ZA25_DEV_W }
  }
  return orient === 'portrait' ? { w: EINK_DEV_W, h: EINK_DEV_H } : { w: EINK_DEV_H, h: EINK_DEV_W }
}

export function paletteFor(profile: EinkProfileId): EinkColorName[] {
  return profile === 'za25' ? ['black', 'white', 'yellow', 'red'] : ['black', 'white', 'red']
}

export function quantizePixel(r: number, g: number, b: number, profile: EinkProfileId = 'elnk'): EinkColorName {
  if (r > 130 && r - g > 35 && r - b > 35) return 'red'
  if (profile === 'za25' && r > 150 && g > 130 && b < 110 && r + g > 2.2 * b) return 'yellow'
  const luma = 0.299 * r + 0.587 * g + 0.114 * b
  return luma >= 160 ? 'white' : 'black'
}

export function colorIndex(name: EinkColorName): number {
  if (name === 'white') return PIX_WHITE
  if (name === 'yellow') return PIX_YELLOW
  if (name === 'red') return PIX_RED
  return PIX_BLACK
}

function mapDeviceToEdit(
  dx: number,
  dy: number,
  orient: EinkOrient,
  devW: number,
  devH: number
): { x: number; y: number } {
  if (orient === 'portrait') {
    return { x: dx, y: dy }
  }
  return { x: devH - 1 - dy, y: dx }
}

export function encodeCanvasToPlanes(
  imageData: ImageData,
  orient: EinkOrient = 'landscape'
): { bw: Uint8Array; red: Uint8Array } {
  const cw = imageData.width
  const ch = imageData.height
  const data = imageData.data

  const getName = (lx: number, ly: number): EinkColorName => {
    if (lx < 0 || ly < 0 || lx >= cw || ly >= ch) return 'white'
    const i = (ly * cw + lx) * 4
    return quantizePixel(data[i], data[i + 1], data[i + 2], 'elnk')
  }

  const bw = new Uint8Array(EINK_PLANE_LEN)
  const red = new Uint8Array(EINK_PLANE_LEN)
  for (let dy = 0; dy < EINK_DEV_H; dy++) {
    for (let bx = 0; bx < EINK_ROW_BYTES; bx++) {
      let bwByte = 0
      let redByte = 0
      for (let bit = 0; bit < 8; bit++) {
        const dx = bx * 8 + bit
        const p = mapDeviceToEdit(dx, dy, orient, EINK_DEV_W, EINK_DEV_H)
        const name = getName(p.x, p.y)
        const mask = 1 << (7 - bit)
        if (name === 'red') {
          bwByte |= mask
          redByte |= mask
        } else if (name === 'white') {
          bwByte |= mask
        }
      }
      bw[dy * EINK_ROW_BYTES + bx] = bwByte
      red[dy * EINK_ROW_BYTES + bx] = redByte
    }
  }
  return { bw, red }
}

export function pack4(p0: number, p1: number, p2: number, p3: number): number {
  return ((p0 & 3) << 6) | ((p1 & 3) << 4) | ((p2 & 3) << 2) | (p3 & 3)
}

export function encodeCanvasToZa25Frame(
  imageData: ImageData,
  orient: EinkOrient = 'landscape'
): Uint8Array {
  const cw = imageData.width
  const ch = imageData.height
  const data = imageData.data

  const getIdx = (lx: number, ly: number): number => {
    if (lx < 0 || ly < 0 || lx >= cw || ly >= ch) return PIX_WHITE
    const i = (ly * cw + lx) * 4
    return colorIndex(quantizePixel(data[i], data[i + 1], data[i + 2], 'za25'))
  }

  const out = new Uint8Array(ZA25_FRAME_LEN)
  for (let y = 0; y < ZA25_DEV_H; y++) {
    const srcY = ZA25_DEV_H - 1 - y
    for (let x = 0; x < ZA25_DEV_W; x += 4) {
      const sample = (devX: number) => {
        const p = mapDeviceToEdit(devX, srcY, orient, ZA25_DEV_W, ZA25_DEV_H)
        return getIdx(p.x, p.y)
      }
      const p0 = sample(x)
      const p1 = sample(x + 1)
      const p2 = sample(x + 2)
      const p3 = sample(x + 3)
      out[y * ZA25_ROW_BYTES + x / 4] = pack4(p0, p1, p2, p3)
    }
  }
  return out
}

export function bytesToBase64(bytes: Uint8Array): string {
  let binary = ''
  const chunk = 0x8000
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk))
  }
  return btoa(binary)
}

export function quantizeImageData(
  imageData: ImageData,
  profile: EinkProfileId,
  dither = false
): ImageData {
  const { width, height, data } = imageData
  const out = new ImageData(width, height)

  const nearest = (r: number, g: number, b: number): [number, number, number] => {
    const name = quantizePixel(r, g, b, profile)
    return EINK_PALETTE[name]
  }

  if (!dither) {
    for (let i = 0; i < data.length; i += 4) {
      const [nr, ng, nb] = nearest(data[i], data[i + 1], data[i + 2])
      out.data[i] = nr
      out.data[i + 1] = ng
      out.data[i + 2] = nb
      out.data[i + 3] = 255
    }
    return out
  }

  const buf = new Float32Array(width * height * 3)
  for (let i = 0, p = 0; i < data.length; i += 4, p += 3) {
    buf[p] = data[i]
    buf[p + 1] = data[i + 1]
    buf[p + 2] = data[i + 2]
  }
  const add = (x: number, y: number, er: number, eg: number, eb: number, w: number) => {
    if (x < 0 || y < 0 || x >= width || y >= height) return
    const p = (y * width + x) * 3
    buf[p] += er * w
    buf[p + 1] += eg * w
    buf[p + 2] += eb * w
  }
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const p = (y * width + x) * 3
      const r = buf[p]
      const g = buf[p + 1]
      const b = buf[p + 2]
      const [nr, ng, nb] = nearest(r, g, b)
      buf[p] = nr
      buf[p + 1] = ng
      buf[p + 2] = nb
      const er = r - nr
      const eg = g - ng
      const eb = b - nb
      add(x + 1, y, er, eg, eb, 7 / 16)
      add(x - 1, y + 1, er, eg, eb, 3 / 16)
      add(x, y + 1, er, eg, eb, 5 / 16)
      add(x + 1, y + 1, er, eg, eb, 1 / 16)
    }
  }
  for (let i = 0, p = 0; i < out.data.length; i += 4, p += 3) {
    out.data[i] = Math.max(0, Math.min(255, buf[p]))
    out.data[i + 1] = Math.max(0, Math.min(255, buf[p + 1]))
    out.data[i + 2] = Math.max(0, Math.min(255, buf[p + 2]))
    out.data[i + 3] = 255
  }
  return out
}

export function fittedImageRect(
  srcW: number,
  srcH: number,
  destW: number,
  destH: number,
  fit: EinkFitMode
): { x: number; y: number; w: number; h: number } {
  const scale = fit === 'cover' ? Math.max(destW / srcW, destH / srcH) : Math.min(destW / srcW, destH / srcH)
  const w = srcW * scale
  const h = srcH * scale
  return { x: (destW - w) / 2, y: (destH - h) / 2, w, h }
}

export function drawImageFitted(
  ctx: CanvasRenderingContext2D,
  img: CanvasImageSource,
  srcW: number,
  srcH: number,
  destW: number,
  destH: number,
  fit: EinkFitMode
) {
  const box = fittedImageRect(srcW, srcH, destW, destH, fit)
  ctx.imageSmoothingEnabled = true
  ctx.drawImage(img, box.x, box.y, box.w, box.h)
}

/** Paint a simple title card onto a landscape 250×128 canvas context. */
export function paintTitleCard(
  ctx: CanvasRenderingContext2D,
  opts: { title: string; subtitle?: string; accent?: 'red' | 'black' }
) {
  const w = 250
  const h = 128
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, w, h)
  const accent = opts.accent === 'black' ? '#111111' : '#c43c28'
  ctx.fillStyle = accent
  ctx.fillRect(0, 0, w, 28)
  ctx.fillStyle = '#ffffff'
  ctx.font = 'bold 16px "PingFang SC", "Noto Sans SC", sans-serif'
  ctx.textAlign = 'left'
  ctx.textBaseline = 'middle'
  ctx.fillText(truncate(opts.title || 'Asset', 18), 12, 14)
  ctx.fillStyle = '#111111'
  ctx.font = '22px "PingFang SC", "Noto Sans SC", sans-serif'
  ctx.fillText(truncate(opts.title || 'Asset', 14), 12, 64)
  if (opts.subtitle) {
    ctx.fillStyle = '#555555'
    ctx.font = '14px "PingFang SC", "Noto Sans SC", sans-serif'
    ctx.fillText(truncate(opts.subtitle, 28), 12, 96)
  }
}

function truncate(text: string, max: number) {
  const s = String(text || '').trim()
  if (s.length <= max) return s
  return `${s.slice(0, Math.max(0, max - 1))}…`
}
