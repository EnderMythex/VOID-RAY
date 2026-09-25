namespace VoidRay.Services;

/// <summary>
/// The panel exposes no past values, so usage snapshots are kept locally and
/// turned into one figure per day (same logic as the subscription page).
/// </summary>
public static class UsageHistory
{
    public const int Days = 14;
    private const int MaxPoints = 400;

    public static void Record(List<long[]> history, long used)
    {
        var now = DateTimeOffset.Now.ToUnixTimeMilliseconds();
        var last = history.Count > 0 ? history[^1] : null;
        // One point every 10 minutes is plenty, unless the counter actually moved.
        if (last is not null && now - last[0] < 600_000 && last[1] == used)
            return;
        history.Add(new[] { now, used });

        var cutoff = now - (Days + 2) * 86_400_000L;
        history.RemoveAll(e => e.Length != 2 || e[0] < cutoff);
        if (history.Count > MaxPoints)
            history.RemoveRange(0, history.Count - MaxPoints);
    }

    /// <summary>Consumption per day for the last <see cref="Days"/> days (null = no data).</summary>
    public static List<double?> Daily(IEnumerable<long[]> history)
    {
        var perDay = new Dictionary<DateTime, (long First, long Last)>();
        foreach (var e in history.Where(e => e.Length == 2))
        {
            var day = DateTimeOffset.FromUnixTimeMilliseconds(e[0]).LocalDateTime.Date;
            perDay[day] = perDay.TryGetValue(day, out var cur)
                ? (Math.Min(cur.First, e[1]), Math.Max(cur.Last, e[1]))
                : (e[1], e[1]);
        }

        var result = new List<double?>();
        long? prevLast = null;
        for (var i = Days - 1; i >= 0; i--)
        {
            var day = DateTime.Today.AddDays(-i);
            if (!perDay.TryGetValue(day, out var entry))
            {
                result.Add(null);
                continue;
            }
            long delta;
            if (prevLast is null) delta = entry.Last - entry.First;          // first day seen
            else if (entry.Last < prevLast) delta = entry.Last;               // counter was reset
            else delta = entry.Last - prevLast.Value;
            prevLast = entry.Last;
            result.Add(Math.Max(0, delta));
        }
        return result;
    }
}
