# 🚀 V.E.C.T.O.R — AI Orchestration Engine

<p align="center">
  <img src="https://img.shields.io/badge/build-passing-brightgreen" />
  <img src="https://img.shields.io/badge/language-Rust-orange" />
  <img src="https://img.shields.io/badge/language-Java-red" />
  <img src="https://img.shields.io/badge/license-MIT-blue" />
  <img src="https://img.shields.io/badge/status-active-success" />
</p>



<p align="center">
  <strong>Project V.E.C.T.O.R</strong> | PBL (Project-Based Learning) Project
</p>

<p align="center">
  ⚠️ Educational Project — <strong>Nexus Labs</strong> is fictional.
</p>

<p align="center">
  A lightweight, offline-first AI orchestration engine designed for low-end hardware using local inference.
</p>

---

## 🧠 What is V.E.C.T.O.R?

V.E.C.T.O.R is a backend AI orchestration engine that acts as a “central brain” for AI systems.

It manages:
- Query routing
- Local AI inference
- Memory & caching
- Offline knowledge retrieval
- RAM optimization
- Model selection logic

👉 Everything runs locally after setup.

---

## 💡 Why V.E.C.T.O.R?

Most AI systems depend on cloud APIs and strong GPUs.

This project is built differently:

- 💻 Runs on weak laptops
- 🔌 Works fully offline
- ⚡ Optimized for low RAM usage
- 🏫 Built for school / learning environments
- 🧠 Smart model routing system

---

## 🧱 Architecture

```
User → API Layer → Processing Pipeline → AI Orchestration → Model → Response
```

### 🔄 Request Flow

1. User sends request (`/api/ask`)
2. Input sanitization
3. Rate limiting
4. Query classification (simple / complex)
5. Cache lookup
6. Wikipedia context (optional)
7. Context optimization
8. Prompt building
9. Model routing
10. Ollama inference
11. Response processing

---

## ⚙️ Core Components

| Layer | Component | Purpose |
|------|----------|--------|
| API | `/api/ask` | Handles user requests |
| Processing | Input Sanitizer | Cleans input |
| Processing | Rate Limiter | Prevents overload |
| Intelligence | Classifier | Simple vs complex |
| Memory | Cache System | Fast responses |
| Knowledge | Wikipedia (Kiwix) | Offline knowledge |
| Context | Optimizer | Reduces token usage |
| Model | Router | Selects AI model |
| Inference | Ollama | Runs local models |

---

## 📊 API Reference

### 🔍 Health Check
```bash
curl http://localhost:8080/api/health
```

### 💬 Ask a Question
```bash
curl -X POST http://localhost:8080/api/ask -H "Content-Type: application/json" -d '{"question":"What is artificial intelligence?"}'
```

### 🤖 Model Status
```bash
curl http://localhost:8080/api/models
```

---

## 📦 Response Format

```json
{
  "answer": "AI is Artificial Intelligence...",
  "model": "gemma-3-1b",
  "source": "local model",
  "complexity": "simple",
  "processingTimeMs": 1200
}
```

---

## 🧠 Model Strategy

| Type | Model | Purpose |
|------|------|--------|
| Simple | Gemma 3 1B | Fast responses |
| Complex | Gemma 2 2B | Deep reasoning |

---

## 🖥️ System Requirements

| Component | Requirement |
|----------|------------|
| OS | Linux (Arch / Ubuntu / Fedora) |
| RAM | 3.6GB (Rust) / 8GB+ (Java) |
| Storage | 3GB+ |
| Ollama | Required |
| Java | 17+ (Java version) |
| Rust | 1.75+ (Rust version) |

---

## ⚙️ Configuration

```properties
vector.rate-limit.requests-per-minute=10
vector.ollama.base-url=http://localhost:11434
vector.model.idle-timeout-minutes=5
vector.wikipedia.enabled=true
```

---

## 🧪 Development

```bash
mvn spring-boot:run
mvn test
mvn clean package
```

---

## 📦 INSTALLATION GUIDE

### 🦀 Rust Version (Recommended — Low-End Devices)

### 1. Install Rust
```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source $HOME/.cargo/env
```

### 2. Install Ollama
```bash
curl -fsSL https://ollama.com/install.sh | sh
ollama serve &
```

### 3. Download Model
```bash
ollama pull gemma3:1b-it-qat
```

### 4. Install Wikipedia Tools
```bash
sudo pacman -S zim-tools
# or: sudo apt install zim-tools
```

### 5. Build & Run
```bash
cd rust
cargo build --release
./target/release/vector_rust
```

---

### ☕ Java Version (High-End PCs)

### 1. Install Dependencies
```bash
sudo pacman -S jdk17 maven
```

### 2. Start Ollama
```bash
ollama serve &
ollama pull gemma3:1b-it-qat
ollama pull phi4-mini:latest
```

### 3. Build & Run
```bash
mvn clean package -DskipTests
java -jar target/vector-1.0.0.jar
```

### (Optional Desktop Mode)
```bash
java -jar target/vector-1.0.0.jar --app
```

---

## ⚡ Quick Install (Auto Setup)

```bash
bash install.sh
```

---

## 🚀 Which Version Should You Use?

### 🏆 Low-End Laptop (3.6GB RAM)
Use Rust version — lightweight and fast.

### 💪 High-End PC (8GB+ RAM)
Use Java version — full feature set.

---

## 📖 About

- PBL (Project-Based Learning) project
- Nexus Labs is fictional
- Focus: AI orchestration + offline systems

---

## 📜 License

MIT License — Nexus Labs