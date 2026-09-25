using System.Diagnostics;
using System.IO.Compression;
using System.Net.Sockets;
using System.Runtime.InteropServices;

namespace VoidRay.Services;

/// <summary>Downloads, starts and stops the xray-core process that carries the tunnel.</summary>
public sealed class XrayCore : IDisposable
{
    private Process? _process;
    private bool _stopping;

    public event Action<string>? Log;
    /// <summary>Raised when the core exits without being asked to.</summary>
    public event Action? Crashed;

    public bool IsRunning => _process is { HasExited: false };

    public static string? FindExecutable()
    {
        var candidates = new[]
        {
            Path.Combine(AppContext.BaseDirectory, "core", "xray.exe"),
            Path.Combine(AppContext.BaseDirectory, "xray.exe"),
            Path.Combine(AppPaths.Core, "xray.exe"),
        };
        return candidates.FirstOrDefault(File.Exists);
    }

    public async Task<string> EnsureInstalledAsync(CancellationToken ct = default)
    {
        var existing = FindExecutable();
        if (existing is not null)
            return existing;

        var asset = RuntimeInformation.OSArchitecture switch
        {
            Architecture.Arm64 => "Xray-windows-arm64-v8a.zip",
            Architecture.X86 => "Xray-windows-32.zip",
            _ => "Xray-windows-64.zip",
        };
        var url = $"https://github.com/XTLS/Xray-core/releases/latest/download/{asset}";
        Log?.Invoke($"Moteur Xray absent, téléchargement de {asset}…");

        using var http = new HttpClient { Timeout = TimeSpan.FromMinutes(5) };
        http.DefaultRequestHeaders.UserAgent.ParseAdd("VoidRay/1.0");
        var zipPath = Path.Combine(AppPaths.Core, asset);
        await using (var source = await http.GetStreamAsync(url, ct))
        await using (var target = File.Create(zipPath))
            await source.CopyToAsync(target, ct);

        ZipFile.ExtractToDirectory(zipPath, AppPaths.Core, overwriteFiles: true);
        File.Delete(zipPath);
        Log?.Invoke("Moteur Xray installé.");

        return FindExecutable() ?? throw new InvalidOperationException("xray.exe introuvable après l'installation.");
    }

    public async Task StartAsync(string exePath, string configPath, int socksPort, CancellationToken ct = default)
    {
        Stop();
        _stopping = false;

        var psi = new ProcessStartInfo(exePath, $"run -c \"{configPath}\"")
        {
            WorkingDirectory = Path.GetDirectoryName(exePath)!,
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
        };
        psi.Environment["XRAY_LOCATION_ASSET"] = Path.GetDirectoryName(exePath)!;

        var process = new Process { StartInfo = psi, EnableRaisingEvents = true };
        process.OutputDataReceived += (_, e) => { if (!string.IsNullOrWhiteSpace(e.Data)) Log?.Invoke(e.Data); };
        process.ErrorDataReceived += (_, e) => { if (!string.IsNullOrWhiteSpace(e.Data)) Log?.Invoke(e.Data); };
        process.Exited += (_, _) =>
        {
            if (!_stopping)
                Crashed?.Invoke();
        };

        if (!process.Start())
            throw new InvalidOperationException("Impossible de démarrer xray.exe.");
        process.BeginOutputReadLine();
        process.BeginErrorReadLine();
        _process = process;

        // Wait until the local SOCKS port answers (or the process dies).
        var deadline = DateTime.UtcNow.AddSeconds(8);
        while (DateTime.UtcNow < deadline)
        {
            if (process.HasExited)
                throw new InvalidOperationException($"Xray s'est arrêté (code {process.ExitCode}). Consulte le journal.");
            try
            {
                using var probe = new TcpClient();
                await probe.ConnectAsync("127.0.0.1", socksPort, ct);
                return;
            }
            catch (SocketException)
            {
                await Task.Delay(150, ct);
            }
        }
        throw new TimeoutException("Xray ne répond pas sur le port local.");
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
