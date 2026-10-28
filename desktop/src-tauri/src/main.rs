// No console window in release builds.
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod app;
mod config;
mod cores;
mod links;
mod models;
mod net;
mod settings;
mod subscription;
mod system;
mod tray;

use std::time::Duration;

use tauri::{Manager, RunEvent, WindowEvent};

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let restarting = args.iter().any(|a| a == "--elevated-restart");
    let connect_now = args.iter().any(|a| a == "--connect");

    // One instance: a second launch brings the first one forward instead.
    if !system::claim_single_instance(if restarting { 15_000 } else { 0 }) {
        return;
    }
    net::cleanup_old_version();
    // Leftover proxy from a crash? Put the user's settings back.
    system::restore_proxy();

    let built = tauri::Builder::default()
        .setup(move |tauri_app| {
            let handle = tauri_app.handle().clone();
            let core = app::Core::new(handle.clone());
            tauri_app.manage(core.clone());
            tray::build(&handle)?;

            let shower = core.clone();
            system::listen_for_show(move || {
                let s = shower.clone();
                let _ = s.handle.clone().run_on_main_thread(move || s.show_window());
            });

            core.spawn_background(connect_now);

            // The page shows the window once drawn; this is the safety net.
            let late = core.clone();
            tauri::async_runtime::spawn(async move {
                tokio::time::sleep(Duration::from_millis(2500)).await;
                if let Some(w) = late.handle.get_webview_window("main") {
                    if !w.is_visible().unwrap_or(true) {
                        let _ = w.show();
                    }
                }
            });
            Ok(())
        })
        .on_window_event(|window, event| {
            // Closing only hides: the VPN keeps running from the notification area.
            if let WindowEvent::CloseRequested { api, .. } = event {
                api.prevent_close();
                let _ = window.hide();
            }
        })
        .invoke_handler(tauri::generate_handler![
            app::get_state,
            app::get_logs,
            app::clear_logs,
            app::sign_in,
            app::refresh,
            app::change_link,
            app::select_server,
            app::ping_all,
            app::set_prefs,
            app::set_mode,
            app::connect,
            app::disconnect,
            app::restart_elevated,
            app::check_update,
            app::install_update,
            app::open_url,
            app::quit,
        ])
        .build(tauri::generate_context!());

    let tauri_app = match built {
        Ok(a) => a,
        Err(e) => {
            eprintln!("VOID-RAY: {e}");
            return;
        }
    };
    tauri_app.run(|handle, event| {
        if let RunEvent::Exit = event {
            if let Some(core) = handle.try_state::<app::Shared>() {
                core.shutdown();
            }
        }
    });
}
