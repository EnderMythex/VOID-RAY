package win.enderr.voidray.core

import org.json.JSONArray
import org.json.JSONObject

/** A single proxy endpoint parsed from the subscription. */
data class ServerProfile(
    var protocol: String = "vless",
    var remark: String = "",
    var address: String = "",
    var port: Int = 0,
    var id: String? = null,           // uuid (vless/vmess) or password (trojan/ss)
    var method: String? = null,       // shadowsocks cipher
    var flow: String? = null,
    var encryption: String? = null,
    var alterId: Int = 0,
    var vmessSecurity: String? = null,
    var network: String = "tcp",
    var security: String = "none",
    var sni: String? = null,
    var fingerprint: String? = null,
    var alpn: String? = null,
    var publicKey: String? = null,
    var shortId: String? = null,
    var spiderX: String? = null,
    var allowInsecure: Boolean = false,
    var path: String? = null,
    var host: String? = null,
    var serviceName: String? = null,
    var mode: String? = null,
    var headerType: String? = null,
    var extra: String? = null,
    /** Full Xray JSON config when the subscription delivers JSON instead of links. */
    var rawXrayJson: String? = null,
    /** Original share link, for the copy button. */
    var rawLink: String? = null,
    /** True when [FirewallBypass] rewrote this server. */
    var isBypassPatched: Boolean = false,
) {
    val key: String get() = "$protocol|$remark|$address|$port"

    fun toJson(): JSONObject = JSONObject().apply {
        put("protocol", protocol); put("remark", remark); put("address", address); put("port", port)
        putOpt("id", id); putOpt("method", method); putOpt("flow", flow); putOpt("encryption", encryption)
        put("alterId", alterId); putOpt("vmessSecurity", vmessSecurity)
        put("network", network); put("security", security)
        putOpt("sni", sni); putOpt("fingerprint", fingerprint); putOpt("alpn", alpn)
        putOpt("publicKey", publicKey); putOpt("shortId", shortId); putOpt("spiderX", spiderX)
        put("allowInsecure", allowInsecure)
        putOpt("path", path); putOpt("host", host); putOpt("serviceName", serviceName)
        putOpt("mode", mode); putOpt("headerType", headerType); putOpt("extra", extra)
        putOpt("rawXrayJson", rawXrayJson); putOpt("rawLink", rawLink)
        put("isBypassPatched", isBypassPatched)
    }

    companion object {
        fun fromJson(o: JSONObject) = ServerProfile(
            protocol = o.optString("protocol", "vless"),
            remark = o.optString("remark"),
            address = o.optString("address"),
            port = o.optInt("port"),
            id = o.str("id"), method = o.str("method"), flow = o.str("flow"), encryption = o.str("encryption"),
            alterId = o.optInt("alterId"), vmessSecurity = o.str("vmessSecurity"),
            network = o.optString("network", "tcp"), security = o.optString("security", "none"),
            sni = o.str("sni"), fingerprint = o.str("fingerprint"), alpn = o.str("alpn"),
            publicKey = o.str("publicKey"), shortId = o.str("shortId"), spiderX = o.str("spiderX"),
            allowInsecure = o.optBoolean("allowInsecure"),
            path = o.str("path"), host = o.str("host"), serviceName = o.str("serviceName"),
            mode = o.str("mode"), headerType = o.str("headerType"), extra = o.str("extra"),
            rawXrayJson = o.str("rawXrayJson"), rawLink = o.str("rawLink"),
            isBypassPatched = o.optBoolean("isBypassPatched"),
        )
    }
}

class SubscriptionInfo(
    var url: String = "",
    var title: String? = null,
    var username: String? = null,
    var status: String? = null,
    var sid: String? = null,
    var emails: List<String> = emptyList(),
    /** Unix milliseconds. */
    var lastOnline: Long? = null,
    /** False when the panel reports the account as disabled. */
    var enabled: Boolean = true,
    var upload: Long = 0,
    var download: Long = 0,
    /** 0 = unlimited. */
    var total: Long = 0,
    /** Unix seconds; null = never expires. */
    var expire: Long? = null,
    var updateIntervalHours: Int? = null,
    var supportUrl: String? = null,
    var webPageUrl: String? = null,
    var announce: String? = null,
    var fetchedAt: Long = System.currentTimeMillis(),
    var servers: List<ServerProfile> = emptyList(),
) {
    val used: Long get() = upload + download

    fun toJson(): JSONObject = JSONObject().apply {
        put("url", url); putOpt("title", title); putOpt("username", username); putOpt("status", status)
        putOpt("sid", sid); put("emails", JSONArray(emails)); putOpt("lastOnline", lastOnline)
        put("enabled", enabled); put("upload", upload); put("download", download); put("total", total)
        putOpt("expire", expire); putOpt("updateIntervalHours", updateIntervalHours)
        putOpt("supportUrl", supportUrl); putOpt("webPageUrl", webPageUrl); putOpt("announce", announce)
        put("fetchedAt", fetchedAt)
        put("servers", JSONArray().apply { servers.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject) = SubscriptionInfo(
            url = o.optString("url"), title = o.str("title"), username = o.str("username"),
            status = o.str("status"), sid = o.str("sid"),
            emails = o.optJSONArray("emails")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            lastOnline = if (o.has("lastOnline")) o.getLong("lastOnline") else null,
            enabled = o.optBoolean("enabled", true),
            upload = o.optLong("upload"), download = o.optLong("download"), total = o.optLong("total"),
            expire = if (o.has("expire")) o.getLong("expire") else null,
            updateIntervalHours = if (o.has("updateIntervalHours")) o.getInt("updateIntervalHours") else null,
            supportUrl = o.str("supportUrl"), webPageUrl = o.str("webPageUrl"), announce = o.str("announce"),
            fetchedAt = o.optLong("fetchedAt"),
            servers = o.optJSONArray("servers")?.let { a ->
                (0 until a.length()).map { ServerProfile.fromJson(a.getJSONObject(it)) }
            } ?: emptyList(),
        )
    }
}

/** optString that returns null for missing, JSON null or empty values. */
internal fun JSONObject.str(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }
