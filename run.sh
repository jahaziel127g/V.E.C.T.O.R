#!/bin/bash
#
# V.E.C.T.O.R - Production Startup Script
# Usage: ./run.sh [dev|start|stop|restart|status]
#

set -e

APP_NAME="vector_rust"
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
BIN_DIR="$APP_DIR/rust/target/release"
LOG_DIR="$APP_DIR/logs"
PID_FILE="/tmp/vector_rust.pid"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Create logs directory
mkdir -p "$LOG_DIR"

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

get_pid() {
    if [ -f "$PID_FILE" ]; then
        cat "$PID_FILE"
    fi
}

is_running() {
    local pid=$(get_pid)
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
        return 0
    fi
    return 1
}

start() {
    if is_running; then
        log_warn "V.E.C.T.O.R is already running (PID: $(get_pid))"
        return 1
    fi

    log_info "Starting V.E.C.T.O.R..."

    # Check if binary exists
    if [ ! -f "$BIN_DIR/$APP_NAME" ]; then
        log_error "Binary not found. Building..."
        cd "$APP_DIR/rust"
        cargo build --release
        cd "$APP_DIR"
    fi

    # Start the app
    cd "$APP_DIR/rust"
    nohup ./target/release/$APP_NAME > "$LOG_DIR/vector.log" 2>&1 &
    echo $! > "$PID_FILE"

    # Wait for startup
    sleep 2

    if is_running; then
        log_info "V.E.C.T.O.R started successfully (PID: $(get_pid))"
        log_info "Backend: http://localhost:8080"
        log_info "Frontend: http://localhost:9000"
    else
        log_error "Failed to start V.E.C.T.O.R"
        cat "$LOG_DIR/vector.log"
        exit 1
    fi
}

stop() {
    if ! is_running; then
        log_warn "V.E.C.T.O.R is not running"
        return 0
    fi

    log_info "Stopping V.E.C.T.O.R..."
    local pid=$(get_pid)
    kill "$pid" 2>/dev/null || true

    # Wait for shutdown
    for i in {1..5}; do
        if ! kill -0 "$pid" 2>/dev/null; then
            break
        fi
        sleep 1
    done

    # Force kill if still running
    if kill -0 "$pid" 2>/dev/null; then
        log_warn "Force killing..."
        kill -9 "$pid" 2>/dev/null || true
    fi

    rm -f "$PID_FILE"
    log_info "V.E.C.T.O.R stopped"
}

status() {
    if is_running; then
        log_info "V.E.C.T.O.R is running (PID: $(get_pid))"
        curl -s http://localhost:8080/api/health | grep -q "healthy" && log_info "API: OK" || log_error "API: DOWN"
    else
        log_warn "V.E.C.T.O.R is not running"
    fi
}

case "${1:-start}" in
    start)
        start
        ;;
    stop)
        stop
        ;;
    restart)
        stop
        start
        ;;
    status)
        status
        ;;
    *)
        echo "Usage: $0 {start|stop|restart|status}"
        exit 1
        ;;
esac