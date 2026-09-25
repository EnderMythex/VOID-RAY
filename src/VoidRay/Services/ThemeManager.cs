using System.Windows;
using System.Windows.Media;
using Microsoft.Win32;

namespace VoidRay.Services;

/// <summary>
/// Dark / light theme and accent colour, mirroring the subscription page.
/// Every brush lives in Application.Resources and is read with DynamicResource,
/// so switching repaints the whole window instantly.
/// </summary>
public static class ThemeManager
{
    public sealed record Accent(string Name, Color Dark, Color Light);

    // Each preset carries a dark and a light shade: the same hue needs more
    // saturation and less lightness to stay legible on a white panel.
    public static IReadOnlyList<Accent> Accents { get; } = new[]
    {
        new Accent("teal", Hex("#52d6bd"), Hex("#0f9d86")),
        new Accent("indigo", Hex("#7f97ff"), Hex("#3f55cf")),
        new Accent("violet", Hex("#b085ff"), Hex("#7440d4")),
        new Accent("rose", Hex("#ff8aab"), Hex("#cf3560")),
        new Accent("amber", Hex("#f0b95f"), Hex("#a5720c")),
        new Accent("lime", Hex("#9ede5a"), Hex("#4d8c19")),
    };

    public static bool IsLight { get; private set; }

    public static bool SystemPrefersLight()
    {
        try
        {
            using var key = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize");
            return key?.GetValue("AppsUseLightTheme") is int v && v == 1;
        }
        catch
        {
            return false;
        }
    }

    public static void Apply(string mode, int accentIndex)
    {
        IsLight = mode == "light" || (mode == "auto" && SystemPrefersLight());
        var accentPreset = Accents[Math.Clamp(accentIndex, 0, Accents.Count - 1)];
        var r = Application.Current.Resources;

        Color bg, ink, ink2, muted, accent, accent2, warn, alert, white;
        if (IsLight)
        {
            bg = Hex("#f2f4f9"); ink = Hex("#0d1220"); ink2 = Hex("#3d4761"); muted = Hex("#5f6982");
            accent = accentPreset.Light; accent2 = Hex("#4a5fd4"); warn = Hex("#a8730d"); alert = Hex("#cf3450");
            white = Colors.White;
            var lineBase = Hex("#0f1629");
            Set(r, "LineBrush", A(lineBase, .09));
            Set(r, "Line2Brush", A(lineBase, .06));
            Set(r, "HiBrush", A(white, .95));
            Set(r, "FieldBrush", A(white, .55));
            Set(r, "FieldHoverBrush", A(white, .85));
            Set(r, "ScrollThumbBrush", A(Hex("#141c32"), .2));
            r["PanelBrush"] = Gradient(A(white, .85), A(white, .55));
            r["ShadowColor"] = Hex("#18213a");
            r["ShadowOpacity"] = 0.18;
            r["AmbientOpacity"] = 0.30;
        }
        else
        {
            bg = Hex("#07090e"); ink = Hex("#e8ecf4"); ink2 = Hex("#aab3c6"); muted = Hex("#737e96");
            accent = accentPreset.Dark; accent2 = Hex("#6d86ff"); warn = Hex("#e2b155"); alert = Hex("#ee6079");
            white = Colors.White;
            Set(r, "LineBrush", A(white, .075));
            Set(r, "Line2Brush", A(white, .05));
            Set(r, "HiBrush", A(white, .14));
            Set(r, "FieldBrush", A(white, .035));
            Set(r, "FieldHoverBrush", A(white, .06));
            Set(r, "ScrollThumbBrush", A(white, .14));
            r["PanelBrush"] = Gradient(A(white, .055), A(white, .018));
            r["ShadowColor"] = Colors.Black;
            r["ShadowOpacity"] = 0.55;
            r["AmbientOpacity"] = 0.42;
        }

        Set(r, "BgBrush", bg);
        Set(r, "InkBrush", ink);
        Set(r, "Ink2Brush", ink2);
        Set(r, "MutedBrush", muted);
        Set(r, "AccentBrush", accent);
        Set(r, "Accent2Brush", accent2);
        Set(r, "WarnBrush", warn);
        Set(r, "AlertBrush", alert);
        r["AccentColor"] = accent;
        r["Accent2Color"] = accent2;

        // color-mix() equivalents used by chips, tags and highlighted buttons.
        Set(r, "AccentSoftBrush", A(accent, .15));
        Set(r, "AccentLineBrush", A(accent, .32));
        Set(r, "AccentTextBrush", Mix(accent, ink, .12));
        Set(r, "Accent2SoftBrush", A(accent2, .18));
        Set(r, "Accent2LineBrush", A(accent2, .32));
        Set(r, "Accent2TextBrush", Mix(accent2, ink, .10));
        Set(r, "WarnSoftBrush", A(warn, .15));
        Set(r, "WarnLineBrush", A(warn, .30));
        Set(r, "AlertSoftBrush", A(alert, .15));
        Set(r, "AlertLineBrush", A(alert, .32));
        Set(r, "SelectionBrush", A(accent, .30));
        Set(r, "BarBrush", A(accent, .58));

        r["AmbientABrush"] = Radial(accent2);
        r["AmbientBBrush"] = Radial(accent);
        r["BrandGradient"] = new LinearGradientBrush(accent2, accent, 0);
    }

    private static void Set(ResourceDictionary r, string key, Color c)
    {
        var brush = new SolidColorBrush(c);
        brush.Freeze();
        r[key] = brush;
    }

    private static Brush Gradient(Color from, Color to)
    {
        var b = new LinearGradientBrush(from, to, new Point(0, 0), new Point(0.45, 1));
        b.Freeze();
        return b;
    }

    private static Brush Radial(Color c)
    {
        var b = new RadialGradientBrush(new GradientStopCollection
        {
            new GradientStop(c, 0),
            new GradientStop(Color.FromArgb(0, c.R, c.G, c.B), 1),
        });
        b.Freeze();
        return b;
    }

    public static Color Hex(string hex) => (Color)ColorConverter.ConvertFromString(hex);

    private static Color A(Color c, double alpha) => Color.FromArgb((byte)Math.Round(alpha * 255), c.R, c.G, c.B);

    private static Color Mix(Color a, Color b, double t) => Color.FromRgb(
        (byte)(a.R + (b.R - a.R) * t), (byte)(a.G + (b.G - a.G) * t), (byte)(a.B + (b.B - a.B) * t));
}
