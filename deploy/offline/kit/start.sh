#!/usr/bin/env bash
set -euo pipefail
# shellcheck source=common.sh
source "$(cd "$(dirname "$0")" && pwd)/common.sh"
need_root "$@"
load_config
prepare_data_dir
prepare_runtime
am_compose up -d
sync_mosquitto_gateway_accounts
t started
