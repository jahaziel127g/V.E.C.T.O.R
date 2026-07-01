use std::path::Path;
use std::time::Duration;

use crate::config::Config;
use crate::stt;
use reqwest::Client;

#[allow(dead_code)]
pub struct SetupResult {
    pub ollama_available: bool,
    pub model_available: bool,
    pub wiki_available: bool,
    pub whisper_available: bool,
}

macro_rules! ok_log  { ($($t:tt)*) => { log::info!("\x1b[32m✓\x1b[0m  {}", format!($($t)*)) } }
macro_rules! warn_log { ($($t:tt)*) => { log::warn!("\x1b[33m⚠\x1b[0m  {}", format!($($t)*)) } }
macro_rules! err_log  { ($($t:tt)*) => { log::error!("\x1b[31m✗\x1b[0m  {}", format!($($t)*)) } }

fn strip_api_suffix(url: &str) -> String {
    url.strip_suffix("/api/chat")
        .unwrap_or(url)
        .trim_end_matches('/')
        .to_string()
}

pub async fn check_ollama(config: &Config, client: &Client) -> bool {
    let base = strip_api_suffix(&config.ollama_url);

    // Quick check — is Ollama already responding?
    if let Ok(resp) = client
        .get(format!("{}/api/tags", base))
        .timeout(Duration::from_secs(2))
        .send()
        .await
    {
        if resp.status().is_success() {
            ok_log!("Ollama is running at {}", base);
            return true;
        }
    }

    // Not running — try to start it
    warn_log!("Ollama not reachable — attempting to start...");
    match tokio::process::Command::new("ollama")
        .args(["serve"])
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::null())
        .spawn()
    {
        Ok(child) => {
            // Don't await — let it run in background
            drop(child);
        }
        Err(e) => {
            err_log!("Failed to start ollama serve: {}", e);
            return false;
        }
    }

    // Wait for it to come online (up to 15s)
    for i in 1..=15 {
        tokio::time::sleep(Duration::from_secs(1)).await;
        if let Ok(resp) = client
            .get(format!("{}/api/tags", base))
            .timeout(Duration::from_secs(1))
            .send()
            .await
        {
            if resp.status().is_success() {
                ok_log!("Ollama started and ready ({}s)", i);
                return true;
            }
        }
    }

    err_log!("Ollama did not start after 15s — AI features unavailable");
    false
}

pub async fn ensure_model(config: &Config, client: &Client) -> bool {
    let base = strip_api_suffix(&config.ollama_url);

    // Check if model exists via Ollama API
    let model_name = &config.model;
    let exists = match client
        .get(format!("{}/api/tags", base))
        .timeout(Duration::from_secs(5))
        .send()
        .await
    {
        Ok(resp) => {
            if let Ok(body) = resp.json::<serde_json::Value>().await {
                if let Some(models) = body["models"].as_array() {
                    models.iter().any(|m| {
                        m["name"].as_str().is_some_and(|n| {
                            n == model_name || n.starts_with(&format!("{}:", model_name))
                        })
                    })
                } else {
                    false
                }
            } else {
                false
            }
        }
        Err(_) => false,
    };

    if exists {
        ok_log!("Model {} is available", model_name);
        return true;
    }

    warn_log!("Model {} not found — pulling...", model_name);
    err_log!("This may take a while depending on network speed and model size");

    let start = std::time::Instant::now();
    let status = tokio::process::Command::new("ollama")
        .args(["pull", model_name])
        .status()
        .await;

    match status {
        Ok(s) if s.success() => {
            let elapsed = start.elapsed().as_secs();
            ok_log!("Model {} pulled successfully ({}s)", model_name, elapsed);
            true
        }
        Ok(s) => {
            err_log!("ollama pull failed with exit code {:?}", s.code());
            false
        }
        Err(e) => {
            err_log!("Failed to run ollama pull: {}", e);
            false
        }
    }
}

pub fn check_wiki(config: &Config) -> bool {
    let path = Path::new(&config.zim_path);
    if path.exists() {
        ok_log!("Wikipedia ZIM file found at {}", config.zim_path);
        true
    } else {
        warn_log!("Wikipedia ZIM file not found at {}", config.zim_path);
        warn_log!("Wiki lookup disabled — set ZIM_PATH or install a ZIM file");
        false
    }
}

pub fn check_ffmpeg() -> bool {
    let ok = std::process::Command::new("ffmpeg")
        .arg("-version")
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::null())
        .status()
        .map(|s| s.success())
        .unwrap_or(false);
    if ok {
        ok_log!("ffmpeg is available");
    } else {
        warn_log!("ffmpeg not found — audio conversion (STT) disabled");
    }
    ok
}

pub fn check_whisper(config: &Config) -> bool {
    if !config.whisper_enabled {
        warn_log!("STT disabled by WHISPER_ENABLED=false");
        return false;
    }

    let path = Path::new(&config.whisper_path);
    if path.exists() {
        ok_log!("whisper.cpp found at {}", config.whisper_path);
        true
    } else {
        warn_log!("STT disabled: whisper.cpp not found at {}", config.whisper_path);
        warn_log!("Install whisper.cpp or set WHISPER_PATH");
        false
    }
}

pub async fn run_initial_checks(config: &Config) -> SetupResult {
    let client = Client::builder()
        .connect_timeout(Duration::from_secs(3))
        .build()
        .unwrap_or_else(|_| Client::new());

    log::info!("─── V.E.C.T.O.R startup checks ───");

    // Clean up temp files from previous runs
    stt::clean_stale_tempfiles();

    let ollama_ok = check_ollama(config, &client).await;
    let model_ok = if ollama_ok {
        ensure_model(config, &client).await
    } else {
        err_log!("Skipping model check — Ollama unavailable");
        false
    };
    let wiki_ok = check_wiki(config);
    let ffmpeg_ok = check_ffmpeg();
    let whisper_ok = check_whisper(config) && ffmpeg_ok;

    log::info!("─── Setup complete ───");
    if !ollama_ok {
        err_log!("Ollama is DOWN — AI features will not work");
    }
    if !wiki_ok {
        warn_log!("Wikipedia lookup unavailable — will use model only");
    }
    if !whisper_ok {
        warn_log!("STT disabled — voice input unavailable");
    }

    SetupResult {
        ollama_available: ollama_ok,
        model_available: model_ok,
        wiki_available: wiki_ok,
        whisper_available: whisper_ok,
    }
}
