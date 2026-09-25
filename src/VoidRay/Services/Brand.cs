using System.Text.RegularExpressions;

namespace VoidRay.Services;

/// <summary>Everything specific to the EnderrVPN service.</summary>
public static class Brand
{
    public const string ServiceName = "EnderrVPN";
    public const string SubHost = "sub.enderr.win";
    public const string LinkExample = "https://sub.enderr.win/ender/…";
    public const string DefaultSupportUrl = "https://discord.gg/BzG6FJz6zK";

    private static readonly Regex LinkPattern = new(
        @"^https://sub\.enderr\.win/ender/(?<sid>[A-Za-z0-9_-]{4,64})/?$",
        RegexOptions.IgnoreCase | RegexOptions.CultureInvariant);

    /// <summary>Accepts only EnderrVPN subscription links and returns them normalised.</summary>
    public static bool TryNormalizeLink(string? input, out string link, out string sid)
    {
        link = "";
        sid = "";
        var m = LinkPattern.Match((input ?? "").Trim());
        if (!m.Success)
            return false;
        sid = m.Groups["sid"].Value;
        link = $"https://{SubHost}/ender/{sid}";
        return true;
    }
}
