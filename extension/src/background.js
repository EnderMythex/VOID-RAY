// Service worker: owns the proxy, the subscription refresh and the credentials.

import { PROXIES, BYPASS, AUTH_FROM_SUBSCRIPTION } from "./config.js";
import { fetchSubscription, NotFoundError, recordSnapshot, clientId, normalizeLink } from "./core.js";

const REFRESH_MINUTES = 5;
const store = chrome.storage.local;

// ------------------------------------------------------------------ proxy auth
// Must be registered synchronously at start-up so Chrome can wake the worker.

const authAttempts = new Map();

chrome.webRequest.onAuthRequired.addListener(
  (details, callback) => {
    if (!details.isProxy) { callback({}); return; }
    const tries = (authAttempts.get(details.requestId) || 0) + 1;
    authAttempts.set(details.requestId, tries);
    if (tries > 2) {
      // Wrong credentials: stop the loop instead of prompting forever.
      store.set({ lastError: "auth" });
      callback({ cancel: true });
      return;
    }
    store.get(["creds"]).then(({ creds }) => {
      callback(creds ? { authCredentials: { username: creds.username, password: creds.password } } : {});
    });
  },
  { urls: ["<all_urls>"] },
  ["asyncBlocking"],
);

chrome.webRequest.onCompleted.addListener((d) => authAttempts.delete(d.requestId), { urls: ["<all_urls>"] });
chrome.webRequest.onErrorOccurred.addListener((d) => authAttempts.delete(d.requestId), { urls: ["<all_urls>"] });

chrome.proxy.onProxyError.addListener((e) => {
  store.set({ lastError: e.error || "proxy" });
});

// ------------------------------------------------------------------ connect

async function applyBadge(connected) {
  const { accentColor } = await store.get(["accentColor"]);
  await chrome.action.setBadgeText({ text: connected ? "ON" : "" });
  await chrome.action.setBadgeBackgroundColor({ color: accentColor || "#52d6bd" });
}

async function connect(proxyId) {
  const { sub, sid } = await store.get(["sub", "sid"]);
  const proxy = PROXIES.find((p) => p.id === proxyId) || PROXIES[0];
  const control = await chrome.proxy.settings.get({ incognito: false });
  if (control.levelOfControl === "controlled_by_other_extensions") throw new Error("otherExtension");
  if (control.levelOfControl === "not_controllable") throw new Error("notControllable");

  const creds = AUTH_FROM_SUBSCRIPTION ? { username: sid, password: clientId(sub) || "" } : null;
  await store.set({ creds, lastError: null });

  await chrome.proxy.settings.set({
    value: {
      mode: "fixed_servers",
      rules: {
        singleProxy: { scheme: proxy.scheme, host: proxy.host, port: proxy.port },
        bypassList: BYPASS,
      },
    },
    scope: "regular",
  });
  // WebRTC would otherwise reveal the real IP.
  try { await chrome.privacy.network.webRTCIPHandlingPolicy.set({ value: "disable_non_proxied_udp" }); } catch { /* optional */ }

  await store.set({ connected: true, connectedAt: Date.now(), proxyId: proxy.id, exit: null });
  await applyBadge(true);
  checkExit();
}

async function disconnect() {
  await chrome.proxy.settings.clear({ scope: "regular" });
  try { await chrome.privacy.network.webRTCIPHandlingPolicy.clear({}); } catch { /* optional */ }
  await store.set({ connected: false, connectedAt: null, exit: null });
  await applyBadge(false);
}

/** Proves the proxy works: fetches the public IP through it. */
async function checkExit() {
  const started = performance.now();
  try {
    const res = await fetch("https://ipwho.is/?fields=ip,country,country_code", { cache: "no-store" });
    const o = await res.json();
    await store.set({ exit: { ip: o.ip, country: o.country, code: o.country_code, delay: Math.round(performance.now() - started) } });
  } catch {
    await store.set({ exit: { failed: true } });
  }
}

// ------------------------------------------------------------------ subscription

async function refresh() {
  const { subUrl, history } = await store.get(["subUrl", "history"]);
  if (!subUrl) return { ok: false };
  await store.set({ refreshing: true });
  try {
    const info = await fetchSubscription(subUrl);
    const used = info.upload + info.download;
    await store.set({
      sub: info, sid: info.sid, history: recordSnapshot(history, used),
      lastRefresh: Date.now(), refreshFailed: false,
    });
    const { connected, creds } = await store.get(["connected", "creds"]);
    if (connected && creds && clientId(info) && creds.password !== clientId(info)) {
      await store.set({ creds: { username: info.sid, password: clientId(info) } });
    }
    return { ok: true, count: info.servers.length };
  } catch (e) {
    if (e instanceof NotFoundError) {
      await signOut("revoked");
      return { ok: false, error: "revoked" };
    }
    await store.set({ refreshFailed: true });
    return { ok: false, error: "network" };
  } finally {
    await store.set({ refreshing: false });
  }
}

async function signIn(input) {
  const n = normalizeLink(input);
  if (!n) return { ok: false, error: "format" };
  try {
    const info = await fetchSubscription(n.link);
    if (!info.servers.length) return { ok: false, error: "empty" };
    await store.set({
      subUrl: n.link, sub: info, sid: info.sid, gateError: null,
      history: recordSnapshot([], info.upload + info.download), lastRefresh: Date.now(), refreshFailed: false,
    });
    chrome.alarms.create("refresh", { periodInMinutes: REFRESH_MINUTES });
    return { ok: true, count: info.servers.length };
  } catch (e) {
    return { ok: false, error: e instanceof NotFoundError ? "notFound" : "network" };
  }
}

async function signOut(reason) {
  await disconnect();
  await chrome.alarms.clear("refresh");
  await store.remove(["subUrl", "sub", "sid", "history", "creds", "lastRefresh"]);
  await store.set({ gateError: reason || null });
}

// ------------------------------------------------------------------ wiring

chrome.alarms.onAlarm.addListener((a) => { if (a.name === "refresh") refresh(); });

chrome.runtime.onStartup.addListener(async () => {
  const { connected, subUrl } = await store.get(["connected", "subUrl"]);
  if (subUrl) chrome.alarms.create("refresh", { periodInMinutes: REFRESH_MINUTES });
  await applyBadge(!!connected);
  if (connected) checkExit();
});

chrome.runtime.onInstalled.addListener(async () => {
  const { subUrl, connected } = await store.get(["subUrl", "connected"]);
  if (subUrl) chrome.alarms.create("refresh", { periodInMinutes: REFRESH_MINUTES });
  await applyBadge(!!connected);
});

chrome.runtime.onMessage.addListener((msg, _sender, reply) => {
  const run = async () => {
    switch (msg.type) {
      case "signIn": return signIn(msg.link);
      case "signOut": await signOut(null); return { ok: true };
      case "refresh": return refresh();
      case "connect":
        try { await connect(msg.proxyId); return { ok: true }; }
        catch (e) { await disconnect(); return { ok: false, error: e.message }; }
      case "disconnect": await disconnect(); return { ok: true };
      case "checkExit": await checkExit(); return { ok: true };
      case "badge": await applyBadge(msg.connected); return { ok: true };
      default: return { ok: false };
    }
  };
  run().then(reply);
  return true; // async reply
});
