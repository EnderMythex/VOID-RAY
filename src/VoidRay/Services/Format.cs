namespace VoidRay.Services;

public static class Format
{
    private static readonly string[] UnitsEn = { "B", "KB", "MB", "GB", "TB", "PB" };
    private static readonly string[] UnitsFr = { "o", "Ko", "Mo", "Go", "To", "Po" };

    /// <summary>Human readable size, e.g. "8.92 GB" / "8,92 Go".</summary>
    public static string Bytes(double bytes)
    {
        var units = Loc.I.Lang == "fr" ? UnitsFr : UnitsEn;
        var value = Math.Max(0, bytes);
        var unit = 0;
        while (value >= 1024 && unit < units.Length - 1)
        {
            value /= 1024;
            unit++;
        }
        var format = unit == 0 || value >= 100 ? "0" : "0.##";
        return value.ToString(format, Loc.I.Culture) + " " + units[unit];
    }

    public static string ShortDate(DateTimeOffset date) =>
        date.ToLocalTime().ToString("d MMM yyyy", Loc.I.Culture);

    public static string Stamp(DateTimeOffset date) =>
        ShortDate(date) + " · " + date.ToLocalTime().ToString("HH:mm", Loc.I.Culture);

    public static string Ago(DateTimeOffset date)
    {
        var diff = DateTimeOffset.Now - date;
        if (diff < TimeSpan.FromMinutes(2)) return Loc.T("justNow");
        if (diff < TimeSpan.FromHours(1)) return Loc.T("minsAgo", (int)Math.Round(diff.TotalMinutes));
        if (diff < TimeSpan.FromDays(1)) return Loc.T("hoursAgo", (int)Math.Round(diff.TotalHours));
        return Loc.T("daysAgo", (int)Math.Round(diff.TotalDays));
    }

    public static string Duration(TimeSpan t) =>
        t.TotalHours >= 1 ? $"{(int)t.TotalHours:00}:{t.Minutes:00}:{t.Seconds:00}" : $"{t.Minutes:00}:{t.Seconds:00}";
}
