using System.ComponentModel;
using System.Windows;
using VoidRay.ViewModels;
using Forms = System.Windows.Forms;
using Drawing = System.Drawing;

namespace VoidRay.Services;

/// <summary>
/// Notification-area icon: closing the window hides it here and the VPN keeps
/// running. Double-click reopens it; the menu connects, disconnects or quits.
/// </summary>
public sealed class TrayIcon : IDisposable
{
    private readonly MainWindow _window;
    private readonly MainViewModel _vm;
    private readonly Forms.NotifyIcon _icon;
    private readonly Forms.ToolStripMenuItem _open, _toggle, _quit;
    private readonly Drawing.Icon _idleIcon, _onIcon;
    private bool _hintShown;

    public TrayIcon(MainWindow window, MainViewModel vm)
    {
        _window = window;
        _vm = vm;

        using (var stream = Application.GetResourceStream(new Uri("pack://application:,,,/Assets/voidray.ico"))!.Stream)
            _idleIcon = new Drawing.Icon(stream, Forms.SystemInformation.SmallIconSize);
        _onIcon = WithDot(_idleIcon);

        _open = new Forms.ToolStripMenuItem { Font = new Drawing.Font(Forms.Control.DefaultFont, Drawing.FontStyle.Bold) };
        _open.Click += (_, _) => _window.ShowFromTray();
        _toggle = new Forms.ToolStripMenuItem();
        _toggle.Click += (_, _) =>
        {
            if (_vm.ToggleConnectionCommand.CanExecute(null))
                _vm.ToggleConnectionCommand.Execute(null);
        };
        _quit = new Forms.ToolStripMenuItem();
        _quit.Click += (_, _) => _window.Quit();

        var menu = new Forms.ContextMenuStrip { Renderer = new DarkRenderer(), ShowImageMargin = false };
        menu.Items.AddRange(new Forms.ToolStripItem[] { _open, new Forms.ToolStripSeparator(), _toggle, new Forms.ToolStripSeparator(), _quit });

        _icon = new Forms.NotifyIcon { Icon = _idleIcon, ContextMenuStrip = menu, Visible = true };
        _icon.MouseClick += (_, e) =>
        {
            if (e.Button == Forms.MouseButtons.Left)
                _window.ShowFromTray();
        };

        _vm.PropertyChanged += OnViewModelChanged;
        Loc.I.Changed += Refresh;
        Refresh();
    }

    /// <summary>Tells the user once per session that closing did not quit.</summary>
    public void NotifyStillRunning()
    {
        if (_hintShown)
            return;
        _hintShown = true;
        _icon.ShowBalloonTip(4000, "VOID-RAY", Loc.T("trayStillRunning"), Forms.ToolTipIcon.None);
    }

    private void OnViewModelChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName is nameof(MainViewModel.State) or nameof(MainViewModel.SelectedServerName)
            or nameof(MainViewModel.IsAuthenticated))
            Refresh();
    }

    private void Refresh()
    {
        var connected = _vm.State == ConnectionState.Connected;
        _open.Text = Loc.T("trayOpen");
        _toggle.Text = Loc.T(connected ? "trayDisconnect" : "trayConnect");
        _toggle.Enabled = _vm.IsAuthenticated && _vm.State is ConnectionState.Connected or ConnectionState.Disconnected;
        _quit.Text = Loc.T("trayQuit");
        _icon.Icon = connected ? _onIcon : _idleIcon;

        // NotifyIcon.Text is limited to 127 characters.
        var text = $"VOID-RAY — {_vm.StateText}";
        if (connected)
            text += "\n" + _vm.SelectedServerName;
        _icon.Text = text.Length > 127 ? text[..127] : text;
    }

    /// <summary>Same icon with a green dot, shown while the tunnel is up.</summary>
    private static Drawing.Icon WithDot(Drawing.Icon source)
    {
        using var bmp = source.ToBitmap();
        using (var g = Drawing.Graphics.FromImage(bmp))
        {
            g.SmoothingMode = Drawing.Drawing2D.SmoothingMode.AntiAlias;
            var d = bmp.Width * 0.46f;
            var x = bmp.Width - d;
            var y = bmp.Height - d;
            using var ring = new Drawing.SolidBrush(Drawing.Color.FromArgb(8, 8, 12));
            using var dot = new Drawing.SolidBrush(Drawing.Color.FromArgb(52, 211, 153));
            g.FillEllipse(ring, x - 1, y - 1, d + 1, d + 1);
            g.FillEllipse(dot, x + 1, y + 1, d - 3, d - 3);
        }
        return Drawing.Icon.FromHandle(bmp.GetHicon());
    }

    public void Dispose()
    {
        _vm.PropertyChanged -= OnViewModelChanged;
        Loc.I.Changed -= Refresh;
        _icon.Visible = false;
        _icon.Dispose();
    }

    private sealed class DarkRenderer : Forms.ToolStripProfessionalRenderer
    {
        public DarkRenderer() : base(new DarkColors()) => RoundedEdges = false;

        protected override void OnRenderItemText(Forms.ToolStripItemTextRenderEventArgs e)
        {
            e.TextColor = e.Item.Enabled ? Drawing.Color.FromArgb(232, 236, 244) : Drawing.Color.FromArgb(115, 126, 150);
            base.OnRenderItemText(e);
        }
    }

    private sealed class DarkColors : Forms.ProfessionalColorTable
    {
        private static readonly Drawing.Color Bg = Drawing.Color.FromArgb(14, 17, 24);
        private static readonly Drawing.Color Hover = Drawing.Color.FromArgb(30, 35, 46);
        private static readonly Drawing.Color Line = Drawing.Color.FromArgb(40, 45, 58);

        public override Drawing.Color ToolStripDropDownBackground => Bg;
        public override Drawing.Color MenuBorder => Line;
        public override Drawing.Color MenuItemBorder => Hover;
        public override Drawing.Color MenuItemSelected => Hover;
        public override Drawing.Color SeparatorDark => Line;
        public override Drawing.Color SeparatorLight => Bg;
        public override Drawing.Color ImageMarginGradientBegin => Bg;
        public override Drawing.Color ImageMarginGradientMiddle => Bg;
        public override Drawing.Color ImageMarginGradientEnd => Bg;
    }
}
