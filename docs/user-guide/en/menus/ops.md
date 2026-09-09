---
description: Platform Administration → System ops (super admin only)
---

# System ops

Path: **Platform Administration → System ops**. **Super admin only**.

| Feature | Notes |
| --- | --- |
| Health | API, worker, … Worker must be up to ingest scans |
| Restart | Some targets require typing `RESTART`. DB/Redis/MQTT restarts interrupt traffic. After a normal offline install, this page can restart every service; if it says Docker is not mounted, upgrade with a newer package |
| Cleanup | Expired inbox, scans, closed alerts, old audit — **not** assets/beacons/gateways/users/rules. Run purge starts a **background** job; watch progress on this page |
| Auto cleanup | On by default after install. Checked around 03:20. Defaults: failed inbox 3 days, scans 2 days, downlink jobs 7 days, presence 14 days, closed alerts 30 days, audit 90 days. You can change days or turn it off |
| License | Paste a GeoTag-signed token to unlock alert notifications (email/webhook) and the login-page copyright. The token may also cap beacon/gateway counts and set an expiry. One token binds to one install ID. Assets, scans, alerts, inventory, buzz, and e-ink work without a token |
| Backup | Downloadable DB backup files |
| Restore | Extra backup first, then overwrite; type `RESTORE`; services restart; sign in again |

{% hint style="info" %}
After a purge, PostgreSQL VACUUM reclaims disk space (not always instantly). Container logs rotate (about 100MB per service) and will not grow without bound.
{% endhint %}

{% hint style="danger" %}
Restore overwrites all business data. Verify the file before you confirm.
{% endhint %}
