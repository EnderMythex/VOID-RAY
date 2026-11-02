//! Notification-area icon: closing the window hides it here and the VPN keeps
//! running. A click reopens it; the menu connects, disconnects or quits.

use std::sync::Mutex;

use tauri::image::Image;
use tauri::menu::{Menu, MenuItem, PredefinedMenuItem};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{AppHandle, Manager, Wry};

use crate::app::{ConnState, Shared};

const IDLE: &[u8] = include_bytes!("../icons/tray.png");
const ON: &[u8] = include_bytes!("../icons/tray-on.png");

pub struct TrayItems {
    open: MenuItem<Wry>,
    toggle: MenuItem<Wry>,
    quit: MenuItem<Wry>,
    idle: Image<'static>,
    on: Image<'static>,
    /// What the tray shows now: most state events (pings, refreshes) change nothing here.
    shown: Mutex<Option<(ConnState, String, bool)>>,
}

fn t(lang: &str, key: &str) -> &'static str {
    let fr = lang == "fr";
    match key {
        "open" => if fr { "Ouvrir VOID-RAY" } else { "Open VOID-RAY" },
        "connect" => if fr { "Se connecter" } else { "Connect" },
        "disconnect" => if fr { "Se déconnecter" } else { "Disconnect" },
        "quit" => if fr { "Quitter" } else { "Quit" },
        "on" => if fr { "Protégé" } else { "Protected" },
        "off" => if fr { "Non protégé" } else { "Not protected" },
        _ => "",
    }
}

pub fn build(app: &AppHandle) -> tauri::Result<()> {
    let open = MenuItem::with_id(app, "open", t("en", "open"), true, None::<&str>)?;
    let toggle = MenuItem::with_id(app, "toggle", t("en", "connect"), true, None::<&str>)?;
    let quit = MenuItem::with_id(app, "quit", t("en", "quit"), true, None::<&str>)?;
    let menu = Menu::with_items(
        app,
        &[&open, &PredefinedMenuItem::separator(app)?, &toggle, &PredefinedMenuItem::separator(app)?, &quit],
    )?;

    let idle = Image::from_bytes(IDLE)?;
    let on = Image::from_bytes(ON)?;
    TrayIconBuilder::with_id("main")
        .icon(idle.clone())
        .tooltip("VOID-RAY")
        .menu(&menu)
        .show_menu_on_left_click(false)
        .on_menu_event(|app, event| {
            let core = app.state::<Shared>().inner().clone();
            match event.id().as_ref() {
                "open" => core.show_window(),
                "toggle" => {
                    tauri::async_runtime::spawn(async move {
                        if core.state() == ConnState::Connected {
                            core.disconnect().await;
                        } else if core.state() == ConnState::Disconnected {
                            if let Err(e) = core.connect().await {
                                // Needs the window (admin prompt, server choice…).
                                core.show_window();
                                let _ = tauri::Emitter::emit(&core.handle, "trayConnectFailed", e);
                            }
                        }
                    });
                }
                "quit" => {
                    core.shutdown();
                    app.exit(0);
                }
                _ => {}
            }
        })
        .on_tray_icon_event(|tray, event| {
            if let TrayIconEvent::Click { button: MouseButton::Left, button_state: MouseButtonState::Up, .. } = event {
                tray.app_handle().state::<Shared>().show_window();
            }
        })
        .build(app)?;

    app.manage(TrayItems { open, toggle, quit, idle, on, shown: Mutex::new(None) });
    Ok(())
}

pub fn refresh(app: &AppHandle, state: ConnState, lang: &str, authenticated: bool) {
    let Some(items) = app.try_state::<TrayItems>() else { return };
    {
        let mut shown = items.shown.lock().unwrap();
        let next = (state, lang.to_string(), authenticated);
        if shown.as_ref() == Some(&next) {
            return;
        }
        *shown = Some(next);
    }
    let connected = state == ConnState::Connected;
    let _ = items.open.set_text(t(lang, "open"));
    let _ = items.toggle.set_text(t(lang, if connected { "disconnect" } else { "connect" }));
    let _ = items.toggle.set_enabled(authenticated && matches!(state, ConnState::Connected | ConnState::Disconnected));
    let _ = items.quit.set_text(t(lang, "quit"));
    if let Some(tray) = app.tray_by_id("main") {
        let _ = tray.set_icon(Some(if connected { items.on.clone() } else { items.idle.clone() }));
        let _ = tray.set_tooltip(Some(format!("VOID-RAY — {}", t(lang, if connected { "on" } else { "off" }))));
    }
}
