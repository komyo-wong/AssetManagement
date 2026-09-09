---
description: MQTT fields to copy into the gateway
---

# Gateway MQTT parameters

After **Devices & Space → Gateways → Provisioning**, copy values from the project **Local broker**.

**Write the fields shown on that page into the gateway.** Each gateway uses its own account; do not switch them to the install-time platform account. Creating or saving provisioning registers that account on the local broker:

| Field | Notes |
| --- | --- |
| host | `PUBLIC_HOST` (LAN IP or domain) |
| port | Typically `1883` |
| usr / pw | Credentials from provisioning |
| clientId | Generated or custom |
| publish topic | The publish field on the provisioning page |
| subscribe topic | The subscribe field on the provisioning page |
| qos | Per firmware, often 0 |

Downlink: buzzer, e-ink, inventory start/stop. Username and password can be changed in provisioning and saved.

## Firewall

Gateways must reach TCP **1883** on the server. They do not need port 80 to upload scans.
