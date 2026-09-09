Asset Management offline install pack
=====================================

For Ubuntu 22.04 / 24.04 x86_64. No internet and no compile on site.
Chinese instructions: README_zh.txt.

1. Unpack
---------
  tar -xzf asset-management-*-linux-amd64.tar.gz
  cd asset-management-<version>

Unpack into a new directory. Do not extract over a running install.

2. Fresh install
----------------
  sudo ./install_en.sh

The installer asks for the web admin username and password first
(or reads ROOT_USERNAME / ROOT_PASSWORD from config.env).
If they are empty, install stops. No default admin account is used
and the admin password is not generated.
Empty PUBLIC_HOST is taken from the first NIC. On a cloud VM that is often
a private address — set the public IP or domain you type in the browser,
and AUTH_ALLOWED_ORIGINS (e.g. http://PUBLIC_IP,http://PUBLIC_IP:80,
http://127.0.0.1,http://localhost). A mismatch rejects login as
"cross-site authentication request was rejected". Then sudo ./restart.sh.
Web and API must be the same origin; do not expose :8080 separately.

If this machine already has data, install is refused. Use step 3.

When install finishes, the terminal prints the web URL, login account and
password, and the gateway MQTT URI, username and password. Use that screen.
You can also read them later from config.env in the install directory.

The local MQTT broker and access rules are created automatically.
Do not publish the topic names. Gateway MQTT is typically mqtt://<host>:1883.
Copy username, password and topics from the install completion screen
or from Gateways → Provisioning.

3. Upgrade (existing data)
--------------------------
  Unpack the new pack into a new directory.
  cd asset-management-<new-version>
  sudo ./upgrade_en.sh

Upgrade only loads new images and switches API / Worker / web.
It does not overwrite the data directory and does not replace the live
config.env with pack defaults. The previous config from
/etc/asset-management/install-root is reused.
Downgrade is refused. Re-running the same version only reloads images.

4. Daily commands
-----------------
  sudo ./start.sh
  sudo ./stop.sh
  sudo ./restart.sh
  sudo ./status.sh
  sudo ./logs.sh
  sudo ./backup.sh
  sudo ./restore.sh <archive>
  sudo ./reset_admin_en.sh     # forgot web admin password
  sudo ./reset_admin_zh.sh
  sudo ./uninstall.sh          # keep data
  sudo ./uninstall.sh --purge  # delete the data directory

5. Data
-------
  $DATA_DIR/postgres/
  $DATA_DIR/redis/
  $DATA_DIR/mosquitto/
  $DATA_DIR/documents/
  $DATA_DIR/backups/

Default DATA_DIR=/var/lib/asset-management

6. Ports
--------
  80    web (HTTP)
  443   web (ENABLE_HTTPS=true only)
  1883  MQTT
  5432 / 6379 / 8080 / 8081  localhost only

HTTPS: edit the live config.env, set ENABLE_HTTPS=true and PUBLIC_HOST to the
certificate domain, place fullchain.pem and privkey.pem in certs/, then
sudo ./start.sh.

7. User guide
-------------
After unpack, open guide/index.html (no network required).
Or run:  ./guide/打开说明书.command
