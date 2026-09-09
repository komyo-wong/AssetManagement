#!/usr/bin/env bash
# Shared helpers for the offline kit. Sourced by install/upgrade/start/stop/etc.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VERSION_FILE="$ROOT/VERSION"
CONFIG_FILE="$ROOT/config.env"
COMPOSE_FILE="$ROOT/compose.yml"
INSTALL_ROOT_FILE="/etc/asset-management/install-root"
AM_LANG="${AM_LANG:-zh}"

# shellcheck source=i18n.sh
source "$ROOT/i18n.sh"

die() {
  local key="${1:-}"
  shift || true
  local fmt
  fmt="$(i18n_text "$key")"
  echo -n "$(i18n_text error_prefix) " >&2
  if [[ "$#" -gt 0 ]]; then
    # shellcheck disable=SC2059
    printf "${fmt}\n" "$@" >&2
  else
    printf '%s\n' "$fmt" >&2
  fi
  exit 1
}

need_root() {
  [[ "$(id -u)" -eq 0 ]] || die need_root "$(basename "$0")" "$*"
}

valid_admin_username() {
  [[ "${1:-}" =~ ^[A-Za-z][A-Za-z0-9._-]{2,31}$ ]]
}

valid_admin_password() {
  local p="${1:-}"
  [[ "$p" =~ ^[A-Za-z0-9!@%^*_+=.,:/\?-]{8,64}$ ]]
}

encode_spring_password() {
  python3 - "$1" <<'PY'
import hashlib, os, sys
password = sys.argv[1].encode("utf-8")
salt = os.urandom(16)
dk = hashlib.pbkdf2_hmac("sha256", password, salt, 310000, dklen=32)
print("{pbkdf2@SpringSecurity_v5_8}" + (salt + dk).hex())
PY
}

wait_postgres() {
  local i
  am_compose up -d postgres >/dev/null
  for i in $(seq 1 30); do
    if am_compose exec -T postgres \
      pg_isready -U "${POSTGRES_USER:-asset_app}" -d "${POSTGRES_DB:-asset_management}" >/dev/null 2>&1; then
      return
    fi
    sleep 2
  done
  die postgres_not_ready
}

load_config() {
  [[ -f "$CONFIG_FILE" ]] || die missing_config "$CONFIG_FILE"
  eval "$(python3 - "$CONFIG_FILE" <<'PY'
import pathlib, shlex, sys
path = pathlib.Path(sys.argv[1])
for raw in path.read_text(encoding="utf-8").splitlines():
    line = raw.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    key, value = line.split("=", 1)
    key = key.strip()
    if not key:
        continue
    if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
        value = value[1:-1]
    print(f"export {key}={shlex.quote(value)}")
PY
)"
  if [[ "${AM_LANG_LOCKED:-0}" != "1" && -n "${LANGUAGE:-}" ]]; then
    case "${LANGUAGE}" in
      en|en-US|en_US) AM_LANG=en ;;
      zh|zh-CN|zh_CN|zh-Hans) AM_LANG=zh ;;
    esac
  fi
}

kit_version() {
  if [[ -f "$VERSION_FILE" ]]; then
    tr -d '[:space:]' < "$VERSION_FILE"
  else
    echo "1.0.7"
  fi
}

am_compose() {
  docker compose --project-directory "$ROOT" --env-file "$CONFIG_FILE" -f "$COMPOSE_FILE" "$@"
}

am_compose_in() {
  local dir="$1"
  shift
  docker compose --project-directory "$dir" --env-file "$dir/config.env" -f "$dir/compose.yml" "$@"
}

upsert_config() {
  local key="$1"
  local value="$2"
  local only_if_empty="${3:-0}"
  python3 - "$CONFIG_FILE" "$key" "$value" "$only_if_empty" <<'PY'
import pathlib, sys
path = pathlib.Path(sys.argv[1])
key, value, only_if_empty = sys.argv[2], sys.argv[3], sys.argv[4] == "1"
text = path.read_text(encoding="utf-8") if path.exists() else ""
lines = text.splitlines(True)
found = False
out = []
for line in lines:
    raw = line.rstrip("\n")
    if raw.startswith("#") or "=" not in raw:
        out.append(line)
        continue
    k, cur = raw.split("=", 1)
    if k != key:
        out.append(line)
        continue
    found = True
    if only_if_empty and cur != "":
        out.append(line)
    else:
        out.append(f"{key}={value}\n")
if not found:
    if text and not text.endswith("\n"):
        out.append("\n")
    out.append(f"{key}={value}\n")
path.write_text("".join(out), encoding="utf-8")
PY
}

config_value() {
  local key="$1"
  local file="${2:-$CONFIG_FILE}"
  python3 - "$file" "$key" <<'PY'
import pathlib, sys
path = pathlib.Path(sys.argv[1])
key = sys.argv[2]
if not path.exists():
    raise SystemExit
for raw in path.read_text(encoding="utf-8").splitlines():
    line = raw.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    k, v = line.split("=", 1)
    if k == key:
        if len(v) >= 2 and v[0] == v[-1] and v[0] in ("'", '"'):
            v = v[1:-1]
        print(v)
        break
PY
}

detect_public_host() {
  hostname -I 2>/dev/null | awk '{print $1}'
}

random_b64() {
  openssl rand -base64 32 | tr -d '\n'
}

random_hex() {
  openssl rand -hex 32 | tr -d '\n'
}

existing_install_root() {
  if [[ -f "$INSTALL_ROOT_FILE" ]]; then
    tr -d '[:space:]' < "$INSTALL_ROOT_FILE"
  fi
}

postgres_data_present() {
  local dir="$1"
  [[ -n "$dir" && -d "$dir/postgres" ]] || return 1
  find "$dir/postgres" -name PG_VERSION -print -quit 2>/dev/null | grep -q .
}

version_file_of() {
  local dir="$1"
  if [[ -f "$dir/VERSION" ]]; then
    tr -d '[:space:]' < "$dir/VERSION"
  else
    config_value APP_IMAGE_TAG "$dir/config.env" 2>/dev/null || true
  fi
}

version_cmp() {
  python3 - "$1" "$2" <<'PY'
import sys

def parts(value: str):
    out = []
    for piece in value.strip().split("."):
        try:
            out.append(int(piece))
        except ValueError:
            out.append(0)
    while len(out) < 3:
        out.append(0)
    return tuple(out[:3])

left, right = parts(sys.argv[1]), parts(sys.argv[2])
if left < right:
    print("lt")
elif left == right:
    print("eq")
else:
    print("gt")
PY
}

require_platform() {
  [[ "$(uname -m)" == "x86_64" ]] || die only_amd64
  [[ -f /etc/os-release ]] || die no_os_release
  # shellcheck disable=SC1091
  . /etc/os-release
  [[ "${ID:-}" == "ubuntu" ]] || die only_ubuntu
  case "${VERSION_CODENAME:-}" in
    jammy|noble) echo "$VERSION_CODENAME" ;;
    *) die ubuntu_codename "${VERSION_CODENAME:-unknown}" ;;
  esac
}

ensure_docker() {
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    systemctl enable --now docker >/dev/null 2>&1 || true
    return
  fi
  local codename="$1"
  local pkgdir="$ROOT/packages/$codename"
  [[ -d "$pkgdir" ]] || die missing_docker_debs_dir "$pkgdir"
  t installing_docker "$codename"
  local debs=()
  mapfile -t debs < <(ls "$pkgdir"/*.deb 2>/dev/null || true)
  [[ ${#debs[@]} -gt 0 ]] || die no_debs "$pkgdir"
  dpkg -i "${debs[@]}" || true
  if ! command -v docker >/dev/null 2>&1; then
    die docker_install_failed
  fi
  systemctl enable --now docker
  docker compose version >/dev/null 2>&1 || die compose_plugin_missing "$codename"
}

load_images() {
  local archive="$ROOT/images/images.tar.gz"
  [[ -f "$archive" ]] || die missing_images "$archive"
  t loading_images
  gzip -dc "$archive" | docker load
}

prepare_data_dir() {
  local dir="${DATA_DIR:-/var/lib/asset-management}"
  mkdir -p "$dir"/{postgres,redis,mosquitto,documents,backups}
  chown 999:999 "$dir/postgres" "$dir/redis" 2>/dev/null || chmod 0777 "$dir/postgres" "$dir/redis"
  chown 1883:1883 "$dir/mosquitto" 2>/dev/null || chmod 0777 "$dir/mosquitto"
  chown 10001:10001 "$dir/documents" 2>/dev/null || chmod 0777 "$dir/documents"
}

prepare_runtime() {
  mkdir -p "$ROOT/runtime/mosquitto"
  local https="${ENABLE_HTTPS:-false}"
  if [[ "$https" == "true" || "$https" == "1" || "$https" == "yes" ]]; then
    [[ -f "$ROOT/certs/fullchain.pem" && -f "$ROOT/certs/privkey.pem" ]] || die https_needs_certs
    cp "$ROOT/compose/nginx/https.conf" "$ROOT/runtime/nginx.conf"
  else
    cp "$ROOT/compose/nginx/http.conf" "$ROOT/runtime/nginx.conf"
  fi

  cat > "$ROOT/runtime/mosquitto/acl" <<EOF
# pattern 对所有已登录用户生效（含网页新建的 gw0001 / gw0002 …）
pattern readwrite GwData
pattern readwrite SrvData
pattern readwrite GwStatus
pattern write _health/%u

# 平台 Worker / 健康检查账号
user ${MQTT_USERNAME}
topic readwrite GwData
topic readwrite SrvData
topic readwrite GwStatus
pattern write _health/%u
EOF

  write_mosquitto_password_file
  chmod 0644 "$ROOT/runtime/mosquitto/acl"
}

write_mosquitto_password_file() {
  local dest="$ROOT/runtime/mosquitto/password_file"
  local args=(-b)
  # -c 只能用于新建；文件已存在时（升级 / 再次 start）必须省略，否则报 File exists
  if [[ ! -s "$dest" ]]; then
    args+=(-c)
  fi
  t mosquitto_passwd
  docker run --rm --user 0 --entrypoint mosquitto_passwd \
    -v "$ROOT/runtime/mosquitto:/mosquitto/config" \
    "${MOSQUITTO_IMAGE:-eclipse-mosquitto:2}" \
    "${args[@]}" /mosquitto/config/password_file "$MQTT_USERNAME" "$MQTT_PASSWORD"
  chown 1883:1883 "$dest" 2>/dev/null || true
  chmod 0600 "$dest"
}

mosquitto_passwd_set() {
  local username="$1"
  local password="$2"
  local dest="$ROOT/runtime/mosquitto/password_file"
  [[ -n "$username" && -n "$password" ]] || return 0
  docker run --rm --user 0 --entrypoint mosquitto_passwd \
    -v "$ROOT/runtime/mosquitto:/mosquitto/config" \
    "${MOSQUITTO_IMAGE:-eclipse-mosquitto:2}" \
    -b /mosquitto/config/password_file "$username" "$password"
  chown 1883:1883 "$dest" 2>/dev/null || true
  chmod 0600 "$dest"
}

reload_mosquitto() {
  local id
  id="$(am_compose ps -q mosquitto 2>/dev/null || true)"
  [[ -n "$id" ]] || return 0
  docker kill -s HUP "$id" >/dev/null 2>&1 || true
}

sync_mosquitto_gateway_accounts() {
  t mqtt_users
  write_mosquitto_password_file
  if ! am_compose exec -T postgres pg_isready -U "${POSTGRES_USER:-asset_app}" -d "${POSTGRES_DB:-asset_management}" >/dev/null 2>&1; then
    reload_mosquitto
    return 0
  fi
  local rows
  rows="$(
    am_compose exec -T postgres \
      psql -U "${POSTGRES_USER:-asset_app}" -d "${POSTGRES_DB:-asset_management}" -Atqc \
      "SELECT mqtt_username || E'\t' || mqtt_password FROM gateways
        WHERE archived_at IS NULL
          AND mqtt_username IS NOT NULL AND btrim(mqtt_username) <> ''
          AND mqtt_password IS NOT NULL AND btrim(mqtt_password) <> ''" \
      2>/dev/null || true
  )"
  if [[ -n "$rows" ]]; then
    while IFS=$'\t' read -r username password; do
      [[ -n "$username" && -n "$password" ]] || continue
      mosquitto_passwd_set "$username" "$password"
    done <<< "$rows"
  fi
  reload_mosquitto
}

install_systemd() {
  mkdir -p /etc/asset-management
  echo "$ROOT" > "$INSTALL_ROOT_FILE"
  cat > /etc/systemd/system/asset-management.service <<EOF
[Unit]
Description=Asset Management
After=docker.service network-online.target
Requires=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=$ROOT
ExecStart=$ROOT/start.sh
ExecStop=$ROOT/stop.sh
TimeoutStartSec=180

[Install]
WantedBy=multi-user.target
EOF
  cat > /etc/systemd/system/asset-management-mqtt-users.service <<EOF
[Unit]
Description=Asset Management MQTT gateway accounts
After=docker.service
Requires=docker.service

[Service]
Type=oneshot
WorkingDirectory=$ROOT
ExecStart=$ROOT/sync-mqtt-users.sh
EOF
  cat > /etc/systemd/system/asset-management-mqtt-users.timer <<EOF
[Unit]
Description=Register web-created gateway MQTT users

[Timer]
OnBootSec=30s
OnUnitActiveSec=20s
AccuracySec=5s
Persistent=true

[Install]
WantedBy=timers.target
EOF
  systemctl daemon-reload
  systemctl enable asset-management.service >/dev/null
  systemctl enable --now asset-management-mqtt-users.timer >/dev/null
}

dump_api_logs() {
  t api_logs
  am_compose logs --tail=120 api || true
}

wait_ready() {
  t wait_api
  local i
  for i in $(seq 1 60); do
    if curl -fsS http://127.0.0.1:8080/actuator/health/readiness 2>/dev/null | grep -q UP \
      || curl -fsS http://127.0.0.1:8080/actuator/health 2>/dev/null | grep -q UP; then
      return
    fi
    sleep 5
  done
  dump_api_logs
  die api_unhealthy
}

bootstrap_log_lines() {
  am_compose logs --tail=3000 api 2>&1 | grep -E "MQTT bootstrap|Local MQTT|Dev bootstrap" || true
}

mqtt_ready_in_db() {
  local count
  count="$(
    am_compose exec -T postgres \
      psql -U "${POSTGRES_USER:-asset_app}" -d "${POSTGRES_DB:-asset_management}" -Atqc \
      "SELECT count(*) FROM mqtt_connections WHERE archived_at IS NULL" 2>/dev/null \
      | tr -d '[:space:]'
  )"
  [[ "${count:-0}" != "" && "${count:-0}" != "0" ]]
}

mqtt_routes_ready_in_db() {
  local count
  count="$(
    am_compose exec -T postgres \
      psql -U "${POSTGRES_USER:-asset_app}" -d "${POSTGRES_DB:-asset_management}" -Atqc \
      "SELECT count(*) FROM mqtt_topic_routes WHERE archived_at IS NULL AND enabled" 2>/dev/null \
      | tr -d '[:space:]'
  )"
  [[ "${count:-0}" != "" && "${count:-0}" -ge 2 ]]
}

print_mqtt_hint() {
  t mqtt_env
  am_compose exec -T api sh -c 'printf "URI=%s\nUSER=%s\nENABLED=%s\n" "$APP_DEV_BOOTSTRAP_MQTT_BROKER_URI" "$APP_DEV_BOOTSTRAP_MQTT_USERNAME" "$APP_DEV_BOOTSTRAP_MQTT_ENABLED"' 2>/dev/null || true
  t bootstrap_logs
  bootstrap_log_lines
}

ensure_mqtt_ready() {
  t wait_mqtt
  local i
  for i in $(seq 1 20); do
    if mqtt_ready_in_db && mqtt_routes_ready_in_db; then
      t mqtt_ready
      return
    fi
    sleep 3
  done
  if am_compose logs --tail=3000 api 2>&1 | grep -q "MQTT bootstrap skipped"; then
    print_mqtt_hint
    die mqtt_skipped
  fi
  if am_compose logs --tail=3000 api 2>&1 | grep -q "MQTT bootstrap failed"; then
    print_mqtt_hint
    die mqtt_failed
  fi
  t mqtt_retry
  am_compose up -d --force-recreate api
  wait_ready
  for i in $(seq 1 20); do
    if mqtt_ready_in_db && mqtt_routes_ready_in_db; then
      t mqtt_ready
      return
    fi
    sleep 3
  done
  print_mqtt_hint
  die mqtt_missing
}

print_summary() {
  local https="${ENABLE_HTTPS:-false}"
  local url="http://${PUBLIC_HOST}/"
  if [[ "$https" == "true" || "$https" == "1" || "$https" == "yes" ]]; then
    url="https://${PUBLIC_HOST}/"
  fi
  echo
  t install_done
  echo
  t web_url "$url"
  t account "$ROOT_USERNAME"
  t password "$ROOT_PASSWORD"
  t data_dir "$DATA_DIR"
  echo
  t mqtt_lan
  t mqtt_uri "$PUBLIC_HOST" "${MQTT_PORT:-1883}"
  t mqtt_user "$MQTT_USERNAME"
  t mqtt_pass "$MQTT_PASSWORD"
  t mqtt_topics
  echo
  t commands
  t cmd_status
  t cmd_logs
  t cmd_restart
  t cmd_reset_admin
  t cmd_uninstall
  t cmd_purge "$DATA_DIR"
  echo
}
