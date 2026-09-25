namespace VoidRay.Services;

public static class AppPaths
{
    public static string Data { get; } = Ensure(Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VoidRay"));

    public static string Core { get; } = Ensure(Path.Combine(Data, "core"));

    public static string Settings => Path.Combine(Data, "settings.json");
    public static string XrayConfig => Path.Combine(Data, "config.json");
    public static string ProxyBackup => Path.Combine(Data, "proxy-backup.json");

    private static string Ensure(string dir)
    {
        Directory.CreateDirectory(dir);
        return dir;
    }
}
