use std::collections::VecDeque;
use std::sync::atomic::AtomicU64;
use std::time::Duration;

use actix_web::web;
use parking_lot::Mutex;
use reqwest::Client;
use sysinfo::System;
use tokio::sync::Semaphore;

use crate::cache::AppCache;
use crate::config::Config;

pub struct Metrics {
    pub total_requests: AtomicU64,
    pub cache_hits: AtomicU64,
    pub wiki_lookups: AtomicU64,
    pub model_calls: AtomicU64,
    pub total_model_latency_ms: AtomicU64,
    // STT metrics
    pub stt_transcriptions: AtomicU64,
    pub stt_ffmpeg_conversions: AtomicU64,
    pub stt_cache_hits: AtomicU64,
    pub stt_timeouts: AtomicU64,
    pub stt_failures: AtomicU64,
    pub stt_total_latency_ms: AtomicU64,
}

impl Metrics {
    pub fn new() -> Self {
        Metrics {
            total_requests: AtomicU64::new(0),
            cache_hits: AtomicU64::new(0),
            wiki_lookups: AtomicU64::new(0),
            model_calls: AtomicU64::new(0),
            total_model_latency_ms: AtomicU64::new(0),
            stt_transcriptions: AtomicU64::new(0),
            stt_ffmpeg_conversions: AtomicU64::new(0),
            stt_cache_hits: AtomicU64::new(0),
            stt_timeouts: AtomicU64::new(0),
            stt_failures: AtomicU64::new(0),
            stt_total_latency_ms: AtomicU64::new(0),
        }
    }
}

pub struct AppState {
    pub config: Config,
    pub client: Client,
    pub cache: AppCache,
    pub metrics: Metrics,
    pub history: Mutex<VecDeque<(String, String)>>,
    pub stt_semaphore: Semaphore,
}

impl AppState {
    pub fn new(config: Config) -> Self {
        let client = Client::builder()
            .pool_max_idle_per_host(config.http_pool_size)
            .connect_timeout(Duration::from_secs(5))
            .tcp_keepalive(Duration::from_secs(60))
            .build()
            .unwrap_or_else(|_| Client::new());

        AppState {
            cache: AppCache::new(config.cache_size),
            client,
            config,
            metrics: Metrics::new(),
            history: Mutex::new(VecDeque::with_capacity(10)),
            stt_semaphore: Semaphore::new(2),
        }
    }

    pub fn add_to_history(&self, question: String, answer: String) {
        let mut history = self.history.lock();
        if history.len() >= self.config.max_history {
            history.pop_front();
        }
        history.push_back((question, answer));
    }

    pub fn clear_history(&self) {
        self.history.lock().clear();
    }
}

fn get_ram_usage_percent() -> u8 {
    let mut sys = System::new_all();
    sys.refresh_memory();
    ((sys.used_memory() as f64 / sys.total_memory() as f64) * 100.0) as u8
}

fn manage_memory(state: &AppState) {
    let ram = get_ram_usage_percent();

    if ram >= state.config.ram_critical {
        log::warn!("RAM critical: {}% - clearing history and stopping Ollama", ram);
        state.clear_history();
        let _ = std::process::Command::new("ollama").arg("stop").output();
    } else if ram >= state.config.ram_warning {
        log::warn!("RAM usage high: {}%", ram);
    }
}

pub fn spawn_ram_monitor(state: web::Data<AppState>) {
    tokio::spawn(async move {
        let mut interval = tokio::time::interval(Duration::from_secs(5));
        interval.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Skip);
        loop {
            interval.tick().await;
            manage_memory(&state);
        }
    });
}
