#!/usr/bin/env bash
set -euo pipefail
# shellcheck source=common.sh
source "$(cd "$(dirname "$0")" && pwd)/common.sh"
need_root "$@"
load_config
am_compose stop
t stopped "$DATA_DIR"
