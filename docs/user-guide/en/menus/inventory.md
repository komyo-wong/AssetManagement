---
description: Asset Center → Inventory
---

# Inventory

Path: **Asset Center → Inventory**.

Starts a roll-call on selected gateways: they enter roll-call scan, then return to normal scan when the session ends.

1. Click **Start inventory**.
2. Select at least one gateway.
3. Scope: all assets / by type / specific assets.
4. Set a countdown (minutes).
5. The platform sends roll-call; when the timer ends or you stop early, gateways return to normal scan.

{% hint style="info" %}
Only reports from **gateways selected for this session** count. The session ends automatically when everything in scope is seen.
{% endhint %}

While running you can watch coverage and stop early. Afterward: report (seen / missing / unbound), CSV export, or delete the report.

**Inventory result** alert rules can warn when coverage is below a threshold (subscribe under **My notifications**).
