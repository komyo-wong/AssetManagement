---
description: Asset Center → Assets
---

# Assets

Path: **Asset Center → Assets**. The list auto-refreshes about every 3 seconds.

An asset belongs to an **asset type**. After a beacon is bound, online/offline follows [Presence TTL](presence-ttl.md). Unbound assets show **Unbound** and cannot be located by beacon.

Name is required on create; you can use the beacon MAC as the code. Filter by code/name/gateway/beacon, type, online status, protocol, and bound/unbound. On create/edit you can mark **E-ink** and pick 2.13\" or 2.8\" (needed when 2.8\" is not seen in advertisements, so Edit screen appears). You can also pick a library photo or upload an image up to 100KB; the list shows a small preview that enlarges on hover.

## Bind and unbind

- **Bind beacon**: pick a registered beacon that is not already bound to another asset.
- **Unbind**: locating via that beacon stops. A bound beacon must be unbound before you can delete it on the Beacons page.

## Buzzer (find)

For a bound beacon: short buzz (~3 times), long buzz (~10 times), or stop. If no last-hop gateway is known, nearby gateways may be tried.

## E-ink

Beacons recognized as GeoTag e-ink, or assets marked as e-ink, can **update the screen**: pick the panel if needed, use a template, landscape/portrait, optional image upload or handwriting, then send. An unlock code must be configured on the beacon; the platform refuses to push without one. Optional preferred native gateway; HCBG is not supported. Reopening restores the last pushed layout or a local draft; only a compressed preview of images is kept.
