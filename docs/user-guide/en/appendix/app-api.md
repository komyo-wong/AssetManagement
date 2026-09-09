---
description: How a field app uses the same /api/v1 — login flow and endpoint list
---

# App API

{% hint style="info" %}
Accounts are printed when install finishes, or stored in `config.env`. This guide does not list default usernames. Give field staff a business role — do not use the super-admin account as the daily app login.
{% endhint %}

## How to call it

1. `POST /api/v1/auth/login` with `"client": "mobile"`.
2. Store `accessToken` and `refreshToken` from `data` in the OS keychain (not in logs or plain files).
3. Send `Authorization: Bearer <accessToken>` on business requests.
4. Call `GET /api/v1/auth/me`. Read `tenantId` from `tenantMemberships[0]` and `projectId` from that tenant’s `projects[0]`. Every business path needs those two UUIDs.
5. When the access token expires (~2 hours), POST the refresh token; save the **new** pair (the old refresh token is revoked).
6. On sign-out, POST the current refresh token.

The web client must **omit** `client`. Then behaviour stays as today: refresh lives only in the Cookie, and JSON has no `refreshToken`.

Do not send a browser `Origin` header from the native app.

Successful bodies are wrapped as:

```json
{ "success": true, "code": "OK", "data": { }, "traceId": "...", "timestamp": "..." }
```

## Sign-in

`POST /api/v1/auth/login`

```json
{ "account": "username or email", "password": "secret", "client": "mobile" }
```

| Field | Notes |
| --- | --- |
| `accessToken` | Header token, ~2 hours |
| `expiresInSeconds` | TTL |
| `refreshToken` | Only when `client=mobile`, ~30 days |

`POST /api/v1/auth/refresh` with `{ "refreshToken" }`.  
`POST /api/v1/auth/logout` with `{ "refreshToken" }`.  
`GET /api/v1/auth/me` (Bearer) for permissions and workspace IDs.

## Endpoints for the app

`{base}` = `/api/v1/tenants/{tenantId}/projects/{projectId}`.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/public/branding` | Login branding (no token) |
| GET | `/api/v1/auth/me` | User, permissions, tenant/project IDs |
| PUT | `/api/v1/me/profile` | Display name, email |
| PUT | `/api/v1/me/avatar` | Avatar |
| PUT | `/api/v1/me/password` | Change own password |
| GET | `{base}/dashboard/summary` | Overview figures |
| GET | `{base}/asset-types` | Asset types |
| GET | `{base}/assets` | Assets |
| POST | `{base}/assets/{id}/buzzer` | Find / buzzer |
| GET | `{base}/beacons` | Beacons |
| GET | `{base}/beacons/{id}/scans` | Scan history |
| GET | `{base}/beacons/{id}/presence-events` | Presence events |
| GET | `{base}/gateways` | Gateways (no MQTT secrets) |
| GET | `{base}/maps` | Maps |
| GET | `{base}/zones` | Zones |
| GET | `{base}/tracking/live` | Live positions (poll) |
| GET | `{base}/tracking/history` | History |
| GET | `{base}/alerts` | Alert events |
| POST | `{base}/alerts/{id}/acknowledge` | Acknowledge |
| POST | `{base}/alerts/{id}/resolve` | Resolve |
| GET | `{base}/inventory-sessions` | Inventory list |
| GET | `{base}/inventory-sessions/{id}` | Inventory detail |
| POST | `{base}/inventory-sessions` | Start inventory |
| POST | `{base}/inventory-sessions/{id}/close` | Close inventory |

Other writes still exist on the same API; the user’s role decides whether they succeed.

## Blocked for the app

These return 403 even for a super-admin mobile session:

| Path | Why |
| --- | --- |
| `/api/v1/platform/ops/**` | Backup, restore, container restart |
| `/api/v1/platform/mail-settings` | SMTP |
| `{base}/gateways/{id}/provision` | MQTT credentials |
| `/actuator/info`, `/actuator/prometheus` | Ops metrics |
| `/v3/api-docs`, `/swagger-ui` | API docs UI |

Keep using the web UI for those. The app must **not** connect to the Worker (8081) or MQTT; gateways still report over MQTT.

## Permissions

Platform roles still apply. If the user lacks `asset:read`, the asset list is 403. Grant modules under **Roles** on the web.
