use std::sync::atomic::Ordering;
use std::time::SystemTime;

use actix_multipart::Multipart;
use actix_web::{web, HttpResponse, Responder};
use futures::StreamExt;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use uuid::Uuid;

use crate::engine;
use crate::state::AppState;
use crate::stt::{self, TempAudio};

#[derive(Deserialize)]
pub struct AskRequest {
    pub question: String,
}

#[derive(Serialize)]
pub struct AskResponse {
    pub answer: String,
    pub model: String,
    pub source: String,
    pub complexity: String,
    pub processing_time_ms: u64,
}

fn elapsed_ms(start: SystemTime) -> u64 {
    start.elapsed().unwrap_or_default().as_millis() as u64
}

pub async fn ask(
    web::Json(req): web::Json<AskRequest>,
    state: web::Data<AppState>,
) -> impl Responder {
    let start = SystemTime::now();
    let req_id = Uuid::new_v4().to_string();

    log::info!("[{}] POST /api/ask: {}", req_id, req.question);

    state
        .metrics
        .total_requests
        .fetch_add(1, Ordering::Relaxed);

    let query = req.question.trim().to_string();
    if query.is_empty() {
        return HttpResponse::BadRequest().json(serde_json::json!({"error": "Empty question"}));
    }

    let result = engine::process_question(&query, &state, &req_id).await;

    let elapsed = elapsed_ms(start);
    log::info!("[{}] done: source={} {}ms", req_id, result.source, elapsed);

    HttpResponse::Ok().json(AskResponse {
        answer: result.answer,
        model: result.model,
        source: result.source,
        complexity: "simple".to_string(),
        processing_time_ms: elapsed,
    })
}

fn build_ollama_request(question: &str, model: &str) -> Value {
    serde_json::json!({
        "model": model,
        "messages": [
            {"role": "system", "content": "You are V.E.C.T.O.R, a knowledgeable AI assistant.\n\nRules:\n- Answer the user directly and confidently.\n- Use tables, lists, and headings to structure information when helpful.\n- Do not add code unless requested.\n- Provide thorough, detailed answers."},
            {"role": "user", "content": question}
        ],
        "stream": true,
        "options": {
            "temperature": 0.7,
            "top_p": 0.9,
            "num_ctx": 4096
        }
    })
}

async fn call_ollama_stream(
    client: &reqwest::Client,
    ollama_url: &str,
    ollama_req: &Value,
    req_id: &str,
) -> Result<reqwest::Response, HttpResponse> {
    client
        .post(ollama_url)
        .json(ollama_req)
        .send()
        .await
        .map_err(|e| {
            log::error!("[{}] Ollama stream request failed: {}", req_id, e);
            HttpResponse::InternalServerError().json(serde_json::json!({
                "error": format!("Ollama request failed: {}", e)
            }))
        })
}

fn build_ollama_stream(
    response: reqwest::Response,
) -> impl futures::Stream<Item = Result<actix_web::web::Bytes, actix_web::Error>> {
    futures::stream::unfold(
        (response.bytes_stream().fuse(), String::new(), false),
        |(mut upstream, mut buf, mut in_think)| async move {
            loop {
                match upstream.next().await {
                    Some(Ok(bytes)) => {
                        buf.push_str(&String::from_utf8_lossy(&bytes));
                    }
                    Some(Err(e)) => {
                        return Some((Err::<actix_web::web::Bytes, actix_web::Error>(actix_web::error::ErrorInternalServerError(e)), (upstream, buf, in_think)));
                    }
                    None => return None,
                }

                if let Some(pos) = buf.find('\n') {
                    let line = buf[..pos].trim().to_string();
                    buf.drain(..=pos);

                    if line.is_empty() {
                        continue;
                    }

                    let mut output = String::new();
                    if let Ok(json) = serde_json::from_str::<Value>(&line) {
                        if let Some(token) = json.get("message").and_then(|m| m.get("content")).and_then(Value::as_str) {
                            let filtered = filter_stream_token(token, &mut in_think);
                            if !filtered.is_empty() {
                                output.push_str(&format!("data: {}\n\n", serde_json::to_string(&filtered).unwrap_or_default()));
                            }
                        }
                        if json.get("done").and_then(Value::as_bool).unwrap_or(false) {
                            output.push_str("data: \"[DONE]\"\n\n");
                        }
                    }

                    return Some((Ok::<_, actix_web::Error>(actix_web::web::Bytes::copy_from_slice(output.as_bytes())), (upstream, buf, in_think)));
                }
            }
        },
    )
}

pub async fn ask_stream_get(
    web::Query(params): web::Query<AskRequest>,
    state: web::Data<AppState>,
) -> impl Responder {
    let req_id = Uuid::new_v4().to_string();
    log::info!("[{}] GET /api/ask/stream: {}", req_id, params.question);

    state.metrics.total_requests.fetch_add(1, Ordering::Relaxed);

    let ollama_req = build_ollama_request(&params.question, &state.config.model);
    let response = match call_ollama_stream(&state.client, &state.config.ollama_url, &ollama_req, &req_id).await {
        Ok(resp) => resp,
        Err(err_resp) => return err_resp,
    };

    let stream = build_ollama_stream(response);
    HttpResponse::Ok()
        .content_type("text/event-stream")
        .append_header(("Cache-Control", "no-cache"))
        .append_header(("Connection", "keep-alive"))
        .streaming(stream)
}

fn filter_stream_token(token: &str, _in_think: &mut bool) -> String {
    token.to_string()
}

pub async fn ask_stream(
    web::Json(req): web::Json<AskRequest>,
    state: web::Data<AppState>,
) -> impl Responder {
    let req_id = Uuid::new_v4().to_string();
    log::info!("[{}] POST /api/ask/stream: {}", req_id, req.question);

    state.metrics.total_requests.fetch_add(1, Ordering::Relaxed);

    let ollama_req = build_ollama_request(&req.question, &state.config.model);
    let response = match call_ollama_stream(&state.client, &state.config.ollama_url, &ollama_req, &req_id).await {
        Ok(resp) => resp,
        Err(err_resp) => return err_resp,
    };

    let stream = build_ollama_stream(response);
    HttpResponse::Ok()
        .content_type("text/event-stream")
        .append_header(("Cache-Control", "no-cache"))
        .append_header(("Connection", "keep-alive"))
        .streaming(stream)
}

pub async fn stt(
    mut payload: Multipart,
    state: web::Data<AppState>,
) -> HttpResponse {
    let req_id = Uuid::new_v4().to_string();
    log::info!("[{}] POST /api/stt", req_id);

    let mut audio_data: Option<Vec<u8>> = None;

    while let Some(item) = payload.next().await {
        let mut field = match item {
            Ok(f) => f,
            Err(e) => {
                log::error!("[{}] multipart error: {}", req_id, e);
                return HttpResponse::BadRequest()
                    .json(serde_json::json!({"error": "Invalid multipart data"}));
            }
        };

        let field_name = field
            .content_disposition()
            .and_then(|cd| cd.get_name())
            .unwrap_or("")
            .to_string();

        if field_name != "file" {
            continue;
        }

        let mut buf = Vec::new();
        while let Some(chunk) = field.next().await {
            match chunk {
                Ok(bytes) => buf.extend_from_slice(&bytes),
                Err(e) => {
                    log::error!("[{}] read chunk error: {}", req_id, e);
                    return HttpResponse::BadRequest()
                        .json(serde_json::json!({"error": "Failed to read upload"}));
                }
            }
        }

        audio_data = Some(buf);
        break;
    }

    let data = match audio_data {
        Some(d) => d,
        None => {
            return HttpResponse::BadRequest()
                .json(serde_json::json!({"error": "No file field found in upload"}));
        }
    };

    if let Err(msg) = stt::validate_audio(&data) {
        log::warn!("[{}] validation failed: {}", req_id, msg);
        return HttpResponse::BadRequest()
            .json(serde_json::json!({"error": msg}));
    }

    let tmp = match TempAudio::new(&data) {
        Ok(t) => t,
        Err(e) => {
            log::error!("[{}] temp audio write: {}", req_id, e);
            return HttpResponse::InternalServerError()
                .json(serde_json::json!({"error": "Server error"}));
        }
    };

    log::info!("[{}] saved {} bytes to {:?}", req_id, data.len(), tmp.path);

    let result = stt::transcribe(
        tmp.path.clone(),
        &state.config,
        &state.cache,
        &data,
        &state.stt_semaphore,
        &state.metrics,
    )
    .await;

    // tmp drops here → file cleaned up

    match result {
        Ok(text) => {
            log::info!("[{}] transcription: {}", req_id, text);
            HttpResponse::Ok().json(serde_json::json!({"text": text}))
        }
        Err(e) => {
            log::error!("[{}] transcription failed: {}", req_id, e);
            HttpResponse::InternalServerError()
                .json(serde_json::json!({"error": e}))
        }
    }
}

pub async fn health() -> impl Responder {
    HttpResponse::Ok().json(serde_json::json!({
        "status": "healthy",
        "service": "V.E.C.T.O.R Rust",
        "version": env!("CARGO_PKG_VERSION")
    }))
}

pub async fn stats(state: web::Data<AppState>) -> impl Responder {
    let (answer_count, wiki_count, stt_cache_count) = state.cache.stats();
    let m = &state.metrics;
    let total = m.total_requests.load(Ordering::Relaxed);
    let hits = m.cache_hits.load(Ordering::Relaxed);
    let lookups = m.wiki_lookups.load(Ordering::Relaxed);
    let calls = m.model_calls.load(Ordering::Relaxed);
    let total_latency = m.total_model_latency_ms.load(Ordering::Relaxed);
    let avg_latency = total_latency.checked_div(calls).unwrap_or(0);

    let stt_tx = m.stt_transcriptions.load(Ordering::Relaxed);
    let stt_latency = m.stt_total_latency_ms.load(Ordering::Relaxed);
    let avg_stt_latency = stt_latency.checked_div(stt_tx).unwrap_or(0);

    HttpResponse::Ok().json(serde_json::json!({
        "total_requests": total,
        "answer_cache_size": answer_count,
        "wiki_cache_size": wiki_count,
        "stt_cache_size": stt_cache_count,
        "cache_hit_rate": if total > 0 { format!("{:.1}%", (hits as f64 / total as f64) * 100.0) } else { "0.0%".to_string() },
        "wiki_lookups": lookups,
        "model_calls": calls,
        "avg_model_latency_ms": avg_latency,
        "model": state.config.model,
        "stt": {
            "transcriptions": stt_tx,
            "avg_latency_ms": avg_stt_latency,
            "cache_hits": m.stt_cache_hits.load(Ordering::Relaxed),
            "ffmpeg_conversions": m.stt_ffmpeg_conversions.load(Ordering::Relaxed),
            "timeouts": m.stt_timeouts.load(Ordering::Relaxed),
            "failures": m.stt_failures.load(Ordering::Relaxed),
        },
        "status": "operational"
    }))
}
