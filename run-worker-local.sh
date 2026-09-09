#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
export JAVA_HOME="${JAVA_HOME:-$ROOT/.tools/jdk-25}"
export PATH="$JAVA_HOME/bin:$ROOT/.tools/maven/bin:$PATH"

set -a
# shellcheck disable=SC1091
source "$ROOT/server/.env.local"
set +a

# Always enable for local worker process; .env.local may keep it false for API-only runs.
export MQTT_WORKER_ENABLED=true
export WORKER_MANAGEMENT_PORT="${WORKER_MANAGEMENT_PORT:-8081}"

JAR="$ROOT/server/mqtt-worker/target/mqtt-worker-1.0.7.jar"
if [[ ! -f "$JAR" ]]; then
  echo "Missing $JAR — build first:" >&2
  echo "  mvn -DskipTests package -pl mqtt-worker -am -Dmaven.repo.local=/private/tmp/asset-management-m2" >&2
  exit 1
fi

exec java -jar "$JAR" --spring.profiles.active=dev
