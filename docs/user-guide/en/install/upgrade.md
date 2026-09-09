---
description: Load new images only; never wipe the database or live config.env
---

# Upgrade without overwriting data

Upgrade **loads new images and switches API / Worker / web**.  
It does **not** wipe `$DATA_DIR` and does **not** replace the live `config.env` with pack defaults.

## Steps

1. Unpack the new tarball into a **new directory**. You may also unpack over the current install directory, then upgrade; **do not delete the data directory**.
2. Run:

```bash
sudo ./upgrade_en.sh
```

(`upgrade_zh.sh` for Chinese prompts.)

3. The script:
   - Reuses `config.env` from `/etc/asset-management/install-root`
   - Allows only **new version ≥ installed version** (no downgrade)
   - Same version: reload images only
   - Recreates app containers; Postgres / files / Redis / Mosquitto data stay
   - Schema changes run via **Flyway** on API start (additive)

{% hint style="success" %}
Assets, beacons, gateways, users, alert rules and MQTT connections are kept.
{% endhint %}

If data exists but the old `config.env` is missing, copy it from the previous install directory into the new pack, then run upgrade again.

To roll back a version, restore a backup — see [Daily commands](ops.md).
