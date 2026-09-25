package win.enderr.voidray.core

import java.util.Base64

object Base64Util {
    /** Decodes standard or URL-safe base64, with or without padding. Null when invalid. */
    fun tryDecode(input: String): String? {
        val sb = StringBuilder(input.length + 3)
        for (c in input) {
            if (c.isWhitespace()) continue
            sb.append(
                when (c) {
                    '-' -> '+'
                    '_' -> '/'
                    else -> c
                }
            )
        }
        var t = sb.toString().trimEnd('=')
        when (t.length % 4) {
            1 -> return null
            2 -> t += "=="
            3 -> t += "="
        }
        return try {
            String(Base64.getDecoder().decode(t), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
