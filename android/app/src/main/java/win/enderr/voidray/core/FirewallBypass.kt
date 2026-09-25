package win.enderr.voidray.core

/**
 * The "Firewall Bypass" server works only when it goes through Cloudflare over
 * TLS. The subscription ships it as plain WebSocket, so it is rewritten on
 * import with the settings that get through restrictive networks.
 */
object FirewallBypass {
    const val CLEAN_IP = "172.67.186.245"
    const val PORT = 443
    const val DOMAIN = "xray.enderr.win"
    const val DEFAULT_PATH = "/x7k2p"
    const val FINGERPRINT = "chrome"
    const val ALPN = "http/1.1"

    private val marker = Regex("""firewall[\s_-]*bypass|bypass[\s_-]*firewall""", RegexOption.IGNORE_CASE)

    fun isBypassServer(p: ServerProfile) =
        p.rawXrayJson == null && p.protocol == "vless" && marker.containsMatchIn(p.remark)

    /** Rewrites every bypass server in place. Returns how many were changed. */
    fun apply(servers: List<ServerProfile>): Int {
        var count = 0
        for (p in servers.filter(::isBypassServer)) {
            p.address = CLEAN_IP
            p.port = PORT
            p.network = "ws"
            p.host = DOMAIN
            p.path = p.path?.takeIf { it.isNotBlank() } ?: DEFAULT_PATH
            p.security = "tls"
            p.sni = DOMAIN
            p.fingerprint = FINGERPRINT
            p.alpn = ALPN
            p.allowInsecure = false
            p.flow = null
            p.encryption = "none"
            p.publicKey = null
            p.shortId = null
            p.isBypassPatched = true
            count++
        }
        return count
    }
}
