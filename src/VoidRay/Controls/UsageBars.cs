using System.Windows;
using System.Windows.Media;

namespace VoidRay.Controls;

/// <summary>Bar chart of the last 14 days of usage (null = no data that day).</summary>
public sealed class UsageBars : FrameworkElement
{
    public static readonly DependencyProperty ValuesProperty = DependencyProperty.Register(
        nameof(Values), typeof(IReadOnlyList<double?>), typeof(UsageBars),
        new FrameworkPropertyMetadata(null, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty BarProperty = DependencyProperty.Register(
        nameof(Bar), typeof(Brush), typeof(UsageBars),
        new FrameworkPropertyMetadata(Brushes.Teal, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty VoidProperty = DependencyProperty.Register(
        nameof(Void), typeof(Brush), typeof(UsageBars),
        new FrameworkPropertyMetadata(Brushes.Gray, FrameworkPropertyMetadataOptions.AffectsRender));

    public IReadOnlyList<double?>? Values
    {
        get => (IReadOnlyList<double?>?)GetValue(ValuesProperty);
        set => SetValue(ValuesProperty, value);
    }

    public Brush Bar { get => (Brush)GetValue(BarProperty); set => SetValue(BarProperty, value); }
    public Brush Void { get => (Brush)GetValue(VoidProperty); set => SetValue(VoidProperty, value); }

    protected override void OnRender(DrawingContext dc)
    {
        var values = Values;
        if (values is null || values.Count == 0 || ActualWidth <= 0)
            return;
        const double gap = 3;
        var w = ActualWidth;
        var h = ActualHeight;
        var bw = (w - gap * (values.Count - 1)) / values.Count;
        var peak = Math.Max(1, values.Where(v => v.HasValue).Select(v => v!.Value).DefaultIfEmpty(1).Max());

        for (var i = 0; i < values.Count; i++)
        {
            var x = i * (bw + gap);
            if (values[i] is not { } v)
            {
                dc.DrawRectangle(Void, null, new Rect(x, h - 2, bw, 2));
                continue;
            }
            var bh = Math.Max(2, v / peak * h);
            dc.DrawRectangle(Bar, null, new Rect(x, h - bh, bw, bh));
        }
    }
}
