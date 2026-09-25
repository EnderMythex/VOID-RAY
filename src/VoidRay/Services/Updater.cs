using System.Diagnostics;
using System.Reflection;
using System.Text.Json;

namespace VoidRay.Services;

/// <summary>
/// Checks the GitHub releases of the project and replaces VoidRay.exe in place.
/// A running exe cannot be overwritten but can be renamed, so the new file takes
/// its name and the old one is deleted on the next start.
/// </summary>
public static class Updater
{
    public const string Repo = "EnderMythex/VOID-RAY";
    public const string AssetName = "VoidRay-win-x64.exe";
    public static string ReleasesPage => $"https://github.com/{Repo}/releases/latest";

    public sealed record Release(Version Version, string Tag, string? DownloadUrl, string PageUrl, string Notes);

    public static Version Current { get; } = Normalize(Assembly.GetExecutingAssembly().GetName().Version ?? new Version(1, 0, 0));

    public static string CurrentText => $"v{Current.Major}.{Current.Minor}.{Current.Build}";

    private static Version Normalize(Version v) => new(v.Major, v.Minor, Math.Max(0, v.Build));

    /// <summary>Latest published release, or null when none is reachable.</summary>
    public static async Task<Release?> GetLatestAsync(CancellationToken ct = default)
    {
        using var http = new HttpClient { Timeout = TimeSpan.FromSeconds(15) };
        http.DefaultRequestHeaders.UserAgent.ParseAdd("VoidRay/" + CurrentText);
        http.DefaultRequestHeaders.Accept.ParseAdd("application/vnd.github+json");
        using var res = await http.GetAsync($"https://api.github.com/repos/{Repo}/releases/latest", ct);
        if (!res.IsSuccessStatusCode)
            return null;
        using var doc = JsonDocument.Parse(await res.Content.ReadAsStringAsync(ct));
        var root = doc.RootElement;
        var tag = root.GetProperty("tag_name").GetString() ?? "";
        if (!Version.TryParse(tag.TrimStart('v', 'V'), out var version))
            return null;
        string? url = null;
        foreach (var asset in root.GetProperty("assets").EnumerateArray())
        {
            if (string.Equals(asset.GetProperty("name").GetString(), AssetName, StringComparison.OrdinalIgnoreCase))
                url = asset.GetProperty("browser_download_url").GetString();
        }
        return new Release(Normalize(version), tag, url,
            root.TryGetProperty("html_url", out var page) ? page.GetString() ?? ReleasesPage : ReleasesPage,
            root.TryGetProperty("body", out var body) ? body.GetString() ?? "" : "");
    }

    /// <summary>Downloads the new exe next to the current one and swaps them.</summary>
    public static async Task InstallAsync(Release release, IProgress<int>? progress, CancellationToken ct = default)
    {
        if (release.DownloadUrl is null)
            throw new InvalidOperationException("Aucun fichier Windows dans cette release.");
        var exe = Environment.ProcessPath ?? throw new InvalidOperationException("Chemin de l'application inconnu.");
        var dir = Path.GetDirectoryName(exe)!;
        var incoming = Path.Combine(dir, "VoidRay.update.exe");

        using (var http = new HttpClient { Timeout = TimeSpan.FromMinutes(10) })
        {
            http.DefaultRequestHeaders.UserAgent.ParseAdd("VoidRay/" + CurrentText);
            using var res = await http.GetAsync(release.DownloadUrl, HttpCompletionOption.ResponseHeadersRead, ct);
            res.EnsureSuccessStatusCode();
            var total = res.Content.Headers.ContentLength ?? 0;
            await using var source = await res.Content.ReadAsStreamAsync(ct);
            await using var target = File.Create(incoming);
            var buffer = new byte[81920];
            long done = 0;
            int read;
            while ((read = await source.ReadAsync(buffer, ct)) > 0)
            {
                await target.WriteAsync(buffer.AsMemory(0, read), ct);
                done += read;
                if (total > 0)
                    progress?.Report((int)(done * 100 / total));
            }
        }

        var old = exe + ".old";
        if (File.Exists(old))
            File.Delete(old);
        File.Move(exe, old);
        try
        {
            File.Move(incoming, exe);
        }
        catch
        {
            File.Move(old, exe); // put the running version back
            throw;
        }
    }

    /// <summary>Starts the freshly installed exe (it waits for this instance to exit).</summary>
    public static void Relaunch()
    {
        var exe = Environment.ProcessPath!;
        Process.Start(new ProcessStartInfo(exe, Elevation.RestartArg) { UseShellExecute = true });
    }

    /// <summary>Removes the previous version left behind by an update.</summary>
    public static void CleanupOldVersion()
    {
        try
        {
            var exe = Environment.ProcessPath;
            if (exe is not null && File.Exists(exe + ".old"))
                File.Delete(exe + ".old");
        }
        catch
        {
            // Still locked a moment after the swap: retried next start.
        }
    }
}
