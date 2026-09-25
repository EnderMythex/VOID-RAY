namespace VoidRay.Models;

public sealed class AppSettings
{
    public string SubscriptionUrl { get; set; } = "";
    public string? SelectedServerKey { get; set; }
    public int SocksPort { get; set; } = 10808;
    public int HttpPort { get; set; } = 10809;
    public SubscriptionInfo? CachedSubscription { get; set; }
}
