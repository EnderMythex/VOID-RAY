package win.enderr.voidray.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import win.enderr.voidray.core.SubscriptionInfo

/** Small persistent settings, kept in SharedPreferences. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("voidray", Context.MODE_PRIVATE)

    var subscriptionUrl: String
        get() = prefs.getString("subscriptionUrl", "").orEmpty()
        set(v) = prefs.edit().putString("subscriptionUrl", v).apply()

    var selectedKey: String?
        get() = prefs.getString("selectedKey", null)
        set(v) = prefs.edit().putString("selectedKey", v).apply()

    /** "auto", "dark" or "light". */
    var theme: String
        get() = prefs.getString("theme", "auto") ?: "auto"
        set(v) = prefs.edit().putString("theme", v).apply()

    var accent: Int
        get() = prefs.getInt("accent", 0)
        set(v) = prefs.edit().putInt("accent", v).apply()

    /** "auto", "en" or "fr". */
    var language: String
        get() = prefs.getString("language", "auto") ?: "auto"
        set(v) = prefs.edit().putString("language", v).apply()

    /** True once the user accepted the VPN disclosure shown before the first connection. */
    var vpnDisclosureAccepted: Boolean
        get() = prefs.getBoolean("vpnDisclosureAccepted", false)
        set(v) = prefs.edit().putBoolean("vpnDisclosureAccepted", v).apply()

    var cachedSubscription: SubscriptionInfo?
        get() = prefs.getString("cachedSubscription", null)?.let {
            runCatching { SubscriptionInfo.fromJson(JSONObject(it)) }.getOrNull()
        }
        set(v) = prefs.edit().putString("cachedSubscription", v?.toJson()?.toString()).apply()

    /** Usage snapshots [ts ms, used bytes] for one subscription id. */
    fun history(sid: String): MutableList<LongArray> {
        val raw = prefs.getString("history:$sid", null) ?: return mutableListOf()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { val e = a.getJSONArray(it); longArrayOf(e.getLong(0), e.getLong(1)) }.toMutableList()
        }.getOrDefault(mutableListOf())
    }

    fun saveHistory(sid: String, history: List<LongArray>) {
        val a = JSONArray()
        history.forEach { a.put(JSONArray().put(it[0]).put(it[1])) }
        prefs.edit().putString("history:$sid", a.toString()).apply()
    }

    /** Forgets the subscription and everything derived from it: link, cache, chosen server, usage history. */
    fun clearSubscription() {
        val editor = prefs.edit().remove("subscriptionUrl").remove("cachedSubscription").remove("selectedKey")
        prefs.all.keys.filter { it.startsWith("history:") }.forEach { editor.remove(it) }
        editor.apply()
    }
}
