use actix_web::{web, App, HttpServer, Responder, HttpResponse};
use actix_cors::Cors;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use reqwest::Client;
use std::time::{Duration, SystemTime};
use std::process::Command;
use parking_lot::RwLock;
use std::collections::HashMap;
use futures::StreamExt;
#[allow(unused_imports)]
use std::sync::atomic::{AtomicBool, Ordering};

const VERSION: &str = env!("CARGO_PKG_VERSION");
const DEFAULT_OLLAMA_URL: &str = "http://localhost:11434";
const DEFAULT_TIMEOUT_SECS: u64 = 60;
const DEFAULT_MODEL: &str = "gemma3:1b-it-qat";
const DEFAULT_ZIM_PATH: &str = "/home/jahazielo/Downloads/wikipedia_en_simple_all_nopic_2026-02.zim";
const WIKI_MIN_QUERY_LEN: usize = 15;
const WIKI_MIN_CONTENT: usize = 50;
const WIKI_MAX_CONTENT: usize = 1500;
const HTTP_POOL_SIZE: usize = 16;
const HTTP_CONNECT_TIMEOUT_SECS: u64 = 5;
const CACHE_COMPLEXITY: &str = "simple";
const SOURCE_LOCAL: &str = "local model";
const SOURCE_WIKI: &str = "wikipedia + local model";
const SOURCE_CACHE_ANSWER: &str = "answer_cache";
const SOURCE_CACHE_WIKI: &str = "wikipedia_cache";
const SOURCE_CACHED: &str = "cached";
const ERROR_PARSE_RESPONSE: &str = "Error parsing response";
const ERROR_CONNECT: &str = "Error: Failed to connect to Ollama";

#[allow(dead_code)]
static SHUTDOWN_FLAG: AtomicBool = AtomicBool::new(false);

#[derive(Deserialize)]
struct AskRequest {
    question: String,
}

#[derive(Serialize)]
struct AskResponse {
    answer: String,
    model: String,
    source: String,
    complexity: String,
    processing_time_ms: u64,
}

struct AppState {
    config: Config,
    client: Client,
    answer_cache: RwLock<HashMap<String, String>>,
    wiki_cache: RwLock<HashMap<String, String>>,
    request_count: RwLock<u64>,
}

#[derive(Clone)]
struct Config {
    ollama_url_full: String,
    ollama_timeout: Duration,
    model: String,
    zim_path: String,
}

impl Default for Config {
    fn default() -> Self {
        let ollama_url = std::env::var("OLLAMA_URL").unwrap_or_else(|_| DEFAULT_OLLAMA_URL.to_string());
        let timeout = std::env::var("OLLAMA_TIMEOUT")
            .ok()
            .and_then(|v| v.parse().ok())
            .unwrap_or(DEFAULT_TIMEOUT_SECS);
        
        Config {
            ollama_url_full: format!("{}/api/generate", ollama_url),
            ollama_timeout: Duration::from_secs(timeout),
            model: std::env::var("MODEL").unwrap_or_else(|_| DEFAULT_MODEL.to_string()),
            zim_path: std::env::var("ZIM_PATH").unwrap_or_else(|_| DEFAULT_ZIM_PATH.to_string()),
        }
    }
}

impl Config {
    fn ollama_request(&self, prompt: &str, stream: bool) -> Value {
        serde_json::json!({
            "model": self.model,
            "prompt": prompt,
            "stream": stream,
            "options": {
                "temperature": 0.7,
                "top_p": 0.9,
                "num_predict": 150,
                "num_ctx": 256
            }
        })
    }
}

fn search_wikipedia(query: &str, zim_path: &str) -> Option<String> {
    let output = Command::new("zimsearch")
        .args([zim_path, query])
        .output()
        .ok()?;

    let search_result = String::from_utf8_lossy(&output.stdout);
    let search_result = search_result.trim();
    if search_result.is_empty() {
        return None;
    }

    let article_path = search_result
        .lines()
        .find(|line| line.trim().starts_with("article:"))?
        .trim_start_matches("article:")
        .trim();

    if article_path.is_empty() {
        return None;
    }

    let article_name = std::path::Path::new(article_path)
        .file_name()?
        .to_str()?;

    let dump_output = Command::new("zimdump")
        .args([zim_path, article_name])
        .output()
        .ok()?;

    let content = String::from_utf8_lossy(&dump_output.stdout);
    let mut article_content = String::with_capacity(WIKI_MAX_CONTENT + 100);
    let mut in_content = false;

    for line in content.lines() {
        if line.starts_with("Content:") {
            in_content = true;
            continue;
        }
        if in_content && !line.trim().is_empty() {
            article_content.push_str(line);
            article_content.push(' ');
        }
    }

    if article_content.len() < WIKI_MIN_CONTENT {
        return None;
    }

    if article_content.len() > WIKI_MAX_CONTENT {
        let truncated = article_content[..WIKI_MAX_CONTENT].to_string();
        if let Some(pos) = truncated.rfind('.') {
            if pos > WIKI_MAX_CONTENT / 2 {
                return Some(truncated[..pos + 1].to_string());
            }
        }
        return Some(truncated);
    }

    Some(article_content)
}

fn elapsed_ms(start: SystemTime) -> u64 {
    start.elapsed().unwrap_or_default().as_millis() as u64
}

async fn ask(web::Json(req): web::Json<AskRequest>, state: web::Data<AppState>) -> impl Responder {
    {
        let mut count = state.request_count.write();
        *count += 1;
    }

    let start_time = SystemTime::now();
    let query_lower = req.question.to_lowercase();

    // Answer cache check
    {
        let cache = state.answer_cache.read();
        if let Some(cached) = cache.get(&query_lower) {
            return HttpResponse::Ok().json(AskResponse {
                answer: cached.clone(),
                model: SOURCE_CACHED.to_string(),
                source: SOURCE_CACHE_ANSWER.to_string(),
                complexity: CACHE_COMPLEXITY.to_string(),
                processing_time_ms: elapsed_ms(start_time),
            });
        }
    }

    // Wiki cache check
    {
        let cache = state.wiki_cache.read();
        if let Some(cached) = cache.get(&query_lower) {
            return HttpResponse::Ok().json(AskResponse {
                answer: cached.clone(),
                model: SOURCE_CACHED.to_string(),
                source: SOURCE_CACHE_WIKI.to_string(),
                complexity: CACHE_COMPLEXITY.to_string(),
                processing_time_ms: elapsed_ms(start_time),
            });
        }
    }

    // Search Wikipedia
    let wiki_context = if req.question.len() > WIKI_MIN_QUERY_LEN {
        search_wikipedia(&req.question, &state.config.zim_path)
    } else {
        None
    };

    // Build prompt
    let prompt = if let Some(ref ctx) = wiki_context {
        format!("Context: {}\n\nQuestion: {}\nAnswer:", ctx, req.question)
    } else {
        format!("Question: {}\nAnswer:", req.question)
    };

    // Call Ollama
    let ollama_req = state.config.ollama_request(&prompt, false);
    let response = state.client
        .post(&state.config.ollama_url_full)
        .json(&ollama_req)
        .timeout(state.config.ollama_timeout)
        .send()
        .await;

    let answer = match response {
        Ok(resp) => {
            if let Ok(json) = resp.json::<Value>().await {
                json.get("response")
                    .and_then(Value::as_str)
                    .unwrap_or(ERROR_PARSE_RESPONSE)
                    .to_string()
            } else {
                ERROR_PARSE_RESPONSE.to_string()
            }
        }
        Err(_) => ERROR_CONNECT.to_string(),
    };

    // Cache answer
    {
        let mut cache = state.answer_cache.write();
        cache.insert(query_lower.clone(), answer.clone());
    }

    // Cache wiki context
    if let Some(ref ctx) = wiki_context {
        let mut cache = state.wiki_cache.write();
        cache.insert(query_lower, ctx.clone());
    }

    let duration = elapsed_ms(start_time);
    let source = if wiki_context.is_some() { SOURCE_WIKI } else { SOURCE_LOCAL };

    HttpResponse::Ok().json(AskResponse {
        answer,
        model: state.config.model.clone(),
        source: source.to_string(),
        complexity: CACHE_COMPLEXITY.to_string(),
        processing_time_ms: duration,
    })
}

async fn ask_stream(web::Json(req): web::Json<AskRequest>, state: web::Data<AppState>) -> impl Responder {
    let prompt = format!("Question: {}\nAnswer:", req.question);
    let ollama_req = state.config.ollama_request(&prompt, true);

    let response = match state.client
        .post(&state.config.ollama_url_full)
        .json(&ollama_req)
        .timeout(state.config.ollama_timeout)
        .send()
        .await
    {
        Ok(resp) => resp,
        Err(e) => {
            return HttpResponse::InternalServerError().json(serde_json::json!({
                "error": format!("Ollama request failed: {}", e)
            }));
        }
    };

    let stream = response.bytes_stream().map(|chunk| {
        match chunk {
            Ok(bytes) => {
                let line = String::from_utf8_lossy(&bytes);
                let mut output = String::with_capacity(line.len() * 2);
                for l in line.lines() {
                    if let Ok(json) = serde_json::from_str::<Value>(l) {
                        if let Some(token) = json.get("response").and_then(Value::as_str) {
                            use std::fmt::Write;
                            let _ = write!(output, "data: {}\n\n", token);
                        }
                        if json.get("done").and_then(Value::as_bool).unwrap_or(false) {
                            output.push_str("data: [DONE]\n\n");
                        }
                    }
                }
                Ok(actix_web::web::Bytes::copy_from_slice(output.as_bytes()))
            }
            Err(e) => Err(actix_web::error::ErrorInternalServerError(e)),
        }
    });

    HttpResponse::Ok()
        .content_type("text/event-stream")
        .append_header(("Cache-Control", "no-cache"))
        .streaming(stream)
}

async fn health() -> impl Responder {
    HttpResponse::Ok().json(serde_json::json!({
        "status": "healthy",
        "service": "V.E.C.T.O.R Rust",
        "version": VERSION,
        "uptime": "running"
    }))
}

async fn stats(state: web::Data<AppState>) -> impl Responder {
    let answer_count = state.answer_cache.read().len();
    let wiki_count = state.wiki_cache.read().len();
    let req_count = *state.request_count.read();

    HttpResponse::Ok().json(serde_json::json!({
        "total_requests": req_count,
        "answer_cache_size": answer_count,
        "wiki_cache_size": wiki_count,
        "model": state.config.model,
        "status": "operational"
    }))
}

#[actix_web::main]
async fn main() -> std::io::Result<()> {
    env_logger::init();

    let state = web::Data::new(AppState {
        config: Config::default(),
        client: Client::builder()
            .pool_max_idle_per_host(HTTP_POOL_SIZE)
            .connect_timeout(Duration::from_secs(HTTP_CONNECT_TIMEOUT_SECS))
            .build()
            .unwrap_or_else(|_| Client::new()),
        answer_cache: RwLock::new(HashMap::new()),
        wiki_cache: RwLock::new(HashMap::new()),
        request_count: RwLock::new(0),
    });

    println!("V.E.C.T.O.R Rust starting on http://0.0.0.0:8080");

    tokio::spawn(async {
        tokio::signal::ctrl_c().await.expect("Failed to listen for Ctrl+C");
        println!("\nStopping V.E.C.T.O.R...");
        std::process::exit(0);
    });

    HttpServer::new(move || {
        App::new()
            .app_data(state.clone())
            .wrap(Cors::default().allow_any_origin().allow_any_method().allow_any_header())
            .route("/api/ask", web::post().to(ask))
            .route("/api/ask/stream", web::post().to(ask_stream))
            .route("/api/health", web::get().to(health))
            .route("/api/stats", web::get().to(stats))
    })
    .bind("0.0.0.0:8080")?
    .run()
    .await
}