using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Animation;

namespace VoidRay.Controls;

/// <summary>
/// The dial of the subscription page: 52 ticks on a 240° arc. With a quota the
/// ticks fill up to the used ratio; without one a glow sweeps the arc.
/// </summary>
public sealed class TickGauge : FrameworkElement
{
    private const int N = 52;
    private const double Start = 150, Sweep = 240, RIn = 66, ROut = 80;

    public static readonly DependencyProperty RatioProperty = Register(nameof(Ratio), 0.0);
    public static readonly DependencyProperty UnlimitedProperty = Register(nameof(Unlimited), true);
    public static readonly DependencyProperty LiveProperty = Register(nameof(Live), true);
    public static readonly DependencyProperty PhaseProperty = Register(nameof(Phase), 0.0);
    public static readonly DependencyProperty ToneProperty = Register<Brush>(nameof(Tone), Brushes.Teal);
    public static readonly DependencyProperty IdleProperty = Register<Brush>(nameof(Idle), Brushes.Gray);

    public double Ratio { get => (double)GetValue(RatioProperty); set => SetValue(RatioProperty, value); }
    public bool Unlimited { get => (bool)GetValue(UnlimitedProperty); set => SetValue(UnlimitedProperty, value); }
    public bool Live { get => (bool)GetValue(LiveProperty); set => SetValue(LiveProperty, value); }
    public double Phase { get => (double)GetValue(PhaseProperty); set => SetValue(PhaseProperty, value); }
    public Brush Tone { get => (Brush)GetValue(ToneProperty); set => SetValue(ToneProperty, value); }
    public Brush Idle { get => (Brush)GetValue(IdleProperty); set => SetValue(IdleProperty, value); }

    public TickGauge()
    {
        Loaded += (_, _) => BeginAnimation(PhaseProperty, new DoubleAnimation(0, 1, TimeSpan.FromSeconds(5.5))
        {
            RepeatBehavior = RepeatBehavior.Forever,
        });
        Unloaded += (_, _) => BeginAnimation(PhaseProperty, null);
    }

    private static DependencyProperty Register<T>(string name, T fallback) =>
        DependencyProperty.Register(name, typeof(T), typeof(TickGauge),
            new FrameworkPropertyMetadata(fallback, FrameworkPropertyMetadataOptions.AffectsRender));

    protected override Size MeasureOverride(Size available) => new(200, 152);

    protected override void OnRender(DrawingContext dc)
    {
        var scale = Math.Min(ActualWidth / 200, ActualHeight / 152);
        if (scale <= 0)
            return;
        var ox = (ActualWidth - 200 * scale) / 2;
        var tone = (Tone as SolidColorBrush)?.Color ?? Colors.Teal;
        var idle = (Idle as SolidColorBrush)?.Color ?? Colors.Gray;
        var lit = (int)Math.Round(Math.Clamp(Ratio, 0, 1) * N);

        for (var i = 0; i < N; i++)
        {
            var a = (Start + Sweep * (i / (double)(N - 1))) * Math.PI / 180;
            var cos = Math.Cos(a);
            var sin = Math.Sin(a);

            double glow; // 0 = idle tick, 1 = fully lit
            if (!Unlimited)
            {
                glow = i < lit ? 1 : 0;
            }
            else if (!Live)
            {
                glow = 0;
            }
            else
            {
                // Same keyframes as the page: bright at 5 %, back to dim at 13 %.
                var ph = (Phase + (1 - i / (double)N)) % 1;
                glow = ph < 0.05 ? ph / 0.05 : ph < 0.13 ? 1 - (ph - 0.05) / 0.08 : 0;
            }

            var color = Lerp(idle, tone, glow);
            var opacity = 0.16 + 0.84 * glow;
            var pen = new Pen(new SolidColorBrush(color) { Opacity = opacity }, 2.9 * scale)
            {
                StartLineCap = PenLineCap.Round,
                EndLineCap = PenLineCap.Round,
            };
            dc.DrawLine(pen,
                new Point(ox + (100 + RIn * cos) * scale, (100 + RIn * sin) * scale),
                new Point(ox + (100 + ROut * cos) * scale, (100 + ROut * sin) * scale));
        }
    }

    private static Color Lerp(Color a, Color b, double t) => Color.FromRgb(
        (byte)(a.R + (b.R - a.R) * t), (byte)(a.G + (b.G - a.G) * t), (byte)(a.B + (b.B - a.B) * t));
}
