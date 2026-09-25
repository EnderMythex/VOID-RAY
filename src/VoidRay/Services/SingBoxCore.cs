using System.Diagnostics;
using System.IO.Compression;
using System.Net.Http.Headers;
using System.Runtime.InteropServices;
using System.Text.Json;

namespace VoidRay.Services;

/// <summary>
/// sing-box creates the virtual network card (TUN) that captures the whole PC's
/// traffic — games included — and hands it to Xray's local SOCKS inbound.
/// The version is pinned because sing-box's config format changes between releases.
/// </summary>
public sealed class SingBoxCore : IDisposable
{
    public const string Version = "1.14.2";

    private Process? _process;
    private bool _stopping;

    public event Action<string>? Log;
    public event Action? Crashed;

    public static string ExePath => Path.Combine(AppPaths.Core, "sing-box.exe");
    private static string VersionFile => Path.Combine(AppPaths.Core, "sing-box.version");

    public async Task<string> EnsureInstalledAsync(CancellationToken ct = default)
    {
        if (File.Exists(ExePath) && File.Exists(VersionFile) && File.ReadAllText(VersionFile).Trim() == Version)
            return ExePath;

        var arch = RuntimeInformation.OSArchitecture switch
        {
            Architecture.Arm64 => "arm64",
            Architecture.X86 => "386",
            _ => "amd64",
        };
        Log?.Invoke($"Moteur TUN absent, téléchargement de sing-box {Version} ({arch})…");

        using var http = new HttpClient { Timeout = TimeSpan.FromMinutes(5) };
        http.DefaultRequestHeaders.UserAgent.ParseAdd("VoidRay/1.0");
        var url = await FindAssetUrlAsync(http, arch, ct)
                  ?? $"https://github.com/SagerNet/sing-box/releases/download/v{Version}/sing-box-{Version}-windows-{arch}.zip";

        var zipPath = Path.Combine(AppPaths.Core, "sing-box.zip");
        await using (var source = await http.GetStreamAsync(url, ct))
        await using (var target = File.Create(zipPath))
            await source.CopyToAsync(target, ct);

        using (var zip = ZipFile.OpenRead(zipPath))
        {
            // The archive holds a versioned folder: take every file out of it.
            foreach (var entry in zip.Entries.Where(e => e.Name.Length > 0))
                entry.ExtractToFile(Path.Combine(AppPaths.Core, entry.Name), overwrite: true);
        }
        File.Delete(zipPath);
        if (!File.Exists(ExePath))
            throw new InvalidOperationException("sing-box.exe introuvable dans l'archive.");
        File.WriteAllText(VersionFile, Version);
        Log?.Invoke("Moteur TUN installé.");
        return ExePath;
    }

    private static async Task<string?> FindAssetUrlAsync(HttpClient http, string arch, CancellationToken ct)
    {
        try
        {
            using var req = new HttpRequestMessage(HttpMethod.Get,
                $"https://api.github.com/repos/SagerNet/sing-box/releases/tags/v{Version}");
            req.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/vnd.github+json"));
            using var res = await http.SendAsync(req, ct);
            if (!res.IsSuccessStatusCode)
                return null;
            using var doc = JsonDocument.Parse(await res.Content.ReadAsStringAsync(ct));
            foreach (var asset in doc.RootElement.GetProperty("assets").EnumerateArray())
            {
                var name = asset.GetProperty("name").GetString() ?? "";
                if (name.EndsWith($"-windows-{arch}.zip", StringComparison.OrdinalIgnoreCase)
                    && !name.Contains("legacy", StringComparison.OrdinalIgnoreCase))
                    return asset.GetProperty("browser_download_url").GetString();
            }
        }
        catch
        {
            // Fall back to the conventional asset name.
        }
        return null;
    }

    public async Task StartAsync(string exePath, string configPath, CancellationToken ct = default)
    {
        Stop();
        _stopping = false;

        var psi = new ProcessStartInfo(exePath, $"run -c \"{configPath}\" --disable-color")
        {
            WorkingDirectory = Path.GetDirectoryName(exePath)!,
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
        };
        var process = new Process { StartInfo = psi, EnableRaisingEvents = true };
        process.OutputDataReceived += (_, e) => { if (!string.IsNullOrWhiteSpace(e.Data)) Log?.Invoke("[tun] " + e.Data); };
        process.ErrorDataReceived += (_, e) => { if (!string.IsNullOrWhiteSpace(e.Data)) Log?.Invoke("[tun] " + e.Data); };
        process.Exited += (_, _) =>
        {
            if (!_stopping)
                Crashed?.Invoke();
        };
        if (!process.Start())
            throw new InvalidOperationException("Impossible de démarrer sing-box.exe.");
        process.BeginOutputReadLine();
        process.BeginErrorReadLine();
        _process = process;

        // The TUN adapter needs a moment; a bad config makes sing-box exit right away.
        for (var i = 0; i < 20; i++)
        {
            await Task.Delay(150, ct);
            if (process.HasExited)
                throw new InvalidOperationException($"Le mode TUN n'a pas démarré (code {process.ExitCode}). Consulte le journal.");
        }
    }

    public void Stop()
    {
        var process = _process;
        _process = null;
        if (process is null)
            return;
        _stopping = true;
        try
        {
            if (!process.HasExited)
            {
                process.Kill(entireProcessTree: true);
                process.WaitForExit(3000);
            }
        }
        catch
        {
            // Already gone.
        }
        finally
        {
            process.Dispose();
        }
    }

    public void Dispose() => Stop();
}
