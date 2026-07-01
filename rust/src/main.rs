mod api;
mod cache;
mod config;
mod engine;
mod planner;
mod prompt;
mod setup;
mod state;
mod stt;
mod wiki;

use actix_cors::Cors;
use actix_web::{web, App, HttpServer};

use crate::config::Config;
use crate::setup::run_initial_checks;
use crate::state::{spawn_ram_monitor, AppState};

#[actix_web::main]
async fn main() -> std::io::Result<()> {
    env_logger::init();

    std::panic::set_hook(Box::new(|info| {
        log::error!("Worker panic: {:?}", info);
        #[cfg(debug_assertions)]
        log::error!("Backtrace: {:?}", std::backtrace::Backtrace::capture());
    }));

    let config = Config::from_env();
    let state = web::Data::new(AppState::new(config.clone()));
    let port = config.port;
    let workers = config
        .workers
        .unwrap_or_else(num_cpus::get);
    spawn_ram_monitor(state.clone());

    // Run startup checks (non-critical failures logged, not fatal)
    run_initial_checks(&config).await;

    log::info!(
        "V.E.C.T.O.R Rust starting on http://0.0.0.0:{} (workers: {})",
        port,
        workers,
    );

    let cors_origin = config.cors_origin().to_string();

    let server = HttpServer::new(move || {
        // Cors uses Rc<Inner> (not Clone); build fresh per worker
        let cors = if cors_origin == "*" {
            Cors::default()
                .allow_any_origin()
                .allow_any_method()
                .allow_any_header()
        } else {
            Cors::default()
                .allowed_origin(&cors_origin)
                .allow_any_method()
                .allow_any_header()
        };

        App::new()
            .app_data(web::JsonConfig::default().limit(1_048_576))
            .app_data(state.clone())
            .wrap(cors)
            .route("/api/ask", web::post().to(api::ask))
            .route("/api/ask/stream", web::post().to(api::ask_stream))
            .route("/api/ask/stream", web::get().to(api::ask_stream_get))
            .route("/api/stt", web::post().to(api::stt))
            .route("/api/health", web::get().to(api::health))
            .route("/api/stats", web::get().to(api::stats))
    })
    .workers(workers)
    .bind(format!("0.0.0.0:{}", port))?
    .run();

    let srv = server.handle();
    tokio::spawn(async move {
        tokio::signal::ctrl_c()
            .await
            .expect("Failed to listen for Ctrl+C");
        log::info!("Shutting down gracefully...");
        srv.stop(true).await;
    });

    server.await
}
