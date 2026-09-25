namespace VoidRay.Models;

public sealed class SubscriptionInfo
{
    public string Url { get; set; } = "";
    public string? Title { get; set; }
    public string? Username { get; set; }
    public string? Status { get; set; }
    public string? Sid { get; set; }
    public List<string> Emails { get; set; } = new();
    public DateTimeOffset? LastOnline { get; set; }
    /// <summary>False when the panel reports the account as disabled.</summary>
    public bool Enabled { get; set; } = true;

    public long Upload { get; set; }
    public long Download { get; set; }
    /// <summary>0 = unlimited.</summary>
    public long Total { get; set; }
    /// <summary>null = never expires.</summary>
    public DateTimeOffset? Expire { get; set; }

    public int? UpdateIntervalHours { get; set; }
    public string? SupportUrl { get; set; }
    public string? WebPageUrl { get; set; }
    public string? Announce { get; set; }

    public DateTimeOffset FetchedAt { get; set; }
    public List<ServerProfile> Servers { get; set; } = new();

    public long Used => Upload + Download;
}
