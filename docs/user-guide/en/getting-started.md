---
description: Recommended path from an empty server to live gateway data
---

# First-time setup

Follow this order so you do not end up with online gateways and every asset **Unbound**.

## 1. Install and sign in

1. Complete [Offline install](install/offline.md).
2. Open `http://<server-ip>/` (same host as `PUBLIC_HOST` in `config.env`; cloud VMs: [Offline install](install/offline.md)).
3. Sign in with the account printed on the **install completion screen** (you can also look them up later in `config.env` on the server). If login is rejected as cross-site, fix the URL settings in Offline install and restart.
4. Switch **简体中文 / English** in the header (the choice is remembered).

{% hint style="warning" %}
Change the admin password in **Account** and set a real **email** (password reset and alert mail both need it).
{% endhint %}

## 2. Confirm MQTT

**MQTT Integration → Connections** should list **Local broker**.

Topic routes are written at install time. Do not change them, and do not copy rule tables into other documents. Copy gateway fields from **Gateways → Provisioning**.

## 3. Space: map → zone → gateway

1. **Devices & Space → Maps**: upload a floor image and real width/height (metres).
2. **Zones**: each zone must belong to a map.
3. **Gateways → New**: after create, copy host, port, username, password and topics from the provisioning page into the gateway.
4. Pick a zone and click the install point; optional 1 m calibration RSSI.

See [Gateway MQTT parameters](appendix/gateway.md).

## 4. Register beacons and assets

1. **Beacons**: add or CSV-import MACs. **Only registered MACs are stored.**
2. **Asset types**: create categories first.
3. **Assets**: create assets and **bind beacons**. Unbound assets cannot be located by beacon.

## 5. Presence and alerts

1. **Presence TTL**: gateway default ~90 s, beacon/asset ~300 s.
2. **Alert rules**: offline, low battery, weak RSSI, inventory result.
3. **Platform Administration → Mail settings**: SMTP must work before **Forgot password** and email notifications.
4. **My notifications**: email or webhook subscriptions.

## 6. Daily work

| Goal | Where |
| --- | --- |
| Health | **Overview**, **Asset status** |
| Find an asset | **Assets** buzzer; floor plan for proximity |
| E-ink | **Assets → Update screen** |
| Stocktake | **Inventory** with selected gateways |

**Platform Administration** is super-admin only. The super admin cannot be disabled on the Users page; change email in **Account**.

A field app uses the same HTTP API (`client=mobile` on login). See [App API](appendix/app-api.md).
