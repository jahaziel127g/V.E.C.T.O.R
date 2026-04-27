#!/bin/bash

set -e

echo "=== V.E.C.T.O.R Backend Installer ==="

if [ "$EUID" -eq 0 ]; then
    echo "Warning: Running as root. This script is designed for user-level installation."
fi

echo "[1/5] Checking Java..."
if ! command -v java &> /dev/null; then
    echo "Java not found. Installing OpenJDK 17..."
    if command -v apt-get &> /dev/null; then
        sudo apt-get update && sudo apt-get install -y openjdk-17-jdk
    elif command -v pacman &> /dev/null; then
        sudo pacman -S --noconfirm jdk17-openjdk
    elif command -v dnf &> /dev/null; then
        sudo dnf install -y java-17-openjdk-devel
    else
        echo "Please install Java 17 manually"
        exit 1
    fi
fi

java -version 2>&1 | head -1

echo "[2/5] Checking Maven..."
if ! command -v mvn &> /dev/null; then
    echo "Maven not found. Installing via SDKMAN..."
    
    if [ ! -d "$HOME/.sdkman" ]; then
        echo "Installing SDKMAN..."
        curl -s "https://get.sdkman.io" | bash
        source "$HOME/.sdkman/bin/sdkman-init.sh"
    else
        source "$HOME/.sdkman/bin/sdkman-init.sh"
    fi
    
    sdk install maven 3.9.9
fi

mvn --version | head -1

echo "[3/5] Checking Ollama..."
if ! command -v ollama &> /dev/null; then
    echo "Installing Ollama..."
    curl -fsSL https://ollama.com/install.sh | sh
fi

echo "[4/5] Downloading AI models..."
source "$HOME/.sdkman/bin/sdkman-init.sh" 2>/dev/null || true
ollama serve &
OLLAMA_PID=$!
sleep 5

echo "  - Pulling simple model (1B)..."
ollama pull hf.co/Andycurrent/Gemma-3-1B-it-GLM-4.7-Flash-Heretic-Uncensored-Thinking_GGUF:Q4_K_M 2>/dev/null || \
ollama pull gemma:1b 2>/dev/null || echo "  Warning: Could not pull simple model"

echo "  - Pulling complex model (2B)..."
ollama pull hf.co/bartowski/gemma-2-2b-it-GGUF:Q4_K_M 2>/dev/null || \
ollama pull gemma:2b 2>/dev/null || echo "  Warning: Could not pull complex model"

kill $OLLAMA_PID 2>/dev/null || true

echo "[5/5] Building V.E.C.T.O.R..."
cd "$(dirname "$0")"

mvn clean package -DskipTests -q

echo ""
echo "=== Installation Complete ==="
echo ""
echo "To start the backend:"
echo "  java -jar target/vector-1.0.0.jar"
echo ""
echo "API will be available at: http://localhost:8080"
echo "Test: curl http://localhost:8080/api/health"