#!/usr/bin/env bash
# Finish deploy after API/worker images exist: cancel stuck node pull, rebuild web from dist, up.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="$(tr -d '[:space:]' < "$ROOT/VERSION")"
if [[ -z "${REMOTE_HOST:-}" && -f "$ROOT/deploy/.remote-host" ]]; then
  REMOTE_HOST="$(tr -d '[:space:]' < "$ROOT/deploy/.remote-host")"
fi
if [[ -z "${REMOTE_HOST:-}" ]]; then
  echo "请设置 REMOTE_HOST，或把主机写进 deploy/.remote-host（已 gitignore）" >&2
  exit 1
fi
export REMOTE_HOST
REMOTE_USER="${REMOTE_USER:-root}"
REMOTE_DIR="${REMOTE_DIR:-/root/AssetManagement/deploy}"

SSHPASS_VALUE="${SSHPASS:-${REMOTE_PASSWORD:-}}"
if [[ -z "$SSHPASS_VALUE" && -f "$ROOT/deploy/.sshpass" ]]; then
  SSHPASS_VALUE="$(tr -d '\n\r' < "$ROOT/deploy/.sshpass")"
fi
export SSHPASS="$SSHPASS_VALUE"
SSH_OPTS=(-o StrictHostKeyChecking=no -o PreferredAuthentications=password -o PubkeyAuthentication=no)
SSH_CMD=(sshpass -e ssh "${SSH_OPTS[@]}")
RSYNC_CMD=(sshpass -e rsync -e "ssh ${SSH_OPTS[*]}")

cd "$ROOT"
rm -rf deploy/build
mkdir -p deploy/build/dist
cp "server/platform-api/target/platform-api-${VERSION}.jar" deploy/build/
cp "server/mqtt-worker/target/mqtt-worker-${VERSION}.jar" deploy/build/
cp -R web/dist/. deploy/build/dist/
if [[ -f docs/说明书/index.html ]]; then
  mkdir -p deploy/build/dist/guide
  rsync -a --delete --exclude '.DS_Store' --exclude '*.command' docs/说明书/ deploy/build/dist/guide/
fi

# regenerate .env if missing
if [[ ! -f deploy/.env ]]; then
  python3 - <<'PY'
from pathlib import Path
import os
root = Path.cwd()
host = os.environ["REMOTE_HOST"]
version = os.environ.get("APP_IMAGE_TAG") or Path("VERSION").read_text().strip()
local = {}
for line in (root / "server" / ".env.local").read_text().splitlines():
    line = line.strip()
    if not line or line.startswith("#") or "=" not in line: continue
    k, v = line.split("=", 1)
    local[k.strip()] = v.strip()
origins = f"http://{host},http://{host}:80,http://localhost,http://127.0.0.1"
(root / "deploy" / ".env").write_text(f"""APP_IMAGE_TAG={version}
SPRING_PROFILES_ACTIVE=dev
POSTGRES_DB=asset_management
DB_USERNAME={local['DB_USERNAME']}
DB_PASSWORD={local['DB_PASSWORD']}
REDIS_PASSWORD={local['REDIS_PASSWORD']}
AUTH_ACCESS_TOKEN_SECRET={local['AUTH_ACCESS_TOKEN_SECRET']}
AUTH_ACCESS_TOKEN_TTL={local.get('AUTH_ACCESS_TOKEN_TTL', '2h')}
AUTH_ALLOWED_ORIGINS={origins}
APP_SECRET_ACTIVE_KEY_VERSION={local['APP_SECRET_ACTIVE_KEY_VERSION']}
APP_SECRET_ACTIVE_KEY_BASE64={local['APP_SECRET_ACTIVE_KEY_BASE64']}
MQTT_CONNECTION_TEST_ALLOWED_PRIVATE_ADDRESSES={host},127.0.0.1,mosquitto
API_PORT=8080
WORKER_PORT=8081
WEB_PORT=80
API_BIND_ADDRESS=0.0.0.0
WORKER_BIND_ADDRESS=127.0.0.1
WEB_BIND_ADDRESS=0.0.0.0
""")
PY
fi

"${SSH_CMD[@]}" "${REMOTE_USER}@${REMOTE_HOST}" bash -s <<'REMOTE'
set -euo pipefail
# stop stuck builds
pkill -f 'docker-buildx|docker compose build|buildkit' 2>/dev/null || true
cd /root/AssetManagement/deploy || exit 1
docker compose kill 2>/dev/null || true
REMOTE

"${RSYNC_CMD[@]}" -az --delete "$ROOT/deploy/docker/" "${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}/docker/"
"${RSYNC_CMD[@]}" -az --delete "$ROOT/deploy/build/" "${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}/build/"
"${RSYNC_CMD[@]}" -az "$ROOT/deploy/docker-compose.yml" "$ROOT/deploy/.env" "${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}/"

"${SSH_CMD[@]}" "${REMOTE_USER}@${REMOTE_HOST}" bash -s <<REMOTE
set -euo pipefail
cd '${REMOTE_DIR}'
# Reuse existing api/worker images; rebuild web only (nginx + dist)
docker compose build web
docker compose up -d --remove-orphans
docker compose ps
for i in \$(seq 1 40); do
  if curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
    echo API_OK
    curl -fsS http://127.0.0.1:8080/actuator/health; echo
    break
  fi
  sleep 3
done
curl -fsS http://127.0.0.1:8081/actuator/health || true; echo
curl -fsSI http://127.0.0.1/ | head -8 || true
echo Web: http://${REMOTE_HOST}/
REMOTE
