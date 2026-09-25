package win.enderr.voidray.core

import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a subscription link and extracts the server list plus the account
 * information (traffic, expiry, title…) exposed by 3x-ui style panels.
 */
class SubscriptionFetcher(private val log: (String) -> Unit = {}) {

    /** The panel does not know the link (wrong or deleted subscription). */
    class NotFoundException(message: String) : Exception(message)

    private val userAgents = listOf("VoidRay-Android/1.0", "v2rayNG/1.10.0", "v2rayN/6.45")

    private class Response(val code: Int, val body: String, val headers: Map<String, String>)

    private fun get(url: String, userAgent: String, accept: String, timeoutMs: Int = 20_000): Response {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", accept)
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            val headers = conn.headerFields
                .filterKeys { it != null }
                .mapKeys { it.key!!.lowercase() }
                .mapValues { it.value.firstOrNull().orEmpty() }
            return Response(code, body, headers)
        } finally {
            conn.disconnect()
        }
    }

    fun fetch(url: String): SubscriptionInfo {
        var info: SubscriptionInfo? = null
        for (ua in userAgents) {
            val r = get(url, ua, "*/*")
            if (r.code == 404 || r.code == 400 || r.code == 403) throw NotFoundException("Subscription not found (${r.code})")
            if (r.code !in 200..299) throw IllegalStateException("Server answered ${r.code}")

            val candidate = SubscriptionInfo(url = url)
            readHeaders(r.headers, candidate)
            val parsed = LinkParser.parseSubscriptionBody(r.body)
            candidate.servers = parsed.servers
            if (parsed.unsupported > 0) log("${parsed.unsupported} server(s) skipped (protocol not supported by Xray).")

            info = if (info == null || candidate.servers.size > info.servers.size) merge(candidate, info) else merge(info, candidate)
            if (info.servers.isNotEmpty()) break
            log("No server with user agent \"$ua\", retrying…")
        }
        val result = info!!
        readHtmlPage(url, result)
        return result
    }

    private fun merge(target: SubscriptionInfo, other: SubscriptionInfo?): SubscriptionInfo {
        if (other == null) return target
        target.title = target.title ?: other.title
        if (target.upload + target.download + target.total == 0L) {
            target.upload = other.upload; target.download = other.download; target.total = other.total
        }
        target.expire = target.expire ?: other.expire
        target.updateIntervalHours = target.updateIntervalHours ?: other.updateIntervalHours
        target.supportUrl = target.supportUrl ?: other.supportUrl
        target.webPageUrl = target.webPageUrl ?: other.webPageUrl
        target.announce = target.announce ?: other.announce
        return target
    }

    private fun decodeHeader(value: String?): String? {
        val v = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (v.startsWith("base64:", ignoreCase = true)) Base64Util.tryDecode(v.substring(7)) ?: v else v
    }

    private fun readHeaders(h: Map<String, String>, info: SubscriptionInfo) {
        // subscription-userinfo: upload=123; download=456; total=789; expire=1700000000
        decodeHeader(h["subscription-userinfo"])?.split(';')?.forEach { part ->
            val kv = part.split('=', limit = 2).map { it.trim() }
            if (kv.size != 2) return@forEach
            val n = kv[1].toDoubleOrNull()?.toLong() ?: return@forEach
            when (kv[0].lowercase()) {
                "upload" -> info.upload = n
                "download" -> info.download = n
                "total" -> info.total = n
                "expire" -> info.expire = n.takeIf { it > 0 }
            }
        }
        info.title = decodeHeader(h["profile-title"])
        info.supportUrl = decodeHeader(h["support-url"])
        info.webPageUrl = decodeHeader(h["profile-web-page-url"])
        info.announce = decodeHeader(h["announce"])
        info.updateIntervalHours = decodeHeader(h["profile-update-interval"])?.toIntOrNull()?.takeIf { it > 0 }
    }

    private val dataBlock = Regex("""<div[^>]*\bid="data"[^>]*>""", RegexOption.IGNORE_CASE)
    private val dataAttr = Regex("""data-([a-z-]+)="([^"]*)"""", RegexOption.IGNORE_CASE)
    private val mailAttr = Regex("""data-mail="([^"]*)"""", RegexOption.IGNORE_CASE)
    private val linkAttr = Regex("""data-link="([^"]*)"""", RegexOption.IGNORE_CASE)

    /**
     * 3x-ui serves a subscription page to browsers; its data block carries what
     * the headers do not: account e-mails, last online time, support link, state.
     */
    private fun readHtmlPage(url: String, info: SubscriptionInfo) {
        try {
            val r = get(url, "Mozilla/5.0 (Linux; Android) VoidRay/1.0", "text/html,application/xhtml+xml", 10_000)
            if (r.code !in 200..299) return
            val block = dataBlock.find(r.body)?.value ?: return
            val data = dataAttr.findAll(block).associate { it.groupValues[1].lowercase() to htmlDecode(it.groupValues[2]).trim() }
            fun d(k: String) = data[k]?.takeIf { it.isNotEmpty() }
            fun n(k: String) = d(k)?.toLongOrNull() ?: 0L

            info.enabled = d("enabled") != "0"
            info.sid = d("sid") ?: info.sid
            info.supportUrl = info.supportUrl ?: d("support")
            if (n("last-online") > 0) info.lastOnline = n("last-online")
            if (info.upload + info.download == 0L) {
                info.upload = n("upload-byte"); info.download = n("download-byte")
            }
            if (info.total == 0L) info.total = n("total-byte")
            if (info.expire == null && n("expire") > 0) info.expire = n("expire")

            info.emails = mailAttr.findAll(r.body).map { htmlDecode(it.groupValues[1]).trim() }
                .filter { it.isNotEmpty() }.distinct().toList()
            info.username = info.username ?: info.emails.firstOrNull()

            if (info.servers.isEmpty()) {
                val links = linkAttr.findAll(r.body).joinToString("\n") { htmlDecode(it.groupValues[1]) }
                info.servers = LinkParser.parseSubscriptionBody(links).servers
            }
        } catch (e: Exception) {
            // Optional: the page may not be served.
        }
    }

    private fun htmlDecode(s: String) = s
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">")

}
