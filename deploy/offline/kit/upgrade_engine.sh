# Upgrade: load new images and switch app containers. Never overwrite DATA_DIR or live passwords.
need_root "$@"

resolve_live_config() {
  local old_root data_dir
  old_root="$(existing_install_root)"
  if [[ -n "$old_root" && -f "$old_root/config.env" ]]; then
    printf '%s\n' "$old_root/config.env"
    return
  fi
  if [[ -f "$ROOT/config.env" ]]; then
    data_dir="$(config_value DATA_DIR "$ROOT/config.env" || true)"
    data_dir="${data_dir:-/var/lib/asset-management}"
    if postgres_data_present "$data_dir"; then
      printf '%s\n' "$ROOT/config.env"
      return
    fi
  fi
  data_dir="/var/lib/asset-management"
  if postgres_data_present "$data_dir"; then
    die upgrade_need_config "$data_dir"
  fi
  die use_install
}

live_config="$(resolve_live_config)"
old_dir="$(cd "$(dirname "$live_config")" && pwd)"
new_ver="$(kit_version)"
old_ver="$(version_file_of "$old_dir")"
if [[ -z "$old_ver" ]]; then
  old_ver="$(config_value APP_IMAGE_TAG "$live_config" || true)"
fi
old_ver="${old_ver:-0.0.0}"

cmp="$(version_cmp "$new_ver" "$old_ver")"
if [[ "$cmp" == "lt" ]]; then
  die upgrade_downgrade "$old_ver" "$new_ver"
fi
if [[ "$cmp" == "eq" ]]; then
  t upgrade_same "$new_ver"
else
  t upgrade_from_to "$old_ver" "$new_ver"
fi

codename="$(require_platform)"
ensure_docker "$codename"

t upgrade_keep_config
if [[ "$live_config" != "$ROOT/config.env" ]]; then
  cp "$live_config" "$ROOT/config.env"
  if [[ -d "$old_dir/certs" ]]; then
    mkdir -p "$ROOT/certs"
    cp -a "$old_dir/certs/." "$ROOT/certs/" 2>/dev/null || true
  fi
fi
CONFIG_FILE="$ROOT/config.env"
load_config
upsert_config APP_IMAGE_TAG "$new_ver"
load_config

load_images

if [[ "$old_dir" != "$ROOT" ]]; then
  t upgrade_stop_old
  am_compose_in "$old_dir" down --remove-orphans || true
fi

prepare_runtime

if [[ "$old_dir" == "$ROOT" ]]; then
  t upgrade_apps_only
  am_compose up -d api worker web
  wait_ready
  ensure_mqtt_ready
else
  t start_infra
  am_compose up -d postgres redis mosquitto
  t start_api
  am_compose up -d api
  wait_ready
  ensure_mqtt_ready
  t start_apps
  am_compose up -d worker web
fi

install_systemd
echo
t upgrade_done "$DATA_DIR"
echo
