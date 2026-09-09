/** Built-in asset photo catalog. Stored as `builtin:{id}`. */
export const ASSET_PHOTO_MAX_BYTES = 100 * 1024

export const ASSET_PHOTO_GROUPS = [
  {
    id: 'it',
    ids: [
      'laptop',
      'desktop',
      'phone',
      'tablet',
      'monitor',
      'tv',
      'printer',
      'scanner',
      'projector',
      'server',
      'router',
      'ups',
      'camera',
      'cctv',
      'radio',
      'headset',
      'speaker',
      'watch',
      'pos',
      'barcode'
    ]
  },
  {
    id: 'safety',
    ids: ['badge', 'keys', 'helmet', 'vest', 'boots', 'extinguisher', 'firstaid']
  },
  {
    id: 'warehouse',
    ids: ['package', 'pallet', 'crate', 'barrel', 'container', 'cart', 'palletjack', 'forklift']
  },
  {
    id: 'vehicle',
    ids: ['car', 'van', 'truck', 'bicycle', 'motorcycle', 'excavator']
  },
  {
    id: 'tool',
    ids: ['toolbox', 'drill', 'ladder', 'generator', 'battery', 'sensor', 'antenna']
  },
  {
    id: 'facility',
    ids: ['chair', 'desk', 'cabinet', 'fridge', 'ac', 'wheelchair', 'stretcher', 'generic']
  }
] as const

export const ASSET_PHOTO_IDS = ASSET_PHOTO_GROUPS.flatMap((group) => [...group.ids])

export type AssetPhotoId = (typeof ASSET_PHOTO_IDS)[number]

export function builtinPhotoUrl(id: string) {
  return `builtin:${id}`
}

export function builtinPhotoSrc(id: string) {
  return `/asset-photos/${id}.svg`
}

export function isBuiltinPhoto(src?: string | null) {
  return !!src && /^builtin:[a-z0-9-]+$/i.test(src.trim())
}

export function builtinPhotoId(src?: string | null): AssetPhotoId | '' {
  if (!src) return ''
  const match = /^builtin:([a-z0-9-]+)$/i.exec(src.trim())
  const id = match?.[1]?.toLowerCase() || ''
  return (ASSET_PHOTO_IDS as readonly string[]).includes(id) ? (id as AssetPhotoId) : ''
}

export function isUploadedPhoto(src?: string | null) {
  return !!src && src.includes('/api/v1/') && src.includes('/documents/')
}
