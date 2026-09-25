using System.Diagnostics;
using System.Net;
using System.Net.Sockets;
using System.Text.Json;

namespace VoidRay.Services;

public static class NetTools
{
    /// <summary>TCP handshake latency to the server, in ms (null on failure).</summary>
    public static async Task<int?> TcpPingAsync(string host, int port, int timeoutMs = 3000)
    {
        try
        {
            using var cts = new CancellationTokenSource(timeoutMs);
            var addresses = await Dns.GetHostAddressesAsync(host, cts.Token);
            var address = addresses.FirstOrDefault(a => a.AddressFamily == AddressFamily.InterNetwork) ?? addresses.First();
            using var client = new TcpClient(address.AddressFamily);
            var sw = Stopwatch.StartNew();
            await client.ConnectAsync(address, port, cts.Token);
            return (int)Math.Max(1, sw.ElapsedMilliseconds);
        }
        catch
        {
            return null;
        }
    }

    public sealed record ExitInfo(string Ip, string? Country, string? CountryCode, int DelayMs);

    /// <summary>Queries the public IP through the local proxy: proves the tunnel works end to end.</summary>
    public static async Task<ExitInfo?> CheckExitAsync(int httpPort, CancellationToken ct = default)
    {
        using var http = new HttpClient(new HttpClientHandler
        {
            Proxy = new WebProxy($"http://127.0.0.1:{httpPort}"),
            UseProxy = true,
        })
        { Timeout = TimeSpan.FromSeconds(10) };
        http.DefaultRequestHeaders.UserAgent.ParseAdd("VoidRay/1.0");

        try
        {
            var sw = Stopwatch.StartNew();
            var json = await http.GetStringAsync("https://ipwho.is/?fields=ip,country,country_code", ct);
            var delay = (int)sw.ElapsedMilliseconds;
            using var doc = JsonDocument.Parse(json);
            var r = doc.RootElement;
            return new ExitInfo(
                r.GetProperty("ip").GetString() ?? "?",
                r.TryGetProperty("country", out var c) ? c.GetString() : null,
                r.TryGetProperty("country_code", out var cc) ? cc.GetString() : null,
                delay);
        }
        catch
        {
            try
            {
                var sw = Stopwatch.StartNew();
                var ip = (await http.GetStringAsync("https://api.ipify.org", ct)).Trim();
                return new ExitInfo(ip, null, null, (int)sw.ElapsedMilliseconds);
            }
            catch
            {
                return null;
            }
        }
    }

    public static int FindFreePort(int preferred)
    {
        try
        {
            var probe = new TcpListener(IPAddress.Loopback, preferred);
            probe.Start();
            probe.Stop();
            return preferred;
        }
        catch (SocketException)
        {
            var any = new TcpListener(IPAddress.Loopback, 0);
            any.Start();
            var port = ((IPEndPoint)any.LocalEndpoint).Port;
            any.Stop();
            return port;
        }
    }
}
