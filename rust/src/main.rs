use actix_web::{web, App, HttpServer, Responder, HttpResponse};
use actix_cors::Cors;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use reqwest::Client;
use std::time::{Duration, SystemTime};
use std::process::Command;
use std::collections::HashMap;
use std::sync::Mutex;

// Request/Response structs
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

// App state
struct AppState {
    config: Config,
    answer_cache: Mutex<HashMap<String, String>>,
    wiki_cache: Mutex<HashMap<String, String>>,
    request_count: Mutex<u64>,
}

// Configuration
struct Config {
    ollama_url: String,
    ollama_timeout: Duration,
    model: String,
    zim_path: String,
}

impl Default for Config {
    fn default() -> Self {
        Config {
            ollama_url: std::env::var("OLLAMA_URL").unwrap_or_else(|_| "http://localhost:11434".to_string()),
            ollama_timeout: Duration::from_secs(
                std::env::var("OLLAMA_TIMEOUT").ok().and_then(|v| v.parse().ok()).unwrap_or(60)
            ),
            model: std::env::var("MODEL").unwrap_or_else(|_| "gemma3:1b-it-qat".to_string()),
            zim_path: std::env::var("ZIM_PATH").unwrap_or_else(|_| 
                "/home/jahazielo/Downloads/wikipedia_en_simple_all_nopic_2026-02.zim".to_string()
            ),
        }
    }
}

// Wikipedia search using zim tools
fn search_wikipedia(query: &str, zim_path: &str) -> Option<String> {
    // Step 1: Search for article
    let output = Command::new("zimsearch")
        .args(&[zim_path, query])
        .output()
        .ok()?;
    
    let search_result = String::from_utf8_lossy(&output.stdout);
    if search_result.trim().is_empty() {
        return None;
    }
    
    // Extract article path
    let article_path = search_result.lines()
        .find(|line| line.trim().starts_with("article:"))?
        .trim_start_matches("article:")
        .trim();
    
    if article_path.is_empty() {
        return None;
    }
    
    // Extract article name
    let article_name = std::path::Path::new(article_path)
        .file_name()?
        .to_str()?;
    
    // Step 2: Dump article content
    let dump_output = Command::new("zimdump")
        .args(&[zim_path, article_name])
        .output()
        .ok()?;
    
    let content = String::from_utf8_lossy(&dump_output.stdout);
    let mut article_content = String::new();
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
    
    if article_content.len() < 50 {
        None
    } else {
        // Truncate to 1500 chars
        let max_len = 1500;
        if article_content.len() > max_len {
            let truncated = article_content[..max_len].to_string();
            if let Some(pos) = truncated.rfind('.') {
                if pos > max_len / 2 {
                    return Some(truncated[..pos + 1].to_string());
                }
            }
            Some(truncated)
        } else {
            Some(article_content)
        }
    }
}

async fn ask(web::Json(req): web::Json<AskRequest>, state: web::Data<AppState>) -> impl Responder {
    // Increment request count
    {
        let mut count = state.request_count.lock().unwrap();
        *count += 1;
    }
    
    let start_time = SystemTime::now();
    let query_lower = req.question.to_lowercase();
    
    // Check answer cache first
    {
        let cache = state.answer_cache.lock().unwrap();
        if let Some(cached) = cache.get(&query_lower) {
            let duration = start_time.elapsed().unwrap_or_default();
            return HttpResponse::Ok().json(AskResponse {
                answer: cached.clone(),
                model: "cached".to_string(),
                source: "answer_cache".to_string(),
                complexity: "simple".to_string(),
                processing_time_ms: duration.as_millis() as u64,
            });
        }
    }
    
    // Check Wikipedia cache
    {
        let cache = state.wiki_cache.lock().unwrap();
        if let Some(cached) = cache.get(&query_lower) {
            let duration = start_time.elapsed().unwrap_or_default();
            return HttpResponse::Ok().json(AskResponse {
                answer: cached.clone(),
                model: "cached".to_string(),
                source: "wikipedia_cache".to_string(),
                complexity: "simple".to_string(),
                processing_time_ms: duration.as_millis() as u64,
            });
        }
    }
    
    // Search Wikipedia if query is long enough
    let wiki_context = if req.question.len() > 15 {
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
    let client = Client::new();
    let ollama_req = serde_json::json!({
        "model": state.config.model,
        "prompt": prompt,
        "stream": false,
        "options": {
            "temperature": 0.7,
            "top_p": 0.9,
            "num_predict": 150,
            "num_ctx": 256
        }
    });
    
    let response = client
        .post(format!("{}/api/generate", state.config.ollama_url))
        .json(&ollama_req)
        .timeout(state.config.ollama_timeout)
        .send()
        .await;
    
    let answer = match response {
        Ok(resp) => {
            if let Ok(json) = resp.json::<Value>().await {
                json.get("response")
                    .and_then(|v| v.as_str())
                    .unwrap_or("Error parsing response")
                    .to_string()
            } else {
                "Error: Failed to parse Ollama response".to_string()
            }
        }
        Err(_) => "Error: Failed to connect to Ollama".to_string(),
    };
    
    // Store in cache
    {
        let mut cache = state.answer_cache.lock().unwrap();
        cache.insert(query_lower.clone(), answer.clone());
    }
    
    if let Some(ctx) = &wiki_context {
        let mut cache = state.wiki_cache.lock().unwrap();
        cache.insert(query_lower, ctx.clone());
    }
    
    let duration = start_time.elapsed().unwrap_or_default();
    let source = if wiki_context.is_some() {
        "wikipedia + local model"
    } else {
        "local model"
    };
    
    HttpResponse::Ok().json(AskResponse {
        answer,
        model: state.config.model.clone(),
        source: source.to_string(),
        complexity: "simple".to_string(),
        processing_time_ms: duration.as_millis() as u64,
    })
}

async fn health() -> impl Responder {
    HttpResponse::Ok().json(serde_json::json!({
        "status": "healthy",
        "service": "V.E.C.T.O.R Rust"
    }))
}

async fn stats(state: web::Data<AppState>) -> impl Responder {
    let answer_count = state.answer_cache.lock().unwrap().len();
    let wiki_count = state.wiki_cache.lock().unwrap().len();
    let req_count = *state.request_count.lock().unwrap();
    
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
        answer_cache: Mutex::new(HashMap::new()),
        wiki_cache: Mutex::new(HashMap::new()),
        request_count: Mutex::new(0),
    });
    
    println!("V.E.C.T.O.R Rust starting on http://localhost:8080");
    
    HttpServer::new(move || {
        let cors = Cors::default()
            .allow_any_origin()
            .allow_any_method()
            .allow_any_header();
        
        App::new()
            .app_data(state.clone())
            .wrap(cors)
            .route("/api/ask", web::post().to(ask))
            .route("/api/health", web::get().to(health))
            .route("/api/stats", web::get().to(stats))
    })
    .bind("localhost:8080")?
    .run()
    .await
}
