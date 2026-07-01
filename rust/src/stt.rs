use std::path::{Path, PathBuf};
use std::sync::atomic::Ordering;

use tokio::io::AsyncReadExt;
use tokio::process::Command;
use tokio::sync::{Semaphore, SemaphorePermit};
use tokio::time::{timeout, Duration};
use uuid::Uuid;

use crate::cache::AppCache;
use crate::config::Config;
use crate::state::Metrics;

/// Detect audio format from magic bytes.
fn detect_ext(data: &[u8]) -> &'static str {
    if data.len() >= 12 && data[..4] == *b"RIFF" && data[8..12] == *b"WAVE" {
        "wav"
    } else if data.starts_with(b"OggS") {
        "ogg"
    } else if data.starts_with(b"ID3") || data.starts_with(&[0xFF, 0xFB]) {
        "mp3"
    } else {
        "webm"
    }
}

/// Check whether a file on disk starts with WAV magic bytes.
fn is_wav_file(path: &Path) -> bool {
    let mut buf = [0u8; 12];
    let mut f = match std::fs::File::open(path) {
        Ok(f) => f,
        Err(_) => return false,
    };
    use std::io::Read;
    f.read_exact(&mut buf).is_ok()
        && buf[..4] == *b"RIFF"
        && buf[8..12] == *b"WAVE"
}

/// Run an external process with concurrent pipe readers (prevents pipe
/// deadlock when the process writes more than the OS pipe buffer) and a
/// kill-on-timeout safety net.
async fn run_cmd(
    cmd: &mut Command,
    dur: Duration,
) -> Result<(std::process::ExitStatus, Vec<u8>, Vec<u8>), String> {
    let mut child = cmd
        .stdout(std::process::Stdio::piped())
        .stderr(std::process::Stdio::piped())
        .spawn()
        .map_err(|e| format!("spawn failed: {}", e))?;

    // Spawn pipe readers BEFORE waiting — this is critical. If we wait()
    // first and the child fills the pipe buffer, it blocks forever while we
    // sit here waiting for it to exit. Classic subprocess deadlock.
    let stdout_handle = child.stdout.take().map(|mut s| {
        tokio::spawn(async move {
            let mut buf = Vec::new();
            s.read_to_end(&mut buf).await.ok();
            buf
        })
    });
    let stderr_handle = child.stderr.take().map(|mut s| {
        tokio::spawn(async move {
            let mut buf = Vec::new();
            s.read_to_end(&mut buf).await.ok();
            buf
        })
    });

    let result = timeout(dur, child.wait()).await;

    // If the child didn't exit cleanly, kill it now so the pipe readers can
    // finish (they'll get EOF when the write end is closed).
    if result.is_err() || result.as_ref().ok().and_then(|r| r.as_ref().err()).is_some() {
        child.kill().await.ok();
        child.wait().await.ok();
    }

    // Collect pipe readers — by now the child is either naturally done or
    // has been killed, so both pipes are closed and the tasks are finished.
    let stdout = match stdout_handle {
        Some(h) => h.await.unwrap_or_default(),
        None => Vec::new(),
    };
    let stderr = match stderr_handle {
        Some(h) => h.await.unwrap_or_default(),
        None => Vec::new(),
    };

    match result {
        Ok(Ok(status)) => Ok((status, stdout, stderr)),
        Ok(Err(e)) => Err(format!("wait error: {}", e)),
        Err(_) => Err("timed out".into()),
    }
}

/// Temp file that writes data on construction and cleans up on drop.
pub struct TempAudio {
    pub path: PathBuf,
}

impl TempAudio {
    pub fn new(data: &[u8]) -> std::io::Result<Self> {
        let ext = detect_ext(data);
        let path = std::env::temp_dir().join(format!("vector_stt_{}.{}", Uuid::new_v4(), ext));
        std::fs::write(&path, data)?;
        log::info!("stt: saved {} bytes to {:?}", data.len(), path);
        Ok(TempAudio { path })
    }
}

impl Drop for TempAudio {
    fn drop(&mut self) {
        let _ = std::fs::remove_file(&self.path);
    }
}

/// Validate uploaded audio file size bounds.
pub fn validate_audio(data: &[u8]) -> Result<(), String> {
    if data.is_empty() {
        return Err("Empty audio file".into());
    }
    if data.len() > 10_485_760 {
        return Err("Audio file too large (max 10 MB)".into());
    }
    Ok(())
}

/// Clean up stale temp files left after a crash.
pub fn clean_stale_tempfiles() {
    let tmp = std::env::temp_dir();
    if let Ok(entries) = std::fs::read_dir(&tmp) {
        let mut count = 0u32;
        for entry in entries.flatten() {
            let name = entry.file_name();
            let s = name.to_string_lossy();
            if (s.starts_with("vector_stt_") || s.starts_with("vector_ffmpeg_"))
                && std::fs::remove_file(entry.path()).is_ok()
            {
                count += 1;
            }
        }
        if count > 0 {
            log::info!("stt: cleaned {} stale temp files", count);
        }
    }
}

const FFMPEG_TIMEOUT: Duration = Duration::from_secs(15);
const WHISPER_TIMEOUT: Duration = Duration::from_secs(30);

/// If the file is not WAV, convert via ffmpeg (async, killed on timeout).
/// Returns true if an actual conversion happened (for metrics).
async fn ensure_wav(input: &Path, output: &Path) -> Result<bool, String> {
    if is_wav_file(input) {
        return Ok(false);
    }

    let mut cmd = Command::new("ffmpeg");
    cmd.arg("-y")
        .arg("-i")
        .arg(input)
        .arg("-ar")
        .arg("16000")
        .arg("-ac")
        .arg("1")
        .arg("-sample_fmt")
        .arg("s16")
        .arg("-af")
        .arg("dynaudnorm")
        .arg("-f")
        .arg("wav")
        .arg(output);

    let (status, _stdout, stderr) = run_cmd(&mut cmd, FFMPEG_TIMEOUT).await.map_err(|e| {
        let _ = std::fs::remove_file(output);
        if e == "timed out" {
            "ffmpeg conversion timed out (15s)".to_string()
        } else {
            format!("ffmpeg not found: {}", e)
        }
    })?;

    if !status.success() {
        let _ = std::fs::remove_file(output);
        let msg = String::from_utf8_lossy(&stderr).trim().to_string();
        return Err(format!("ffmpeg conversion failed: {}", msg));
    }

    let wav_len = std::fs::metadata(output).map(|m| m.len()).unwrap_or(0);
    let dur_ms = if wav_len > 44 {
        (wav_len - 44) as f64 / 32.0  // 16-bit mono 16kHz: 32000 bytes/sec -> 32 bytes per ms
    } else {
        0.0
    };
    log::info!("whisper: converted {:?} -> {:?} ({} bytes, {:.1}ms)", input, output, wav_len, dur_ms);
    Ok(true)
}

/// Run whisper.cpp to transcribe audio (async, killed on timeout).
pub async fn transcribe(
    audio_path: PathBuf,
    config: &Config,
    cache: &AppCache,
    audio_data: &[u8],
    semaphore: &Semaphore,
    metrics: &Metrics,
) -> Result<String, String> {
    if !config.whisper_enabled {
        return Err("Speech-to-text is disabled".into());
    }

    // Fast cache check using raw audio hash (zero extra I/O)
    let hash = blake3::hash(audio_data).to_hex().to_string();
    if let Some(cached) = cache.get_stt(&hash) {
        log::info!("whisper: cache hit ({} chars)", cached.len());
        metrics.stt_cache_hits.fetch_add(1, Ordering::Relaxed);
        return Ok(cached);
    }

    let start = std::time::Instant::now();

    // Convert to WAV if needed
    let conv_path = std::env::temp_dir().join(format!("vector_ffmpeg_{}.wav", Uuid::new_v4()));
    let did_convert = ensure_wav(&audio_path, &conv_path).await?;
    if did_convert {
        metrics.stt_ffmpeg_conversions.fetch_add(1, Ordering::Relaxed);
    }
    let wav_path = if did_convert { &conv_path } else { &audio_path };
    let needs_cleanup = did_convert;

    // Minimum duration: reject audio shorter than 1 second (likely noise/glitch)
    let wav_meta = std::fs::metadata(wav_path).map(|m| m.len()).unwrap_or(0);
    if wav_meta > 44 {
        let wav_dur_ms = (wav_meta - 44) as f64 / 32.0;
        if wav_dur_ms < 1000.0 {
            if needs_cleanup { let _ = std::fs::remove_file(wav_path); }
            log::warn!("whisper: audio too short ({:.0}ms, minimum 1000ms)", wav_dur_ms);
            metrics.stt_failures.fetch_add(1, Ordering::Relaxed);
            return Err("Audio too short (minimum 1 second)".into());
        }
        log::info!("whisper: wav duration = {:.0}ms", wav_dur_ms);
    }

    log::info!(
        "whisper: starting {} -m {} -f {} -nt{}",
        config.whisper_path,
        config.whisper_model_path,
        wav_path.display(),
        config.whisper_language.as_ref().map_or(String::new(), |l| format!(" --language {}", l)),
    );

    // Acquire a semaphore permit BEFORE launching whisper to cap CPU load.
    let _permit: SemaphorePermit<'_> = semaphore
        .acquire()
        .await
        .map_err(|_| {
            if needs_cleanup {
                let _ = std::fs::remove_file(wav_path);
            }
            "STT semaphore closed".to_string()
        })?;

    let mut cmd = Command::new(&config.whisper_path);
    cmd.arg("-m")
        .arg(&config.whisper_model_path)
        .arg("-f")
        .arg(wav_path)
        .arg("-nt");
    if let Some(lang) = &config.whisper_language {
        cmd.arg("--language").arg(lang);
    }

    let (status, stdout, stderr) = run_cmd(&mut cmd, WHISPER_TIMEOUT).await.map_err(|e| {
        if needs_cleanup {
            let _ = std::fs::remove_file(wav_path);
        }
        if e == "timed out" {
            metrics.stt_timeouts.fetch_add(1, Ordering::Relaxed);
            "Whisper transcription timed out (30s)".to_string()
        } else {
            metrics.stt_failures.fetch_add(1, Ordering::Relaxed);
            format!("Failed to run whisper: {}", e)
        }
    })?;

    if needs_cleanup {
        let _ = std::fs::remove_file(wav_path);
    }

    if !status.success() {
        let msg = String::from_utf8_lossy(&stderr).trim().to_string();
        log::error!("whisper: process failed: {}", msg);
        metrics.stt_failures.fetch_add(1, Ordering::Relaxed);
        return Err(format!("Transcription failed: {}", msg));
    }

    let raw = String::from_utf8_lossy(&stdout);
    log::info!("whisper stdout raw: {:?}", raw);
    let stderr_str = String::from_utf8_lossy(&stderr);
    log::info!("whisper stderr (last 200): {:?}", &stderr_str[stderr_str.len().saturating_sub(200)..]);

    // Strip timestamp prefixes like [00:00.000 --> 00:02.000] but keep the
    // speech text that follows.  Old bug: we deleted the entire line, which
    // silently ate the transcription when timestamps were present.
    let text = raw
        .lines()
        .filter_map(|l| {
            let t = l.trim();
            if t.is_empty() {
                return None;
            }
            // Only strip leading [timestamp] — not arbitrary bracketed text.
            // rfind(']') would break on diagnostics like [audio not loud enough].
            let content = if t.starts_with('[') {
                match t.find(']') {
                    Some(idx) => t[idx + 1..].trim(),
                    None => t,
                }
            } else if t.starts_with('(') && t.ends_with(')') {
                // Parenthetical labels like "(water running)", "(beeping)" — these
                // are whisper's non-speech sound-effect labels, not actual speech.
                return None;
            } else {
                t
            };
            if content.is_empty() {
                None
            } else {
                Some(content)
            }
        })
        .collect::<Vec<_>>()
        .join(" ");

    log::info!("whisper: parsed text = {:?}", text);
    if text.is_empty() {
        metrics.stt_failures.fetch_add(1, Ordering::Relaxed);
        return Err("No speech detected".into());
    }

    // Filter common whisper single-word hallucinations (noise interpreted as speech)
    let trimmed = text.trim().to_lowercase();
    let hallucinations = [
        "you", "the", "a", "thank", "thanks", "music", "applause",
        "you.", "the.", "a.",
    ];
    if hallucinations.contains(&trimmed.as_str()) {
        log::warn!("whisper: filtered hallucination {:?}", text);
        metrics.stt_failures.fetch_add(1, Ordering::Relaxed);
        return Err("No speech detected".into());
    }

    let elapsed_ms = start.elapsed().as_millis() as u64;
    log::info!("whisper: transcribed {} chars in {}ms", text.len(), elapsed_ms);

    // Track metrics
    metrics.stt_transcriptions.fetch_add(1, Ordering::Relaxed);
    metrics
        .stt_total_latency_ms
        .fetch_add(elapsed_ms, Ordering::Relaxed);

    // Cache by raw audio hash
    cache.insert_stt(&hash, text.clone());

    Ok(text)
}
