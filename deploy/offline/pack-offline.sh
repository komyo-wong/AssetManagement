#!/usr/bin/env bash
# Build a customer-facing offline tarball:
#   deploy/offline/dist/asset-management-<ver>-linux-amd64.tar.gz
#
# Run on the development machine (needs Docker amd64, JDK, Maven, network once).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
KIT_SRC="$ROOT/deploy/offline/kit"
DIST_ROOT="$ROOT/deploy/offline/dist"
VERSION="$(tr -d '[:space:]' < "$ROOT/VERSION")"
STAGE="$DIST_ROOT/asset-management-$VERSION"
TARBALL="$DIST_ROOT/asset-management-$VERSION-linux-amd64.tar.gz"
PLATFORM="${DOCKER_PLATFORM:-linux/amd64}"

JAVA_HOME="${JAVA_HOME:-$ROOT/.tools/jdk-25}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:${ROOT}/.tools/maven/bin:$PATH"

POSTGRES_IMAGE="${POSTGRES_IMAGE:-postgis/postgis:18-3.6}"
REDIS_IMAGE="${REDIS_IMAGE:-redis:8-alpine}"
MOSQUITTO_IMAGE="${MOSQUITTO_IMAGE:-eclipse-mosquitto:2}"

echo "=== 离线包 ${VERSION}  (${PLATFORM}) ==="

build_jars() {
  echo ">>> 构建后端 jar"
  (
    cd "$ROOT/server"
    mvn -DskipTests package -pl platform-api,mqtt-worker -am \
      -Dmaven.repo.local="${MAVEN_REPO_LOCAL:-/private/tmp/asset-management-m2}"
  )
  rm -rf "$ROOT/deploy/build"
  mkdir -p "$ROOT/deploy/build/dist"
  cp "$ROOT/server/platform-api/target/platform-api-${VERSION}.jar" "$ROOT/deploy/build/"
  cp "$ROOT/server/mqtt-worker/target/mqtt-worker-${VERSION}.jar" "$ROOT/deploy/build/"
}

build_web() {
  echo ">>> 准备前端 dist"
  if [[ -d "$ROOT/web/dist" && -f "$ROOT/web/dist/index.html" && "${FORCE_WEB_BUILD:-0}" != "1" ]]; then
    echo "    使用已有 web/dist"
  else
    command -v pnpm >/dev/null 2>&1 || {
      echo "缺少 web/dist 且本机没有 pnpm，无法构建前端" >&2
      exit 1
    }
    (cd "$ROOT/web" && pnpm build)
  fi
  cp -R "$ROOT/web/dist/." "$ROOT/deploy/build/dist/"
  copy_guide_into_web
}

copy_guide_into_web() {
  local src="$ROOT/docs/说明书"
  local dest="$ROOT/deploy/build/dist/guide"
  if [[ ! -f "$src/index.html" ]]; then
    return 0
  fi
  echo ">>> 写入网页 /guide/"
  mkdir -p "$dest"
  if command -v rsync >/dev/null 2>&1; then
    rsync -a --delete --exclude '.DS_Store' --exclude '*.command' "$src/" "$dest/"
  else
    rm -rf "$dest"
    mkdir -p "$dest"
    cp -R "$src/." "$dest/"
    rm -f "$dest/"*.command 2>/dev/null || true
  fi
}

build_app_images() {
  echo ">>> 构建应用镜像 ($PLATFORM)"
  docker build --platform "$PLATFORM" -f "$ROOT/deploy/docker/Dockerfile.api" \
    -t "asset-management-api:${VERSION}" "$ROOT/deploy"
  docker build --platform "$PLATFORM" -f "$ROOT/deploy/docker/Dockerfile.worker" \
    -t "asset-management-worker:${VERSION}" "$ROOT/deploy"
  docker build --platform "$PLATFORM" -f "$ROOT/deploy/docker/Dockerfile.web" \
    -t "asset-management-web:${VERSION}" "$ROOT/deploy"
}

pull_infra_images() {
  echo ">>> 拉取基础镜像"
  local img
  for img in "$POSTGRES_IMAGE" "$REDIS_IMAGE" "$MOSQUITTO_IMAGE"; do
    if docker image inspect "$img" >/dev/null 2>&1; then
      echo "    已有 $img，跳过拉取"
      continue
    fi
    docker pull --platform "$PLATFORM" "$img"
  done
}

save_images() {
  local out="$1"
  mkdir -p "$(dirname "$out")"
  echo ">>> 导出镜像 -> $out"
  docker save \
    "$POSTGRES_IMAGE" \
    "$REDIS_IMAGE" \
    "$MOSQUITTO_IMAGE" \
    "asset-management-api:${VERSION}" \
    "asset-management-worker:${VERSION}" \
    "asset-management-web:${VERSION}" \
    | gzip -1 > "$out"
}

download_docker_debs() {
  local dest_root="$1"
  echo ">>> 下载 Docker 离线 .deb（jammy / noble amd64）"
  python3 - "$dest_root" <<'PY'
import re, sys, time, urllib.error, urllib.request
from pathlib import Path

dest_root = Path(sys.argv[1])
prefixes = (
    "containerd.io",
    "docker-ce-cli",
    "docker-ce_",
    "docker-buildx-plugin",
    "docker-compose-plugin",
)
codenames = ("jammy", "noble")
mirrors = (
    "https://mirrors.aliyun.com/docker-ce/linux/ubuntu",
    "https://mirrors.tuna.tsinghua.edu.cn/docker-ce/linux/ubuntu",
    "https://download.docker.com/linux/ubuntu",
)

opener = urllib.request.build_opener()
opener.addheaders = [("User-Agent", "asset-management-offline-pack")]


def fetch(url: str, timeout: int = 90):
    last_error = None
    for attempt in range(1, 4):
        try:
            return opener.open(url, timeout=timeout)
        except (urllib.error.URLError, TimeoutError, OSError) as error:
            last_error = error
            time.sleep(attempt * 2)
    raise last_error


def list_debs(base: str, codename: str) -> list[str]:
    url = f"{base}/dists/{codename}/pool/stable/amd64/"
    with fetch(url) as resp:
        html = resp.read().decode("utf-8", "replace")
    return re.findall(r'href="([^"]+_amd64\.deb)"', html)

def pick(names: list[str], prefix: str) -> str:
    matched = [name for name in names if name.startswith(prefix)]
    if not matched:
        raise FileNotFoundError(prefix)
    matched.sort()
    return matched[-1]

for codename in codenames:
    names = None
    base_used = None
    for base in mirrors:
        try:
            names = list_debs(base, codename)
            base_used = base
            print(f"    index {codename} <- {base}")
            break
        except Exception as error:
            print(f"    index {codename} 失败 {base}: {error}")
    if not names:
        raise SystemExit(f"无法列出 {codename} 的 Docker .deb")
    out_dir = dest_root / codename
    out_dir.mkdir(parents=True, exist_ok=True)
    for prefix in prefixes:
        filename = pick(names, prefix)
        target = out_dir / filename
        if target.exists() and target.stat().st_size > 0:
            print(f"    skip {codename}/{filename}")
            continue
        url = f"{base_used}/dists/{codename}/pool/stable/amd64/{filename}"
        print(f"    get  {codename}/{filename}")
        tmp = target.with_suffix(target.suffix + ".part")
        with fetch(url, timeout=180) as resp, tmp.open("wb") as fh:
            while True:
                chunk = resp.read(1024 * 1024)
                if not chunk:
                    break
                fh.write(chunk)
        tmp.replace(target)
PY
}

assemble_kit() {
  echo ">>> 组装目录 $STAGE"
  local saved_images="" saved_packages=""
  if [[ -f "$STAGE/images/images.tar.gz" ]]; then
    saved_images="$(mktemp /tmp/am-images.XXXXXX.tar.gz)"
    mv "$STAGE/images/images.tar.gz" "$saved_images"
    echo "    保留已导出镜像 $saved_images"
  fi
  if [[ -d "$STAGE/packages" ]] && find "$STAGE/packages" -name '*.deb' -print -quit | grep -q .; then
    saved_packages="$(mktemp -d /tmp/am-debs.XXXXXX)"
    mv "$STAGE/packages" "$saved_packages/packages"
    echo "    保留已下载 Docker .deb"
  fi
  rm -rf "$STAGE"
  mkdir -p "$STAGE"
  if command -v rsync >/dev/null 2>&1; then
    rsync -a \
      --exclude 'config.env' \
      --exclude 'runtime/' \
      --exclude 'images/' \
      --exclude 'packages/' \
      --exclude '.DS_Store' \
      "$KIT_SRC/" "$STAGE/"
  else
    cp -R "$KIT_SRC/." "$STAGE/"
    rm -rf "$STAGE/config.env" "$STAGE/runtime" "$STAGE/images" "$STAGE/packages"
  fi
  cp "$ROOT/VERSION" "$STAGE/VERSION"
  mkdir -p "$STAGE/images" "$STAGE/packages" "$STAGE/runtime" "$STAGE/certs"
  if [[ -n "$saved_images" ]]; then
    mv "$saved_images" "$STAGE/images/images.tar.gz"
  fi
  if [[ -n "$saved_packages" ]]; then
    rm -rf "$STAGE/packages"
    mv "$saved_packages/packages" "$STAGE/packages"
    rmdir "$saved_packages" 2>/dev/null || true
  fi
  chmod +x "$STAGE"/*.sh
  local guide="$ROOT/docs/说明书"
  if [[ -f "$guide/index.html" ]]; then
    echo ">>> 写入说明书 guide/"
    mkdir -p "$STAGE/guide"
    if command -v rsync >/dev/null 2>&1; then
      rsync -a --delete --exclude '.DS_Store' "$guide/" "$STAGE/guide/"
    else
      rm -rf "$STAGE/guide"
      mkdir -p "$STAGE/guide"
      cp -R "$guide/." "$STAGE/guide/"
    fi
    chmod +x "$STAGE/guide/"*.command 2>/dev/null || true
  fi
}

if [[ "${SKIP_JAVA:-0}" != "1" ]]; then
  build_jars
fi
if [[ "${SKIP_WEB:-0}" != "1" ]]; then
  build_web
else
  copy_guide_into_web
fi
if [[ "${SKIP_IMAGES:-0}" != "1" ]]; then
  build_app_images
  pull_infra_images
fi

assemble_kit
if [[ "${SKIP_IMAGES:-0}" != "1" ]]; then
  if [[ -f "$STAGE/images/images.tar.gz" && "${FORCE_SAVE:-0}" != "1" ]]; then
    echo ">>> 已有镜像包，跳过导出"
  else
    save_images "$STAGE/images/images.tar.gz"
  fi
fi
if [[ "${SKIP_DEBS:-0}" != "1" ]]; then
  download_docker_debs "$STAGE/packages"
fi

mkdir -p "$DIST_ROOT"
echo ">>> 打包 $TARBALL"
tar -C "$DIST_ROOT" -czf "$TARBALL" "asset-management-$VERSION"
ls -lh "$TARBALL"
echo "完成。"
