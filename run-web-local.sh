#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT/web"

resolve_node() {
  if command -v node >/dev/null 2>&1; then
    command -v node
    return
  fi
  local candidates=(
    "$ROOT/.tools/node/bin/node"
    "/Applications/Cursor.app/Contents/Resources/app/resources/helpers/node"
  )
  local c
  for c in "${candidates[@]}"; do
    if [[ -x "$c" ]]; then
      echo "$c"
      return
    fi
  done
  echo "Node.js not found. Install Node >=20 or put a portable Node at .tools/node" >&2
  exit 1
}

NODE="$(resolve_node)"
if [[ ! -x ./node_modules/vite/bin/vite.js ]]; then
  echo "Missing web/node_modules — run: corepack enable && pnpm install" >&2
  exit 1
fi

exec "$NODE" ./node_modules/vite/bin/vite.js --host 0.0.0.0 --port "${VITE_PORT:-3006}"
