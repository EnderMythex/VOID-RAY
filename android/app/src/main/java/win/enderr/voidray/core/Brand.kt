package win.enderr.voidray.core

/** Everything specific to the EnderrVPN service. */
object Brand {
    const val SERVICE_NAME = "EnderrVPN"
    const val SUB_HOST = "sub.enderr.win"
    const val LINK_EXAMPLE = "https://sub.enderr.win/ender/…"
    const val DEFAULT_SUPPORT_URL = "https://discord.gg/BzG6FJz6zK"

    private val linkPattern = Regex(
        """^https://sub\.enderr\.win/ender/([A-Za-z0-9_-]{4,64})/?$""",
        RegexOption.IGNORE_CASE,
    )

    /** Accepts only EnderrVPN subscription links; returns (normalised link, subscription id). */
    fun normalizeLink(input: String?): Pair<String, String>? {
        val m = linkPattern.matchEntire(input.orEmpty().trim()) ?: return null
        val sid = m.groupValues[1]
        return "https://$SUB_HOST/ender/$sid" to sid
    }
}
