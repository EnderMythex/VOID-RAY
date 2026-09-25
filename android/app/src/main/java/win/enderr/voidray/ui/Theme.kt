package win.enderr.voidray.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colours of the subscription page: dark / light, with six accent presets. */
data class Palette(
    val isLight: Boolean,
    val bg: Color,
    val ink: Color,
    val ink2: Color,
    val muted: Color,
    val accent: Color,
    val accent2: Color,
    val warn: Color,
    val alert: Color,
    val line: Color,
    val line2: Color,
    val hi: Color,
    val field: Color,
    val fieldHover: Color,
    val panelTop: Color,
    val panelBottom: Color,
    val ambient: Float,
) {
    val accentSoft get() = accent.copy(alpha = .15f)
    val accentLine get() = accent.copy(alpha = .32f)
    val accentText get() = lerp(accent, ink, .12f)
    val accent2Soft get() = accent2.copy(alpha = .18f)
    val accent2Line get() = accent2.copy(alpha = .32f)
    val accent2Text get() = lerp(accent2, ink, .10f)
    val warnSoft get() = warn.copy(alpha = .15f)
    val warnLine get() = warn.copy(alpha = .30f)
    val alertSoft get() = alert.copy(alpha = .15f)
    val alertLine get() = alert.copy(alpha = .32f)
}

class Accent(val name: String, val dark: Color, val light: Color)

val Accents = listOf(
    Accent("teal", Color(0xFF52D6BD), Color(0xFF0F9D86)),
    Accent("indigo", Color(0xFF7F97FF), Color(0xFF3F55CF)),
    Accent("violet", Color(0xFFB085FF), Color(0xFF7440D4)),
    Accent("rose", Color(0xFFFF8AAB), Color(0xFFCF3560)),
    Accent("amber", Color(0xFFF0B95F), Color(0xFFA5720C)),
    Accent("lime", Color(0xFF9EDE5A), Color(0xFF4D8C19)),
)

fun palette(light: Boolean, accentIndex: Int): Palette {
    val a = Accents[accentIndex.coerceIn(0, Accents.size - 1)]
    val white = Color.White
    return if (light) {
        val lineBase = Color(0xFF0F1629)
        Palette(
            isLight = true,
            bg = Color(0xFFF2F4F9), ink = Color(0xFF0D1220), ink2 = Color(0xFF3D4761), muted = Color(0xFF5F6982),
            accent = a.light, accent2 = Color(0xFF4A5FD4), warn = Color(0xFFA8730D), alert = Color(0xFFCF3450),
            line = lineBase.copy(alpha = .09f), line2 = lineBase.copy(alpha = .06f), hi = white.copy(alpha = .95f),
            field = white.copy(alpha = .55f), fieldHover = white.copy(alpha = .85f),
            panelTop = white.copy(alpha = .85f), panelBottom = white.copy(alpha = .55f), ambient = .30f,
        )
    } else {
        Palette(
            isLight = false,
            bg = Color(0xFF07090E), ink = Color(0xFFE8ECF4), ink2 = Color(0xFFAAB3C6), muted = Color(0xFF737E96),
            accent = a.dark, accent2 = Color(0xFF6D86FF), warn = Color(0xFFE2B155), alert = Color(0xFFEE6079),
            line = white.copy(alpha = .075f), line2 = white.copy(alpha = .05f), hi = white.copy(alpha = .14f),
            field = white.copy(alpha = .035f), fieldHover = white.copy(alpha = .06f),
            panelTop = white.copy(alpha = .055f), panelBottom = white.copy(alpha = .018f), ambient = .42f,
        )
    }
}

fun lerp(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

val LocalPalette = staticCompositionLocalOf { palette(false, 0) }
