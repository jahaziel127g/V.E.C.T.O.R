# AGENTS.md - V.E.C.T.O.R

## Quick Commands

```bash
# Build (skips tests by default)
mvn clean package -DskipTests

# Run backend only
java -jar target/vector-1.0.0.jar

# Run desktop app (AWT GUI)
java -jar target/vector-1.0.0.jar --app

# Run tests
mvn test

# API Health Check
curl http://localhost:8080/api/health
```

## Desktop App (--app)

- Uses AWT (native OS widgets, lighter than Swing)
- On tiling WMs (Hyprland/Sway), window must be floated:
  ```
  hyprctl dispatch float
  ```
- Or add to `hyprland.conf`:
  ```
  windowrulev2 float, title:^V.E.C.T.O.R$
  windowrulev2 size 500 600, title:^V.E.C.T.O.R$
  ```

## Architecture

- **Entry:** `VectorApplication.java` - Spring Boot main
- **API:** `AskController.java` - POST /api/ask
- **Pipeline:** InputSanitizer → RateLimiter → QuestionClassifier → AnswerCache → WikipediaService → PromptBuilder → ModelManager → OllamaClient → ResponseProcessor

## Models

Use Q3_K_S quantize for faster loading on limited RAM:
```bash
ollama pull hf.co/.../modelname:Q3_K_S
```

Timeout should be 60s+ for model switching.

## Config (application.properties)

| Setting | Purpose |
|----------|---------|
| `vector.model.simple-model` | Fast model for factual queries |
| `vector.model.complex-model` | Larger model for reasoning |
| `vector.ollama.timeout-seconds` | Request timeout (60s min) |
| `vector.kiwix.zim-path` | Wikipedia ZIM file path |
| `vector.wikipedia.enabled` | Enable/disable Wikipedia |

## Notes

- Models auto-unload after 5min idle
- Startup runs async (non-blocking)
- Desktop app connects to localhost:8080 API