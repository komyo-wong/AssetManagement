#!/usr/bin/env bash
set -euo pipefail
# shellcheck source=common.sh
source "$(cd "$(dirname "$0")" && pwd)/common.sh"
need_root "$@"
load_config

archive="${1:-}"
[[ -n "$archive" && -f "$archive" ]] || die restore_usage

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
tar -C "$work" -xzf "$archive"
[[ -f "$work/database.sql" ]] || die restore_missing_sql

t restore_stop
am_compose stop api worker web

t restore_db
am_compose exec -T postgres \
  psql -U "${POSTGRES_USER:-asset_app}" -d postgres -v ON_ERROR_STOP=1 \
  -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='${POSTGRES_DB:-asset_management}' AND pid <> pg_backend_pid();" \
  >/dev/null
am_compose exec -T postgres dropdb -U "${POSTGRES_USER:-asset_app}" --if-exists "${POSTGRES_DB:-asset_management}"
am_compose exec -T postgres createdb -U "${POSTGRES_USER:-asset_app}" "${POSTGRES_DB:-asset_management}"
am_compose exec -T postgres psql -U "${POSTGRES_USER:-asset_app}" -d "${POSTGRES_DB:-asset_management}" -v ON_ERROR_STOP=1 \
  < "$work/database.sql" >/dev/null

if [[ -f "$work/documents.tar" ]]; then
  t restore_docs
  rm -rf "${DATA_DIR}/documents"
  mkdir -p "$DATA_DIR"
  tar -C "$DATA_DIR" -xf "$work/documents.tar"
  chown 10001:10001 "${DATA_DIR}/documents" 2>/dev/null || chmod 0777 "${DATA_DIR}/documents"
fi

t restore_start
am_compose up -d
t restore_done
