// Everything specific to the EnderrVPN service.

export const SERVICE_NAME = "EnderrVPN";
export const SUB_HOST = "sub.enderr.win";
export const LINK_EXAMPLE = "https://sub.enderr.win/ender/…";
export const DEFAULT_SUPPORT_URL = "https://discord.gg/BzG6FJz6zK";

/**
 * HTTPS proxies the browser can use (Xray "http" inbounds with TLS, see README).
 * Chrome cannot speak VLESS/Reality, so the extension relies on these.
 */
export const PROXIES = [
  { id: "main", name: "EnderrVPN · HTTPS", host: "proxy.enderr.win", port: 8443, scheme: "https" },
];

/**
 * Proxy credentials are derived from the subscription so each subscriber has
 * their own account on the proxy inbound:
 *   username = subscription id (the last part of the link)
 *   password = the client UUID found in the subscription's configurations
 */
export const AUTH_FROM_SUBSCRIPTION = true;

/** Never proxied. */
export const BYPASS = ["localhost", "127.0.0.1", "::1", "<local>", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"];
