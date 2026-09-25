import { PROXIES, DEFAULT_SUPPORT_URL, SERVICE_NAME } from "./config.js";
import { dailyUsage, HIST_DAYS } from "./core.js";

const store = chrome.storage.local;
const $ = (id) => document.getElementById(id);
const send = (msg) => chrome.runtime.sendMessage(msg);

// ================================================================== i18n

const T = {
  en: {
    gateTitle: "Connect your subscription",
    gateText: "Paste the subscription link you received from EnderrVPN. Only sub.enderr.win links are accepted.",
    gateLabel: "Subscription link", gateButton: "Continue", gateChecking: "Checking…",
    gateHelp: "No subscription yet?", gateContact: "Contact support",
    errFormat: "This is not an EnderrVPN link. It should look like https://sub.enderr.win/ender/…",
    errNotFound: "Subscription not found. Check your link.",
    errNetwork: "Can't reach sub.enderr.win. Check your internet connection.",
    errEmpty: "This subscription has no server.",
    errRevoked: "Your subscription is no longer valid. Enter a new link.",
    connection: "Browser protection", stateOff: "Not protected", stateOn: "Protected", stateConnecting: "Connecting…",
    hintOff: "Click the button to protect Chrome", hintOn: "Only web pages go through the VPN",
    duration: "duration", publicIp: "public IP", location: "location", realDelay: "latency",
    scopeNote: "Only Chrome's web pages use the proxy. Other apps are not affected.",
    upload: "upload", download: "download", history: "Daily usage", histAvg: "{0}/day",
    histWait: "Your daily usage will appear here.",
    capUsed: "used · no limit", capLeft: "left of {0}",
    stActive: "Active", stUnlimited: "Unlimited", stAlmost: "Almost out", stQuota: "Quota reached",
    stExpired: "Expired", stDisabled: "Disabled",
    link: "Subscription link", copy: "Copy", details: "Subscription details",
    rowId: "Subscription ID", rowAccount: "Account", rowAccounts: "Accounts", rowStatus: "Status",
    rowDown: "Downloaded", rowUp: "Uploaded", rowUsed: "Data used", rowQuota: "Total quota", rowLeft: "Data left",
    rowOnline: "Last online", rowExpiry: "Expiry", noExpiry: "No expiry", expired: "expired", today: "today",
    inDay: "in {0} day", inDays: "in {0} days", neverSeen: "Never seen", justNow: "just now",
    minsAgo: "{0} min ago", hoursAgo: "{0} h ago", daysAgo: "{0} days ago",
    support: "Support", freshNow: "updated just now", freshMin: "updated {0} min ago", freshHour: "updated {0} h ago",
    freshFail: "update failed", copied: "Link copied", refreshed: "Subscription updated · {0} server(s)",
    loaded: "Subscription loaded · {0} server(s)", failed: "failed",
    errAuth: "The proxy refused your account. Contact support.",
    errProxy: "Can't reach the proxy server. Try again later.",
    errOther: "Another extension controls Chrome's proxy. Disable it and try again.",
    errNoTraffic: "Connected, but no page loads through the proxy.",
    themeAuto: "System theme", themeDark: "Dark theme", themeLight: "Light theme", langLabel: "Language",
    accentLabel: "Accent colour", refresh: "Refresh", changeLink: "Use another link",
  },
  fr: {
    gateTitle: "Connecte ton abonnement",
    gateText: "Colle le lien d'abonnement reçu d'EnderrVPN. Seuls les liens sub.enderr.win sont acceptés.",
    gateLabel: "Lien d'abonnement", gateButton: "Continuer", gateChecking: "Vérification…",
    gateHelp: "Pas encore d'abonnement ?", gateContact: "Contacter l'assistance",
    errFormat: "Ce n'est pas un lien EnderrVPN. Il doit ressembler à https://sub.enderr.win/ender/…",
    errNotFound: "Abonnement introuvable. Vérifie ton lien.",
    errNetwork: "Impossible de joindre sub.enderr.win. Vérifie ta connexion internet.",
    errEmpty: "Cet abonnement ne contient aucun serveur.",
    errRevoked: "Ton abonnement n'est plus valide. Entre un nouveau lien.",
    connection: "Protection du navigateur", stateOff: "Non protégé", stateOn: "Protégé", stateConnecting: "Connexion…",
    hintOff: "Clique sur le bouton pour protéger Chrome", hintOn: "Seules les pages web passent par le VPN",
    duration: "durée", publicIp: "IP publique", location: "localisation", realDelay: "latence",
    scopeNote: "Seules les pages web de Chrome passent par le proxy. Les autres applications ne sont pas concernées.",
    upload: "envoyé", download: "reçu", history: "Consommation par jour", histAvg: "{0}/jour",
    histWait: "Ta consommation quotidienne apparaîtra ici.",
    capUsed: "consommés · sans limite", capLeft: "restants sur {0}",
    stActive: "Actif", stUnlimited: "Illimité", stAlmost: "Presque épuisé", stQuota: "Quota atteint",
    stExpired: "Expiré", stDisabled: "Désactivé",
    link: "Lien d'abonnement", copy: "Copier", details: "Détails de l'abonnement",
    rowId: "Identifiant", rowAccount: "Compte", rowAccounts: "Comptes", rowStatus: "État",
    rowDown: "Reçu", rowUp: "Envoyé", rowUsed: "Trafic utilisé", rowQuota: "Quota total", rowLeft: "Trafic restant",
    rowOnline: "Dernière connexion", rowExpiry: "Expiration", noExpiry: "Aucune expiration", expired: "expiré",
    today: "aujourd'hui", inDay: "dans {0} jour", inDays: "dans {0} jours", neverSeen: "Jamais vu",
    justNow: "à l'instant", minsAgo: "il y a {0} min", hoursAgo: "il y a {0} h", daysAgo: "il y a {0} jours",
    support: "Assistance", freshNow: "mis à jour à l'instant", freshMin: "mis à jour il y a {0} min",
    freshHour: "mis à jour il y a {0} h", freshFail: "mise à jour impossible", copied: "Lien copié",
    refreshed: "Abonnement mis à jour · {0} serveur(s)", loaded: "Abonnement chargé · {0} serveur(s)", failed: "échec",
    errAuth: "Le proxy a refusé ton compte. Contacte l'assistance.",
    errProxy: "Impossible de joindre le serveur proxy. Réessaie plus tard.",
    errOther: "Une autre extension contrôle le proxy de Chrome. Désactive-la puis réessaie.",
    errNoTraffic: "Connecté, mais aucune page ne passe par le proxy.",
    themeAuto: "Thème automatique", themeDark: "Thème sombre", themeLight: "Thème clair", langLabel: "Langue",
    accentLabel: "Couleur d'accent", refresh: "Actualiser", changeLink: "Changer de lien",
  },
};

let lang = "en";
let langMode = "auto";
function t(key, ...args) {
  let s = (T[lang] && T[lang][key]) || T.en[key] || key;
  args.forEach((a, i) => { s = s.replace(`{${i}}`, a); });
  return s;
}
const locale = () => (lang === "fr" ? "fr-FR" : "en-GB");

// ================================================================== theme & accent

const ACCENTS = [
  { name: "teal", dark: "#52d6bd", light: "#0f9d86" },
  { name: "indigo", dark: "#7f97ff", light: "#3f55cf" },
  { name: "violet", dark: "#b085ff", light: "#7440d4" },
  { name: "rose", dark: "#ff8aab", light: "#cf3560" },
  { name: "amber", dark: "#f0b95f", light: "#a5720c" },
  { name: "lime", dark: "#9ede5a", light: "#4d8c19" },
];
const ICONS = {
  auto: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="8"/><path d="M12 4a8 8 0 0 0 0 16z" fill="currentColor" stroke="none"/></svg>',
  dark: '<svg viewBox="0 0 24 24"><path d="M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5z"/></svg>',
  light: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="4.2"/><path d="M12 2v2.4M12 19.6V22M2 12h2.4M19.6 12H22M4.9 4.9l1.7 1.7M17.4 17.4l1.7 1.7M19.1 4.9l-1.7 1.7M6.6 17.4l-1.7 1.7"/></svg>',
};
const media = window.matchMedia("(prefers-color-scheme: light)");
let themeMode = "auto";
let accentIndex = 0;
const resolvedTheme = () => (themeMode === "auto" ? (media.matches ? "light" : "dark") : themeMode);

function applyTheme() {
  document.documentElement.dataset.theme = resolvedTheme();
  $("theme-btn").innerHTML = ICONS[themeMode];
  $("theme-btn").title = t(themeMode === "auto" ? "themeAuto" : themeMode === "dark" ? "themeDark" : "themeLight");
  const preset = ACCENTS[accentIndex] || ACCENTS[0];
  const color = resolvedTheme() === "light" ? preset.light : preset.dark;
  document.documentElement.style.setProperty("--accent", color);
  document.querySelectorAll("#palette .swatch").forEach((s, i) => s.setAttribute("aria-pressed", String(i === accentIndex)));
  store.set({ accentColor: color });
}

function buildPalette() {
  const box = $("palette");
  ACCENTS.forEach((preset, i) => {
    const s = document.createElement("button");
    s.className = "swatch";
    s.type = "button";
    s.title = preset.name;
    s.style.setProperty("--sw", preset.dark);
    s.addEventListener("click", async () => {
      accentIndex = i;
      await store.set({ accent: i });
      applyTheme();
      box.classList.remove("open");
      const { connected } = await store.get("connected");
      send({ type: "badge", connected: !!connected });
    });
    box.appendChild(s);
  });
  $("palette-btn").addEventListener("click", (e) => { e.stopPropagation(); box.classList.toggle("open"); });
  document.addEventListener("click", (e) => { if (!box.contains(e.target)) box.classList.remove("open"); });
}

function applyLang() {
  lang = langMode !== "auto" ? langMode : /^fr/i.test(navigator.language) ? "fr" : "en";
  document.documentElement.lang = lang;
  document.querySelectorAll("[data-i18n]").forEach((el) => { el.textContent = t(el.dataset.i18n); });
  $("lang-btn").querySelector(".lang").textContent = langMode === "auto" ? "A/" + lang.toUpperCase() : lang.toUpperCase();
  $("lang-btn").title = t("langLabel");
  $("palette-btn").title = t("accentLabel");
  $("refresh-btn").title = t("refresh");
  $("change-btn").title = t("changeLink");
}

// ================================================================== formatting

const UNITS_EN = ["B", "KB", "MB", "GB", "TB"];
const UNITS_FR = ["o", "Ko", "Mo", "Go", "To"];
function bytes(n) {
  const units = lang === "fr" ? UNITS_FR : UNITS_EN;
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

let toastTimer;
function toast(msg, error = false) {
  const el = $("toast");
  el.textContent = msg;
  el.classList.toggle("error", error);
  el.classList.add("show");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove("show"), 3200);
}

// ================================================================== state → view

let S = {};

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
  return { sub, used, unlimited, ratio, live, status };
}

function drawGauge(d) {
  const g = $("ticks");
  g.innerHTML = "";
  const N = 52;
  const tone = !d.live ? "var(--alert)" : d.ratio >= 0.9 ? "var(--warn)" : "var(--accent)";
  const lit = Math.round(d.ratio * N);
  for (let i = 0; i < N; i++) {
    const a = ((150 + 240 * (i / (N - 1))) * Math.PI) / 180;
    const line = document.createElementNS("http://www.w3.org/2000/svg", "line");
    line.setAttribute("class", "tick");
    line.setAttribute("x1", (100 + 66 * Math.cos(a)).toFixed(2));
    line.setAttribute("y1", (100 + 66 * Math.sin(a)).toFixed(2));
    line.setAttribute("x2", (100 + 80 * Math.cos(a)).toFixed(2));
    line.setAttribute("y2", (100 + 80 * Math.sin(a)).toFixed(2));
    line.style.setProperty("--gauge", tone);
    if (d.unlimited) {
      if (d.live) { line.classList.add("sweep"); line.style.animationDelay = (-(i / N) * 5.5).toFixed(2) + "s"; }
    } else if (i < lit) {
      line.classList.add("on");
    }
    g.appendChild(line);
  }
  const shown = !d.sub ? "—" : d.unlimited ? bytes(d.used) : bytes(Math.max(0, d.sub.total - d.used));
  const [num, ...unit] = shown.split(" ");
  const value = $("gauge-value");
  value.textContent = num;
  if (unit.length) {
    const u = document.createElementNS("http://www.w3.org/2000/svg", "tspan");
    u.setAttribute("class", "unit");
    u.setAttribute("dx", "4");
    u.textContent = unit.join(" ");
    value.appendChild(u);
  }
  $("gauge-caption").textContent = d.unlimited ? t("capUsed") : t("capLeft", bytes(d.sub.total));
}

function drawHistory() {
  const body = $("hist-body");
  const days = dailyUsage(S.history || []);
  const known = days.filter((x) => x.value !== null);
  if (!known.length) {
    body.innerHTML = `<div class="hist-note">${t("histWait")}</div>`;
    $("hist-avg").textContent = "";
    return;
  }
  const peak = Math.max(...known.map((x) => x.value)) || 1;
  $("hist-avg").textContent = t("histAvg", bytes(known.reduce((s, x) => s + x.value, 0) / known.length));
  const W = 300, H = 46, gap = 3, bw = (W - gap * (HIST_DAYS - 1)) / HIST_DAYS;
  let svg = `<svg class="hist-chart" viewBox="0 0 ${W} ${H}" preserveAspectRatio="none">`;
  days.forEach((x, i) => {
    const px = (i * (bw + gap)).toFixed(2);
    if (x.value === null) { svg += `<rect class="bar void" x="${px}" y="${H - 2}" width="${bw.toFixed(2)}" height="2"></rect>`; return; }
    const h = Math.max(2, (x.value / peak) * H);
    svg += `<rect class="bar" x="${px}" y="${(H - h).toFixed(2)}" width="${bw.toFixed(2)}" height="${h.toFixed(2)}"><title>${shortDate(x.day)} · ${bytes(x.value)}</title></rect>`;
  });
  body.innerHTML = svg + "</svg>";
}

function drawDetails(d) {
  const box = $("details");
  box.innerHTML = "";
  const s = d.sub;
  if (!s) return;
  const rows = [[t("rowId"), S.sid || "—", "mono"]];
  if (s.emails?.length) rows.push([t(s.emails.length > 1 ? "rowAccounts" : "rowAccount"), s.emails.join(" · ")]);
  rows.push([t("rowStatus"), { chip: d.status }]);
  rows.push([t("rowDown"), bytes(s.download), "mono"], [t("rowUp"), bytes(s.upload), "mono"], [t("rowUsed"), bytes(d.used), "mono"]);
  rows.push([t("rowQuota"), d.unlimited ? "∞" : bytes(s.total), "mono"]);
  if (!d.unlimited) rows.push([t("rowLeft"), bytes(Math.max(0, s.total - d.used)), "mono"]);
  rows.push([t("rowOnline"), s.lastOnline ? { text: stamp(s.lastOnline), hint: ago(s.lastOnline) } : t("neverSeen")]);
  if (!s.expire) rows.push([t("rowExpiry"), t("noExpiry")]);
  else {
    const days = Math.ceil((s.expire * 1000 - Date.now()) / 86400000);
    const hint = days < 0 ? t("expired") : days === 0 ? t("today") : days === 1 ? t("inDay", 1) : t("inDays", days);
    rows.push([t("rowExpiry"), { text: shortDate(s.expire * 1000), hint }, days <= 3 ? "alert" : days <= 7 ? "warn" : ""]);
  }
  for (const [k, v, cls] of rows) {
    const r = document.createElement("div");
    r.className = "r";
    const kEl = document.createElement("span");
    kEl.className = "k";
    kEl.textContent = k;
    const vEl = document.createElement("span");
    vEl.className = "v" + (cls ? " " + cls : "");
    if (typeof v === "string") vEl.textContent = v;
    else if (v.chip) {
      const c = document.createElement("span");
      c.className = "chip " + (v.chip.cls === "ok" ? "" : v.chip.cls);
      c.textContent = v.chip.label;
      vEl.appendChild(c);
    } else {
      vEl.textContent = v.text;
      const h = document.createElement("span");
      h.className = "hint";
      h.textContent = " · " + v.hint;
      vEl.appendChild(h);
    }
    vEl.title = vEl.textContent;
    r.append(kEl, vEl);
    box.appendChild(r);
  }
}

function drawConnection() {
  const connected = !!S.connected;
  const busy = !!S.busy;
  $("power").className = "power" + (connected ? " on" : "") + (busy ? " busy" : "");
  $("state").textContent = t(busy ? "stateConnecting" : connected ? "stateOn" : "stateOff");
  $("state").className = "state" + (connected ? " on" : "");
  $("hint").textContent = t(connected ? "hintOn" : "hintOff");
  const exit = S.exit;
  $("ip").textContent = exit?.ip || "—";
  $("country").textContent = exit?.country ? `${exit.country} (${exit.code})` : "—";
  $("delay").textContent = exit?.failed ? t("failed") : exit?.delay ? exit.delay + " ms" : "—";
  $("elapsed").textContent = connected && S.connectedAt ? duration(Math.floor((Date.now() - S.connectedAt) / 1000)) : "00:00";
}

function drawFreshness() {
  const el = $("fresh-txt");
  $("fresh").classList.toggle("busy", !!S.refreshing);
  if (S.refreshFailed) { el.textContent = t("freshFail"); return; }
  const diff = Date.now() - (S.lastRefresh || Date.now());
  el.textContent = diff < 90000 ? t("freshNow") : diff < 3600000 ? t("freshMin", Math.round(diff / 60000)) : t("freshHour", Math.round(diff / 3600000));
}

function render() {
  const signedIn = !!S.subUrl;
  $("gate").hidden = signedIn;
  $("dash").hidden = !signedIn;
  const d = derive();
  $("title").textContent = signedIn ? d.sub?.title || SERVICE_NAME : "VOID-RAY";
  $("subtitle").textContent = signedIn ? S.sid || "" : "by EnderrVPN";
  const support = d.sub?.supportUrl || DEFAULT_SUPPORT_URL;
  $("support").href = support;
  $("gate-support").href = DEFAULT_SUPPORT_URL;

  if (!signedIn) {
    const err = S.gateError === "revoked" ? t("errRevoked") : null;
    if (err && $("gate-error").hidden) { $("gate-error").textContent = err; $("gate-error").hidden = false; }
    return;
  }

  drawConnection();
  drawGauge(d);
  const st = $("status");
  st.className = "status " + d.status.cls;
  st.querySelector(".txt").textContent = d.status.label;
  $("up").textContent = bytes(d.sub?.upload || 0);
  $("down").textContent = bytes(d.sub?.download || 0);
  drawHistory();
  drawDetails(d);
  $("sub-url").textContent = S.subUrl;
  $("foot-id").textContent = d.sub?.emails?.length ? d.sub.emails.join(" · ") : "ID " + (S.sid || "");
  drawFreshness();

  const sel = $("proxy-select");
  if (!sel.options.length) {
    for (const p of PROXIES) sel.add(new Option(p.name, p.id));
  }
  sel.value = S.proxyId || PROXIES[0].id;

  if (S.lastError && S.lastError !== S.shownError) {
    S.shownError = S.lastError;
    toast(t(S.lastError === "auth" ? "errAuth" : "errProxy"), true);
  }
}

// ================================================================== actions

$("gate-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const btn = $("gate-submit");
  btn.disabled = true;
  $("gate-submit-txt").textContent = t("gateChecking");
  $("gate-error").hidden = true;
  const res = await send({ type: "signIn", link: $("gate-input").value });
  btn.disabled = false;
  $("gate-submit-txt").textContent = t("gateButton");
  if (!res.ok) {
    const key = { format: "errFormat", notFound: "errNotFound", empty: "errEmpty" }[res.error] || "errNetwork";
    $("gate-error").textContent = t(key);
    $("gate-error").hidden = false;
    return;
  }
  $("gate-input").value = "";
  toast(t("loaded", res.count));
});

$("gate-paste").addEventListener("click", async () => {
  try { $("gate-input").value = (await navigator.clipboard.readText()).trim(); } catch { $("gate-input").focus(); }
});

$("power").addEventListener("click", async () => {
  if (S.busy) return;
  if (S.connected) { await send({ type: "disconnect" }); return; }
  S.busy = true;
  drawConnection();
  const res = await send({ type: "connect", proxyId: $("proxy-select").value });
  S.busy = false;
  drawConnection();
  if (!res.ok) toast(t(res.error === "otherExtension" ? "errOther" : "errProxy"), true);
});

$("proxy-select").addEventListener("change", async () => {
  if (S.connected) await send({ type: "connect", proxyId: $("proxy-select").value });
  else await store.set({ proxyId: $("proxy-select").value });
});

$("refresh-btn").addEventListener("click", async () => {
  const res = await send({ type: "refresh" });
  if (res.ok) toast(t("refreshed", res.count));
  else if (res.error === "network") toast(t("errNetwork"), true);
});

$("change-btn").addEventListener("click", () => send({ type: "signOut" }));

$("copy-btn").addEventListener("click", async () => {
  try { await navigator.clipboard.writeText(S.subUrl); toast(t("copied")); } catch { /* ignore */ }
});

$("lang-btn").addEventListener("click", async () => {
  langMode = { auto: "en", en: "fr", fr: "auto" }[langMode];
  await store.set({ lang: langMode });
  applyLang();
  applyTheme();
  render();
});

$("theme-btn").addEventListener("click", async () => {
  themeMode = { auto: "dark", dark: "light", light: "auto" }[themeMode];
  await store.set({ theme: themeMode });
  applyTheme();
});

media.addEventListener("change", () => { if (themeMode === "auto") applyTheme(); });

// ================================================================== start

chrome.storage.onChanged.addListener(async (changes, area) => {
  if (area !== "local") return;
  S = { ...S, ...(await store.get(null)), busy: S.busy, shownError: S.shownError };
  render();
});

(async () => {
  S = await store.get(null);
  themeMode = ["auto", "dark", "light"].includes(S.theme) ? S.theme : "auto";
  accentIndex = Number.isInteger(S.accent) ? S.accent : 0;
  langMode = ["auto", "en", "fr"].includes(S.lang) ? S.lang : "auto";
  buildPalette();
  applyLang();
  applyTheme();
  S.shownError = S.lastError;
  render();
  if (S.subUrl && Date.now() - (S.lastRefresh || 0) > 60000) send({ type: "refresh" });
  if (S.connected && !S.exit) send({ type: "checkExit" });
  setInterval(() => { if (S.connected) drawConnection(); drawFreshness(); }, 1000);
})();
