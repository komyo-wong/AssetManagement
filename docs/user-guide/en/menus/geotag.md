---
description: Platform Administration → GeoTag integration; GeoTag menus appear after it is enabled
---

# GeoTag integration

Path: **Platform Administration → GeoTag Integration**. Super admin only.

Connects their cloud GPS tags. Keep this separate from indoor BLE floor plans.

Fill in the API base URL, business number, and their platform public key. The system generates the key pair and shows the system public key. Copy it and give it to the GeoTag provider, or save it yourself on the GeoTag enterprise platform.

**Webhook is optional:**

- **On**: the page builds a callback URL from the public IP (editable) and port. Forward that port on the router/firewall to this API, then use **Test reachability**. They push location updates to that URL; points are stored locally.
- **Off**: a background job pulls `/device/getHistory` into the local table. The web pages only read the database.

For local preview, set the API URL to `mock`. Bound asset device names appear without calling their cloud. Use **Insert a local point** to write a track point.

After enabling and refreshing, the sidebar shows **GeoTag** (the role also needs the **GeoTag** permission):

- **Map**: last stored point per device; filter by asset type; the card shows the latest address
- **Devices**: bound-beacon devices that appear in the GeoTag list
- **Tracks**: stored points (a single point is still a track)

The default basemap is **OpenStreetMap** (free, 3D available). The map and track pages have a switcher:

- OpenStreetMap: free, 3D
- Esri streets: free, 2D only
- CARTO: 2D, key required (https://carto.com/basemaps/)
- MapTiler: 3D, key required (https://cloud.maptiler.com/account/keys/)

Switching to CARTO / MapTiler without a saved key prompts you to apply, then paste it on this page. Save the key here before the map pages can use that basemap.

A local device name that is missing from `getList` does not appear. A cloud SN with no bound asset does not appear either.
