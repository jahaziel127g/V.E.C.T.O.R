# AGENTS.md - V.E.C.T.O.R

## Quick Commands

```bash
# Build
mvn clean package -DskipTests

# Run
java -jar target/vector-1.0.0.jar

# Test
curl http://localhost:8080/api/health
curl -X POST http://localhost:8080/api/ask -H "Content-Type: application/json" -d '{"question":"What is AI?"}'
```

## Architecture

- **Entry:** `VectorApplication.java` - Spring Boot main
- **API:** `AskController.java` - POST /api/ask
- **Config:** `application.properties` - All settings
- **Ollama:** Local port 11434

## Pipeline

1. AskController → InputSanitizer → RateLimiter
2. QuestionClassifier → SIMPLE/COMPLEX
3. AnswerCache check
4. WikipediaService → ZIM search (if enabled)
5. PromptBuilder → ModelRouter
6. OllamaClient → Local model inference
7. ResponseProcessor → JSON response

## Models

| Query Type | Model | Size |
|------------|-------|------|
| Simple | Gemma 3 1B | ~800MB |
| Complex | Gemma 2 2B | ~1.7GB |

## Config

Edit `application.properties`:
- `vector.wikipedia.enabled=true/false`
- `vector.kiwix.zim-path`
- `vector.model.simple-model`
- `vector.model.complex-model`

## Notes

- Wikipedia requires `zim-tools` package (`sudo pacman -S zim-tools`)
- Models unload before switching (RAM safety)
- Startup runs async (non-blocking)
- ZIM file path must exist for Wikipedia