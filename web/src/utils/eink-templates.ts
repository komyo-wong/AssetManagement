/**
 * GeoTag E-lnk screen templates (ported from elink-html).
 * Canvas sizes: landscape 250×128, portrait 128×250.
 * Colors are quantized to black / white / red for encodeCanvasToPlanes.
 */

import type { EinkColorName, EinkOrient, EinkProfileId } from './eink-encode'
import { editorSize } from './eink-encode'
import { drawEinkText } from './eink-text'

export const EINK_LANDSCAPE_W = 250
export const EINK_LANDSCAPE_H = 128
export const EINK_PORTRAIT_W = 128
export const EINK_PORTRAIT_H = 250

export type EinkTemplateOrient = EinkOrient | null

export interface EinkTemplateFieldDef {
  key: string
  label: string
  value: string
  size?: number
}

export interface EinkTemplateMeta {
  id: string
  name: string
  desc: string
  /** Preferred orientation; null = either (blank). */
  orient: EinkTemplateOrient
  fields: EinkTemplateFieldDef[]
}

type ColorRgb = [number, number, number, number]

const COLORS: Record<EinkColorName, ColorRgb> = {
  black: [0, 0, 0, 255],
  white: [255, 255, 255, 255],
  red: [196, 60, 40, 255],
  yellow: [230, 190, 40, 255]
}

export type EinkFieldValues = Record<string, string | number>

type DrawFn = (
  ctx: CanvasRenderingContext2D,
  cw: number,
  ch: number,
  f: EinkFieldValues
) => void

interface TemplateDef extends EinkTemplateMeta {
  draw: DrawFn
}

function sz(f: EinkFieldValues, key: string, fallback: number): number {
  const v = Number(f[`${key}Size`])
  return Number.isFinite(v) && v >= 8 ? Math.round(v) : fallback
}

export function canvasSize(orient: EinkOrient, profile: EinkProfileId = 'elnk'): { cw: number; ch: number } {
  const { w, h } = editorSize(orient, profile)
  return { cw: w, ch: h }
}

function clearCanvas(
  ctx: CanvasRenderingContext2D,
  cw: number,
  ch: number,
  fill: EinkColorName = 'white'
) {
  const c = COLORS[fill]
  ctx.fillStyle = `rgba(${c[0]},${c[1]},${c[2]},1)`
  ctx.fillRect(0, 0, cw, ch)
}

function fillRect(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  w: number,
  h: number,
  name: EinkColorName
) {
  const c = COLORS[name]
  ctx.fillStyle = `rgb(${c[0]},${c[1]},${c[2]})`
  ctx.fillRect(Math.round(x), Math.round(y), Math.round(w), Math.round(h))
}

function hLine(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  w: number,
  name: EinkColorName
) {
  fillRect(ctx, x, y, w, 2, name)
}

function drawText(
  ctx: CanvasRenderingContext2D,
  text: unknown,
  x: number,
  y: number,
  size: number,
  name: EinkColorName,
  align: CanvasTextAlign = 'left'
) {
  drawEinkText(ctx, text, x, y, size, name, align)
}

function drawMultiline(
  ctx: CanvasRenderingContext2D,
  text: unknown,
  x: number,
  y: number,
  size: number,
  name: EinkColorName,
  align: CanvasTextAlign,
  lineH?: number
) {
  const lines = String(text ?? '').split(/\n/)
  const lh = lineH || Math.round(Number(size) || 14) + 4
  lines.forEach((line, i) => drawText(ctx, line, x, y + i * lh, size, name, align))
}

const TEMPLATE_DEFS: TemplateDef[] = [
  {
    id: 'price',
    name: '电子价签',
    desc: '品名 + 大红价 + 单位',
    orient: 'landscape',
    fields: [
      { key: 'name', label: '商品名', value: '有机牛奶 1L', size: 18 },
      { key: 'price', label: '售价', value: '12.90', size: 40 },
      { key: 'unit', label: '单位', value: '元/盒', size: 16 },
      { key: 'sku', label: '货号', value: 'SKU 88214', size: 16 }
    ],
    draw(ctx, cw, ch, f) {
      clearCanvas(ctx, cw, ch, 'white')
      const nameSize = sz(f, 'name', 18)
      fillRect(ctx, 0, 0, cw, nameSize + 10, 'black')
      drawText(ctx, f.name, 6, 5, nameSize, 'white', 'left')
      const priceSize = sz(f, 'price', 40)
      const top = nameSize + 14
      drawText(ctx, '¥', 6, top + 8, Math.max(18, Math.round(priceSize * 0.55)), 'red', 'left')
      drawText(ctx, f.price, 28, top, priceSize, 'red', 'left')
      drawText(ctx, f.unit, cw - 6, top + 14, sz(f, 'unit', 16), 'black', 'right')
      hLine(ctx, 6, ch - 24, cw - 12, 'black')
      drawText(ctx, f.sku, 6, ch - 20, sz(f, 'sku', 16), 'black', 'left')
    }
  },
  {
    id: 'promo',
    name: '促销特价',
    desc: '特价角标 + 划线原价',
    orient: 'landscape',
    fields: [
      { key: 'badge', label: '角标', value: '特价', size: 18 },
      { key: 'name', label: '商品名', value: '蓝牙音箱', size: 16 },
      { key: 'was', label: '原价', value: '199', size: 14 },
      { key: 'now', label: '现价', value: '129', size: 36 }
    ],
    draw(ctx, cw, ch, f) {
      clearCanvas(ctx, cw, ch, 'white')
      fillRect(ctx, 0, 0, 52, ch, 'red')
      drawText(ctx, f.badge, 26, 48, sz(f, 'badge', 18), 'white', 'center')
      drawText(ctx, f.name, 64, 14, sz(f, 'name', 16), 'black', 'left')
      drawText(ctx, `原价 ¥${f.was}`, 64, 42, sz(f, 'was', 14), 'black', 'left')
      hLine(ctx, 64, 50, 70, 'black')
      drawText(ctx, `¥${f.now}`, 64, 60, sz(f, 'now', 36), 'red', 'left')
      drawText(ctx, '限时优惠', 64, 104, 14, 'black', 'left')
    }
  },
  {
    id: 'badge',
    name: '会议工牌',
    desc: '姓名 + 职务 + 公司',
    orient: 'landscape',
    fields: [
      { key: 'name', label: '姓名', value: '王晓明', size: 28 },
      { key: 'title', label: '职务', value: '产品经理', size: 14 },
      { key: 'org', label: '公司', value: 'GeoTag', size: 14 }
    ],
    draw(ctx, cw, ch, f) {
      clearCanvas(ctx, cw, ch, 'white')
      fillRect(ctx, 0, 0, cw, 8, 'red')
      fillRect(ctx, 0, ch - 8, cw, 8, 'black')
      drawText(ctx, f.name, cw / 2, 28, sz(f, 'name', 28), 'black', 'center')
      drawText(ctx, f.title, cw / 2, 66, sz(f, 'title', 14), 'black', 'center')
      drawText(ctx, f.org, cw / 2, 90, sz(f, 'org', 14), 'red', 'center')
    }
  },
  {
    id: 'place',
    name: '桌牌席卡',
    desc: '宾客姓名居中',
    orient: 'landscape',
    fields: [
      { key: 'label', label: '小字', value: '欢迎莅临', size: 14 },
      { key: 'name', label: '姓名', value: '李华', size: 36 },
      { key: 'role', label: '身份', value: '特邀嘉宾', size: 14 }
    ],
    draw(ctx, cw, ch, f) {
      clearCanvas(ctx, cw, ch, 'white')
      drawText(ctx, f.label, cw / 2, 18, sz(f, 'label', 14), 'black', 'center')
      drawText(ctx, f.name, cw / 2, 48, sz(f, 'name', 36), 'black', 'center')
      hLine(ctx, cw / 2 - 40, 92, 80, 'red')
      drawText(ctx, f.role, cw / 2, 100, sz(f, 'role', 14), 'red', 'center')
    }
  },
  {
    id: 'meeting',
    name: '会议室牌',
    desc: '房间 + 状态红条',
    orient: 'landscape',
    fields: [
      { key: 'room', label: '房间', value: '会议 A3', size: 20 },
      { key: 'status', label: '状态', value: '使用中', size: 14 },
      { key: 'time', label: '时段', value: '14:00 – 15:30', size: 14 },
      { key: 'topic', label: '主题', value: '产品评审', size: 16 }
    ],
    draw(ctx, cw, _ch, f) {
      clearCanvas(ctx, cw, _ch, 'white')
      drawText(ctx, f.room, 12, 12, sz(f, 'room', 20), 'black', 'left')
      fillRect(ctx, cw - 78, 10, 66, 24, 'red')
      drawText(ctx, f.status, cw - 45, 14, sz(f, 'status', 14), 'white', 'center')
      drawText(ctx, f.time, 12, 52, sz(f, 'time', 14), 'black', 'left')
      hLine(ctx, 12, 78, cw - 24, 'black')
      drawText(ctx, f.topic, 12, 90, sz(f, 'topic', 16), 'black', 'left')
    }
  },
  {
    id: 'stock',
    name: '库存货位',
    desc: '库位 + 数量 + SKU',
    orient: 'landscape',
    fields: [
      { key: 'loc', label: '货位', value: 'A-12-03', size: 16 },
      { key: 'sku', label: '料号', value: 'P-77821', size: 14 },
      { key: 'qty', label: '数量', value: '128', size: 28 },
      { key: 'name', label: '品名', value: '传感器模组', size: 14 }
    ],
    draw(ctx, cw, _ch, f) {
      clearCanvas(ctx, cw, _ch, 'white')
      fillRect(ctx, 0, 0, cw, 28, 'black')
      drawText(ctx, `LOC  ${f.loc}`, 10, 7, sz(f, 'loc', 16), 'white', 'left')
      drawText(ctx, f.name, 10, 40, sz(f, 'name', 14), 'black', 'left')
      drawText(ctx, f.sku, 10, 64, sz(f, 'sku', 14), 'black', 'left')
      drawText(ctx, 'QTY', 10, 92, 14, 'black', 'left')
      drawText(ctx, f.qty, 50, 82, sz(f, 'qty', 28), 'red', 'left')
    }
  },
  {
    id: 'note',
    name: '提示便签',
    desc: '标题 + 正文',
    orient: 'landscape',
    fields: [
      { key: 'title', label: '标题', value: '注意', size: 22 },
      { key: 'body', label: '正文', value: '请随手关门\n保持安静', size: 14 }
    ],
    draw(ctx, cw, ch, f) {
      clearCanvas(ctx, cw, ch, 'white')
      fillRect(ctx, 0, 0, 8, ch, 'red')
      drawText(ctx, f.title, 20, 16, sz(f, 'title', 22), 'red', 'left')
      const bodySize = sz(f, 'body', 14)
      drawMultiline(ctx, f.body, 20, 52, bodySize, 'black', 'left', bodySize + 4)
    }
  },
  {
    id: 'move_phone',
    name: '挪车电话',
    desc: '临时停车 + 大号手机号',
    orient: 'landscape',
    fields: [
      { key: 'title', label: '标题', value: '临时停车', size: 16 },
      { key: 'phone', label: '手机号', value: '138 0000 0000', size: 28 },
      { key: 'hint', label: '提示', value: '如需挪车请电话联系', size: 14 }
    ],
    draw(ctx, cw, _ch, f) {
      clearCanvas(ctx, cw, _ch, 'white')
      fillRect(ctx, 0, 0, cw, 26, 'red')
      drawText(ctx, f.title, cw / 2, 5, sz(f, 'title', 16), 'white', 'center')
      drawText(ctx, f.phone, cw / 2, 48, sz(f, 'phone', 28), 'black', 'center')
      hLine(ctx, cw / 2 - 70, 86, 140, 'black')
      drawText(ctx, f.hint, cw / 2, 96, sz(f, 'hint', 14), 'black', 'center')
    }
  },
  {
    id: 'portrait_card',
    name: '竖屏名片',
    desc: '竖向姓名卡',
    orient: 'portrait',
    fields: [
      { key: 'name', label: '姓名', value: '陈思远', size: 28 },
      { key: 'title', label: '职务', value: '设计总监', size: 14 },
      { key: 'org', label: '公司', value: 'Studio', size: 14 },
      { key: 'extra', label: '补充', value: '欢迎交流', size: 14 }
    ],
    draw(ctx, cw, _ch, f) {
      clearCanvas(ctx, cw, _ch, 'white')
      fillRect(ctx, 0, 0, cw, 36, 'black')
      drawText(ctx, f.org, cw / 2, 10, sz(f, 'org', 14), 'white', 'center')
      drawText(ctx, f.name, cw / 2, 80, sz(f, 'name', 28), 'black', 'center')
      drawText(ctx, f.title, cw / 2, 120, sz(f, 'title', 14), 'black', 'center')
      fillRect(ctx, 24, 160, cw - 48, 3, 'red')
      drawText(ctx, f.extra, cw / 2, 180, sz(f, 'extra', 14), 'red', 'center')
    }
  },
  {
    id: 'blank',
    name: '空白画布',
    desc: '清空画面',
    orient: null,
    fields: [],
    draw(ctx, cw, ch) {
      clearCanvas(ctx, cw, ch, 'white')
    }
  }
]

const byId = new Map(TEMPLATE_DEFS.map((t) => [t.id, t]))

/** Public metadata (no draw fn). */
export const EINK_TEMPLATES: EinkTemplateMeta[] = TEMPLATE_DEFS.map(
  ({ id, name, desc, orient, fields }) => ({ id, name, desc, orient, fields })
)

export function getEinkTemplate(templateId: string): EinkTemplateMeta | undefined {
  const t = byId.get(templateId)
  if (!t) return undefined
  const { id, name, desc, orient, fields } = t
  return { id, name, desc, orient, fields }
}

export function defaultFieldValues(templateId: string): EinkFieldValues {
  const t = byId.get(templateId)
  const out: EinkFieldValues = {}
  if (!t) return out
  for (const field of t.fields) {
    out[field.key] = field.value
    if (field.size != null) out[`${field.key}Size`] = field.size
  }
  return out
}

/**
 * Paint a template onto an existing canvas context.
 * Resizes the canvas to match orientation and panel family.
 */
export function paintTemplate(
  ctx: CanvasRenderingContext2D,
  templateId: string,
  fieldValues: EinkFieldValues = {},
  orient: EinkOrient = 'landscape',
  profile: EinkProfileId = 'elnk'
): void {
  const { cw, ch } = canvasSize(orient, profile)
  const canvas = ctx.canvas
  if (canvas.width !== cw || canvas.height !== ch) {
    canvas.width = cw
    canvas.height = ch
  }
  ctx.imageSmoothingEnabled = false

  const t = byId.get(templateId)
  if (!t) {
    clearCanvas(ctx, cw, ch, 'white')
    drawText(ctx, `未知模版: ${templateId}`, 8, 8, 14, 'black', 'left')
    return
  }

  const merged = { ...defaultFieldValues(templateId), ...fieldValues }
  t.draw(ctx, cw, ch, merged)
}
