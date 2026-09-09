---
description: systemd, start/stop, backups, HTTPS certificates and DNS
---

# Daily commands, backup and HTTPS

Run these in the **install directory** (stored in `/etc/asset-management/install-root`).

| Command | Effect |
| --- | --- |
| `sudo ./start.sh` | Start all containers |
| `sudo ./stop.sh` | Stop containers, **keep data** |
| `sudo ./restart.sh` | Recreate and start |
| `sudo ./status.sh` | Compose status and API health |
| `sudo ./logs.sh` | Logs (`api`, `worker`, …) |
| `sudo ./backup.sh` | DB + documents → `$DATA_DIR/backups/` |
| `sudo ./restore.sh <archive>` | Restore DB (stops API/Worker/Web first) |
| `sudo ./reset_admin_en.sh` | Reset the web admin password (username unchanged; Chinese: `reset_admin_zh.sh`) |
| `sudo ./uninstall.sh` | Remove the service, keep data |
| `sudo ./uninstall.sh --purge` | Also delete the data directory |

Unit name: `asset-management`.

Default data dir `/var/lib/asset-management`: `postgres/`, `redis/`, `mosquitto/`, `documents/`, `backups/`.

A super admin can also backup/restore and purge **telemetry** in the background (not master data) under **Platform Administration → System ops**. New installs enable daily automatic purge. Container logs rotate so they do not fill the disk. The same page can restart Postgres / Redis / MQTT / Web / API / Worker.

## HTTPS and certificates

Certificates are **not** auto-bound to a hostname. Align three things:

1. DNS A/AAAA record → this host
2. Certificate SAN/CN includes that hostname (`fullchain.pem`, `privkey.pem`)
3. Live `config.env`:

```bash
ENABLE_HTTPS=true
PUBLIC_HOST=assets.example.com
AUTH_ALLOWED_ORIGINS=https://assets.example.com,https://assets.example.com:443,http://127.0.0.1,http://localhost
```

`AUTH_ALLOWED_ORIGINS` must include the `https://` origin in the address bar, or login is rejected as cross-site. Then `sudo ./restart.sh` (or `start.sh` below).

Place the files in `certs/` of the **current install directory**, then `sudo ./start.sh`. A certificate name mismatch shows a browser warning. Without HTTPS, use HTTP on port 80.
