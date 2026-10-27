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
            bg = Hex("#f4f5f9"); ink = Hex("#0b0d14"); ink2 = Hex("#3b4459"); muted = Hex("#697289");
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
            Set(r, "SurfaceBrush", A(lineBase, .045));
            Set(r, "SurfaceHoverBrush", A(lineBase, .075));
            r["GlassBrush"] = Gradient(A(white, .82), A(white, .62));
            Set(r, "GlassLineBrush", A(lineBase, .08));
            Set(r, "GlassHiBrush", A(white, 1));
            Set(r, "SolidBrush", Hex("#0b0d12"));
            Set(r, "OnSolidBrush", white);
            Set(r, "DrawerBrush", Hex("#fbfbfd"));
            Set(r, "ScrimBrush", A(Hex("#0b1020"), .28));
            Set(r, "VeilBrush", Hex("#dfe0e8"));
            r["WarmColor"] = Hex("#ffb27a");
            r["AuroraOpacity"] = 0.55;
        }
        else
        {
            bg = Hex("#05060a"); ink = Hex("#eef1f7"); ink2 = Hex("#b3bbcc"); muted = Hex("#7a8399");
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
            Set(r, "SurfaceBrush", A(white, .05));
            Set(r, "SurfaceHoverBrush", A(white, .085));
            r["GlassBrush"] = Gradient(A(white, .075), A(white, .028));
            Set(r, "GlassLineBrush", A(white, .09));
            Set(r, "GlassHiBrush", A(white, .16));
            Set(r, "SolidBrush", Hex("#f2f4f8"));
            Set(r, "OnSolidBrush", Hex("#0b0d12"));
            Set(r, "DrawerBrush", Hex("#0c0e14"));
            Set(r, "ScrimBrush", A(Colors.Black, .45));
            Set(r, "VeilBrush", Hex("#232835"));
            r["WarmColor"] = Hex("#ff9a62");
            r["AuroraOpacity"] = 0.75;
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

        // Aurora behind the window and the orb's colour field.
        var warm = (Color)r["WarmColor"];
        var pink = Mix(warm, Hex("#ff7eb6"), .6);
        r["AuroraABrush"] = Radial(accent2);
        r["AuroraBBrush"] = Radial(accent);
        r["AuroraCBrush"] = Radial(warm);
        r["OrbBaseBrush"] = new LinearGradientBrush(Mix(accent2, Colors.White, .45), Mix(accent, Colors.White, .35), 45);
        r["OrbBlobABrush"] = Radial(warm);
        r["OrbBlobBBrush"] = Radial(accent2);
        r["OrbBlobCBrush"] = Radial(accent);
        r["OrbBlobDBrush"] = Radial(pink);
        r["HaloBrush"] = Radial(A(accent, .55));
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
