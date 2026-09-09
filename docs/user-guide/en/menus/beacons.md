---
description: Devices & Space → Beacons
---

# Beacons

Path: **Devices & Space → Beacons**.

Beacon rows come from gateway scans. **Only MACs registered in advance are stored** (colon, hyphen, or 12 hex digits).

- Add one, or download the CSV template and **import** (existing MACs are skipped)
- Filters: bound, online, battery, protocol
- **Signal history** is about the last 2 days (about one point per gateway every 30 seconds); **status history** is about the last 30 days
- Bind/unbind is also available here
- Bound beacons must be unbound before delete; bulk delete skips bound rows

Battery protocol defaults to **auto**. Scans may mark a GeoTag e-ink tag (E-lnk or ZA25GM2D); Find My tags with the same name prefix are not marked. You can pick the panel type when pushing a screen.
