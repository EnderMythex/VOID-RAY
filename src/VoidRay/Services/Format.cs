using System.Globalization;

namespace VoidRay.Services;

public static class Format
{
    private static readonly CultureInfo Fr = CultureInfo.GetCultureInfo("fr-FR");
    private static readonly string[] Units = { "o", "Ko", "Mo", "Go", "To", "Po" };

    public static string Bytes(long bytes)
    {
        double value = Math.Max(0, bytes);
        var unit = 0;
        while (value >= 1024 && unit < Units.Length - 1)
        {
            value /= 1024;
            unit++;
        }
        return value.ToString(unit == 0 ? "0" : value >= 100 ? "0" : "0.##", Fr) + " " + Units[unit];
    }

    public static string Date(DateTimeOffset date) =>
        date.ToLocalTime().ToString("dd MMMM yyyy · HH:mm", Fr);

    public static string Duration(TimeSpan t) =>
        t.TotalHours >= 1 ? $"{(int)t.TotalHours:00}:{t.Minutes:00}:{t.Seconds:00}" : $"{t.Minutes:00}:{t.Seconds:00}";
}
