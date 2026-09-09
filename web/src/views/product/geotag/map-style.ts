import type { Map as MapLibreMap, RasterDEMSourceSpecification } from 'maplibre-gl'

export type GeotagTerrain = {
  url?: string
  tiles?: string[]
  encoding?: string
  tileSize?: number
  maxzoom?: number
}

export type GeotagMapOption = {
  id: string
  provider?: string
  tiles?: string[]
  attribution?: string
  tileSize?: number
  styleUrl?: string
  supports3d?: boolean
  needsKey?: boolean
  hasKey?: boolean
  locked?: boolean
  applyUrl?: string
  terrain?: GeotagTerrain
}

export type GeotagMapConfig = GeotagMapOption & {
  fallback?: boolean
  requestedProvider?: string
  hasCartoKey?: boolean
  hasMaptilerKey?: boolean
  options?: GeotagMapOption[]
}

export type GeotagDevicePoint = {
  sn: string
  assetName?: string
  assetTypeId?: string | null
  assetTypeName?: string | null
  lat?: number
  lng?: number
  battery?: number
  address?: string
  locationTime?: string
  accuracy?: string
}

const PROVIDER_KEY = 'geotag-map-provider'
const THREE_D_KEY = 'geotag-map-3d'
const TERRAIN_SOURCE = 'geotag-terrain'

const OSM_TILES = ['https://tile.openstreetmap.org/{z}/{x}/{y}.png']

export function geotagMapStyle(config?: GeotagMapConfig | GeotagMapOption | null) {
  if (config?.styleUrl) {
    return config.styleUrl
  }
  const tiles = config?.tiles?.length ? config.tiles : OSM_TILES
  return {
    version: 8 as const,
    sources: {
      basemap: {
        type: 'raster' as const,
        tiles,
        tileSize: config?.tileSize || 256,
        attribution: config?.attribution || '© OpenStreetMap contributors'
      }
    },
    layers: [{ id: 'basemap', type: 'raster' as const, source: 'basemap' }]
  }
}

export function styleKeyOf(config?: GeotagMapConfig | GeotagMapOption | null) {
  if (config?.styleUrl) return config.styleUrl
  return JSON.stringify(config?.tiles?.length ? config.tiles : OSM_TILES)
}

export function providerLabelKey(provider?: string) {
  switch (provider) {
    case 'esri':
      return 'platformAdmin.geotag.mapEsri'
    case 'carto':
      return 'platformAdmin.geotag.mapCarto'
    case 'maptiler':
      return 'platformAdmin.geotag.mapMaptiler'
    default:
      return 'platformAdmin.geotag.mapOsm'
  }
}

export function optionById(config: GeotagMapConfig | null | undefined, id: string) {
  return (config?.options || []).find((item) => item.id === id)
}

export function usableOption(option?: GeotagMapOption | null) {
  return !!option && !option.locked && (!option.needsKey || option.hasKey !== false)
}

export function rememberedProvider(fallback = 'osm') {
  try {
    return localStorage.getItem(PROVIDER_KEY) || fallback
  } catch {
    return fallback
  }
}

export function rememberProvider(id: string) {
  try {
    localStorage.setItem(PROVIDER_KEY, id)
  } catch {
    /* ignore */
  }
}

export function remembered3d() {
  try {
    return localStorage.getItem(THREE_D_KEY) === '1'
  } catch {
    return false
  }
}

export function remember3d(on: boolean) {
  try {
    localStorage.setItem(THREE_D_KEY, on ? '1' : '0')
  } catch {
    /* ignore */
  }
}

export function applyTerrain(map: MapLibreMap, option: GeotagMapOption | null | undefined, on: boolean) {
  map.setMaxPitch(85)
  if (on && option?.supports3d && option.terrain) {
    if (!map.getSource(TERRAIN_SOURCE)) {
      const terrain: RasterDEMSourceSpecification = {
        type: 'raster-dem',
        tileSize: option.terrain.tileSize || 256
      }
      if (option.terrain.url) terrain.url = option.terrain.url
      if (option.terrain.tiles?.length) terrain.tiles = option.terrain.tiles
      if (option.terrain.encoding === 'terrarium' || option.terrain.encoding === 'mapbox') {
        terrain.encoding = option.terrain.encoding
      }
      if (option.terrain.maxzoom) terrain.maxzoom = option.terrain.maxzoom
      map.addSource(TERRAIN_SOURCE, terrain)
    }
    map.setTerrain({ source: TERRAIN_SOURCE, exaggeration: 1.15 })
    map.easeTo({ pitch: 55, duration: 400 })
    return
  }
  map.setTerrain(null)
  map.easeTo({ pitch: 0, duration: 400 })
}

export function locationLabel(item: { address?: string; lat?: number; lng?: number }) {
  if (item.address) return item.address
  if (typeof item.lat === 'number' && typeof item.lng === 'number') {
    return `${item.lat.toFixed(5)}, ${item.lng.toFixed(5)}`
  }
  return ''
}
