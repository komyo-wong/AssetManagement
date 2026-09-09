#!/bin/sh
set -eu

if [ -S /var/run/docker.sock ]; then
  gid="$(stat -c '%g' /var/run/docker.sock)"
  if ! getent group "$gid" >/dev/null 2>&1; then
    groupadd -g "$gid" dockersock
  fi
  grp="$(getent group "$gid" | cut -d: -f1)"
  usermod -aG "$grp" app
fi

mkdir -p /app/data/documents /app/data/backups
chown -R app:app /app/data

exec gosu app sh -c 'exec java $JAVA_OPTS -jar /app/app.jar'
