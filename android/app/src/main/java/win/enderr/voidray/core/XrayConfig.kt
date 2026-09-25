package win.enderr.voidray.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds the xray-core configuration for Android: a "tun" inbound fed with the
 * VpnService file descriptor, plus a local SOCKS inbound used by the app itself
 * (the app is excluded from the VPN) to check the exit IP.
 */
object XrayConfig {
    const val SOCKS_PORT = 10808
    const val TUN_MTU = 1500

    private val privateRanges = listOf(
        "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8",
        "169.254.0.0/16", "100.64.0.0/10", "fc00::/7", "fe80::/10", "::1/128",
    )

    fun build(p: ServerProfile, socksPort: Int = SOCKS_PORT): String {
        val root = if (p.rawXrayJson != null) {
            JSONObject(p.rawXrayJson!!)
        } else {
            JSONObject().put(
                "outbounds", JSONArray()
                    .put(buildOutbound(p))
                    .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
                    .put(JSONObject().put("tag", "block").put("protocol", "blackhole")),
            )
        }

        root.put("log", JSONObject().put("loglevel", "warning"))
        root.put(
            "inbounds", JSONArray()
                .put(
                    JSONObject()
                        .put("tag", "tun")
                        .put("protocol", "tun")
                        .put("settings", JSONObject().put("name", "xray0").put("MTU", TUN_MTU).put("userLevel", 8))
                        .put("sniffing", sniffing()),
                )
                .put(
                    JSONObject()
                        .put("tag", "socks")
                        .put("listen", "127.0.0.1")
                        .put("port", socksPort)
                        .put("protocol", "socks")
                        .put("settings", JSONObject().put("udp", true).put("auth", "noauth"))
                        .put("sniffing", sniffing()),
                ),
        )
        root.remove("api")
        root.remove("stats")

        // DNS: queries reaching the TUN are answered by Xray, resolved through the proxy.
        root.put(
            "dns", JSONObject()
                .put("servers", JSONArray().put("1.1.1.1").put("8.8.8.8"))
                .put("queryStrategy", "UseIPv4"),
        )
        val outbounds = root.getJSONArray("outbounds")
        val hasDnsOut = (0 until outbounds.length()).any { outbounds.optJSONObject(it)?.optString("tag") == "dns-out" }
        if (!hasDnsOut) outbounds.put(JSONObject().put("tag", "dns-out").put("protocol", "dns"))
        if ((0 until outbounds.length()).none { outbounds.optJSONObject(it)?.optString("tag") == "direct" })
            outbounds.put(JSONObject().put("tag", "direct").put("protocol", "freedom"))

        val routing = root.optJSONObject("routing") ?: JSONObject().put("domainStrategy", "AsIs")
        val rules = JSONArray()
            .put(
                JSONObject().put("type", "field").put("inboundTag", JSONArray().put("tun"))
                    .put("port", "53").put("outboundTag", "dns-out"),
            )
            .put(JSONObject().put("type", "field").put("ip", JSONArray(privateRanges)).put("outboundTag", "direct"))
        routing.optJSONArray("rules")?.let { existing -> for (i in 0 until existing.length()) rules.put(existing.get(i)) }
        routing.put("rules", rules)
        root.put("routing", routing)

        return root.toString(2)
    }

    private fun sniffing() = JSONObject()
        .put("enabled", true)
        .put("destOverride", JSONArray().put("http").put("tls").put("quic"))

    private fun buildOutbound(p: ServerProfile): JSONObject {
        val settings = when (p.protocol) {
            "vless" -> vnext(p, JSONObject().put("id", p.id).put("encryption", p.encryption ?: "none").apply {
                if (!p.flow.isNullOrEmpty()) put("flow", p.flow)
            })
            "vmess" -> vnext(p, JSONObject().put("id", p.id).put("alterId", p.alterId).put("security", p.vmessSecurity ?: "auto"))
            "trojan" -> JSONObject().put(
                "servers", JSONArray().put(JSONObject().put("address", p.address).put("port", p.port).put("password", p.id)),
            )
            "shadowsocks" -> JSONObject().put(
                "servers", JSONArray().put(
                    JSONObject().put("address", p.address).put("port", p.port).put("method", p.method).put("password", p.id),
                ),
            )
            else -> throw IllegalArgumentException("Unsupported protocol: ${p.protocol}")
        }
        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", p.protocol)
            .put("settings", settings)
            .put("streamSettings", buildStream(p))
    }

    private fun vnext(p: ServerProfile, user: JSONObject) = JSONObject().put(
        "vnext", JSONArray().put(JSONObject().put("address", p.address).put("port", p.port).put("users", JSONArray().put(user))),
    )

    private fun isIp(s: String) = s.matches(Regex("""^[\d.]+$""")) || s.contains(':')

    private fun buildStream(p: ServerProfile): JSONObject {
        val network = when (val n = p.network.lowercase()) {
            "raw" -> "tcp"
            "splithttp" -> "xhttp"
            else -> n
        }
        val stream = JSONObject().put("network", network)
        val serverName = p.sni ?: p.host ?: p.address.takeUnless(::isIp)

        when (p.security.lowercase()) {
            "tls", "xtls" -> {
                val tls = JSONObject()
                    .putOpt("serverName", serverName)
                    .put("fingerprint", p.fingerprint ?: "chrome")
                    .put("allowInsecure", p.allowInsecure)
                if (!p.alpn.isNullOrEmpty())
                    tls.put("alpn", JSONArray(p.alpn!!.split(',').map { it.trim() }.filter { it.isNotEmpty() }))
                stream.put("security", "tls").put("tlsSettings", tls)
            }
            "reality" -> stream.put("security", "reality").put(
                "realitySettings", JSONObject()
                    .putOpt("serverName", serverName)
                    .put("fingerprint", p.fingerprint ?: "chrome")
                    .putOpt("publicKey", p.publicKey)
                    .put("shortId", p.shortId ?: "")
                    .put("spiderX", p.spiderX ?: ""),
            )
            else -> stream.put("security", "none")
        }

        when (network) {
            "ws" -> stream.put("wsSettings", JSONObject().put("path", p.path ?: "/").put("host", p.host ?: ""))
            "httpupgrade" -> stream.put("httpupgradeSettings", JSONObject().put("path", p.path ?: "/").put("host", p.host ?: ""))
            "grpc" -> stream.put(
                "grpcSettings", JSONObject()
                    .put("serviceName", p.serviceName ?: p.path ?: "")
                    .put("multiMode", p.mode == "multi")
                    .put("authority", p.host ?: ""),
            )
            "xhttp" -> stream.put("xhttpSettings", JSONObject()
                .put("path", p.path ?: "/")
                .put("host", p.host ?: "")
                .put("mode", p.mode ?: "auto")
                .apply {
                    p.extra?.let { runCatching { put("extra", JSONObject(it)) } }
                })
            "kcp" -> stream.put(
                "kcpSettings", JSONObject()
                    .put("header", JSONObject().put("type", p.headerType ?: "none"))
                    .put("seed", p.path ?: ""),
            )
            "tcp" -> if (p.headerType == "http") stream.put(
                "tcpSettings", JSONObject().put(
                    "header", JSONObject().put("type", "http").put(
                        "request", JSONObject()
                            .put("path", JSONArray().put(p.path ?: "/"))
                            .put("headers", JSONObject().put("Host", JSONArray().put(p.host ?: ""))),
                    ),
                ),
            )
        }
        return stream
    }
}
