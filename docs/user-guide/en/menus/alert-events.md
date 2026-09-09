---
description: Alert Center → Alert events
---

# Alert events

Path: **Alert Center → Alert events**. Refreshes about every 5 seconds. A matching rule **opens a ticket immediately**, even if email is off.

| Status | Meaning |
| --- | --- |
| Open | New ticket |
| Acknowledged | Seen; still open; repeat nagging stops |
| Resolved | Closed; recovery notifications may fire |

Severities: info / warning / critical. Filter by title, message, status, severity. Typical titles: asset/gateway/beacon offline, low battery, weak signal, inventory result.

Email / webhook delivery is configured under [My notifications](notifications.md).
