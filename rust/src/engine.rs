use std::sync::atomic::Ordering;
use std::time::SystemTime;

use serde_json::Value;

use crate::planner::{self, Intent};
use crate::prompt::build_prompt;
use crate::state::AppState;
use crate::wiki::{decide_wiki_usage, search_wikipedia, WikiDecision};

pub struct EngineResult {
    pub answer: String,
    pub source: String,
    pub model: String,
}

fn ollama_request_body(model: &str, messages: &[Value]) -> Value {
    serde_json::json!({
        "model": model,
        "messages": messages,
        "stream": false,
        "options": {
            "temperature": 0.7,
            "top_p": 0.9,
            "num_ctx": 4096
        }
    })
}

async fn call_ollama(
    client: &reqwest::Client,
    ollama_url: &str,
    model: &str,
    timeout: std::time::Duration,
    messages: Vec<Value>,
    req_id: &str,
) -> Result<(String, u64), String> {
    let start = SystemTime::now();
    let body = ollama_request_body(model, &messages);

    let response = tokio::time::timeout(timeout, client.post(ollama_url).json(&body).send())
        .await
        .map_err(|_| {
            log::warn!("[{}] Ollama timed out", req_id);
            "Request timed out".to_string()
        })?
        .map_err(|e| {
            log::warn!("[{}] Ollama connect fail: {}", req_id, e);
            format!("Ollama unavailable: {}", e)
        })?;

    let json: Value = response
        .json()
        .await
        .map_err(|_| "Bad response from model".to_string())?;

    let latency = start.elapsed().unwrap_or_default().as_millis() as u64;
    let answer = json
        .get("message")
        .and_then(|m| m.get("content"))
        .and_then(Value::as_str)
        .map(|s| s.trim().to_string())
        .unwrap_or_else(|| "(no response)".to_string());

    Ok((answer, latency))
}

pub async fn process_question(
    question: &str,
    state: &AppState,
    req_id: &str,
) -> EngineResult {
    let query = question.trim();
    let plan = planner::plan_request(query, state);

    if let Some(cached) = plan.cached_answer {
        log::info!("[{}] cache hit", req_id);
        state.metrics.cache_hits.fetch_add(1, Ordering::Relaxed);
        return EngineResult {
            answer: cached,
            model: "cached".into(),
            source: "cached".into(),
        };
    }

    // Greeting fast path — skip wiki, skip history, just reply
    if plan.intent == Intent::Greeting {
        log::info!("[{}] greeting detected, fast path", req_id);
        let messages = build_prompt(query, plan.intent, None, &Default::default());
        let (raw, latency_ms) = match call_ollama(
            &state.client,
            &state.config.ollama_url,
            &state.config.model,
            state.config.ollama_timeout,
            messages,
            req_id,
        )
        .await
        {
            Ok((answer, ms)) => (answer, ms),
            Err(e) => {
                log::error!("[{}] model failed: {}", req_id, e);
                ("Hi!".into(), 0)
            }
        };
        state.metrics.model_calls.fetch_add(1, Ordering::Relaxed);
        state.metrics.total_model_latency_ms.fetch_add(latency_ms, Ordering::Relaxed);
        state.cache.insert_answer(query, raw.clone());
        state.add_to_history(query.to_string(), raw.clone());
        return EngineResult {
            answer: raw,
            source: "local model".into(),
            model: state.config.model.clone(),
        };
    }

    // Resolve wiki context
    let raw_wiki = if let Some(cached) = plan.wiki_from_cache {
        log::info!("[{}] wiki cache hit", req_id);
        Some(cached)
    } else if plan.needs_wiki_search {
        state.metrics.wiki_lookups.fetch_add(1, Ordering::Relaxed);
        search_wikipedia(query, &state.config.zim_path, req_id)
            .await
            .inspect(|ctx| {
                log::info!("[{}] wiki lookup: {} chars", req_id, ctx.len());
                state.cache.insert_wiki(query, ctx.clone());
            })
    } else {
        None
    };

    // Score wiki relevance and decide how to use it
    let wiki_for_prompt: Option<String>;
    let wiki_standalone: Option<String>;
    let wiki_used: bool;

    match raw_wiki {
        Some(ctx) => match decide_wiki_usage(query, &ctx) {
            WikiDecision::UseDirectly(c) => {
                log::info!("[{}] wiki relevance: high — using directly", req_id);
                wiki_for_prompt = Some(c.clone());
                wiki_standalone = Some(c);
                wiki_used = true;
            }
            WikiDecision::CombineWithModel(c) => {
                log::info!("[{}] wiki relevance: medium — combining with model", req_id);
                wiki_for_prompt = Some(c);
                wiki_standalone = None;
                wiki_used = true;
            }
            WikiDecision::Ignore => {
                log::info!("[{}] wiki relevance: low — ignoring", req_id);
                wiki_for_prompt = None;
                wiki_standalone = None;
                wiki_used = false;
            }
        },
        None => {
            wiki_for_prompt = None;
            wiki_standalone = None;
            wiki_used = false;
        }
    }

    // If factual and wiki has high relevance, answer from context without model
    if plan.intent == planner::Intent::Factual && wiki_standalone.is_some() && !plan.needs_model {
        let answer = format_answer_from_wiki(query, wiki_standalone.as_deref().unwrap_or(""));
        log::info!("[{}] wiki-only answer (factual)", req_id);
        state.cache.insert_answer(query, answer.clone());
        return EngineResult {
            answer,
            model: state.config.model.clone(),
            source: "wikipedia".into(),
        };
    }

    // Otherwise call model
    let history = state.history.lock().clone();
    let messages = build_prompt(query, plan.intent, wiki_for_prompt.as_deref(), &history);

    log::info!(
        "[{}] model call: model={}, wiki={}, history={}",
        req_id,
        state.config.model,
        wiki_for_prompt.is_some(),
        history.len(),
    );

    state.metrics.model_calls.fetch_add(1, Ordering::Relaxed);

    let (raw, latency_ms) = match call_ollama(
        &state.client,
        &state.config.ollama_url,
        &state.config.model,
        state.config.ollama_timeout,
        messages,
        req_id,
    )
    .await
    {
        Ok((answer, ms)) => {
            state.metrics.total_model_latency_ms.fetch_add(ms, Ordering::Relaxed);
            (answer, ms)
        }
        Err(e) => {
            log::error!("[{}] model failed: {}", req_id, e);
            ("I'm unable to answer right now. Please try again.".into(), 0)
        }
    };

    log::info!("[{}] model latency: {}ms", req_id, latency_ms);

    // Cache and store
    let for_cache = raw.clone();
    state.cache.insert_answer(query, for_cache);
    state.add_to_history(query.to_string(), raw.clone());

    let source = if wiki_used {
        if wiki_standalone.is_some() {
            "wikipedia"
        } else {
            "wikipedia + local model"
        }
    } else {
        "local model"
    };

    EngineResult {
        answer: raw,
        source: source.into(),
        model: state.config.model.clone(),
    }
}

fn format_answer_from_wiki(_question: &str, context: &str) -> String {
    if context.is_empty() {
        return "I don't have enough information about that.".to_string();
    }
    format!("Based on available information:\n{}", context)
}
