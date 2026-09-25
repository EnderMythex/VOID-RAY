package win.enderr.voidray.core

import win.enderr.voidray.i18n.L
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

object Format {
    private val unitsEn = listOf("B", "KB", "MB", "GB", "TB", "PB")
    private val unitsFr = listOf("o", "Ko", "Mo", "Go", "To", "Po")

    /** Human readable size, e.g. "8.92 GB" / "8,92 Go". */
    fun bytes(bytes: Double): String {
        val units = if (L.lang == "fr") unitsFr else unitsEn
        var value = maxOf(0.0, bytes)
        var unit = 0
        while (value >= 1024 && unit < units.size - 1) {
            value /= 1024; unit++
        }
        val pattern = if (unit == 0 || value >= 100) "0" else "0.##"
        return DecimalFormat(pattern, DecimalFormatSymbols(L.locale)).format(value) + " " + units[unit]
    }

    fun bytes(bytes: Long) = bytes(bytes.toDouble())

    private fun zoned(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())

    fun shortDate(ms: Long): String = zoned(ms).format(DateTimeFormatter.ofPattern("d MMM yyyy", L.locale))

    fun stamp(ms: Long): String = shortDate(ms) + " · " + zoned(ms).format(DateTimeFormatter.ofPattern("HH:mm", L.locale))

    fun ago(ms: Long): String {
        val diff = System.currentTimeMillis() - ms
        return when {
            diff < 120_000 -> L.t("justNow")
            diff < 3_600_000 -> L.t("minsAgo", (diff / 60_000.0).roundToInt())
            diff < 86_400_000 -> L.t("hoursAgo", (diff / 3_600_000.0).roundToInt())
            else -> L.t("daysAgo", (diff / 86_400_000.0).roundToInt())
        }
    }

    fun duration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}
