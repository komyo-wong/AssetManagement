---
description: Devices & Space → Presence TTL
---

# Presence TTL

Path: **Devices & Space → Presence TTL**.

| Item | Default | Range | Meaning |
| --- | --- | --- | --- |
| Gateway offline | 90 s | 30–3600 | No heartbeat or scan → gateway offline |
| Beacon/asset offline | 300 s | 60–86400 | Bound beacon not scanned → offline |

{% hint style="info" %}
Keep the gateway TTL at least as long as the gateway MQTT keepalive so a long keepalive does not look like an offline event.
{% endhint %}
