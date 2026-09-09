#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Asset Management 部署脚本 ==="
echo ""

# 检查前置条件
check_prerequisites() {
    local missing=()
    command -v docker >/dev/null 2>&1 || missing+=("docker")
    command -v docker compose >/dev/null 2>&1 || missing+=("docker compose")
    command -v mvn >/dev/null 2>&1 || missing+=("mvn (Maven 3.9.9+)")
    command -v java >/dev/null 2>&1 || missing+=("java (JDK 25)")
    command -v node >/dev/null 2>&1 || missing+=("node (20.19+)")
    command -v pnpm >/dev/null 2>&1 || missing+=("pnpm (8.8+)")
    
    if [[ ${#missing[@]} -gt 0 ]]; then
        echo "❌ 缺少依赖: ${missing[*]}"
        echo "   请先安装后再运行此脚本"
        exit 1
    fi
    echo "✅ 前置依赖检查通过"
}

# 启动基础设施
start_infra() {
    echo ""
    echo "=== 1. 启动基础设施 ==="
    cd infra
    
    if [[ ! -f ".env" ]]; then
        echo "❌ infra/.env 不存在，请先配置"
        exit 1
    fi
    
    if [[ ! -f "mosquitto/secrets/password_file" ]]; then
        echo "⚠️  Mosquitto 密码文件不存在，正在生成..."
        MQTT_PASS=$(grep '^MQTT_PASSWORD=' .env | cut -d= -f2-)
        docker run --rm -it \
            -v "$(pwd)/mosquitto/secrets:/mosquitto/secrets" \
            eclipse-mosquitto:2 \
            bash -c "echo '$MQTT_PASS' | mosquitto_passwd -c /mosquitto/secrets/password_file asset_platform_local && echo '$MQTT_PASS' | mosquitto_passwd -U /mosquitto/secrets/password_file"
        echo "✅ Mosquitto 密码文件已生成"
    fi
    
    docker compose config >/dev/null
    docker compose up -d
    echo "⏳ 等待服务就绪..."
    sleep 5
    
    # 验证健康状态
    docker compose ps --format json 2>/dev/null | python3 -c "
import sys, json
for line in sys.stdin:
    try:
        d = json.loads(line.strip())
        name = d.get('Name', '?')
        health = d.get('Health', 'unknown')
        status = d.get('Status', '?')
        icon = '✅' if 'healthy' in status.lower() else '⏳'
        print(f'  {icon} {name}: {status}')
    except: pass
" || docker compose ps
    
    echo "✅ 基础设施启动完成"
}

# 构建后端
build_server() {
    echo ""
    echo "=== 2. 构建后端 ==="
    cd "$SCRIPT_DIR/server"
    mvn clean verify -q
    echo "✅ 后端构建完成"
}

# 运行后端
run_server() {
    echo ""
    echo "=== 3. 启动后端服务 ==="
    cd "$SCRIPT_DIR/server"
    
    local pg_pass redis_pass jwt_secret cipher_key cipher_version
    pg_pass=$(grep '^POSTGRES_PASSWORD=' ../infra/.env | cut -d= -f2-)
    redis_pass=$(grep '^REDIS_PASSWORD=' ../infra/.env | cut -d= -f2-)
    jwt_secret=$(grep 'AUTH_ACCESS_TOKEN_SECRET' DEPLOY_SECRETS.txt 2>/dev/null | cut -d= -f2- || echo "")
    cipher_key=$(grep 'APP_SECRET_ACTIVE_KEY_BASE64' DEPLOY_SECRETS.txt 2>/dev/null | cut -d= -f2- || echo "")
    cipher_version=$(grep 'APP_SECRET_ACTIVE_KEY_VERSION' DEPLOY_SECRETS.txt 2>/dev/null | cut -d= -f2- || echo "1")
    
    if [[ -z "$jwt_secret" || -z "$cipher_key" ]]; then
        echo "❌ 缺少应用密钥，请检查 DEPLOY_SECRETS.txt"
        exit 1
    fi
    
    export DB_URL="jdbc:postgresql://postgres:5432/asset_management"
    export DB_USERNAME="asset_app"
    export DB_PASSWORD="$pg_pass"
    export REDIS_HOST="redis"
    export REDIS_PASSWORD="$redis_pass"
    export AUTH_ACCESS_TOKEN_SECRET="$jwt_secret"
    export APP_SECRET_ACTIVE_KEY_VERSION="$cipher_version"
    export APP_SECRET_ACTIVE_KEY_BASE64="$cipher_key"
    export MQTT_WORKER_ENABLED="false"  # 待 MQTT 协议确认后开启
    
    echo "🚀 启动 API (http://localhost:8080)..."
    mvn -pl platform-api -am spring-boot:run -Dspring-boot.run.profiles=dev &
    API_PID=$!
    
    echo "🚀 启动 Worker (可选，MQTT_WORKER_ENABLED=true)..."
    MQTT_WORKER_ENABLED="false" mvn -pl mqtt-worker -am spring-boot:run -Dspring-boot.run.profiles=dev &
    WORKER_PID=$!
    
    echo "✅ 后端服务已启动 (API PID=$API_PID, Worker PID=$WORKER_PID)"
    echo "   按 Ctrl+C 停止所有服务"
    
    trap "kill $API_PID $WORKER_PID 2>/dev/null; exit" INT TERM
    wait
}

# 构建前端
build_web() {
    echo ""
    echo "=== 4. 构建前端 ==="
    cd "$SCRIPT_DIR/web"
    
    if [[ ! -f ".env.local" ]]; then
        cp .env.example .env.local
        echo "✅ 已创建 .env.local"
    fi
    
    if [[ ! -d "node_modules" ]]; then
        echo "⏳ 安装前端依赖..."
        pnpm install --frozen-lockfile
    fi
    
    pnpm build
    echo "✅ 前端构建完成 (dist/)"
}

# 主菜单
main() {
    local action="${1:-menu}"
    
    case "$action" in
        check)
            check_prerequisites
            ;;
        infra)
            start_infra
            ;;
        build-server)
            build_server
            ;;
        run-server)
            run_server
            ;;
        build-web)
            build_web
            ;;
        all)
            check_prerequisites
            start_infra
            build_server
            build_web
            echo ""
            echo "=== 部署完成 ==="
            echo "  API:     http://localhost:8080"
            echo "  Swagger: http://localhost:8080/swagger-ui.html"
            echo "  Web:     http://localhost:3006"
            echo ""
            echo "运行 'bash deploy.sh run-server' 启动后端服务"
            ;;
        *)
            echo "用法: bash deploy.sh [check|infra|build-server|run-server|build-web|all]"
            echo ""
            echo "  check       - 检查前置依赖"
            echo "  infra       - 启动 Docker 基础设施"
            echo "  build-server - 构建后端 (mvn verify)"
            echo "  run-server  - 启动后端服务"
            echo "  build-web   - 构建前端 (pnpm build)"
            echo "  all         - 执行全部构建步骤"
            ;;
    esac
}

main "$@"
