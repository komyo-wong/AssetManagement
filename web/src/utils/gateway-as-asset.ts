import type { NamedResource } from '@/api/asset-platform'

/** Map a gateway onto the asset-status row shape. Gateways are first-class assets. */
export function asAssetStatusRow(gateway: NamedResource, typeName: string): NamedResource {
  const mac = String(gateway.fields?.macAddress || '').trim()
  const zone = String(gateway.fields?.zoneName || '').trim()
  const map = String(gateway.fields?.mapName || '').trim()
  const place = [zone, map].filter(Boolean).join(' / ')
  return {
    ...gateway,
    fields: {
      ...gateway.fields,
      kind: 'GATEWAY',
      assetTypeName: typeName,
      lastSeenAt: gateway.fields?.lastSeenAt,
      lastZoneName: zone || undefined,
      lastMapName: map || undefined,
      lastGatewayName: gateway.name,
      boundBeaconMac: mac || undefined,
      boundBeaconName: mac || undefined,
      locationLabel: place || undefined,
      liveLocationLabel: place || typeName,
      protocolType: 'GATEWAY'
    }
  }
}

export function isGatewayAsset(row: NamedResource): boolean {
  return String(row.fields?.kind || '') === 'GATEWAY'
}
