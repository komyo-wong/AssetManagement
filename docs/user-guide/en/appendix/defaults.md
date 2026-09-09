---
description: Offline pack ports and data directory (accounts are on the install completion screen)
---

# Ports and data directory

{% hint style="warning" %}
Web login, gateway MQTT username and password are printed when **install finishes**. This guide does not list default accounts, passwords or topic names. Change the admin password in **Account** before production, and restrict who can read `config.env` on the server.
{% endhint %}

## Web

Open the URL printed at the end of install (typically `http://<PUBLIC_HOST>/`). Empty `PUBLIC_HOST` becomes the first NIC IPv4; on a cloud VM set the public IP or domain and matching `AUTH_ALLOWED_ORIGINS`, or login is rejected. See [Offline install](../install/offline.md).

## Gateway MQTT

Typically `mqtt://<PUBLIC_HOST>:1883`. Username, password and the topics the gateway should use are on the install completion screen, or copy them from **Gateways → Provisioning**.

## Loopback only (not public)

| Port | Service |
| --- | --- |
| 5432 | PostgreSQL |
| 6379 | Redis |
| 8080 | API |
| 8081 | Worker health |

Web 80; 443 if HTTPS. Gateways need **1883**.

## Infra accounts

Database name and middleware credentials live in `config.env` on the server. The install completion screen also prints the data directory. Default data directory: `/var/lib/asset-management`.
