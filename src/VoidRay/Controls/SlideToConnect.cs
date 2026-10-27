using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;

namespace VoidRay.Controls;

/// <summary>
/// Slide to connect: drag the knob to the end to turn the VPN on, back to the
/// start to turn it off. Enter / Space toggles too. The knob follows
/// <see cref="IsOn"/> when the real state changes.
/// Template parts: PART_Knob (its RenderTransform is replaced), PART_Fill, PART_Label.
/// </summary>
public sealed class SlideToConnect : Control
{
    public static readonly DependencyProperty IsOnProperty = DependencyProperty.Register(
        nameof(IsOn), typeof(bool), typeof(SlideToConnect),
        new PropertyMetadata(false, (d, _) => ((SlideToConnect)d).Settle(animate: true)));

    public static readonly DependencyProperty IsBusyProperty = DependencyProperty.Register(
        nameof(IsBusy), typeof(bool), typeof(SlideToConnect), new PropertyMetadata(false));

    public static readonly DependencyProperty LabelProperty = DependencyProperty.Register(
        nameof(Label), typeof(string), typeof(SlideToConnect), new PropertyMetadata(""));

    public static readonly DependencyProperty CommandProperty = DependencyProperty.Register(
        nameof(Command), typeof(ICommand), typeof(SlideToConnect), new PropertyMetadata(null));

    /// <summary>Knob position in pixels from the left; drives the visuals.</summary>
    public static readonly DependencyProperty OffsetProperty = DependencyProperty.Register(
        nameof(Offset), typeof(double), typeof(SlideToConnect),
        new PropertyMetadata(0.0, (d, _) => ((SlideToConnect)d).UpdateVisuals()));

    /// <summary>0 at the start, 1 at the end (read-only for templates).</summary>
    public static readonly DependencyProperty ProgressProperty = DependencyProperty.Register(
        nameof(Progress), typeof(double), typeof(SlideToConnect), new PropertyMetadata(0.0));

    public bool IsOn { get => (bool)GetValue(IsOnProperty); set => SetValue(IsOnProperty, value); }
    public bool IsBusy { get => (bool)GetValue(IsBusyProperty); set => SetValue(IsBusyProperty, value); }
    public string Label { get => (string)GetValue(LabelProperty); set => SetValue(LabelProperty, value); }
    public ICommand? Command { get => (ICommand?)GetValue(CommandProperty); set => SetValue(CommandProperty, value); }
    public double Offset { get => (double)GetValue(OffsetProperty); set => SetValue(OffsetProperty, value); }
    public double Progress { get => (double)GetValue(ProgressProperty); private set => SetValue(ProgressProperty, value); }

    private FrameworkElement? _knob;
    private FrameworkElement? _fill;
    private FrameworkElement? _label;
    private readonly TranslateTransform _shift = new();
    private bool _dragging;
    private double _grabX;
    private double _grabOffset;
    private double _travelled;

    public SlideToConnect()
    {
        Focusable = true;
        FocusVisualStyle = null;
        SizeChanged += (_, _) => Settle(animate: false);
    }

    private double Max => _knob is null ? 0 : Math.Max(0, ActualWidth - _knob.ActualWidth);

    public override void OnApplyTemplate()
    {
        if (_knob is not null)
        {
            _knob.MouseLeftButtonDown -= KnobDown;
            _knob.MouseMove -= KnobMove;
            _knob.MouseLeftButtonUp -= KnobUp;
            _knob.LostMouseCapture -= KnobLost;
        }
        base.OnApplyTemplate();
        _knob = GetTemplateChild("PART_Knob") as FrameworkElement;
        _fill = GetTemplateChild("PART_Fill") as FrameworkElement;
        _label = GetTemplateChild("PART_Label") as FrameworkElement;
        if (_knob is not null)
        {
            _knob.RenderTransform = _shift;
            _knob.MouseLeftButtonDown += KnobDown;
            _knob.MouseMove += KnobMove;
            _knob.MouseLeftButtonUp += KnobUp;
            _knob.LostMouseCapture += KnobLost;
            _knob.Cursor = Cursors.SizeWE;
        }
        Settle(animate: false);
    }

    private void KnobDown(object sender, MouseButtonEventArgs e)
    {
        if (IsBusy || _knob is null)
            return;
        Focus();
        _dragging = true;
        _travelled = 0;
        _grabX = e.GetPosition(this).X;
        _grabOffset = Offset;
        BeginAnimation(OffsetProperty, null);
        Offset = _grabOffset;
        _knob.CaptureMouse();
        e.Handled = true;
    }

    private void KnobMove(object sender, MouseEventArgs e)
    {
        if (!_dragging)
            return;
        var dx = e.GetPosition(this).X - _grabX;
        _travelled = Math.Max(_travelled, Math.Abs(dx));
        Offset = Math.Clamp(_grabOffset + dx, 0, Max);
    }

    private void KnobUp(object sender, MouseButtonEventArgs e)
    {
        if (!_dragging)
            return;
        _dragging = false;
        _knob?.ReleaseMouseCapture();
        Release();
        e.Handled = true;
    }

    private void KnobLost(object sender, MouseEventArgs e)
    {
        if (!_dragging)
            return;
        _dragging = false;
        Release();
    }

    private void Release()
    {
        var max = Max;
        if (max <= 0)
            return;
        if (_travelled < 4)
        {
            Nudge();
            return;
        }
        var turnOn = !IsOn && Offset > max * 0.82;
        var turnOff = IsOn && Offset < max * 0.18;
        if ((turnOn || turnOff) && Command?.CanExecute(null) == true)
        {
            // Stay where the finger left it; IsOn will animate the rest of the way.
            AnimateTo(turnOn ? max : 0, 180);
            Command.Execute(null);
        }
        else
        {
            AnimateTo(IsOn ? max : 0, 320, bounce: true);
        }
    }

    /// <summary>A click without a drag: hint at the gesture.</summary>
    private void Nudge()
    {
        var rest = IsOn ? Max : 0;
        var hint = IsOn ? rest - 26 : rest + 26;
        var anim = new DoubleAnimationUsingKeyFrames();
        anim.KeyFrames.Add(new EasingDoubleKeyFrame(hint, KeyTime.FromTimeSpan(TimeSpan.FromMilliseconds(160)),
            new CubicEase { EasingMode = EasingMode.EaseOut }));
        anim.KeyFrames.Add(new EasingDoubleKeyFrame(rest, KeyTime.FromTimeSpan(TimeSpan.FromMilliseconds(520)),
            new ElasticEase { Oscillations = 1, Springiness = 4, EasingMode = EasingMode.EaseOut }));
        BeginAnimation(OffsetProperty, anim);
    }

    protected override void OnKeyDown(KeyEventArgs e)
    {
        base.OnKeyDown(e);
        if (e.Key is Key.Enter or Key.Space && !IsBusy && Command?.CanExecute(null) == true)
        {
            Command.Execute(null);
            e.Handled = true;
        }
    }

    private void Settle(bool animate)
    {
        if (_dragging || _knob is null)
            return;
        var target = IsOn ? Max : 0;
        if (animate)
        {
            AnimateTo(target, 420, bounce: true);
        }
        else
        {
            BeginAnimation(OffsetProperty, null);
            Offset = target;
        }
    }

    private void AnimateTo(double to, int ms, bool bounce = false)
    {
        var anim = new DoubleAnimation(to, TimeSpan.FromMilliseconds(ms))
        {
            EasingFunction = bounce
                ? new BackEase { Amplitude = 0.25, EasingMode = EasingMode.EaseOut }
                : new CubicEase { EasingMode = EasingMode.EaseOut },
            FillBehavior = FillBehavior.HoldEnd,
        };
        BeginAnimation(OffsetProperty, anim);
    }

    private void UpdateVisuals()
    {
        var max = Max;
        var offset = Math.Clamp(Offset, 0, Math.Max(max, 0));
        _shift.X = offset;
        var progress = max > 0 ? offset / max : 0;
        Progress = progress;
        if (_fill is not null && _knob is not null)
            _fill.Width = offset + _knob.ActualWidth;
        if (_label is not null)
        {
            // The label fades as the knob travels away from its resting side.
            var away = IsOn ? 1 - progress : progress;
            _label.Opacity = IsBusy ? 1 : Math.Clamp(1 - away * 1.6, 0.12, 1);
        }
    }

    protected override void OnRenderSizeChanged(SizeChangedInfo sizeInfo)
    {
        base.OnRenderSizeChanged(sizeInfo);
        UpdateVisuals();
    }
}
