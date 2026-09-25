using System.ComponentModel;
using System.Diagnostics;
using System.Security.Principal;

namespace VoidRay.Services;

/// <summary>The TUN adapter can only be created by an administrator.</summary>
public static class Elevation
{
    public const string RestartArg = "--elevated-restart";
    public const string ConnectArg = "--connect";

    public static bool IsAdministrator()
    {
        using var identity = WindowsIdentity.GetCurrent();
        return new WindowsPrincipal(identity).IsInRole(WindowsBuiltInRole.Administrator);
    }

    /// <summary>Starts a new elevated instance. False when the user declined the UAC prompt.</summary>
    public static bool StartElevated(bool connect)
    {
        var exe = Environment.ProcessPath ?? Process.GetCurrentProcess().MainModule!.FileName;
        try
        {
            Process.Start(new ProcessStartInfo(exe, RestartArg + (connect ? " " + ConnectArg : ""))
            {
                UseShellExecute = true,
                Verb = "runas",
            });
            return true;
        }
        catch (Win32Exception ex) when (ex.NativeErrorCode == 1223)
        {
            return false; // "The operation was canceled by the user."
        }
    }
}
