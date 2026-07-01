# AGENTS.md — V.E.C.T.O.R

Rust-based AI orchestration engine (Actix-web). Requires Ollama (or llama.cpp for self-contained mode).

## Commands

```bash
./run.sh [install|build|run|start|stop|restart|status]   # management (Rust backend + frontend)
cd rust && cargo build --release                           # manual build
./target/release/vector_rust                               # manual run
```

Frontend (separate process): `python3 -m http.server 9000` from `frontend/`.

## API

| Endpoint | Method | Notes |
|----------|--------|-------|
| `/api/ask` | POST | Body: `{"question":"..."}` |
| `/api/ask/stream` | POST | SSE streaming |
| `/api/stt` | POST | Speech-to-text via whisper.cpp |
| `/api/health` | GET | |
| `/api/stats` | GET | Requests, cache sizes, model info |

## Architecture

- **Entry:** `main.rs` — Actix-web server on `0.0.0.0:8080`
- **Modules:** `config`, `state`, `cache`, `wiki`, `planner`, `prompt`, `engine`, `api`, `stt`, `setup`
- **Flow:** `api::ask` → `engine::process_question` → `planner::plan_request` (cache check + intent classification) → `wiki::search_wikipedia` (if needed) → `prompt::build_prompt` → Ollama API (`/api/chat`) → response
- **STT:** `api::stt` → `stt::transcribe` via whisper.cpp CLI (capped at 2 concurrent, 30s timeout)
- **RAM monitor:** `state::spawn_ram_monitor` checks every 5s; 85% warning → clear history; 90% critical → also `ollama stop`

## Config (env vars)

| Env var | Default | Note |
|---------|---------|------|
| `OLLAMA_URL` | `http://localhost:11434` | |
| `OLLAMA_TIMEOUT` | `60` | Seconds |
| `MODEL` | `openbmb/minicpm5:latest` | |
| `ZIM_PATH` | user-specific | Wikipedia ZIM file |
| `MAX_HISTORY` | `3` | Conversation turns |
| `CACHE_SIZE` | `10000` | Max cache entries |
| `RAM_WARNING` | `85` | % RAM → log warning |
| `RAM_CRITICAL` | `90` | % RAM → clear history + ollama stop |
| `PORT` | `8080` | HTTP server port |
| `HTTP_POOL_SIZE` | `16` | HTTP connection pool |
| `FRONTEND_URL` | `*` | CORS origin (set for deploy) |
| `WORKERS` | `(cpus)` | Actix worker threads (override auto-tuning) |
| `WHISPER_PATH` | `./whisper.cpp/main` | whisper.cpp CLI binary |
| `WHISPER_MODEL_PATH` | `./models/ggml-base.en.bin` | Whisper model file |
| `WHISPER_ENABLED` | `true` | Set `false` to disable STT |

## Quirks

- **Cargo.lock is gitignored** (`.gitignore` lists `rust/Cargo.lock`) — unusual for Rust
- **No tests** — Rust codebase has zero tests (no `#[test]` or test modules anywhere)
- **Wikipedia:** queries < 15 chars skip lookup; requires `zimsearch`/`zimdump` CLI tools + ZIM file
- **Startup:** `setup.rs` runs checks: verifies Ollama is running (auto-starts if needed), checks model exists (pulls if missing), checks ZIM file (graceful warn), checks whisper.cpp binary (graceful warn)
- **Frontend:** not served by backend — run `python3 -m http.server 9000` separately
- **Self-contained mode:** use `./run-self-contained.sh` with llama.cpp backend (GGUF models in `models/`)
- **Intent classification:** `planner.rs` classifies queries into Greeting/Factual/Explanation/Debugging/General; greeting fast path skips wiki + history
- **Cache:** answer + wiki + STT caches use `moka` (concurrent); STT uses blake3 hash of raw audio for cache key
- **STT:** ffmpeg converts to 16kHz mono WAV before whisper; pipe readers spawned before `wait()` to prevent pipe deadlock
