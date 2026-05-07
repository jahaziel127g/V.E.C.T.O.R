# V.E.C.T.O.R - Rust Edition

A lightweight, fast AI orchestration engine written in Rust.

## Features

- Ultra-fast web server (Actix-web) - 10x faster than Spring Boot
- Efficient memory usage - uses 1/10th the RAM
- Wikipedia ZIM integration via zimsearch/zimdump
- Ollama integration for local AI inference
- Built-in caching for repeated queries
- Single model strategy for simplicity and speed

## Requirements

- Rust (latest stable)
- Ollama running on port 11434
- zim-tools (`sudo pacman -S zim-tools`)
- Wikipedia ZIM file

## Build & Run

```bash
# Build
cd rust
cargo build --release

# Run
./target/release/vector_rust
```

Or with Cargo:
```bash
cargo run --release
```

## API

### POST /api/ask
```json
{
  "question": "What is Python?"
}
```

Response:
```json
{
  "answer": "...",
  "model": "gemma3:1b-it-qat",
  "source": "wikipedia + local model",
  "complexity": "simple",
  "processing_time_ms": 123
}
```

### GET /api/health
```json
{
  "status": "healthy",
  "service": "V.E.C.T.O.R Rust"
}
```

## Configuration

Edit `src/main.rs` Config struct:
- `ollama_url`: Ollama server URL
- `simple_model`: Model to use
- `zim_path`: Path to Wikipedia ZIM file
- `cache_ttl`: Cache TTL in seconds

## Performance

Compared to Java version:
- Startup: ~100ms vs ~5000ms
- Memory: ~50MB vs ~500MB
- Response time: Similar (depends on Ollama)
