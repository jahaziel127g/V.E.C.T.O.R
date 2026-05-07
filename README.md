# V.E.C.T.O.R - AI Orchestration Engine

<p align="center">
  <strong>Project V.E.C.T.O.R</strong> | A PBL (Project-Based Learning) Project
</p>

<p align="center">
  ⚠️ <strong>Disclaimer:</strong> This is a learning project for educational purposes. <strong>Nexus Labs</strong> is not a real company.
</p>

<p align="center">
  A lightweight, offline-first AI orchestration engine designed to run efficiently on low-end hardware using local inference.
</p>

---

## 📦 **INSTALLATION GUIDE**

### **For YU (jahazielo - 3.6GB RAM):**
**Recommanded: RUST VERSION**

1. **Install Rust:**
```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
# Restart terminal or: source $HOME/.cargo/env
```

2. **Install Ollama:**
```bash
curl -fsSL https://ollama.com/install.sh | sh
ollama serve &
```

3. **Pull AI Model:**
```bash
ollama pull gemma3:1b-it-qat
```

4. **Install zim-tools (for Wikipedia):**
```bash
sudo pacman -S zim-tools
```

5. **Build & Run Rust Version:**
```bash
cd /home/jahazielo/V.E.C.T.O.R/rust
cargo build --release
./target/release/vector_rust
```

6. **Test it:**
```bash
curl http://localhost:8080/api/health
curl -X POST http://localhost:8080/api/ask -H "Content-Type: application/json" -d '{"question":"what is Python?"}'
```

---

### **For High-End Machines (16GB+ RAM):**
**Option: JAVA VERSION**

1. **Install Java 17+ & Maven:**
```bash
sudo pacman -S jdk17 maven
```

2. **Start Ollama & Pull Models:**
```bash
ollama serve &
ollama pull gemma3:1b-it-qat
ollama pull phi4-mini:latest
```

3. **Build & Run:**
```bash
cd /home/jahazielo/V.E.C.T.O.R
mvn clean package -DskipTests
java -jar target/vector-1.0.0.jar
```

4. **Desktop App (optional):**
```bash
java -jar target/vector-1.0.0.jar --app
```

---

### **Quick Install (Automated):**
```bash
cd /home/jahazielo/V.E.C.T.O.R
bash install.sh
```

---

## 🚀 **WHICH VERSION TO USE?**

### **For YOU (jahazielo - 3.6GB RAM Laptop):**
🏆 **Use: RUST VERSION** (`rust/` folder)
- **Why:** Uses only 50MB RAM vs Java's 500MB
- **Startup:** Instant (<1s) vs Java's 5s
- **Cached responses:** 0ms (instant!) vs Java's ~3s
- **Wikipedia:** Works perfectly with ZIM file
- **Build:** `cd rust && cargo build --release`
- **Run:** `./target/release/vector_rust`

### **For High-End Machines (16GB+ RAM):**
- **Use: JAVA VERSION** (`main` branch)
- Has desktop app (--app flag) with AWT GUI
- More features: rate limiting, model routing, Kiwix server
- **Run:** `java -jar target/vector-1.0.0.jar`

### **For Developers:**
| Version | Language | RAM Usage | Best For |
|---------|----------|-----------|----------|
| **Rust** ✅ | Rust (Actix-web) | ~50MB | **Low-end hardware, production** |
| Java | Java (Spring Boot) | ~500MB | Desktop app, full features |
| C | C (raw sockets) | ~5MB | Learning, minimal systems |
| Python | Python (Flask) | ~80MB | Quick prototyping |

---

## Overview 📖

V.E.C.T.O.R is a backend system that acts as the central brain for AI-powered applications. It manages user requests, AI model routing, offline knowledge retrieval, memory optimization, and response generation.

**Key Features:**
- Fully offline execution after setup
- Intelligent model routing based on query complexity
- Offline Wikipedia integration via Kiwix
- Two-level caching for speed and efficiency
- RAM-aware model management
- Auto-installation of dependencies

---

## Why V.E.C.T.O.R? 🤔

Most AI systems rely on cloud APIs and high-end hardware.  
V.E.C.T.O.R is designed to run fully offline on low-end machines, making AI more accessible in constrained environments such as school labs or older laptops.

## Low-End Optimization 💻

V.E.C.T.O.R is designed for constrained hardware:

- Uses small 1B–2B parameter models
- Limits RAM usage via configurable thresholds
- Applies rate limiting to prevent overload
- Uses caching to reduce repeated computation
- Supports offline knowledge retrieval

This makes it suitable for older laptops and school environments.

## Architecture 🔨

```
User Input → API Layer → Processing Pipeline → AI Orchestration → Response
```
### Request Flow

1. User sends a question to `/api/ask`
2. Input is sanitized and rate-limited
3. Question is classified as SIMPLE or COMPLEX
4. Cached answers or Wikipedia context are retrieved
5. Context is optimized for token limits
6. Prompt is constructed
7. Model Router selects the appropriate model
8. Ollama generates a response
9. Response is processed and returned

### Pipeline Components

| Layer | Component | Description |
|-------|-----------|-------------|
| API | `/api/ask` | POST endpoint for questions |
| Processing | Input Sanitizer | Cleans/validates input |
| Processing | Rate Limiter | Token bucket rate limiting |
| Intelligence | Question Classifier | Determines SIMPLE/COMPLEX |
| Memory | Answer Cache | Caffeine cache for responses |
| Memory | Wiki Cache | Caffeine cache for Wikipedia |
| Knowledge | Wikipedia Service | Kiwix ZIM file access |
| Context | Context Optimizer | Trims context to token limits |
| Prompt | Prompt Builder | Constructs AI prompts |
| Model | Model Router | Routes to appropriate model |
| Model | Model Manager | RAM safety, idle timeout |
| Inference | Ollama Client | HTTP client for Ollama |
| Response | Response Processor | Cleans AI output |

---

## Quick Start 🔧

### Prerequisites

- Java 17+

> **Note:** Ollama and AI models will be auto-installed on first run!

```bash
# Clone the repository
git clone https://github.com/jahaziel127g/V.E.C.T.O.R.git
cd V.E.C.T.O.R

# Download the JAR from Releases (or build yourself)
# See: https://github.com/jahaziel127g/vector/releases

# Run
java -jar vector-1.0.0.jar
```

The backend will auto-install Ollama and models on first run if not present.

## Automatic Setup 📦

On first run, V.E.C.T.O.R will:

- Detect if Ollama is installed
- Install it automatically if missing
- Start the Ollama server
- Download required AI models

This provides a zero-setup experience, automatically preparing the local AI environment without requiring manual configuration.

> Note: Due to hardware constraints, response quality may be lower compared to large cloud-based models.

### API Usage

```bash
# Health check
curl http://localhost:8080/api/health

# Ask a question
curl -X POST http://localhost:8080/api/ask \
  -H "Content-Type: application/json" \
  -d '{"question":"What is artificial intelligence?"}'

# Get model status
curl http://localhost:8080/api/models
```

### Response Format

```json
{
  "answer": "AI is short for Artificial Intelligence...",
  "model": "hf.co/Andycurrent/Gemma-3-1B-it-...Q4_K_M",
  "source": "local model",
  "complexity": "simple",
  "processingTimeMs": 5951
}
```

---

## Configuration ⚙️

Edit `src/main/resources/application.properties`:

```properties
# Rate limiting
vector.rate-limit.requests-per-minute=10

# Models (simple = fast, complex = reasoning)
vector.model.simple-model=hf.co/Andycurrent/Gemma-3-1B-it-GLM-4.7-Flash-Heretic-Uncensored-Thinking_GGUF:Q4_K_M
vector.model.complex-model=hf.co/bartowski/gemma-2-2b-it-GGUF:Q4_K_M

# Ollama
vector.ollama.base-url=http://localhost:11434
vector.ollama.timeout-seconds=120

# Wikipedia (optional)
vector.wikipedia.enabled=true
vector.kiwix.zim-path=/var/lib/kiwix/wikipedia.zim

# Memory management
vector.model.idle-timeout-minutes=5
vector.model.max-ram-percent=85
```

---

## Model Recommendations 🧠

| Query Type | Model | Size | Use Case |
|------------|-------|------|----------|
| Simple | Gemma 3 1B | ~800MB | Fast, factual answers |
| Complex | Gemma 2 2B | ~1.7GB | Reasoning, analysis |

---

## Development

```bash
# Run tests
mvn test

# Run in development
mvn spring-boot:run

# Build JAR
mvn clean package
```

---

## 🖥️ **SYSTEM REQUIREMENTS**

| Component | Requirement |
|-----------|--------------|
| **OS** | Linux (Arch, Ubuntu, Fedora, etc.) |
| **RAM** | 3.6GB minimum (Rust version), 8GB+ (Java version) |
| **Disk** | 3GB free (models + ZIM file) |
| **Ollama** | v0.1.0+ (local AI runtime) |
| **Rust** | 1.75+ (only for Rust version) |
| **Java** | 17+ (only for Java version) |
| **Maven** | 3.8+ (only for Java version) |
| **zim-tools** | For Wikipedia integration (`sudo pacman -S zim-tools`) |

---

## About ℹ️

**Project V.E.C.T.O.R** is a **PBL (Project-Based Learning)** assignment.

- **Nexus Labs** is NOT a real company - this is a fictional organization for the project
- Purpose: Learn and understand AI orchestration systems, Spring Boot, and offline AI deployment
- This is an educational project, not production-ready software
- Future improvements may include support for additional models, distributed inference, and enhanced knowledge retrieval.

---

## License

MIT License - Nexus Labs

---

<p align="center">
  <strong>Project V.E.C.T.O.R</strong> - PBL Learning Project
</p>
