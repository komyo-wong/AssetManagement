---
description: Unpack the offline tarball on a fresh Ubuntu host and sign in
---

# Offline install

For a **fresh Ubuntu 22.04 / 24.04 x86_64** host. No internet and no compile on site.

{% hint style="danger" %}
If this machine **already has product data**, do not run install. Use [Upgrade](upgrade.md).
{% endhint %}

## Requirements

- Ubuntu 22.04 (jammy) or 24.04 (noble), x86_64
- 8 GB RAM recommended
- Open **80** (web) and **1883** (gateway MQTT); **443** if HTTPS is enabled

## Unpack

```bash
tar -xzf asset-management-*-linux-amd64.tar.gz
cd asset-management-<version>
```

{% hint style="warning" %}
Unpack into a **new directory**. Do not extract over a running install.
{% endhint %}

See `README_en.txt` in the pack.

## Install

```bash
sudo ./install_en.sh
```

Chinese prompts: `sudo ./install_zh.sh`.

The installer asks for the web **admin username and password** first (or reads them from `config.env`). If they are empty, **install stops**. It then installs Docker from bundled `.deb` files if needed, loads images, copies `config.env.example` to `config.env` when missing, fills `PUBLIC_HOST` from the first NIC if empty (on a cloud VM that is often a **private** address — see below), starts the stack with that admin account, and enables systemd unit `asset-management`. First database setup may take 1–3 minutes. The web admin password is not preset and is not generated.

## After install

When install finishes, the terminal prints the web URL, login account and password, and the gateway MQTT URI, username and password. **Use that screen.** This guide does not list plaintext passwords.

Open the printed web URL (typically `http://<PUBLIC_HOST>/`). Use the same host you put in `PUBLIC_HOST` — do not mix a private IP, a public IP and a domain.

{% hint style="info" %}
To look up the login later, read `config.env` in the install directory on the server (restrict who can log in). Change the admin password in **Account** before production.
{% endhint %}

## Which URL to open (cloud VMs)

Login only accepts the origin in the browser address bar. An empty `PUBLIC_HOST` becomes the first NIC IPv4. On a cloud VM that is often a **private** address (`10.x`, `172.x`), while you open a **public IP or domain**. HTTPS makes the mismatch worse. The login page then says **cross-site authentication request was rejected** (or the Chinese equivalent). The site loads; sign-in does not.

Set the address users will type, before or after install, in `config.env`. Public IP only:

```bash
PUBLIC_HOST=203.0.113.10
AUTH_ALLOWED_ORIGINS=http://203.0.113.10,http://203.0.113.10:80,http://127.0.0.1,http://localhost
```

Domain plus HTTPS (also see [Ops and HTTPS](ops.md)):

```bash
ENABLE_HTTPS=true
PUBLIC_HOST=assets.example.com
AUTH_ALLOWED_ORIGINS=https://assets.example.com,https://assets.example.com:443,http://127.0.0.1,http://localhost
```

Then `sudo ./restart.sh` in the install directory. The web UI and API must be the **same origin** (the pack proxies `/api` on port 80/443). Do not serve the page on a domain and the API on `:8080`.

Gateway MQTT uses the same `PUBLIC_HOST` (`mqtt://<PUBLIC_HOST>:1883`). Update the gateway if that host changes.

Ports and data directory: [Ports and data directory](../appendix/defaults.md).

If install refuses because data already exists, go to [Upgrade](upgrade.md).
