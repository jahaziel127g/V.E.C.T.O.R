use std::time::Duration;

#[derive(Clone)]
pub struct Config {
    pub ollama_url: String,
    pub ollama_timeout: Duration,
    pub model: String,
    pub zim_path: String,
    pub max_history: usize,
    pub cache_size: u64,
    pub ram_warning: u8,
    pub ram_critical: u8,
    pub port: u16,
    pub http_pool_size: usize,
    pub frontend_url: String,
    pub workers: Option<usize>,
    pub whisper_path: String,
    pub whisper_model_path: String,
    pub whisper_enabled: bool,
    pub whisper_language: Option<String>,
}

impl Config {
    pub fn from_env() -> Self {
        let ollama_url = std::env::var("OLLAMA_URL")
            .unwrap_or_else(|_| "http://localhost:11434".to_string());
        let timeout = std::env::var("OLLAMA_TIMEOUT")
            .ok()
            .and_then(|v| v.parse().ok())
            .unwrap_or(60);

        Config {
            ollama_url: format!("{}/api/chat", ollama_url),
            ollama_timeout: Duration::from_secs(timeout),
            model: std::env::var("MODEL")
                .unwrap_or_else(|_| "openbmb/minicpm5:latest".to_string()),
            zim_path: std::env::var("ZIM_PATH")
                .unwrap_or_else(|_| "/home/jahazielo/Downloads/wikipedia_en_simple_all_nopic_2026-02.zim".to_string()),
            max_history: std::env::var("MAX_HISTORY")
                .ok().and_then(|v| v.parse().ok()).unwrap_or(3),
            cache_size: std::env::var("CACHE_SIZE")
                .ok().and_then(|v| v.parse().ok()).unwrap_or(10_000),
            ram_warning: std::env::var("RAM_WARNING")
                .ok().and_then(|v| v.parse().ok()).unwrap_or(85),
            ram_critical: std::env::var("RAM_CRITICAL")
                .ok().and_then(|v| v.parse().ok()).unwrap_or(90),
            port: std::env::var("PORT")
                .ok().and_then(|v| v.parse().ok()).unwrap_or(8080),
            http_pool_size: std::env::var("HTTP_POOL_SIZE")
                .ok().and_then(|v| v.parse().ok()).unwrap_or(16),
            frontend_url: std::env::var("FRONTEND_URL")
                .unwrap_or_else(|_| "*".to_string()),
            workers: std::env::var("WORKERS")
                .ok().and_then(|v| v.parse().ok()),
            whisper_path: std::env::var("WHISPER_PATH")
                .unwrap_or_else(|_| "./whisper.cpp/main".to_string()),
            whisper_model_path: std::env::var("WHISPER_MODEL_PATH")
                .unwrap_or_else(|_| "./models/ggml-small.en.bin".to_string()),
            whisper_enabled: std::env::var("WHISPER_ENABLED")
                .ok().map(|v| v == "1" || v.to_lowercase() == "true")
                .unwrap_or(true),
            whisper_language: std::env::var("WHISPER_LANGUAGE")
                .ok()
                .filter(|v| !v.is_empty()),
        }
    }

    pub fn cors_origin(&self) -> &str {
        &self.frontend_url
    }

}
