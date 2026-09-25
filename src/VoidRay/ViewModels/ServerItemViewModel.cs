using VoidRay.Models;

namespace VoidRay.ViewModels;

public enum PingQuality { Unknown, Testing, Good, Medium, Bad, Timeout }

public sealed class ServerItemViewModel : ObservableObject
{
    private int? _ping;
    private PingQuality _quality;

    public ServerItemViewModel(ServerProfile profile) => Profile = profile;

    public ServerProfile Profile { get; }
    public string Name => Profile.Remark;
    public string Protocol => Profile.Protocol == "shadowsocks" ? "SS" : Profile.Protocol.ToUpperInvariant();
    public string Transport => Profile.TransportLabel;

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
