namespace VoidRay.Models;

public sealed class AppSettings
{
    public string SubscriptionUrl { get; set; } = "";
    public string? SelectedServerKey { get; set; }
    /// <summary>"tun" = the whole PC (games included), "proxy" = Windows system proxy only.</summary>
    public string Mode { get; set; } = "tun";
    public int SocksPort { get; set; } = 10808;
    public int HttpPort { get; set; } = 10809;
    public SubscriptionInfo? CachedSubscription { get; set; }

    /// <summary>"auto", "dark" or "light".</summary>
    public string Theme { get; set; } = "auto";
    public int Accent { get; set; }
    /// <summary>"auto", "en" or "fr".</summary>
    public string Language { get; set; } = "auto";

    /// <summary>Usage snapshots per subscription id: [unix ms, used bytes].</summary>
    public Dictionary<string, List<long[]>> UsageHistory { get; set; } = new();
}
