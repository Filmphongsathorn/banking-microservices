#!/bin/bash
# ============================================================
#  Banking System – Deployment Management Script
#  Usage: ./scripts/deploy.sh [up|down|restart|status|logs]
# ============================================================
set -euo pipefail

COMPOSE_FILE="docker-compose.yml"
PROJECT="banking"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
log()   { echo -e "${GREEN}[INFO]${NC}  $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

# ---- Ensure secrets directory ----
init_secrets() {
    mkdir -p secrets
    if [[ ! -f secrets/db_password.txt ]]; then
        warn "Creating default secrets (CHANGE IN PRODUCTION)"
        echo "SecureDBPass@2024!"       > secrets/db_password.txt
        echo "SecureRedisPass@2024!"    > secrets/redis_password.txt
        echo "my-super-secret-jwt-key-at-least-256-bits-long!" > secrets/jwt_secret.txt
        chmod 600 secrets/*.txt
    fi
    if [[ ! -f .env ]]; then
        cp .env.example .env
        warn ".env created from .env.example – please update before production!"
    fi
}

# ---- Startup sequence (ordered) ----
start() {
    init_secrets
    log "Starting middleware layer..."
    docker compose -p $PROJECT -f $COMPOSE_FILE up -d postgres redis zookeeper
    log "Waiting for middleware to be healthy..."
    sleep 10

    log "Starting Kafka..."
    docker compose -p $PROJECT -f $COMPOSE_FILE up -d kafka
    sleep 15

    log "Initializing Kafka topics..."
    docker compose -p $PROJECT -f $COMPOSE_FILE up kafka-init

    log "Starting infrastructure layer..."
    docker compose -p $PROJECT -f $COMPOSE_FILE up -d config-server
    sleep 10
    docker compose -p $PROJECT -f $COMPOSE_FILE up -d eureka-server
    sleep 10
    docker compose -p $PROJECT -f $COMPOSE_FILE up -d api-gateway
    sleep 5

    log "Starting microservices..."
    docker compose -p $PROJECT -f $COMPOSE_FILE up -d \
        auth-service profile-service account-service transaction-service notification-service

    log "All services started! Access points:"
    echo -e "  ${CYAN}API Gateway:${NC}    http://localhost:8080"
    echo -e "  ${CYAN}Eureka:${NC}         http://localhost:8761"
    echo -e "  ${CYAN}Config Server:${NC}  http://localhost:8888"
}

# ---- Observability tools ----
start_obs() {
    log "Starting observability tools..."
    docker compose -p $PROJECT -f $COMPOSE_FILE --profile observability up -d kafka-ui pgadmin
    echo -e "  ${CYAN}Kafka UI:${NC}  http://localhost:9090"
    echo -e "  ${CYAN}pgAdmin:${NC}   http://localhost:5050"
}

# ---- Stop all ----
stop() {
    log "Stopping all services..."
    docker compose -p $PROJECT -f $COMPOSE_FILE down
    log "All services stopped."
}

# ---- Hard reset (removes volumes) ----
nuke() {
    warn "This will DELETE all data! Press Ctrl+C to cancel (5s)..."
    sleep 5
    docker compose -p $PROJECT -f $COMPOSE_FILE down -v --remove-orphans
    log "All services and volumes removed."
}

# ---- Service status ----
status() {
    docker compose -p $PROJECT -f $COMPOSE_FILE ps
}

# ---- Follow logs ----
logs() {
    local SERVICE=${2:-""}
    docker compose -p $PROJECT -f $COMPOSE_FILE logs -f --tail=100 $SERVICE
}

# ---- Health check ----
health() {
    echo -e "\n${CYAN}=== Banking System Health ===${NC}"
    SERVICES=("postgres:5432" "redis:6379" "config-server:8888" "eureka-server:8761" "api-gateway:8080" "auth-service:8081" "profile-service:8082" "account-service:8083" "transaction-service:8084" "notification-service:8085")
    for SVC in "${SERVICES[@]}"; do
        NAME=$(echo $SVC | cut -d: -f1)
        PORT=$(echo $SVC | cut -d: -f2)
        if curl -sf "http://localhost:$PORT/actuator/health" > /dev/null 2>&1; then
            echo -e "  ${GREEN}✓${NC} $NAME (port $PORT)"
        else
            echo -e "  ${RED}✗${NC} $NAME (port $PORT) – not responding"
        fi
    done
    echo ""
}

case "${1:-help}" in
    up|start)       start ;;
    obs)            start_obs ;;
    down|stop)      stop ;;
    nuke)           nuke ;;
    restart)        stop; start ;;
    status|ps)      status ;;
    logs)           logs "$@" ;;
    health)         health ;;
    *)
        echo "Banking System Manager"
        echo "Usage: $0 {up|down|restart|obs|status|logs [service]|health|nuke}"
        ;;
esac
