# AGENTS.md - V.E.C.T.O.R

## Quick Commands

```bash
# Build (skips tests by default)
mvn clean package -DskipTests

# Run JAR
java -jar target/vector-1.0.0.jar

# Run in development (hot reload)
mvn spring-boot:run

# Run tests
mvn test

# API Health Check
curl http://localhost:8080/api/health

# Ask API example
curl -X POST http://localhost:8080/api/ask -H "Content-Type: application/json" -d '{"question":"What is AI?"}'

# Get Model Status
curl http://localhost:8080/api/models
```

## Self-Contained Version (No Ollama Required)

```bash
# Build with llama.cpp support
mvn clean package -DskipTests

# Run with llama.cpp backend (models auto-downloaded on first run)
java -jar target/vector-1.0.0.jar
```

## Architecture

- **Entry:** `VectorApplication.java` - Spring Boot main
- **API:** `AskController.java` - POST /api/ask
- **Config:** `application.properties` - All settings
- **Inference Backends:** 
  - Ollama (default): Local port 11434
  - Llama.cpp: Self-contained, no external dependencies
- **Java:** Java 17+ is required.

## Pipeline

1. AskController → InputSanitizer → RateLimiter
2. QuestionClassifier → SIMPLE/COMPLEX
3. AnswerCache check
4. WikipediaService → ZIM search (if enabled)
5. PromptBuilder → ModelRouter
6. Inference Client → Local model inference (Ollama or llama.cpp)
7. ResponseProcessor → JSON response

## Models

| Query Type | Model | Size | Use Case |
|------------|-------|------|----------|
| Simple | Gemma 3 1B | ~800MB | Fast, factual answers |
| Complex | DeepSeek R1 1.5B | ~1.1GB | Reasoning, analysis |

## Config

Edit `application.properties`:
- `vector.wikipedia.enabled=true/false`
- `vector.kiwix.zim-path`
- `vector.model.simple-model`
- `vector.model.complex-model`
- `vector.model.idle-timeout-minutes`
- `vector.model.max-ram-percent`
- `vector.rate-limit.requests-per-minute`
- `vector.rate-limit.tokens-per-request`
- `vector.cache.answer-max-size`
- `vector.cache.wiki-max-size`
- `vector.cache.expire-after-minutes`
- `vector.ollama.base-url`
- `vector.ollama.timeout-seconds`
- `vector.inference.backend` (ollama or llama.cpp, default: ollama)
- `vector.llama-cli.path` (path to llama.cpp binary)
- `vector.model.simple-model-path` (path to simple model GGUF)
- `vector.model.complex-model-path` (path to complex model GGUF)
- `vector.llama.ctx-size` (default: 512)
- `vector.llama.batch-size` (default: 512)
- `vector.llama.threads` (default: 4)

## Notes

- Ollama and models are auto-installed on first run when using ollama backend.
- Wikipedia integration requires `zim-tools` package (`sudo pacman -S zim-tools`).
- ZIM file path must exist for Wikipedia integration to work.
- Models unload from RAM before switching for memory safety.
- Startup runs asynchronously (non-blocking).
- For self-contained version, set `vector.inference.backend=llama.cpp` and place GGUF models in models/ directory.
