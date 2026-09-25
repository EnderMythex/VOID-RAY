package win.enderr.voidray.core

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

/** Parses share links (vless, vmess, trojan, ss) and subscription bodies. */
object LinkParser {

    class Result(val servers: List<ServerProfile>, val unsupported: Int)

    fun parseSubscriptionBody(body: String): Result {
        val servers = mutableListOf<ServerProfile>()
        var unsupported = 0
        var text = body.trim()
        if (text.isEmpty()) return Result(servers, 0)

        // Some panels return full Xray configs.
        if (text.startsWith("[") || text.startsWith("{")) {
            servers += parseXrayJson(text)
            if (servers.isNotEmpty()) return Result(servers, 0)
        }

        if (!text.contains("://")) {
            val decoded = Base64Util.tryDecode(text)
            if (decoded != null && decoded.contains("://")) text = decoded.trim()
        }

        for (raw in text.split('\r', '\n')) {
            val line = raw.trim()
            if (!line.contains("://")) continue
            val profile = tryParse(line)
            if (profile == null) unsupported++ else servers += profile.also { it.rawLink = line }
        }
        return Result(servers, unsupported)
    }

    fun tryParse(link: String): ServerProfile? = try {
        when (link.substringBefore("://").lowercase()) {
            "vless" -> parseVlessOrTrojan(link, "vless")
            "trojan" -> parseVlessOrTrojan(link, "trojan")
            "vmess" -> parseVmess(link)
            "ss" -> parseShadowsocks(link)
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    // ------------------------------------------------------------ vless / trojan

    private fun parseVlessOrTrojan(link: String, protocol: String): ServerProfile? {
        val u = UrlParts.parse(link) ?: return null
        if (u.userInfo.isEmpty()) return null
        val p = ServerProfile(
            protocol = protocol,
            id = u.userInfo,
            address = u.host,
            port = u.port,
            remark = u.fragment.ifBlank { "${u.host}:${u.port}" },
        )
        fillTransport(p, u.query)
        if (protocol == "trojan" && !u.query.containsKey("security")) p.security = "tls"
        return p
    }

    private fun fillTransport(p: ServerProfile, q: Map<String, String>) {
        fun get(k: String) = q[k]?.takeIf { it.isNotEmpty() }
        p.network = get("type") ?: "tcp"
        p.security = get("security") ?: "none"
        p.sni = get("sni") ?: get("peer")
        p.fingerprint = get("fp")
        p.alpn = get("alpn")
        p.publicKey = get("pbk")
        p.shortId = get("sid")
        p.spiderX = get("spx")
        p.path = get("path")
        p.host = get("host")
        p.serviceName = get("serviceName")
        p.mode = get("mode")
        p.headerType = get("headerType")
        p.flow = get("flow")
        p.encryption = get("encryption")
        p.extra = get("extra")
        p.allowInsecure = get("allowInsecure") in setOf("1", "true") || get("insecure") in setOf("1", "true")
    }

    // ------------------------------------------------------------ vmess

    private fun parseVmess(link: String): ServerProfile? {
        val payload = link.substring(8).substringBefore('#')
        val json = Base64Util.tryDecode(payload) ?: return null
        val o = JSONObject(json)
        fun s(name: String): String? = if (!o.has(name) || o.isNull(name)) null else o.get(name).toString().takeIf { it.isNotEmpty() }
        val address = s("add") ?: return null
        val port = s("port")?.toIntOrNull() ?: return null
        return ServerProfile(
            protocol = "vmess",
            remark = s("ps") ?: "$address:$port",
            address = address,
            port = port,
            id = s("id"),
            alterId = s("aid")?.toIntOrNull() ?: 0,
            vmessSecurity = s("scy") ?: "auto",
            network = s("net") ?: "tcp",
            headerType = s("type"),
            host = s("host"),
            path = s("path"),
            serviceName = if (s("net") == "grpc") s("path") else null,
            security = s("tls") ?: "none",
            sni = s("sni"),
            alpn = s("alpn"),
            fingerprint = s("fp"),
        )
    }

    // ------------------------------------------------------------ shadowsocks

    private fun parseShadowsocks(link: String): ServerProfile? {
        var rest = link.substring(5)
        var fragment = ""
        rest.indexOf('#').takeIf { it >= 0 }?.let {
            fragment = UrlParts.unescape(rest.substring(it + 1))
            rest = rest.substring(0, it)
        }
        rest = rest.substringBefore('?').trimEnd('/')

        val userInfo: String
        val hostPort: String
        val at = rest.lastIndexOf('@')
        if (at >= 0) {
            val raw = rest.substring(0, at)
            val dec = Base64Util.tryDecode(raw)
            userInfo = if (dec != null && dec.contains(':')) dec else UrlParts.unescape(raw)
            hostPort = rest.substring(at + 1)
        } else {
            val decoded = Base64Util.tryDecode(rest) ?: return null
            val at2 = decoded.lastIndexOf('@')
            if (at2 < 0) return null
            userInfo = decoded.substring(0, at2)
            hostPort = decoded.substring(at2 + 1)
        }
        val colon = userInfo.indexOf(':')
        val hp = UrlParts.splitHostPort(hostPort)
        if (colon < 0 || hp == null) return null
        return ServerProfile(
            protocol = "shadowsocks",
            method = userInfo.substring(0, colon),
            id = userInfo.substring(colon + 1),
            address = hp.first,
            port = hp.second,
            remark = fragment.ifBlank { "${hp.first}:${hp.second}" },
        )
    }

    // ------------------------------------------------------------ xray json

    private fun parseXrayJson(text: String): List<ServerProfile> {
        val configs = try {
            when (val root = if (text.startsWith("[")) JSONArray(text) else JSONObject(text)) {
                is JSONArray -> (0 until root.length()).mapNotNull { root.optJSONObject(it) }
                is JSONObject -> listOf(root)
                else -> emptyList()
            }
        } catch (e: Exception) {
            return emptyList()
        }

        val result = mutableListOf<ServerProfile>()
        for (cfg in configs) {
            val outbounds = cfg.optJSONArray("outbounds") ?: continue
            val proxy = (0 until outbounds.length()).mapNotNull { outbounds.optJSONObject(it) }
                .firstOrNull { it.optString("protocol") in setOf("vless", "vmess", "trojan", "shadowsocks") } ?: continue
            val settings = proxy.optJSONObject("settings")
            val server = settings?.optJSONArray("vnext")?.optJSONObject(0)
                ?: settings?.optJSONArray("servers")?.optJSONObject(0)
                ?: settings
            val address = server?.optString("address").orEmpty()
            val port = server?.optInt("port") ?: 0
            val stream = proxy.optJSONObject("streamSettings")
            result += ServerProfile(
                protocol = proxy.getString("protocol"),
                remark = cfg.str("remarks") ?: "$address:$port",
                address = address,
                port = port,
                network = stream?.str("network") ?: "tcp",
                security = stream?.str("security") ?: "none",
                rawXrayJson = cfg.toString(),
            )
        }
        return result
    }
}

internal class UrlParts(
    val userInfo: String,
    val host: String,
    val port: Int,
    val fragment: String,
    val query: Map<String, String>,
) {
    companion object {
        fun parse(link: String): UrlParts? {
            var rest = link.substringAfter("://")
            var fragment = ""
            rest.indexOf('#').takeIf { it >= 0 }?.let {
                fragment = unescape(rest.substring(it + 1))
                rest = rest.substring(0, it)
            }
            var queryString = ""
            rest.indexOf('?').takeIf { it >= 0 }?.let {
                queryString = rest.substring(it + 1)
                rest = rest.substring(0, it)
            }
            rest = rest.trimEnd('/')

            var user = ""
            val at = rest.lastIndexOf('@')
            if (at >= 0) {
                user = unescape(rest.substring(0, at))
                rest = rest.substring(at + 1)
            }
            val (host, port) = splitHostPort(rest) ?: return null

            val query = sortedMapOf<String, String>(String.CASE_INSENSITIVE_ORDER)
            for (pair in queryString.split('&')) {
                if (pair.isEmpty()) continue
                val eq = pair.indexOf('=')
                if (eq < 0) query[unescape(pair)] = ""
                else query[unescape(pair.substring(0, eq))] = unescape(pair.substring(eq + 1))
            }
            return UrlParts(user, host, port, fragment, query)
        }

        fun splitHostPort(value: String): Pair<String, Int>? {
            if (value.startsWith("[")) {
                val close = value.indexOf(']')
                if (close < 0 || value.length <= close + 2) return null
                val port = value.substring(close + 2).toIntOrNull() ?: return null
                return value.substring(1, close) to port
            }
            val colon = value.lastIndexOf(':')
            if (colon <= 0) return null
            val port = value.substring(colon + 1).toIntOrNull() ?: return null
            return value.substring(0, colon) to port
        }

        /** Percent-decoding that keeps '+' as is (share links are not form-encoded). */
        fun unescape(s: String): String = try {
            URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
        } catch (e: Exception) {
            s
        }
    }
}
