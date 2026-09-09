#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
export JAVA_HOME="${JAVA_HOME:-$ROOT/.tools/jdk-25}"
export PATH="$JAVA_HOME/bin:$ROOT/.tools/maven/bin:$PATH"

set -a
# shellcheck disable=SC1091
source "$ROOT/server/.env.local"
set +a

export MQTT_CONNECTION_TEST_ALLOWED_PRIVATE_ADDRESSES="${MQTT_CONNECTION_TEST_ALLOWED_PRIVATE_ADDRESSES:-127.0.0.1}"
export SERVER_PORT="${SERVER_PORT:-8080}"

JAR="$ROOT/server/platform-api/target/platform-api-1.0.7.jar"
if [[ ! -f "$JAR" ]]; then
  echo "Missing $JAR — build first:" >&2
  echo "  mvn -DskipTests package -pl platform-api -am -Dmaven.repo.local=/private/tmp/asset-management-m2" >&2
  exit 1
fi

exec java -jar "$JAR" --spring.profiles.active=dev
