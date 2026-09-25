using System.Net;
using System.Net.Http.Headers;
using System.Text.Json;
using System.Text.RegularExpressions;
using VoidRay.Models;

namespace VoidRay.Services;

/// <summary>
/// Downloads a subscription link and extracts both the server list and the
/// account information (traffic, expiry, title…) exposed by panels such as
/// Marzban, Marzneshin, Remnawave, 3x-ui or Hiddify.
/// </summary>
public sealed class SubscriptionService
{
    private static readonly string[] UserAgents = { "VoidRay/1.0", "v2rayN/6.45", "v2rayNG/1.8.5" };

    private readonly HttpClient _http = new(new HttpClientHandler
    {
        AutomaticDecompression = DecompressionMethods.All,
        AllowAutoRedirect = true,
    })
    {
        Timeout = TimeSpan.FromSeconds(20),
    };

    public event Action<string>? Log;

    /// <summary>Thrown when the panel does not know the link (wrong or deleted subscription).</summary>
    public sealed class NotFoundException : Exception
    {
        public NotFoundException(string message) : base(message) { }
    }

    public async Task<SubscriptionInfo> FetchAsync(string url, CancellationToken ct = default)
    {
        url = url.Trim();
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri) || (uri.Scheme != "https" && uri.Scheme != "http"))
            throw new InvalidOperationException("Lien d'abonnement invalide (il doit commencer par https://).");

        SubscriptionInfo? info = null;
        foreach (var ua in UserAgents)
        {
            using var request = new HttpRequestMessage(HttpMethod.Get, uri);
            request.Headers.UserAgent.ParseAdd(ua);
            request.Headers.Accept.ParseAdd("*/*");

            using var response = await _http.SendAsync(request, ct);
            if (response.StatusCode is HttpStatusCode.NotFound or HttpStatusCode.BadRequest or HttpStatusCode.Forbidden)
                throw new NotFoundException($"Abonnement introuvable ({(int)response.StatusCode}).");
            if (!response.IsSuccessStatusCode)
                throw new InvalidOperationException($"Le serveur a répondu {(int)response.StatusCode} {response.ReasonPhrase}.");

            var body = await response.Content.ReadAsStringAsync(ct);
            var candidate = new SubscriptionInfo { Url = url, FetchedAt = DateTimeOffset.Now };
            ReadHeaders(response, candidate);
            candidate.Servers = LinkParser.ParseSubscriptionBody(body, out var unsupported);
            if (unsupported > 0)
                Log?.Invoke($"{unsupported} serveur(s) ignoré(s) (protocole non supporté par Xray).");

            // Keep the richest answer we get (headers can appear on any of them).
            if (info is null || candidate.Servers.Count > info.Servers.Count)
                info = Merge(candidate, info);
            else
                Merge(info, candidate);

            if (info.Servers.Count > 0)
                break;
            Log?.Invoke($"Aucun serveur avec l'agent « {ua} », nouvel essai…");
        }

        if (!await TryReadHtmlPageAsync(uri, info!, ct))
            await TryReadInfoEndpointAsync(uri, info!, ct);
        return info!;
    }

    private static SubscriptionInfo Merge(SubscriptionInfo target, SubscriptionInfo? other)
    {
        if (other is null)
            return target;
        target.Title ??= other.Title;
        target.Username ??= other.Username;
        target.Status ??= other.Status;
        if (target.Upload + target.Download + target.Total == 0)
        {
            target.Upload = other.Upload;
            target.Download = other.Download;
            target.Total = other.Total;
        }
        target.Expire ??= other.Expire;
        target.UpdateIntervalHours ??= other.UpdateIntervalHours;
        target.SupportUrl ??= other.SupportUrl;
        target.WebPageUrl ??= other.WebPageUrl;
        target.Announce ??= other.Announce;
        return target;
    }

    private static void ReadHeaders(HttpResponseMessage response, SubscriptionInfo info)
    {
        string? H(string name)
        {
            if (response.Headers.TryGetValues(name, out var v) || response.Content.Headers.TryGetValues(name, out v))
                return DecodeHeader(v.FirstOrDefault());
            return null;
        }

        // subscription-userinfo: upload=123; download=456; total=789; expire=1700000000
        var userInfo = H("subscription-userinfo");
        if (userInfo is not null)
        {
            foreach (var part in userInfo.Split(';', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
            {
                var kv = part.Split('=', 2, StringSplitOptions.TrimEntries);
                if (kv.Length != 2 || !double.TryParse(kv[1], System.Globalization.NumberStyles.Float,
                        System.Globalization.CultureInfo.InvariantCulture, out var number))
                    continue;
                var value = (long)number;
                switch (kv[0].ToLowerInvariant())
                {
                    case "upload": info.Upload = value; break;
                    case "download": info.Download = value; break;
                    case "total": info.Total = value; break;
                    case "expire": info.Expire = value > 0 ? DateTimeOffset.FromUnixTimeSeconds(value) : null; break;
                }
            }
        }

        info.Title = H("profile-title");
        info.SupportUrl = H("support-url");
        info.WebPageUrl = H("profile-web-page-url");
        info.Announce = H("announce");
        if (int.TryParse(H("profile-update-interval"), out var hours) && hours > 0)
            info.UpdateIntervalHours = hours;

        // Fallback title: filename from Content-Disposition.
        if (info.Title is null && response.Content.Headers.ContentDisposition is ContentDispositionHeaderValue cd)
            info.Title = (cd.FileNameStar ?? cd.FileName)?.Trim('"');
    }

    private static string? DecodeHeader(string? value)
    {
        if (string.IsNullOrWhiteSpace(value))
            return null;
        value = value.Trim();
        if (value.StartsWith("base64:", StringComparison.OrdinalIgnoreCase))
            return Base64.TryDecode(value[7..]) ?? value;
        return value;
    }

    private static readonly Regex DataBlock = new(@"<div[^>]*\bid=""data""[^>]*>", RegexOptions.IgnoreCase);
    private static readonly Regex DataAttr = new(@"data-([a-z-]+)=""([^""]*)""", RegexOptions.IgnoreCase);
    private static readonly Regex MailAttr = new(@"data-mail=""([^""]*)""", RegexOptions.IgnoreCase);
    private static readonly Regex LinkAttr = new(@"data-link=""([^""]*)""", RegexOptions.IgnoreCase);

    /// <summary>
    /// 3x-ui serves a subscription page to browsers. Its data block carries what
    /// the headers do not: account e-mails, last online time, support link, state.
    /// </summary>
    private async Task<bool> TryReadHtmlPageAsync(Uri uri, SubscriptionInfo info, CancellationToken ct)
    {
        try
        {
            using var cts = CancellationTokenSource.CreateLinkedTokenSource(ct);
            cts.CancelAfter(TimeSpan.FromSeconds(10));
            using var request = new HttpRequestMessage(HttpMethod.Get, uri);
            request.Headers.UserAgent.ParseAdd("Mozilla/5.0 (Windows NT 10.0; Win64; x64) VoidRay/1.0");
            request.Headers.Accept.ParseAdd("text/html,application/xhtml+xml");
            using var response = await _http.SendAsync(request, cts.Token);
            if (!response.IsSuccessStatusCode)
                return false;
            var html = await response.Content.ReadAsStringAsync(cts.Token);
            var block = DataBlock.Match(html);
            if (!block.Success)
                return false;

            var data = DataAttr.Matches(block.Value).ToDictionary(
                m => m.Groups[1].Value.ToLowerInvariant(),
                m => WebUtility.HtmlDecode(m.Groups[2].Value).Trim());
            string? D(string key) => data.TryGetValue(key, out var v) && v.Length > 0 ? v : null;
            long N(string key) => long.TryParse(D(key), out var n) ? n : 0;

            info.Enabled = D("enabled") != "0";
            info.Sid = D("sid") ?? info.Sid;
            info.SupportUrl ??= D("support");
            if (N("last-online") > 0)
                info.LastOnline = DateTimeOffset.FromUnixTimeMilliseconds(N("last-online"));
            if (info.Upload + info.Download == 0)
            {
                info.Upload = N("upload-byte");
                info.Download = N("download-byte");
            }
            if (info.Total == 0)
                info.Total = N("total-byte");
            if (info.Expire is null && N("expire") > 0)
                info.Expire = DateTimeOffset.FromUnixTimeSeconds(N("expire"));

            info.Emails = MailAttr.Matches(html)
                .Select(m => WebUtility.HtmlDecode(m.Groups[1].Value).Trim())
                .Where(e => e.Length > 0).Distinct().ToList();
            info.Username ??= info.Emails.FirstOrDefault();

            // Fallback when the plain subscription body could not be read.
            if (info.Servers.Count == 0)
            {
                var links = string.Join('\n', LinkAttr.Matches(html).Select(m => WebUtility.HtmlDecode(m.Groups[1].Value)));
                info.Servers = LinkParser.ParseSubscriptionBody(links, out _);
            }
            return true;
        }
        catch
        {
            return false;
        }
    }

    /// <summary>Marzban / Remnawave expose "{link}/info" with the username and status.</summary>
    private async Task TryReadInfoEndpointAsync(Uri uri, SubscriptionInfo info, CancellationToken ct)
    {
        try
        {
            var infoUri = new Uri(uri.GetLeftPart(UriPartial.Path).TrimEnd('/') + "/info");
            using var cts = CancellationTokenSource.CreateLinkedTokenSource(ct);
            cts.CancelAfter(TimeSpan.FromSeconds(8));
            using var request = new HttpRequestMessage(HttpMethod.Get, infoUri);
            request.Headers.UserAgent.ParseAdd(UserAgents[0]);
            using var response = await _http.SendAsync(request, cts.Token);
            if (!response.IsSuccessStatusCode)
                return;
            var json = await response.Content.ReadAsStringAsync(cts.Token);
            using var doc = JsonDocument.Parse(json);
            var root = doc.RootElement;
            // Remnawave wraps everything in { "response": { "user": { … } } }
            if (root.TryGetProperty("response", out var r))
                root = r.TryGetProperty("user", out var user) ? user : r;

            info.Username ??= Str(root, "username");
            info.Status ??= Str(root, "status") ?? Str(root, "userStatus");

            if (info.Total == 0 && Num(root, "data_limit", "trafficLimitBytes") is > 0 and var limit)
                info.Total = limit;
            if (info.Used == 0 && Num(root, "used_traffic", "trafficUsedBytes") is > 0 and var used)
                info.Download = used;
            if (info.Expire is null)
            {
                var exp = root.TryGetProperty("expire", out var e) ? e
                    : root.TryGetProperty("expiresAt", out e) ? e
                    : default;
                if (exp.ValueKind == JsonValueKind.Number && exp.GetInt64() > 0)
                    info.Expire = DateTimeOffset.FromUnixTimeSeconds(exp.GetInt64());
                else if (exp.ValueKind == JsonValueKind.String && DateTimeOffset.TryParse(exp.GetString(), out var dt))
                    info.Expire = dt;
            }
        }
        catch
        {
            // Optional endpoint: silently ignored when unavailable.
        }
    }

    private static string? Str(JsonElement e, string name) =>
        e.TryGetProperty(name, out var v) && v.ValueKind == JsonValueKind.String ? v.GetString() : null;

    private static long? Num(JsonElement e, params string[] names)
    {
        foreach (var name in names)
        {
            if (!e.TryGetProperty(name, out var v))
                continue;
            if (v.ValueKind == JsonValueKind.Number && v.TryGetInt64(out var n))
                return n;
            if (v.ValueKind == JsonValueKind.String && long.TryParse(v.GetString(), out n))
                return n;
        }
        return null;
    }
}
