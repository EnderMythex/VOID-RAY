using System.Text.RegularExpressions;
using VoidRay.Models;

namespace VoidRay.ViewModels;

public enum PingQuality { Unknown, Testing, Good, Medium, Bad, Timeout }

public sealed class ServerItemViewModel : ObservableObject
{
    private static readonly Regex EmailSuffix = new(@"\s*[-–—|]\s*[^\s@]+@[^\s@]+\.[A-Za-z]{2,}\s*$");

    private int? _ping;
    private PingQuality _quality;

    public ServerItemViewModel(ServerProfile profile)
    {
        Profile = profile;
        // 3x-ui appends "-email" to every remark: keep the readable part only.
        var name = EmailSuffix.Replace(profile.Remark, "").Trim();
        Name = name.Length > 0 ? name : profile.Remark;
    }

    public ServerProfile Profile { get; }
    public string Name { get; }
    public string FullName => Profile.Remark;

    public string ProtocolTag => Profile.Protocol == "shadowsocks" ? "SS" : Profile.Protocol.ToUpperInvariant();

    public string NetworkTag => Profile.Network.ToLowerInvariant() switch
    {
        "raw" => "TCP",
        "splithttp" => "XHTTP",
        var n => n.ToUpperInvariant(),
    };

    public string? SecurityTag => Profile.Security is null or "" or "none" ? null : Profile.Security.ToUpperInvariant();
    public bool HasSecurityTag => SecurityTag is not null;
    public bool IsBypass => Profile.IsBypassPatched;
    public string? RawLink => Profile.RawLink;

    public int? Ping
    {
        get => _ping;
        private set => Set(ref _ping, value);
    }

    public PingQuality Quality
    {
        get => _quality;
        private set
        {
            if (Set(ref _quality, value))
                OnPropertyChanged(nameof(PingText));
        }
    }

    public string PingText => Quality switch
    {
        PingQuality.Unknown => "—",
        PingQuality.Testing => "···",
        PingQuality.Timeout => "timeout",
        _ => $"{Ping} ms",
    };

    public void BeginTest() => Quality = PingQuality.Testing;

    public void SetPing(int? ms)
    {
        Ping = ms;
        Quality = ms switch
        {
            null => PingQuality.Timeout,
            < 150 => PingQuality.Good,
            < 350 => PingQuality.Medium,
            _ => PingQuality.Bad,
        };
    }
}

public enum Tone { Normal, Ok, Warn, Alert }

/// <summary>One line of the "Subscription details" card.</summary>
public sealed record DetailRow(string Key, string Value, string? Hint = null, bool Mono = false,
    Tone Tone = Tone.Normal, bool IsChip = false)
{
    public bool HasHint => !string.IsNullOrEmpty(Hint);
}

public sealed class AccentOption : ObservableObject
{
    private bool _isSelected;

    public AccentOption(int index, string name, System.Windows.Media.Brush swatch)
    {
        Index = index;
        Name = name;
        Swatch = swatch;
    }

    public int Index { get; }
    public string Name { get; }
    public System.Windows.Media.Brush Swatch { get; }

    public bool IsSelected
    {
        get => _isSelected;
        set => Set(ref _isSelected, value);
    }
}
