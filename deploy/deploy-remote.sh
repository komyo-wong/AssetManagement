#!/usr/bin/env bash
# Build jars locally, sync to REMOTE_HOST, build images and start compose there.
# Frontend is built inside Docker on the remote host (no local Node required).
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

JAVA_HOME="${JAVA_HOME:-$ROOT/.tools/jdk-25}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$ROOT/.tools/maven/bin:$PATH"

cd "$ROOT"

echo "=== 1/4 构建后端 jar (${VERSION}) ==="
(
  cd server
  mvn -DskipTests package -pl platform-api,mqtt-worker -am \
    -Dmaven.repo.local="${MAVEN_REPO_LOCAL:-/private/tmp/asset-management-m2}"
)

echo "=== 2/4 准备 deploy 产物与 .env ==="
rm -rf deploy/build
mkdir -p deploy/build/dist
cp "server/platform-api/target/platform-api-${VERSION}.jar" deploy/build/
cp "server/mqtt-worker/target/mqtt-worker-${VERSION}.jar" deploy/build/
# Prefer freshly built dist when present; otherwise ship existing dist (no local Node required).
if [[ -d web/dist ]] && [[ -f web/dist/index.html ]]; then
  cp -R web/dist/. deploy/build/dist/
else
  echo "❌ 缺少 web/dist，请先本地 pnpm build" >&2
  exit 1
fi
if [[ -f docs/说明书/index.html ]]; then
  mkdir -p deploy/build/dist/guide
  rsync -a --delete --exclude '.DS_Store' --exclude '*.command' docs/说明书/ deploy/build/dist/guide/
fi

APP_IMAGE_TAG="$VERSION" python3 - <<'PY'
from pathlib import Path
import os
root = Path.cwd()
version = os.environ["APP_IMAGE_TAG"]

def load_env(path):
    values = {}
    if not path.exists():
        return values
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        values[k.strip()] = v.strip().strip('"').strip("'")
    return values

local = load_env(root / "server" / ".env.local")
infra = load_env(root / "infra" / ".env")
host = os.environ["REMOTE_HOST"]
origins = f"http://{host},http://{host}:80,http://localhost,http://127.0.0.1"
mqtt_user = infra.get("MQTT_USERNAME") or local.get("MQTT_USERNAME") or "asset_platform_local"
mqtt_password = infra.get("MQTT_PASSWORD") or local.get("MQTT_PASSWORD") or ""
if not mqtt_password:
    raise SystemExit("infra/.env 缺少 MQTT_PASSWORD")
content = f"""APP_IMAGE_TAG={version}
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
MQTT_USERNAME={mqtt_user}
MQTT_PASSWORD={mqtt_password}
APP_DEV_BOOTSTRAP_MQTT_ENABLED=true
APP_DEV_BOOTSTRAP_MQTT_BROKER_URI=mqtt://{host}:1883
APP_DEV_BOOTSTRAP_MQTT_USERNAME={mqtt_user}
APP_DEV_BOOTSTRAP_MQTT_PASSWORD={mqtt_password}
MQTT_PUBLIC_BROKER_HOST={host}
API_PORT=8080
WORKER_PORT=8081
WEB_PORT=80
API_BIND_ADDRESS=0.0.0.0
WORKER_BIND_ADDRESS=127.0.0.1
WEB_BIND_ADDRESS=0.0.0.0
"""
(root / "deploy" / ".env").write_text(content)
print("wrote deploy/.env")
PY

echo "=== 3/4 同步到 ${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR} ==="
SSH_OPTS=(-o StrictHostKeyChecking=no -o PreferredAuthentications=password -o PubkeyAuthentication=no)

if [[ -n "$SSHPASS_VALUE" ]]; then
  export SSHPASS="$SSHPASS_VALUE"
  SSH_CMD=(sshpass -e ssh "${SSH_OPTS[@]}")
  # sshpass must wrap rsync itself; putting it in rsync -e often fails on macOS.
  RSYNC_CMD=(sshpass -e rsync -e "ssh ${SSH_OPTS[*]}")
else
  SSH_CMD=(ssh "${SSH_OPTS[@]}")
  RSYNC_CMD=(rsync -e "ssh ${SSH_OPTS[*]}")
fi

"${SSH_CMD[@]}" "${REMOTE_USER}@${REMOTE_HOST}" "mkdir -p '${REMOTE_DIR}/build' '${REMOTE_DIR}/docker/nginx'; docker compose -f '${REMOTE_DIR}/docker-compose.yml' down 2>/dev/null || true"

"${RSYNC_CMD[@]}" -az --delete \
  "$ROOT/deploy/docker/" \
  "${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}/docker/"

"${RSYNC_CMD[@]}" -az --delete \
  "$ROOT/deploy/build/" \
  "${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}/build/"

"${RSYNC_CMD[@]}" -az \
  "$ROOT/deploy/docker-compose.yml" \
  "$ROOT/deploy/.env" \
  "${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}/"

echo "=== 4/4 远程构建并启动 ==="
"${SSH_CMD[@]}" "${REMOTE_USER}@${REMOTE_HOST}" bash -s <<REMOTE
set -euo pipefail
cd '${REMOTE_DIR}'
docker compose build
docker compose up -d --remove-orphans
# Mosquitto 可能跑在默认 bridge 上，补接到业务网络，让主机名 mosquitto 可解析
if docker inspect mosquitto >/dev/null 2>&1; then
  docker network connect asset-management-backend mosquitto 2>/dev/null || true
fi
echo '--- ps ---'
docker compose ps
echo '--- wait api ---'
ok=0
for i in \$(seq 1 40); do
  if curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
    echo "API healthy after \$((i*3))s"
    curl -fsS http://127.0.0.1:8080/actuator/health || true
    echo
    ok=1
    break
  fi
  sleep 3
done
if [[ "\$ok" != "1" ]]; then
  echo 'API health check failed; recent logs:'
  docker compose logs --tail=80 api || true
  exit 1
fi
echo '--- worker ---'
curl -fsS http://127.0.0.1:8081/actuator/health || docker compose logs --tail=40 worker || true
echo
echo '--- web ---'
curl -fsSI http://127.0.0.1/ | head -8 || true
echo
echo 'Web:  http://${REMOTE_HOST}/'
echo 'API:  http://${REMOTE_HOST}:8080/actuator/health'
REMOTE

echo "✅ 部署完成"
