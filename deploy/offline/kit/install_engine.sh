# Fresh install only. Refuses to run when Postgres data already exists.
need_root "$@"

refuse_if_existing_data() {
  local old_root data_dir
  old_root="$(existing_install_root)"
  if [[ -n "$old_root" && -f "$old_root/config.env" ]]; then
    die use_upgrade
  fi
  if [[ -f "$CONFIG_FILE" ]]; then
    data_dir="$(config_value DATA_DIR "$CONFIG_FILE" || true)"
  fi
  data_dir="${data_dir:-/var/lib/asset-management}"
  if postgres_data_present "$data_dir" || postgres_data_present "/var/lib/asset-management"; then
    die use_upgrade
  fi
}

prompt_admin_credentials() {
  local user pass pass2
  user="$(config_value ROOT_USERNAME || true)"
  pass="$(config_value ROOT_PASSWORD || true)"

  if [[ -n "$user" && -n "$pass" ]]; then
    valid_admin_username "$user" || die invalid_admin_username
    valid_admin_password "$pass" || die invalid_admin_password
    t using_config_admin
    return
  fi

  if [[ ! -r /dev/tty ]]; then
    die admin_credentials_required
  fi

  echo
  t prompt_admin_intro
  if [[ -z "$user" ]]; then
    t prompt_admin_user
    IFS= read -r user < /dev/tty || true
  fi
  user="${user//$'\r'/}"
  [[ -n "$user" ]] || die admin_username_required
  valid_admin_username "$user" || die invalid_admin_username

  if [[ -z "$pass" ]]; then
    t prompt_admin_password
    IFS= read -r -s pass < /dev/tty || true
    echo
    t prompt_admin_password_again
    IFS= read -r -s pass2 < /dev/tty || true
    echo
    [[ -n "$pass" ]] || die admin_password_required
    [[ "$pass" == "$pass2" ]] || die admin_password_mismatch
  fi
  valid_admin_password "$pass" || die invalid_admin_password

  upsert_config ROOT_USERNAME "$user"
  upsert_config ROOT_PASSWORD "$pass"
}

prepare_config() {
  if [[ ! -f "$CONFIG_FILE" ]]; then
    [[ -f "$ROOT/config.env.example" ]] || die missing_example
    cp "$ROOT/config.env.example" "$CONFIG_FILE"
    t wrote_default_config "$CONFIG_FILE"
  fi
  load_config
  prompt_admin_credentials
  load_config

  local host
  host="$(config_value PUBLIC_HOST)"
  if [[ -z "$host" ]]; then
    host="$(detect_public_host)"
    [[ -n "$host" ]] || die cannot_detect_host
    upsert_config PUBLIC_HOST "$host"
  fi

  upsert_config LANGUAGE "$AM_LANG"
  upsert_config APP_IMAGE_TAG "$(kit_version)"
  upsert_config COMPOSE_PROJECT_NAME "asset-management"
  upsert_config TZ "${TZ:-Asia/Shanghai}" 1
  upsert_config POSTGRES_DB "asset_management"
  upsert_config POSTGRES_USER "asset_app"
  upsert_config POSTGRES_IMAGE "postgis/postgis:18-3.6"
  upsert_config REDIS_IMAGE "redis:8-alpine"
  upsert_config MOSQUITTO_IMAGE "eclipse-mosquitto:2"
  upsert_config DATA_DIR "/var/lib/asset-management" 1
  upsert_config MQTT_USERNAME "gw" 1
  upsert_config MQTT_PORT "1883" 1
  upsert_config MQTT_BIND_ADDRESS "0.0.0.0" 1
  upsert_config WEB_BIND_ADDRESS "0.0.0.0" 1
  upsert_config WEB_PORT "80" 1
  upsert_config WEB_HTTPS_PORT "443" 1
  upsert_config APP_SECRET_ACTIVE_KEY_VERSION "1" 1
  upsert_config AUTH_ACCESS_TOKEN_ISSUER "asset-management-api" 1
  upsert_config AUTH_ACCESS_TOKEN_AUDIENCE "asset-management-web" 1

  local mqtt_pw redis_pw pg_pw token_secret secret_key
  mqtt_pw="$(config_value MQTT_PASSWORD)"
  if [[ -z "$mqtt_pw" ]]; then
    mqtt_pw="$(random_hex)"
    upsert_config MQTT_PASSWORD "$mqtt_pw"
    t generated_mqtt_password
  fi
  redis_pw="$(config_value REDIS_PASSWORD)"
  if [[ -z "$redis_pw" ]]; then
    upsert_config REDIS_PASSWORD "$(random_hex)"
  fi
  pg_pw="$(config_value POSTGRES_PASSWORD)"
  if [[ -z "$pg_pw" ]]; then
    upsert_config POSTGRES_PASSWORD "$(random_hex)"
  fi
  token_secret="$(config_value AUTH_ACCESS_TOKEN_SECRET)"
  if [[ -z "$token_secret" ]]; then
    upsert_config AUTH_ACCESS_TOKEN_SECRET "$(random_hex)"
  fi
  secret_key="$(config_value APP_SECRET_ACTIVE_KEY_BASE64)"
  if [[ -z "$secret_key" ]]; then
    upsert_config APP_SECRET_ACTIVE_KEY_BASE64 "$(random_b64)"
  fi

  load_config
  local https="${ENABLE_HTTPS:-false}"
  local origins="http://${PUBLIC_HOST},http://${PUBLIC_HOST}:80,http://127.0.0.1,http://localhost"
  if [[ "$https" == "true" || "$https" == "1" || "$https" == "yes" ]]; then
    origins="https://${PUBLIC_HOST},https://${PUBLIC_HOST}:443,${origins}"
    upsert_config AUTH_REFRESH_COOKIE_SECURE "true"
  else
    upsert_config AUTH_REFRESH_COOKIE_SECURE "false"
  fi
  upsert_config AUTH_ALLOWED_ORIGINS "$origins"
  upsert_config SPRING_PROFILES_ACTIVE "install"
  upsert_config APP_DEV_BOOTSTRAP_MQTT_ENABLED "true"
  upsert_config APP_DEV_BOOTSTRAP_ROOT_USERNAME "$(config_value ROOT_USERNAME)"
  upsert_config APP_DEV_BOOTSTRAP_ROOT_PASSWORD "$(config_value ROOT_PASSWORD)"
  upsert_config APP_DEV_BOOTSTRAP_ROOT_DISPLAY_NAME "$(config_value ROOT_USERNAME)"
  upsert_config APP_DEV_BOOTSTRAP_ROOT_EMAIL "$(config_value ROOT_USERNAME)@local"
  upsert_config ROOT_EMAIL "$(config_value ROOT_USERNAME)@local"
  upsert_config APP_DEV_BOOTSTRAP_MQTT_USERNAME "$(config_value MQTT_USERNAME)"
  upsert_config APP_DEV_BOOTSTRAP_MQTT_PASSWORD "$(config_value MQTT_PASSWORD)"
  upsert_config APP_DEV_BOOTSTRAP_MQTT_BROKER_URI "mqtt://${PUBLIC_HOST}:${MQTT_PORT:-1883}"
  upsert_config MQTT_PUBLIC_BROKER_HOST "$PUBLIC_HOST"
  upsert_config MQTT_CONNECTION_TEST_ALLOWED_PRIVATE_ADDRESSES "${PUBLIC_HOST},127.0.0.1,mosquitto"
  load_config
}

refuse_if_existing_data
codename="$(require_platform)"
prepare_config
ensure_docker "$codename"
load_images
prepare_data_dir
prepare_runtime
t start_infra
am_compose up -d postgres redis mosquitto
t start_api
am_compose up -d api
wait_ready
ensure_mqtt_ready
t start_apps
am_compose up -d worker web
install_systemd
print_summary
