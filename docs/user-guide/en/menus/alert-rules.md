---
description: Alert Center → Alert rules
---

# Alert rules

Path: **Alert Center → Alert rules**. Enable/disable per rule. Hits create [Alert events](alert-events.md) and can push to [My notifications](notifications.md).

| Condition | Threshold |
| --- | --- |
| Beacon offline | No scan for N seconds |
| Gateway offline | No heartbeat/report for N seconds |
| Asset offline | Bound beacon not scanned for N seconds |
| Low battery | Percent ≤ threshold (or band estimate) |
| Weak signal | Smoothed RSSI weaker than threshold (e.g. -85 dBm) |
| Inventory result | After a session; WARNING if coverage below threshold, else INFO |
