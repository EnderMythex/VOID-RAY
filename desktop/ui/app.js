// VOID-RAY — interface logic. The Rust side owns the state and sends a full
// snapshot on every change ("state" event); this file only draws it.
"use strict";

const TAURI = window.__TAURI__ || window.__VOIDRAY_MOCK__;
const invoke = (cmd, args) => TAURI.core.invoke(cmd, args);
const listen = (event, fn) => TAURI.event.listen(event, (e) => fn(e.payload));
const appWindow = () => TAURI.window.getCurrentWindow();
const $ = (id) => document.getElementById(id);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

let S = null;
let lang = "en";
let tab = "overview";

// ================================================================== i18n & format

function resolveLang(mode) {
  if (mode === "en" || mode === "fr") return mode;
  return (navigator.language || "en").toLowerCase().startsWith("fr") ? "fr" : "en";
}
function t(key, ...args) {
  let s = (STRINGS[lang] && STRINGS[lang][key]) || STRINGS.en[key] || key;
  args.forEach((a, i) => { s = s.replace(`{${i}}`, a); });
  return s;
}
const locale = () => (lang === "fr" ? "fr-FR" : "en-GB");
function bytes(n) {
  const units = lang === "fr" ? ["o", "Ko", "Mo", "Go", "To"] : ["B", "KB", "MB", "GB", "TB"];
  if (!n || n < 1) return "0 " + units[0];
  let i = 0, v = n;
  while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
  return new Intl.NumberFormat(locale(), { maximumFractionDigits: v >= 100 || i === 0 ? 0 : 2 }).format(v) + " " + units[i];
}
const shortDate = (ms) => new Date(ms).toLocaleDateString(locale(), { day: "numeric", month: "short", year: "numeric" });
const stamp = (ms) => shortDate(ms) + " · " + new Date(ms).toLocaleTimeString(locale(), { hour: "2-digit", minute: "2-digit" });
function ago(ms) {
  const d = Date.now() - ms;
  if (d < 120000) return t("justNow");
  if (d < 3600000) return t("minsAgo", Math.round(d / 60000));
  if (d < 86400000) return t("hoursAgo", Math.round(d / 3600000));
  return t("daysAgo", Math.round(d / 86400000));
}
function duration(sec) {
  const h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), s = sec % 60;
  const p = (x) => String(x).padStart(2, "0");
  return h ? `${p(h)}:${p(m)}:${p(s)}` : `${p(m)}:${p(s)}`;
}
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

function applyStrings() {
  document.documentElement.lang = lang;
  $$("[data-i18n]").forEach((el) => { el.textContent = t(el.dataset.i18n); });
}

// ================================================================== theme

const ACCENTS = [
  { name: "teal", dark: "#52d6bd", light: "#0f9d86" },
  { name: "indigo", dark: "#7f97ff", light: "#3f55cf" },
  { name: "violet", dark: "#b085ff", light: "#7440d4" },
  { name: "rose", dark: "#ff8aab", light: "#cf3560" },
  { name: "amber", dark: "#f0b95f", light: "#a5720c" },
  { name: "lime", dark: "#9ede5a", light: "#4d8c19" },
];
const media = window.matchMedia("(prefers-color-scheme: light)");
media.addEventListener("change", () => S && applyTheme());

function applyTheme() {
  const mode = S.theme;
  const theme = mode === "auto" ? (media.matches ? "light" : "dark") : mode;
  document.documentElement.dataset.theme = theme;
  const preset = ACCENTS[S.accent] || ACCENTS[0];
  document.documentElement.style.setProperty("--accent", theme === "light" ? preset.light : preset.dark);
  $$(".swatch").forEach((s, i) => s.classList.toggle("on", i === S.accent));
  setSeg($("theme-seg"), mode);
}

function savePrefs(changes) {
  Object.assign(S, changes);
  lang = resolveLang(S.language);
  invoke("set_prefs", { theme: S.theme, accent: S.accent, language: S.language, uiLang: lang });
  applyStrings();
  render();
}

// ================================================================== segmented controls

function setSeg(seg, value, attr = "v") {
  const buttons = $$("button", seg);
  const idx = Math.max(0, buttons.findIndex((b) => b.dataset[attr] === value));
  buttons.forEach((b, i) => b.classList.toggle("on", i === idx));
  const thumb = seg.querySelector(".seg-thumb");
  const b = buttons[idx];
  if (thumb && b) {
    thumb.style.width = b.offsetWidth + "px";
    thumb.style.transform = `translateX(${b.offsetLeft - 4}px)`;
  }
}

// ================================================================== toast & dialog

let toastTimer;
function toast(text, error = false) {
  const el = $("toast");
  el.querySelector("span").textContent = text;
  el.classList.toggle("error", error);
  el.classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove("show"), 3600);
}
function toastKey(code, error = true) {
  const [key, ...rest] = String(code).split(":");
  toast(t(key, rest.join(":")), error);
}

function ask({ title, text, notes, yes, no }) {
  return new Promise((resolve) => {
    $("dlg-title").textContent = title;
    $("dlg-text").textContent = text;
    $("dlg-notes").hidden = !notes;
    $("dlg-notes").textContent = notes || "";
    $("dlg-yes").textContent = yes;
    $("dlg-no").textContent = no;
    const box = $("dialog");
    box.hidden = false;
    const done = (v) => { box.hidden = true; $("dlg-yes").onclick = $("dlg-no").onclick = null; resolve(v); };
    $("dlg-yes").onclick = () => done(true);
    $("dlg-no").onclick = () => done(false);
  });
}

// ================================================================== derived values

function derive() {
  const sub = S.sub;
  const used = sub ? sub.upload + sub.download : 0;
  const unlimited = !sub || !(sub.total > 0);
  const ratio = unlimited ? 0 : Math.min(1, used / sub.total);
  const expired = !!(sub && sub.expire && sub.expire * 1000 < Date.now());
  const drained = !unlimited && used >= sub.total;
  const live = !!sub && sub.enabled !== false && !expired && !drained;
  let status = { label: "—", cls: "" };
  if (sub) {
    if (sub.enabled === false) status = { label: t("stDisabled"), cls: "alert" };
    else if (expired) status = { label: t("stExpired"), cls: "alert" };
    else if (drained) status = { label: t("stQuota"), cls: "alert" };
    else if (unlimited) status = { label: t("stUnlimited"), cls: "ok" };
    else if (ratio >= 0.9) status = { label: t("stAlmost"), cls: "warn" };
    else status = { label: t("stActive"), cls: "ok" };
  }
  const figure = !sub ? "—" : unlimited ? bytes(used) : bytes(Math.max(0, sub.total - used));
  const [value, ...unit] = figure.split(" ");
  return { sub, used, unlimited, ratio, live, status, value, unit: unit.join(" "),
    caption: unlimited ? t("capUsed") : t("capLeft", bytes(sub.total)) };
}

function dailyUsage(hist) {
  const key = (ms) => { const d = new Date(ms); return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`; };
  const perDay = new Map();
  for (const [ts, used] of hist || []) {
    const cur = perDay.get(key(ts));
    if (!cur) perDay.set(key(ts), { first: used, last: used });
    else { cur.last = Math.max(cur.last, used); cur.first = Math.min(cur.first, used); }
  }
  const out = [];
  const today = new Date(); today.setHours(0, 0, 0, 0);
  let prev = null;
  for (let i = 13; i >= 0; i--) {
    const day = today.getTime() - i * 86400000;
    const e = perDay.get(key(day));
    if (!e) { out.push({ day, value: null }); continue; }
    const delta = prev === null ? e.last - e.first : e.last < prev ? e.last : e.last - prev;
    prev = e.last;
    out.push({ day, value: Math.max(0, delta) });
  }
  return out;
}

const selected = () => S.servers.find((s) => s.key === S.selectedKey);

// ================================================================== render

function render() {
  if (!S) return;
  const state = S.state;
  document.body.dataset.state = state;
  $("gate").hidden = S.authenticated;
  $("home").hidden = !S.authenticated;
  $("foot-left").style.visibility = S.authenticated ? "visible" : "hidden";
  $("link-block").hidden = !S.authenticated;
  $$(".orb").forEach((o) => { o.dataset.state = o.closest("#gate") ? "disconnected" : state; });

  $("identity").textContent = S.authenticated ? (S.sub?.title || "EnderrVPN") : "by EnderrVPN";

  // gate
  const gateErr = $("gate-error");
  if (S.gateError) { gateErr.textContent = t(S.gateError); gateErr.hidden = false; }

  // update
  const upd = S.update;
  $("update-pill").hidden = !upd;
  if (upd) $("update-pill-text").textContent = t("updAvailable", upd.tag);
  $("version").textContent = S.version;
  $("about-version").textContent = "VOID-RAY " + S.version;
  const updLabel = S.checkingUpdate ? t("updChecking") : upd ? t("updAvailable", upd.tag) : t("updCheck");
  $("foot-update").textContent = updLabel;
  $("foot-update").classList.toggle("accent", !!upd);
  $("about-update").textContent = updLabel;
  $("about-update").classList.toggle("accent", !!upd);

  // settings
  applyTheme();
  setSeg($("lang-seg"), S.language);
  $("sub-url").textContent = S.subUrl;

  if (!S.authenticated) return;
  const d = derive();

  // hero
  const srv = selected();
  $("server-name").textContent = srv ? srv.name : t("noServer");
  $("server-ping").textContent = srv && srv.ping ? srv.ping + " ms" : "";
  $("hero-title").textContent = t({ disconnected: "homeOff", connecting: "homeConnecting", connected: "homeOn", disconnecting: "homeDisconnecting" }[state]);
  $("state-text").textContent = t({ disconnected: "stateOff", connecting: "stateConnecting", connected: "stateOn", disconnecting: "stateDisconnecting" }[state]);
  renderLive();
  renderSlider();

  // overview
  setSeg($("mode-seg"), S.mode, "mode");
  $("mode-hint").textContent = t(S.mode === "proxy" ? "modeProxyHint" : "modeTunHint");
  $("t-delay").textContent = S.exitFailed ? t("failed") : S.exit ? S.exit.delay + " ms" : "—";
  $("t-ip").textContent = S.exit?.ip || "—";
  $("t-country").textContent = S.exit?.country ? `${S.exit.country} (${S.exit.code})` : "—";
  $("o-value").textContent = d.value;
  $("o-unit").textContent = d.unit;
  $("o-caption").textContent = d.caption;
  for (const id of ["o-status", "g-status"]) {
    const el = $(id);
    el.className = "status " + d.status.cls;
    el.querySelector("span").textContent = d.status.label;
  }
  const days = dailyUsage(S.history);
  drawBars($("o-bars"), days);

  // servers
  $("server-count").textContent = `${S.servers.length} ${t("configs")}`;
  $("no-servers").hidden = S.servers.length > 0;
  $("servers").innerHTML = S.servers.map((s) => `
    <li class="${s.key === S.selectedKey ? "sel" : ""}" data-key="${esc(s.key)}" title="${esc(s.fullName)}">
      <div><div class="n">${esc(s.name)}</div>
        <div class="tags"><span class="tag p">${esc(s.protocol)}</span><span class="tag">${esc(s.network)}</span>${s.security ? `<span class="tag s">${esc(s.security)}</span>` : ""}${s.bypass ? '<span class="tag w">BYPASS</span>' : ""}</div></div>
      <span class="ms ${s.quality}">${s.quality === "testing" ? "···" : s.quality === "timeout" ? "timeout" : s.ping ? s.ping + " ms" : "—"}</span>
      <button class="copy" data-copy="${esc(s.key)}" title="${esc(t("copy"))}"><svg viewBox="0 0 24 24"><path d="M9 9h12v12H9zM5 15V5a2 2 0 0 1 2-2h10"/></svg></button>
    </li>`).join("");

  // subscription
  drawGauge(d);
  $("g-value").textContent = d.value;
  $("g-unit").textContent = d.unit;
  $("g-caption").textContent = d.caption;
  $("g-up").textContent = bytes(d.sub?.upload || 0);
  $("g-down").textContent = bytes(d.sub?.download || 0);
  const known = days.filter((x) => x.value !== null);
  $("hist-avg").textContent = known.length ? t("histAvg", bytes(known.reduce((a, x) => a + x.value, 0) / known.length)) : "";
  drawBars($("p-bars"), days, true);
  drawDetails(d);

  // footer
  renderFresh();
  $("fresh-dot").classList.toggle("busy", S.refreshing);
  $("accounts").textContent = S.sub?.emails?.length ? S.sub.emails.join(" · ") : S.sid ? "ID " + S.sid : "";
}

function renderLive() {
  const el = $("live-detail");
  if (S.state === "connected" && S.connectedAt) {
    const parts = [duration(Math.max(0, Math.floor((Date.now() - S.connectedAt) / 1000)))];
    if (S.exit) parts.push(S.exit.ip, S.exit.country ? `${S.exit.country}` : null);
    el.textContent = parts.filter(Boolean).join("  ·  ");
    $("t-elapsed").textContent = parts[0];
  } else {
    el.textContent = t(S.mode === "proxy" ? "modeProxyHint" : "modeTunHint");
    $("t-elapsed").textContent = "00:00";
  }
}

function renderFresh() {
  let txt;
  if (S.refreshFailed) txt = t("freshFail");
  else {
    const d = Date.now() - S.lastRefresh;
    txt = d < 90000 ? t("freshNow") : d < 3600000 ? t("freshMin", Math.round(d / 60000)) : t("freshHour", Math.round(d / 3600000));
  }
  $("fresh").textContent = txt;
}

function drawBars(box, days, tall) {
  const known = days.filter((x) => x.value !== null);
  if (!known.length) {
    box.innerHTML = tall ? `<p class="bars-note">${esc(t("histWait"))}</p>` : "";
    box.style.display = tall ? "block" : "none";
    return;
  }
  box.style.display = "";
  const peak = Math.max(...known.map((x) => x.value)) || 1;
  box.innerHTML = days.map((x) => x.value === null
    ? '<i class="void"></i>'
    : `<i style="height:${Math.max(4, (x.value / peak) * 100)}%" title="${esc(shortDate(x.day) + " · " + bytes(x.value))}"></i>`).join("");
}

function drawGauge(d) {
  const g = $("ticks");
  const N = 52;
  const tone = !d.live ? "var(--alert)" : d.ratio >= 0.9 ? "var(--warn)" : "var(--accent)";
  const lit = Math.round(d.ratio * N);
  let html = "";
  for (let i = 0; i < N; i++) {
    const a = ((150 + 240 * (i / (N - 1))) * Math.PI) / 180;
    const cls = d.unlimited ? (d.live ? "sweep" : "") : i < lit ? "on" : "";
    const delay = d.unlimited && d.live ? `animation-delay:${(-(i / N) * 5.5).toFixed(2)}s;` : "";
    html += `<line class="${cls}" style="--gauge:${tone};${delay}" x1="${(100 + 66 * Math.cos(a)).toFixed(2)}" y1="${(100 + 66 * Math.sin(a)).toFixed(2)}" x2="${(100 + 80 * Math.cos(a)).toFixed(2)}" y2="${(100 + 80 * Math.sin(a)).toFixed(2)}"/>`;
  }
  g.innerHTML = html;
}

function drawDetails(d) {
  const s = d.sub;
  const box = $("details");
  if (!s) { box.innerHTML = ""; return; }
  const rows = [[t("rowId"), S.sid || "—", "mono"]];
  if (s.emails?.length) rows.push([t(s.emails.length > 1 ? "rowAccounts" : "rowAccount"), s.emails.join(" · ")]);
  else if (s.username) rows.push([t("rowAccount"), s.username]);
  rows.push([t("rowStatus"), { chip: d.status.label, cls: d.status.cls === "ok" ? "" : d.status.cls }]);
  rows.push([t("rowDown"), bytes(s.download), "mono"], [t("rowUp"), bytes(s.upload), "mono"], [t("rowUsed"), bytes(d.used), "mono"]);
  rows.push([t("rowQuota"), d.unlimited ? "∞" : bytes(s.total), "mono"]);
  if (!d.unlimited) rows.push([t("rowLeft"), bytes(Math.max(0, s.total - d.used)), "mono"]);
  rows.push(s.lastOnline ? [t("rowOnline"), { text: stamp(s.lastOnline), hint: ago(s.lastOnline) }] : [t("rowOnline"), t("neverSeen")]);
  if (!s.expire) rows.push([t("rowExpiry"), t("noExpiry")]);
  else {
    const days = Math.ceil((s.expire * 1000 - Date.now()) / 86400000);
    const hint = days < 0 ? t("expired") : days === 0 ? t("today") : days === 1 ? t("inDay", 1) : t("inDays", days);
    rows.push([t("rowExpiry"), { text: shortDate(s.expire * 1000), hint }, days <= 3 ? "alert" : days <= 7 ? "warn" : ""]);
  }
  box.innerHTML = rows.map(([k, v, cls = ""]) => {
    let val;
    if (v && v.chip) val = `<span class="chip ${v.cls}">${esc(v.chip)}</span>`;
    else if (v && v.text) val = `<span class="v ${cls}">${esc(v.text)} <span class="h">· ${esc(v.hint)}</span></span>`;
    else val = `<span class="v ${cls}">${esc(v)}</span>`;
    return `<div class="r"><span class="k">${esc(k)}</span>${val}</div>`;
  }).join("");
}

// ================================================================== tabs

function showTab(name) {
  tab = name;
  setSeg($("tabs"), name, "tab");
  $$(".page").forEach((p) => { p.hidden = p.dataset.page !== name; });
}

// ================================================================== slide to connect

const slider = { x: 0, dragging: false, grab: 0, start: 0, moved: 0 };
const isOnSide = () => S.state === "connected" || S.state === "connecting";
const isBusy = () => S.state === "connecting" || S.state === "disconnecting";

function sliderMax() { return Math.max(0, $("slider").clientWidth - $("knob").offsetWidth); }

function placeKnob(x, animate) {
  slider.x = x;
  const knob = $("knob"), fill = $("slider-fill");
  knob.classList.toggle("settle", !!animate);
  fill.classList.toggle("settle", !!animate);
  knob.style.transform = `translateX(${x}px)`;
  fill.style.width = x + knob.offsetWidth + "px";
  const max = sliderMax() || 1;
  const away = isOnSide() ? 1 - x / max : x / max;
  $("slider-label").style.opacity = isBusy() ? 1 : Math.max(0.12, 1 - away * 1.6);
}

function renderSlider() {
  const el = $("slider");
  el.classList.toggle("on", isOnSide());
  el.classList.toggle("busy", isBusy());
  el.setAttribute("aria-checked", String(S.state === "connected"));
  $("slider-label").textContent = t(S.state === "connected" ? "slideOff" : S.state === "disconnected" ? "slideOn" : "slideWait");
  if (!slider.dragging) placeKnob(isOnSide() ? sliderMax() : 0, true);
}

function initSlider() {
  const knob = $("knob");
  knob.addEventListener("pointerdown", (e) => {
    if (isBusy()) return;
    slider.dragging = true;
    slider.grab = e.clientX;
    slider.start = slider.x;
    slider.moved = 0;
    knob.setPointerCapture(e.pointerId);
  });
  knob.addEventListener("pointermove", (e) => {
    if (!slider.dragging) return;
    const dx = e.clientX - slider.grab;
    slider.moved = Math.max(slider.moved, Math.abs(dx));
    placeKnob(Math.min(sliderMax(), Math.max(0, slider.start + dx)), false);
  });
  const release = () => {
    if (!slider.dragging) return;
    slider.dragging = false;
    const max = sliderMax();
    if (slider.moved < 4) { nudge(); return; }
    const on = isOnSide();
    if ((!on && slider.x > max * 0.82) || (on && slider.x < max * 0.18)) {
      placeKnob(on ? 0 : max, true);
      toggleConnection();
    } else {
      placeKnob(on ? max : 0, true);
    }
  };
  knob.addEventListener("pointerup", release);
  knob.addEventListener("pointercancel", release);
  $("slider").addEventListener("keydown", (e) => {
    if ((e.key === "Enter" || e.key === " ") && !isBusy()) { e.preventDefault(); toggleConnection(); }
  });
  window.addEventListener("resize", () => S && S.authenticated && placeKnob(isOnSide() ? sliderMax() : 0, false));
}

/** A click without a drag: show which way to slide. */
function nudge() {
  const rest = isOnSide() ? sliderMax() : 0;
  placeKnob(isOnSide() ? rest - 28 : rest + 28, true);
  setTimeout(() => placeKnob(rest, true), 220);
}

async function toggleConnection() {
  try {
    if (S.state === "connected") await invoke("disconnect");
    else if (S.state === "disconnected") await invoke("connect");
  } catch (err) {
    handleConnectError(err);
  }
}

async function handleConnectError(err) {
  const code = String(err);
  render();
  if (code === "needAdmin") {
    const yes = await ask({ title: t("adminTitle"), text: t("adminText"), yes: t("adminYes"), no: t("cancel") });
    if (!yes) return;
    try { await invoke("restart_elevated"); } catch (e) { toastKey(e); }
    return;
  }
  if (code === "pickServer") showTab("servers");
  toastKey(code);
}

// ================================================================== updates

async function checkUpdates() {
  try {
    const release = await invoke("check_update", { manual: true });
    if (!release) { toast(t("updLatest", S.version)); return; }
    const yes = await ask({
      title: t("updTitle", release.tag), text: t("updText", S.version),
      notes: (release.notes || "").trim().slice(0, 900), yes: t("updInstall"), no: t("later"),
    });
    if (yes) await invoke("install_update");
  } catch (err) {
    toastKey(err);
  }
}

// ================================================================== settings drawer

function openSettings() {
  const o = $("settings");
  o.hidden = false;
  o.classList.remove("closing");
  invoke("get_logs").then((lines) => { $("log").textContent = lines.join("\n"); $("log").scrollTop = 1e9; });
  requestAnimationFrame(() => { setSeg($("theme-seg"), S.theme); setSeg($("lang-seg"), S.language); });
}
function closeSettings() {
  const o = $("settings");
  if (o.hidden || o.classList.contains("closing")) return;
  o.classList.add("closing");
  setTimeout(() => { o.hidden = true; o.classList.remove("closing"); }, 250);
}

// ================================================================== wiring

async function copy(text, label) {
  try { await navigator.clipboard.writeText(text); toast(t("copied", t(label)), false); } catch { /* ignored */ }
}

function wire() {
  $("win-min").onclick = () => appWindow().minimize();
  $("win-max").onclick = () => appWindow().toggleMaximize();
  $("win-close").onclick = () => appWindow().hide();

  // gate
  const submit = async () => {
    const btn = $("gate-submit"), err = $("gate-error");
    err.hidden = true;
    document.querySelector(".field").classList.remove("bad");
    btn.disabled = true;
    $("gate-submit-txt").textContent = t("gateChecking");
    try {
      await invoke("sign_in", { url: $("gate-input").value });
      $("gate-input").value = "";
    } catch (code) {
      err.textContent = t(String(code));
      err.hidden = false;
      document.querySelector(".field").classList.add("bad");
    } finally {
      btn.disabled = false;
      $("gate-submit-txt").textContent = t("gateButton");
    }
  };
  $("gate-submit").onclick = submit;
  $("gate-input").addEventListener("keydown", (e) => { if (e.key === "Enter") submit(); });
  $("gate-input").addEventListener("input", () => { $("gate-error").hidden = true; document.querySelector(".field").classList.remove("bad"); });
  $("gate-paste").onclick = async () => {
    try { $("gate-input").value = (await navigator.clipboard.readText()).trim(); $("gate-input").focus(); } catch { /* no clipboard */ }
  };
  $("gate-support").onclick = (e) => { e.preventDefault(); invoke("open_url", { url: "https://discord.gg/BzG6FJz6zK" }); };

  // home
  $("server-chip").onclick = () => showTab("servers");
  $("plan-card").onclick = () => showTab("plan");
  $$("#tabs button").forEach((b) => { b.onclick = () => showTab(b.dataset.tab); });
  $$("#mode-seg button").forEach((b) => {
    b.onclick = async () => { try { await invoke("set_mode", { mode: b.dataset.mode }); } catch (e) { handleConnectError(e); } };
  });
  $("servers").addEventListener("click", (e) => {
    const copyBtn = e.target.closest("[data-copy]");
    if (copyBtn) {
      const s = S.servers.find((x) => x.key === copyBtn.dataset.copy);
      if (s?.rawLink) copy(s.rawLink, "lblConfig");
      return;
    }
    const li = e.target.closest("li[data-key]");
    if (li) invoke("select_server", { key: li.dataset.key });
  });
  $("ping-all").onclick = () => invoke("ping_all");
  $("copy-id").onclick = () => copy(S.sid, "lblId");

  // settings
  $("home-settings").onclick = openSettings;
  $("gate-settings").onclick = openSettings;
  $$("#settings [data-close]").forEach((el) => { el.onclick = closeSettings; });
  $$("#theme-seg button").forEach((b) => { b.onclick = () => savePrefs({ theme: b.dataset.v }); });
  $$("#lang-seg button").forEach((b) => { b.onclick = () => savePrefs({ language: b.dataset.v }); });
  const sw = $("swatches");
  ACCENTS.forEach((a, i) => {
    const b = document.createElement("button");
    b.className = "swatch";
    b.title = a.name;
    b.style.setProperty("--sw", a.dark);
    b.innerHTML = '<svg viewBox="0 0 24 24"><path d="M5 12.5l5 5 9-10.5"/></svg>';
    b.onclick = () => savePrefs({ accent: i });
    sw.appendChild(b);
  });
  $("copy-link").onclick = () => copy(S.subUrl, "lblLink");
  $("refresh").onclick = async () => { try { await invoke("refresh"); } catch (e) { toastKey(e); } };
  $("change-link").onclick = async () => { closeSettings(); await invoke("change_link"); };
  $("check-update").onclick = checkUpdates;
  $("update-pill").onclick = checkUpdates;
  $("foot-update").onclick = (e) => { e.preventDefault(); checkUpdates(); };
  const support = (e) => { e?.preventDefault(); invoke("open_url", { url: S.sub?.supportUrl || "https://discord.gg/BzG6FJz6zK" }); };
  $("about-support").onclick = support;
  $("foot-support").onclick = support;
  $("quit").onclick = () => invoke("quit");
  $("copy-logs").onclick = () => copy($("log").textContent, "lblLog");
  $("clear-logs").onclick = () => { invoke("clear_logs"); $("log").textContent = ""; };

  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") {
      if (!$("dialog").hidden) $("dlg-no").click();
      else closeSettings();
    }
  });
  initSlider();
}

// ================================================================== start

async function start() {
  wire();
  S = await invoke("get_state");
  lang = resolveLang(S.language);
  applyStrings();
  render();
  showTab(tab);
  // Tell Rust which language the tray menu should use.
  invoke("set_prefs", { theme: S.theme, accent: S.accent, language: S.language, uiLang: lang });

  listen("state", (snap) => { S = snap; render(); });
  listen("toast", (m) => toast(t(m.key, ...(m.args || [])), m.error));
  listen("log", (line) => {
    const log = $("log");
    if ($("settings").hidden) return;
    log.textContent += (log.textContent ? "\n" : "") + line;
    log.scrollTop = 1e9;
  });
  listen("updateProgress", (p) => toast(t("updDownloading", p)));
  listen("trayConnectFailed", (code) => handleConnectError(code));

  // Ticks: elapsed time and "updated x min ago".
  setInterval(() => { if (S?.authenticated) { renderLive(); renderFresh(); } }, 1000);

  requestAnimationFrame(() => { showTab(tab); appWindow().show(); appWindow().setFocus(); });
}

start();
