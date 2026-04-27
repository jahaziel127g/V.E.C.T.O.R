# V.E.C.T.O.R - AI Orchestration Engine

<p align="center">
  <strong>Project V.E.C.T.O.R</strong> | A PBL (Project-Based Learning) Project
</p>

<p align="center">
  ⚠️ <strong>Disclaimer:</strong> This is a learning project for educational purposes. <strong>Nexus Labs</strong> is not a real company.
</p>

<p align="center">
  A lightweight, high-efficiency AI orchestration engine built with Spring Boot.
</p>

---

## Overview

V.E.C.T.O.R is a backend system that acts as the central brain for AI-powered applications. It manages user requests, AI model routing, offline knowledge retrieval, memory optimization, and response generation.

**Key Features:**
- Fully offline execution after setup
- Intelligent model routing based on query complexity
- Offline Wikipedia integration via Kiwix
- Two-level caching for speed and efficiency
- RAM-aware model management
- Auto-installation of dependencies

---

## Architecture

```
User Input → API Layer → Processing Pipeline → AI Orchestration → Response
```

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

## Quick Start

### Prerequisites

- Java 17+
- Ollama

### Installation

```bash
# Clone the repository
git clone https://github.com/jahaziel127g/vector.git
cd vector

# Build
mvn clean package

# Run
java -jar target/vector-1.0.0.jar
```

The backend will auto-install Ollama and models on first run if not present.

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

## Configuration

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

## Model Recommendations

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

## About

**Project V.E.C.T.O.R** is a **PBL (Project-Based Learning)** assignment.

- **Nexus Labs** is NOT a real company - this is a fictional organization for the project
- Purpose: Learn and understand AI orchestration systems, Spring Boot, and offline AI deployment
- This is an educational project, not production-ready software

---

## License

MIT License - Nexus Labs

---

<p align="center">
  <strong>Project V.E.C.T.O.R</strong> - PBL Learning Project
</p>