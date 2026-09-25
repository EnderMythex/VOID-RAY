using System.Runtime.InteropServices;
using System.Text.Json;
using Microsoft.Win32;

namespace VoidRay.Services;

/// <summary>Sets / restores the Windows system proxy (WinINet, used by browsers and most apps).</summary>
public static class SystemProxy
{
    private const string KeyPath = @"Software\Microsoft\Windows\CurrentVersion\Internet Settings";
    private const string Bypass =
        "localhost;127.*;10.*;172.16.*;172.17.*;172.18.*;172.19.*;172.20.*;172.21.*;172.22.*;172.23.*;" +
        "172.24.*;172.25.*;172.26.*;172.27.*;172.28.*;172.29.*;172.30.*;172.31.*;192.168.*;<local>";

    private sealed record Snapshot(int ProxyEnable, string? ProxyServer, string? ProxyOverride, string? AutoConfigUrl);

    public static void Enable(string host, int port)
    {
        using var key = Registry.CurrentUser.OpenSubKey(KeyPath, writable: true)
                        ?? throw new InvalidOperationException("Paramètres Internet Windows inaccessibles.");

        var server = $"{host}:{port}";
        // Only back up when the current proxy is not already ours (crash recovery).
        if (!File.Exists(AppPaths.ProxyBackup) && (key.GetValue("ProxyServer") as string) != server)
        {
            var snapshot = new Snapshot(
                key.GetValue("ProxyEnable") is int e ? e : 0,
                key.GetValue("ProxyServer") as string,
                key.GetValue("ProxyOverride") as string,
                key.GetValue("AutoConfigURL") as string);
            File.WriteAllText(AppPaths.ProxyBackup, JsonSerializer.Serialize(snapshot));
        }

        key.SetValue("ProxyEnable", 1, RegistryValueKind.DWord);
        key.SetValue("ProxyServer", server, RegistryValueKind.String);
        key.SetValue("ProxyOverride", Bypass, RegistryValueKind.String);
        key.DeleteValue("AutoConfigURL", throwOnMissingValue: false);
        Refresh();
    }

    /// <summary>Restores the proxy settings saved by <see cref="Enable"/>. Safe to call repeatedly.</summary>
    public static void Restore()
    {
        try
        {
            using var key = Registry.CurrentUser.OpenSubKey(KeyPath, writable: true);
            if (key is null)
                return;

            Snapshot? snapshot = null;
            if (File.Exists(AppPaths.ProxyBackup))
            {
                try { snapshot = JsonSerializer.Deserialize<Snapshot>(File.ReadAllText(AppPaths.ProxyBackup)); }
                catch (JsonException) { }
            }
            else
            {
                return; // Nothing of ours to undo.
            }

            key.SetValue("ProxyEnable", snapshot?.ProxyEnable ?? 0, RegistryValueKind.DWord);
            SetOrDelete(key, "ProxyServer", snapshot?.ProxyServer);
            SetOrDelete(key, "ProxyOverride", snapshot?.ProxyOverride);
            SetOrDelete(key, "AutoConfigURL", snapshot?.AutoConfigUrl);
            File.Delete(AppPaths.ProxyBackup);
            Refresh();
        }
        catch
        {
            // Best effort.
        }
    }

    private static void SetOrDelete(RegistryKey key, string name, string? value)
    {
        if (value is null)
            key.DeleteValue(name, throwOnMissingValue: false);
        else
            key.SetValue(name, value, RegistryValueKind.String);
    }

    private const int INTERNET_OPTION_SETTINGS_CHANGED = 39;
    private const int INTERNET_OPTION_REFRESH = 37;

    [DllImport("wininet.dll", SetLastError = true)]
    private static extern bool InternetSetOption(IntPtr hInternet, int dwOption, IntPtr lpBuffer, int dwBufferLength);

    private static void Refresh()
    {
        InternetSetOption(IntPtr.Zero, INTERNET_OPTION_SETTINGS_CHANGED, IntPtr.Zero, 0);
        InternetSetOption(IntPtr.Zero, INTERNET_OPTION_REFRESH, IntPtr.Zero, 0);
    }
}
