#!/usr/bin/env bash
set -euo pipefail
# shellcheck source=common.sh
source "$(cd "$(dirname "$0")" && pwd)/common.sh"
need_root "$@"
load_config
am_compose ps
echo
curl -fsS http://127.0.0.1:8080/actuator/health 2>/dev/null || t api_not_ready
echo
