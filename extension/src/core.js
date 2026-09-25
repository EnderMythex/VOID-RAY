// Subscription logic shared by the service worker and the popup (no DOM here).

import { SUB_HOST } from "./config.js";

// ------------------------------------------------------------------ link gate

const LINK_PATTERN = /^https:\/\/sub\.enderr\.win\/ender\/([A-Za-z0-9_-]{4,64})\/?$/i;

/** Accepts only EnderrVPN subscription links. Returns { link, sid } or null. */
export function normalizeLink(input) {
  const m = LINK_PATTERN.exec(String(input || "").trim());
  if (!m) return null;
  return { link: `https://${SUB_HOST}/ender/${m[1]}`, sid: m[1] };
}

// ------------------------------------------------------------------ helpers

export function b64decode(input) {
  let t = String(input).replace(/\s+/g, "").replace(/-/g, "+").replace(/_/g, "/").replace(/=+$/, "");
  if (t.length % 4 === 1) return null;
  while (t.length % 4) t += "=";
  try {
    const bin = atob(t);
    const bytes = Uint8Array.from(bin, (c) => c.charCodeAt(0));
    return new TextDecoder().decode(bytes);
  } catch {
    return null;
  }
}

function decodeHeader(value) {
  if (!value) return null;
  const v = value.trim();
  if (!v) return null;
  return /^base64:/i.test(v) ? b64decode(v.slice(7)) ?? v : v;
}

function htmlDecode(s) {
  return s.replace(/&amp;/g, "&").replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&lt;/g, "<").replace(/&gt;/g, ">");
}

export class NotFoundError extends Error {}

// ------------------------------------------------------------------ servers

const EMAIL_SUFFIX = /\s*[-–—|]\s*[^\s@]+@[^\s@]+\.[A-Za-z]{2,}\s*$/;

/** Minimal parse of share links: enough to list them and find the client id. */
export function parseLinks(body) {
  let text = String(body || "").trim();
  if (text && !text.includes("://")) {
    const decoded = b64decode(text);
    if (decoded && decoded.includes("://")) text = decoded.trim();
  }
  const servers = [];
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    const scheme = line.split("://")[0].toLowerCase();
    if (!["vless", "vmess", "trojan", "ss"].includes(scheme)) continue;
    try {
      if (scheme === "vmess") {
        const o = JSON.parse(b64decode(line.slice(8).split("#")[0]) || "{}");
        servers.push({ protocol: "vmess", id: o.id, name: o.ps || o.add, network: o.net || "tcp", security: o.tls || "none", link: line });
        continue;
      }
      const hash = line.indexOf("#");
      const name = hash >= 0 ? decodeURIComponent(line.slice(hash + 1)) : "";
      const noFrag = hash >= 0 ? line.slice(0, hash) : line;
      const q = new URLSearchParams(noFrag.includes("?") ? noFrag.slice(noFrag.indexOf("?") + 1) : "");
      const auth = noFrag.slice(noFrag.indexOf("://") + 3).split("@")[0];
      servers.push({
        protocol: scheme === "ss" ? "shadowsocks" : scheme,
        id: scheme === "ss" ? null : decodeURIComponent(auth),
        name: name || noFrag,
        network: q.get("type") || "tcp",
        security: q.get("security") || (scheme === "trojan" ? "tls" : "none"),
        link: line,
      });
    } catch {
      /* skip malformed line */
    }
  }
  for (const s of servers) s.name = (s.name || "").replace(EMAIL_SUFFIX, "").trim() || s.name;
  return servers;
}

// ------------------------------------------------------------------ fetch

/**
 * Reads a 3x-ui subscription: the plain answer (headers + base64 links) and the
 * HTML page served to browsers (account e-mails, last online, support, state).
 */
export async function fetchSubscription(link) {
  const res = await fetch(link, { cache: "no-store", headers: { Accept: "*/*" } });
  if ([400, 403, 404].includes(res.status)) throw new NotFoundError(`HTTP ${res.status}`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);

  const info = {
    url: link, title: null, upload: 0, download: 0, total: 0, expire: null,
    supportUrl: null, webPageUrl: null, announce: null, updateIntervalHours: null,
    sid: link.replace(/\/+$/, "").split("/").pop(), emails: [], lastOnline: null, enabled: true,
    servers: [], fetchedAt: Date.now(),
  };

  const userInfo = decodeHeader(res.headers.get("subscription-userinfo"));
  if (userInfo) {
    for (const part of userInfo.split(";")) {
      const [k, v] = part.split("=").map((x) => x && x.trim());
      const n = Number(v);
      if (!Number.isFinite(n)) continue;
      if (k === "upload") info.upload = n;
      else if (k === "download") info.download = n;
      else if (k === "total") info.total = n;
      else if (k === "expire") info.expire = n > 0 ? n : null;
    }
  }
  info.title = decodeHeader(res.headers.get("profile-title"));
  info.supportUrl = decodeHeader(res.headers.get("support-url"));
  info.webPageUrl = decodeHeader(res.headers.get("profile-web-page-url"));
  info.announce = decodeHeader(res.headers.get("announce"));
  const interval = Number(decodeHeader(res.headers.get("profile-update-interval")));
  if (interval > 0) info.updateIntervalHours = interval;

  info.servers = parseLinks(await res.text());

  try {
    const page = await fetch(link, { cache: "no-store", headers: { Accept: "text/html,application/xhtml+xml" } });
    if (page.ok) {
      const html = await page.text();
      const block = /<div[^>]*\bid="data"[^>]*>/i.exec(html);
      if (block) {
        const data = {};
        for (const m of block[0].matchAll(/data-([a-z-]+)="([^"]*)"/gi)) data[m[1].toLowerCase()] = htmlDecode(m[2]).trim();
        const num = (k) => Number(data[k]) || 0;
        info.enabled = data.enabled !== "0";
        if (data.sid) info.sid = data.sid;
        info.supportUrl = info.supportUrl || data.support || null;
        if (num("last-online") > 0) info.lastOnline = num("last-online");
        if (!info.upload && !info.download) { info.upload = num("upload-byte"); info.download = num("download-byte"); }
        if (!info.total) info.total = num("total-byte");
        if (!info.expire && num("expire") > 0) info.expire = num("expire");
        info.emails = [...new Set([...html.matchAll(/data-mail="([^"]*)"/gi)].map((m) => htmlDecode(m[1]).trim()).filter(Boolean))];
        if (!info.servers.length) {
          info.servers = parseLinks([...html.matchAll(/data-link="([^"]*)"/gi)].map((m) => htmlDecode(m[1])).join("\n"));
        }
      }
    }
  } catch {
    /* the page is optional */
  }
  return info;
}

/** The client UUID shared by every configuration of this subscriber. */
export function clientId(info) {
  return info?.servers?.find((s) => s.id)?.id || null;
}

// ------------------------------------------------------------------ usage history

export const HIST_DAYS = 14;

export function recordSnapshot(history, used, now = Date.now()) {
  const hist = Array.isArray(history) ? history.slice() : [];
  const last = hist[hist.length - 1];
  if (last && now - last[0] < 600000 && last[1] === used) return hist;
  hist.push([now, used]);
  const cutoff = now - (HIST_DAYS + 2) * 86400000;
  let trimmed = hist.filter((e) => e[0] >= cutoff);
  if (trimmed.length > 400) trimmed = trimmed.slice(trimmed.length - 400);
  return trimmed;
}

const dayKey = (ms) => { const d = new Date(ms); return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`; };

export function dailyUsage(hist) {
  const perDay = new Map();
  for (const [ts, used] of hist || []) {
    const k = dayKey(ts);
    const cur = perDay.get(k);
    if (!cur) perDay.set(k, { first: used, last: used });
    else { cur.last = Math.max(cur.last, used); cur.first = Math.min(cur.first, used); }
  }
  const out = [];
  const today = new Date(); today.setHours(0, 0, 0, 0);
  let prevLast = null;
  for (let i = HIST_DAYS - 1; i >= 0; i--) {
    const day = today.getTime() - i * 86400000;
    const e = perDay.get(dayKey(day));
    if (!e) { out.push({ day, value: null }); continue; }
    let delta;
    if (prevLast === null) delta = e.last - e.first;
    else if (e.last < prevLast) delta = e.last;
    else delta = e.last - prevLast;
    prevLast = e.last;
    out.push({ day, value: Math.max(0, delta) });
  }
  return out;
}
