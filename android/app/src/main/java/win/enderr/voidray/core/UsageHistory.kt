package win.enderr.voidray.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The panel exposes no past values, so usage snapshots are kept locally and
 * turned into one figure per day (same logic as the subscription page).
 */
object UsageHistory {
    const val DAYS = 14
    private const val MAX_POINTS = 400

    /** Adds a [ts ms, used bytes] point to [history] (mutated in place). */
    fun record(history: MutableList<LongArray>, used: Long, now: Long = System.currentTimeMillis()) {
        val last = history.lastOrNull()
        // One point every 10 minutes is plenty, unless the counter actually moved.
        if (last != null && now - last[0] < 600_000 && last[1] == used) return
        history += longArrayOf(now, used)
        val cutoff = now - (DAYS + 2) * 86_400_000L
        history.removeAll { it.size != 2 || it[0] < cutoff }
        while (history.size > MAX_POINTS) history.removeAt(0)
    }

    /** Consumption per day for the last [DAYS] days (null = no data that day). */
    fun daily(history: List<LongArray>, zone: ZoneId = ZoneId.systemDefault()): List<Long?> {
        val perDay = HashMap<LocalDate, LongArray>() // [first, last]
        for (e in history) {
            if (e.size != 2) continue
            val day = Instant.ofEpochMilli(e[0]).atZone(zone).toLocalDate()
            val cur = perDay[day]
            perDay[day] = if (cur == null) longArrayOf(e[1], e[1]) else longArrayOf(minOf(cur[0], e[1]), maxOf(cur[1], e[1]))
        }
        val today = LocalDate.now(zone)
        var prevLast: Long? = null
        return (DAYS - 1 downTo 0).map { i ->
            val entry = perDay[today.minusDays(i.toLong())] ?: return@map null
            val delta = when {
                prevLast == null -> entry[1] - entry[0]          // first day seen
                entry[1] < prevLast!! -> entry[1]                // counter was reset
                else -> entry[1] - prevLast!!
            }
            prevLast = entry[1]
            maxOf(0L, delta)
        }
    }
}
