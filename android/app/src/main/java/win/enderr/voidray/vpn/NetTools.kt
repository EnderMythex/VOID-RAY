package win.enderr.voidray.vpn

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL

object NetTools {
    /** TCP handshake latency to the server in ms (null on failure). */
    fun tcpPing(host: String, port: Int, timeoutMs: Int = 3000): Int? = try {
        val address = InetAddress.getAllByName(host).let { all -> all.firstOrNull { it.address.size == 4 } ?: all.first() }
        Socket().use { s ->
            val start = System.nanoTime()
            s.connect(InetSocketAddress(address, port), timeoutMs)
            maxOf(1, ((System.nanoTime() - start) / 1_000_000).toInt())
        }
    } catch (e: Exception) {
        null
    }

    class ExitInfo(val ip: String, val country: String?, val countryCode: String?, val delayMs: Int)

    /**
     * Queries the public IP through Xray's local SOCKS inbound. The app itself is
     * excluded from the VPN, so this is how it proves the tunnel works end to end.
     */
    fun checkExit(socksPort: Int): ExitInfo? {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
        fun get(url: String): Pair<String, Int> {
            val conn = URL(url).openConnection(proxy) as HttpURLConnection
            try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("User-Agent", "VoidRay-Android/1.0")
                val start = System.nanoTime()
                val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                return body to ((System.nanoTime() - start) / 1_000_000).toInt()
            } finally {
                conn.disconnect()
            }
        }
        return try {
            val (body, delay) = get("https://ipwho.is/?fields=ip,country,country_code")
            val o = JSONObject(body)
            ExitInfo(o.getString("ip"), o.optString("country").ifEmpty { null }, o.optString("country_code").ifEmpty { null }, delay)
        } catch (e: Exception) {
            try {
                val (ip, delay) = get("https://api.ipify.org")
                ExitInfo(ip.trim(), null, null, delay)
            } catch (e2: Exception) {
                null
            }
        }
    }
}
