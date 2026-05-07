use actix_web::{web, App, HttpServer, Responder, HttpResponse};
use actix_cors::Cors;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use reqwest::Client;
use cached::proc_macro::cached;
use regex::Regex;
use std::time::{Duration, SystemTime};
use std::process::{Command, Stdio};
use std::io::{BufRead, BufReader};
use std::fs;
use std::path::Path;

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
    processing_time_ms: u128,
}

// Configuration
struct Config {
    ollama_url: String,
    ollama_timeout: Duration,
    simple_model: String,
    zim_path: String,
    cache_ttl: Duration,
}

impl Default for Config {
    fn default() -> Self {
        Config {
            ollama_url: "http://localhost:11434".to_string(),
            ollama_timeout: Duration::from_secs(60),
            simple_model: "gemma3:1b-it-qat".to_string(),
            zim_path: "/home/jahazielo/Downloads/wikipedia_en_simple_all_nopic_2026-02.zim".to_string(),
            cache_ttl: Duration::from_secs(3600),
        }
    }
}

// Cached Wikipedia search
#[cached(time = 3600)]
fn search_wikipedia(query: String, zim_path: String) -> Option<String> {
    search_zim(&query, &zim_path)
}

fn search_zim(query: &str, zim_path: &str) -> Option<String> {
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
    let article_name = Path::new(article_path)
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

async fn ask(web::Json(req): web::Json<AskRequest>, config: web::Data<Config>) -> impl Responder {
    let start_time = SystemTime::now();
    
    // Check cache
    if let Some(cached) = search_wikipedia(req.question.clone(), config.zim_path.clone()) {
        let duration = start_time.elapsed().unwrap_or_default();
        return HttpResponse::Ok().json(AskResponse {
            answer: cached,
            model: "cached".to_string(),
            source: "wikipedia_cache".to_string(),
            complexity: "simple".to_string(),
            processing_time_ms: duration.as_millis(),
        });
    }
    
    // Search Wikipedia
    let wiki_context = search_zim(&req.question, &config.zim_path);
    
    // Build prompt
    let prompt = if let Some(ref ctx) = wiki_context {
        format!("Context: {}\n\nQuestion: {}\nAnswer:", ctx, req.question)
    } else {
        format!("Question: {}\nAnswer:", req.question)
    };
    
    // Call Ollama
    let client = Client::new();
    let ollama_req = serde_json::json!({
        "model": config.simple_model,
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
        .post(format!("{}/api/generate", config.ollama_url))
        .json(&ollama_req)
        .timeout(config.ollama_timeout)
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
    
    let duration = start_time.elapsed().unwrap_or_default();
    let source = if wiki_context.is_some() {
        "wikipedia + local model"
    } else {
        "local model"
    };
    
    HttpResponse::Ok().json(AskResponse {
        answer,
        model: config.simple_model.clone(),
        source: source.to_string(),
        complexity: "simple".to_string(),
        processing_time_ms: duration.as_millis(),
    })
}

async fn health() -> impl Responder {
    HttpResponse::Ok().json(serde_json::json!({
        "status": "healthy",
        "service": "V.E.C.T.O.R Rust"
    }))
}

#[actix_web::main]
async fn main() -> std::io::Result<()> {
    env_logger::init();
    
    let config = web::Data::new(Config::default());
    
    println!("V.E.C.T.O.R Rust starting on http://localhost:8080");
    
    HttpServer::new(move || {
        let cors = Cors::default()
            .allow_any_origin()
            .allow_any_method()
            .allow_any_header();
        
        App::new()
            .app_data(config.clone())
            .wrap(cors)
            .route("/api/ask", web::post().to(ask))
            .route("/api/health", web::get().to(health))
    })
    .bind("localhost:8080")?
    .run()
    .await
}
