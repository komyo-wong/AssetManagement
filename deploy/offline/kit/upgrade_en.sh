#!/usr/bin/env bash
set -euo pipefail
export AM_LANG=en
export AM_LANG_LOCKED=1
KIT="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=common.sh
source "$KIT/common.sh"
# shellcheck source=upgrade_engine.sh
source "$KIT/upgrade_engine.sh"
