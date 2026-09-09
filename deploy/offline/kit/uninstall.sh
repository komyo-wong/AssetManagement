#!/usr/bin/env bash
set -euo pipefail
# shellcheck source=common.sh
source "$(cd "$(dirname "$0")" && pwd)/common.sh"
need_root "$@"
load_config

purge=0
if [[ "${1:-}" == "--purge" ]]; then
  purge=1
fi

if systemctl list-unit-files asset-management.service >/dev/null 2>&1; then
  systemctl disable --now asset-management.service >/dev/null 2>&1 || true
  rm -f /etc/systemd/system/asset-management.service
  systemctl daemon-reload || true
fi
rm -f /etc/asset-management/install-root
rmdir /etc/asset-management 2>/dev/null || true

am_compose down --remove-orphans || true

if [[ "$purge" -eq 1 ]]; then
  t uninstalled_purge_warn "$DATA_DIR"
  rm -rf "$DATA_DIR"
  t uninstalled_purged
else
  t uninstalled_keep "$DATA_DIR"
  t purge_hint "$0"
fi
