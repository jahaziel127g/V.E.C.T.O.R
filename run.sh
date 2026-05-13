#!/bin/bash
#
# V.E.C.T.O.R - Complete Management Script
# Usage: ./run.sh [install|start|stop|restart|status|build|clean]
#

set -e

APP_NAME="vector_rust"
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
BIN_DIR="$APP_DIR/rust/target/release"
LOG_DIR="$APP_DIR/logs"
BACKEND_PID="/tmp/vector_backend.pid"
FRONTEND_PID="/tmp/vector_frontend.pid"
FRONTEND_PORT=9000
BACKEND_PORT=8080

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

mkdir -p "$LOG_DIR"

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step()  { echo -e "${BLUE}[STEP]${NC} $1"; }

# ============ INSTALL ============
install() {
    log_info "Installing V.E.C.T.O.R dependencies..."

    # Check Rust
    if ! command -v cargo &> /dev/null; then
        log_info "Installing Rust..."
        curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
        source "$HOME/.cargo/env"
    fi

    # Check Python
    if ! command -v python3 &> /dev/null; then
        log_error "Python3 not found. Please install Python3."
        exit 1
    fi

    # Check Ollama (optional)
    if command -v ollama &> /dev/null; then
        log_info "Ollama found: $(ollama --version)"
        log_info "Make sure Ollama is running: ollama serve"
    else
        log_warn "Ollama not found. Install from: https://ollama.ai"
    fi

    # Check zim tools (optional)
    if command -v zimsearch &> /dev/null; then
        log_info "zim tools found"
    else
        log_warn "zim tools not found. Wikipedia search will be disabled."
    fi

    # Build release
    log_step "Building V.E.C.T.O.R..."
    cd "$APP_DIR/rust"
    cargo build --release
    cd "$APP_DIR"

    log_info "Installation complete!"
    log_info "Run './run.sh start' to start the server"
}

# ============ BUILD ============
build() {
    log_step "Building release binary..."
    cd "$APP_DIR/rust"
    cargo build --release
    cd "$APP_DIR"
    log_info "Build complete: $BIN_DIR/$APP_NAME"
}

# ============ CLEAN ============
clean() {
    log_step "Cleaning build artifacts..."
    cd "$APP_DIR/rust"
    cargo clean
    cd "$APP_DIR"
    log_info "Clean complete"
}

# ============ HELPERS ============
get_pid() {
    local file=$1
    [ -f "$file" ] && cat "$file" 2>/dev/null
}

is_running_pid() {
    local pid=$1
    [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null
}

backend_running() {
    local pid=$(get_pid $BACKEND_PID)
    is_running_pid "$pid"
}

frontend_running() {
    local pid=$(get_pid $FRONTEND_PID)
    is_running_pid "$pid"
}

# ============ START ============
start() {
    log_info "Starting V.E.C.T.O.R..."

    # Build if needed
    if [ ! -f "$BIN_DIR/$APP_NAME" ]; then
        log_warn "Binary not found. Building..."
        build
    fi

    # Start Backend
    cd "$APP_DIR/rust"
    nohup ./target/release/$APP_NAME > "$LOG_DIR/backend.log" 2>&1 &
    echo $! > "$BACKEND_PID"

    # Start Frontend
    cd "$APP_DIR/frontend"
    nohup python3 -m http.server $FRONTEND_PORT > "$LOG_DIR/frontend.log" 2>&1 &
    echo $! > "$FRONTEND_PID"

    sleep 2

    # Check status
    if backend_running; then
        log_info "Backend started (PID: $(get_pid $BACKEND_PID))"
    else
        log_error "Backend failed to start"
        cat "$LOG_DIR/backend.log"
        exit 1
    fi

    if frontend_running; then
        log_info "Frontend started (PID: $(get_pid $FRONTEND_PID))"
    else
        log_error "Frontend failed to start"
        cat "$LOG_DIR/frontend.log"
        exit 1
    fi

    echo ""
    log_info "V.E.C.T.O.R is running!"
    echo "  Backend: http://localhost:$BACKEND_PORT"
    echo "  Frontend: http://localhost:$FRONTEND_PORT"
    echo "  API: http://localhost:$BACKEND_PORT/api/health"
}

# ============ STOP ============
stop() {
    log_info "Stopping everything..."

    # Stop Backend
    if backend_running; then
        local bpid=$(get_pid $BACKEND_PID)
        log_info "Stopping backend (PID: $bpid)..."
        kill $bpid 2>/dev/null || true
        for i in {1..5}; do
            if ! is_running_pid $bpid; then break; fi
            sleep 1
        done
        is_running_pid $bpid && kill -9 $bpid 2>/dev/null || true
    fi
    rm -f "$BACKEND_PID"

    # Stop Frontend
    if frontend_running; then
        local fpid=$(get_pid $FRONTEND_PID)
        log_info "Stopping frontend (PID: $fpid)..."
        kill $fpid 2>/dev/null || true
        for i in {1..5}; do
            if ! is_running_pid $fpid; then break; fi
            sleep 1
        done
        is_running_pid $fpid && kill -9 $fpid 2>/dev/null || true
    fi
    rm -f "$FRONTEND_PID"

    # Kill any orphaned processes
    pkill -f "vector_rust" 2>/dev/null || true
    pkill -f "python.*http.server.*$FRONTEND_PORT" 2>/dev/null || true

    log_info "All services stopped"
}

# ============ STATUS ============
status() {
    echo "V.E.C.T.O.R Status:"
    echo ""

    if backend_running; then
        log_info "Backend: RUNNING (PID: $(get_pid $BACKEND_PID))"
        curl -s http://localhost:$BACKEND_PORT/api/health 2>/dev/null | grep -q "healthy" && log_info "  API: OK" || log_error "  API: DOWN"
    else
        log_warn "Backend: STOPPED"
    fi

    echo ""

    if frontend_running; then
        log_info "Frontend: RUNNING (PID: $(get_pid $FRONTEND_PID))"
        curl -s http://localhost:$FRONTEND_PORT 2>/dev/null | head -1 | grep -q "html" && log_info "  Web: OK" || log_error "  Web: DOWN"
    else
        log_warn "Frontend: STOPPED"
    fi
}

# ============ MAIN ============
run_foreground() {
    log_info "Starting V.E.C.T.O.R in foreground mode..."
    log_info "Press Ctrl+C to stop"
    echo ""
    
    # Kill any existing processes
    stop
    
    # Build if needed
    if [ ! -f "$BIN_DIR/$APP_NAME" ]; then
        log_warn "Binary not found. Building..."
        build
    fi
    
    # Start backend in foreground
    cd "$APP_DIR/rust"
    echo "Starting backend on http://0.0.0.0:8080 ..."
    ./target/release/$APP_NAME &
    BACKEND_PID=$!
    echo "Backend started (PID: $BACKEND_PID)"
    
    # Start frontend in background (can be stopped)
    cd "$APP_DIR/frontend"
    python3 -m http.server $FRONTEND_PORT &
    FRONTEND_PID=$!
    echo "Frontend started (PID: $FRONTEND_PID)"
    cd "$APP_DIR"
    
    echo ""
    echo "========================================="
    echo "V.E.C.T.O.R is running!"
    echo "  Backend: http://localhost:$BACKEND_PORT"
    echo "  Frontend: http://localhost:$FRONTEND_PORT"
    echo "========================================="
    echo ""
    log_info "Press Ctrl+C to stop everything..."
    
    # Wait for Ctrl+C
    trap "stop; exit 0" INT TERM
    wait
}

case "${1:-status}" in
    install)
        install
        ;;
    build)
        build
        ;;
    clean)
        clean
        ;;
    run|dev)
        run_foreground
        ;;
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
        echo "V.E.C.T.O.R Management Script"
        echo ""
        echo "Usage: ./run.sh [command]"
        echo ""
        echo "Commands:"
        echo "  install  - Install dependencies and build"
        echo "  build    - Build release binary"
        echo "  clean    - Clean build artifacts"
        echo "  run      - Run in foreground (Ctrl+C to stop)"
        echo "  dev      - Same as run"
        echo "  start    - Start all services in background"
        echo "  stop     - Stop all services"
        echo "  restart  - Restart all services"
        echo "  status   - Show status"
        exit 1
        ;;
esac