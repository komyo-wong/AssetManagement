#!/usr/bin/env bash
set -euo pipefail
# shellcheck source=common.sh
source "$(cd "$(dirname "$0")" && pwd)/common.sh"
need_root "$@"
load_config

backup_dir="${DATA_DIR}/backups"
mkdir -p "$backup_dir"
ts="$(date +%Y%m%d-%H%M%S)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

t backup_db
am_compose exec -T postgres \
  pg_dump -U "${POSTGRES_USER:-asset_app}" "${POSTGRES_DB:-asset_management}" \
  > "$work/database.sql"

t backup_docs
if [[ -d "${DATA_DIR}/documents" ]]; then
  tar -C "$DATA_DIR" -cf "$work/documents.tar" documents
else
  tar -cf "$work/documents.tar" --files-from /dev/null
fi

out="$backup_dir/asset-management-${ts}.tar.gz"
tar -C "$work" -czf "$out" database.sql documents.tar
t backup_done "$out"
