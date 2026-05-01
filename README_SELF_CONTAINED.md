# V.E.C.T.O.R - Self Contained Version

This version includes llama.cpp binary and can run with GGUF models without requiring Ollama installation.

## Setup

1. Place GGUF models in the `models/` directory:
   - `gemma-3-1b-it.q4_K_M.gguf` (for simple model)
   - `deepseek-r1-1.5b.q4_K_M.gguf` (for complex model)

2. Build the application:
   ```bash
   mvn clean package -DskipTests
   ```

3. Run the application:
   ```bash
   java -jar target/vector-1.0.0.jar
   ```

## Configuration

Edit `application.properties` to select inference backend:

```
# Inference backend: "ollama" or "llama.cpp"
vector.inference.backend=llama.cpp

# Llama.cpp settings
vector.llama-cli.path=/home/jahazielo/V.E.C.T.O.R/llama.cpp/build/bin/llama-cli
vector.model.simple-model-path=/home/jahazielo/V.E.C.T.O.R/models/gemma-3-1b-it.q4_K_M.gguf
vector.model.complex-model-path=/home/jahazielo/V.E.C.T.O.R/models/deepseek-r1-1.5b.q4_K_M.gguf
vector.llama.ctx-size=512
vector.llama.batch-size=512
vector.llama.threads=4
```

## Model Download

If you don't have GGUF models, you can download them:

```bash
# Simple model (Gemma 3 1B)
wget -O models/gemma-3-1b-it.q4_K_M.gguf "https://huggingface.co/second-state/Gemma-3-1B-it-GGUF/resolve/main/gemma-3-1b-it.q4_K_M.gguf"

# Complex model (DeepSeek R1 1.5B) - Example URL, adjust as needed
wget -O models/deepseek-r1-1.5b.q4_K_M.gguf "https://huggingface.co/TheBloke/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/deepseek-r1-1.5b-q4_k_m.gguf"
```

## Notes

- The first run will be slower as models are loaded into memory
- Subsequent requests will be faster
- RAM usage depends on model sizes and context size
- For best performance, adjust `vector.llama.threads` to match your CPU cores
- The application includes automatic fallback to Ollama if llama.cpp fails