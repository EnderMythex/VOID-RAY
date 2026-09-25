using System.ComponentModel;
using System.Globalization;

namespace VoidRay.Services;

/// <summary>
/// French / English strings. XAML binds with
/// <c>{Binding [key], Source={x:Static svc:Loc.I}}</c> and refreshes live on switch.
/// </summary>
public sealed class Loc : INotifyPropertyChanged
{
    public static Loc I { get; } = new();

    private static readonly Dictionary<string, (string En, string Fr)> Strings = new()
    {
        // ---- gate
        ["gateTitle"] = ("Connect your subscription", "Connecte ton abonnement"),
        ["gateText"] = ("Paste the subscription link you received from EnderrVPN. Only sub.enderr.win links are accepted.",
                        "Colle le lien d'abonnement reçu d'EnderrVPN. Seuls les liens sub.enderr.win sont acceptés."),
        ["gateLabel"] = ("Subscription link", "Lien d'abonnement"),
        ["gateButton"] = ("Continue", "Continuer"),
        ["gateChecking"] = ("Checking…", "Vérification…"),
        ["gateHelp"] = ("No subscription yet?", "Pas encore d'abonnement ?"),
        ["gateContact"] = ("Contact support", "Contacter l'assistance"),
        ["errFormat"] = ("This is not an EnderrVPN link. It should look like https://sub.enderr.win/ender/…",
                         "Ce n'est pas un lien EnderrVPN. Il doit ressembler à https://sub.enderr.win/ender/…"),
        ["errNotFound"] = ("Subscription not found. Check your link.", "Abonnement introuvable. Vérifie ton lien."),
        ["errNetwork"] = ("Can't reach sub.enderr.win. Check your internet connection.",
                          "Impossible de joindre sub.enderr.win. Vérifie ta connexion internet."),
        ["errEmpty"] = ("This subscription has no server.", "Cet abonnement ne contient aucun serveur."),
        ["errRevoked"] = ("Your subscription is no longer valid. Enter a new link.",
                          "Ton abonnement n'est plus valide. Entre un nouveau lien."),

        // ---- top bar
        ["themeAuto"] = ("System theme", "Thème automatique"),
        ["themeDark"] = ("Dark theme", "Thème sombre"),
        ["themeLight"] = ("Light theme", "Thème clair"),
        ["langLabel"] = ("Language", "Langue"),
        ["accentLabel"] = ("Accent colour", "Couleur d'accent"),
        ["minimize"] = ("Minimise", "Réduire"),
        ["maximize"] = ("Maximise", "Agrandir"),
        ["close"] = ("Close", "Fermer"),

        // ---- gauge / usage
        ["gaugeTitle"] = ("Data usage", "Consommation"),
        ["upload"] = ("upload", "envoyé"),
        ["download"] = ("download", "reçu"),
        ["history"] = ("Daily usage", "Consommation par jour"),
        ["histWait"] = ("Come back tomorrow to see your daily usage.", "Reviens demain pour voir ta consommation quotidienne."),
        ["histAvg"] = ("{0}/day", "{0}/jour"),
        ["capUsed"] = ("used · no limit", "consommés · sans limite"),
        ["capLeft"] = ("left of {0}", "restants sur {0}"),
        ["stActive"] = ("Active", "Actif"),
        ["stUnlimited"] = ("Unlimited", "Illimité"),
        ["stAlmost"] = ("Almost out", "Presque épuisé"),
        ["stQuota"] = ("Quota reached", "Quota atteint"),
        ["stExpired"] = ("Expired", "Expiré"),
        ["stDisabled"] = ("Disabled", "Désactivé"),

        // ---- details
        ["details"] = ("Subscription details", "Détails de l'abonnement"),
        ["rowId"] = ("Subscription ID", "Identifiant"),
        ["rowAccount"] = ("Account", "Compte"),
        ["rowAccounts"] = ("Accounts", "Comptes"),
        ["rowStatus"] = ("Status", "État"),
        ["rowDown"] = ("Downloaded", "Reçu"),
        ["rowUp"] = ("Uploaded", "Envoyé"),
        ["rowUsed"] = ("Data used", "Trafic utilisé"),
        ["rowQuota"] = ("Total quota", "Quota total"),
        ["rowLeft"] = ("Data left", "Trafic restant"),
        ["rowOnline"] = ("Last online", "Dernière connexion"),
        ["rowExpiry"] = ("Expiry", "Expiration"),
        ["noExpiry"] = ("No expiry", "Aucune expiration"),
        ["expired"] = ("expired", "expiré"),
        ["today"] = ("today", "aujourd'hui"),
        ["inDay"] = ("in {0} day", "dans {0} jour"),
        ["inDays"] = ("in {0} days", "dans {0} jours"),
        ["neverSeen"] = ("Never seen", "Jamais vu"),
        ["justNow"] = ("just now", "à l'instant"),
        ["minsAgo"] = ("{0} min ago", "il y a {0} min"),
        ["hoursAgo"] = ("{0} h ago", "il y a {0} h"),
        ["daysAgo"] = ("{0} days ago", "il y a {0} jours"),
        ["copyId"] = ("Copy subscription ID", "Copier l'identifiant"),

        // ---- connection
        ["connection"] = ("Connection", "Connexion"),
        ["stateOff"] = ("Not protected", "Non protégé"),
        ["stateConnecting"] = ("Connecting…", "Connexion…"),
        ["stateOn"] = ("Protected", "Protégé"),
        ["stateDisconnecting"] = ("Disconnecting…", "Déconnexion…"),
        ["hintOff"] = ("Click the button to connect", "Clique sur le bouton pour te connecter"),
        ["hintOn"] = ("Click the button to disconnect", "Clique sur le bouton pour te déconnecter"),
        ["hintWait"] = ("Hold on a second…", "Patiente un instant…"),
        ["noServer"] = ("No server selected", "Aucun serveur sélectionné"),
        ["duration"] = ("duration", "durée"),
        ["publicIp"] = ("public IP", "IP publique"),
        ["location"] = ("location", "localisation"),
        ["realDelay"] = ("latency", "latence"),

        // ---- link / configs
        ["link"] = ("Subscription link", "Lien d'abonnement"),
        ["copy"] = ("Copy", "Copier"),
        ["refresh"] = ("Refresh", "Actualiser"),
        ["changeLink"] = ("Use another link", "Changer de lien"),
        ["configs"] = ("Configurations", "Configurations"),
        ["testPing"] = ("Test ping", "Tester le ping"),
        ["noConfigs"] = ("No configurations yet.", "Aucune configuration pour le moment."),
        ["bypassTip"] = ("Firewall Bypass settings applied automatically (Cloudflare · TLS)",
                         "Réglages Firewall Bypass appliqués automatiquement (Cloudflare · TLS)"),

        // ---- footer / misc
        ["support"] = ("Support", "Assistance"),
        ["log"] = ("Log", "Journal"),
        ["clear"] = ("Clear", "Effacer"),
        ["freshNow"] = ("updated just now", "mis à jour à l'instant"),
        ["freshMin"] = ("updated {0} min ago", "mis à jour il y a {0} min"),
        ["freshHour"] = ("updated {0} h ago", "mis à jour il y a {0} h"),
        ["freshFail"] = ("update failed", "mise à jour impossible"),
        ["copied"] = ("{0} copied", "{0} copié"),
        ["lblLink"] = ("Link", "Lien"),
        ["lblConfig"] = ("Configuration", "Configuration"),
        ["lblId"] = ("Subscription ID", "Identifiant"),
        ["lblLog"] = ("Log", "Journal"),
        ["loaded"] = ("Subscription loaded · {0} server(s)", "Abonnement chargé · {0} serveur(s)"),
        ["bypassApplied"] = ("Firewall Bypass configured automatically", "Firewall Bypass configuré automatiquement"),
        ["pickServer"] = ("Pick a server in the list.", "Choisis un serveur dans la liste."),
        ["errConnect"] = ("Connection failed: {0}", "Échec de connexion : {0}"),
        ["errNoTraffic"] = ("Connected, but no traffic gets through. Try another server.",
                            "Connecté, mais aucun trafic ne passe. Essaie un autre serveur."),
        ["errCore"] = ("The Xray engine stopped. See the log.", "Le moteur Xray s'est arrêté. Consulte le journal."),
        ["failed"] = ("failed", "échec"),

        // ---- connection mode
        ["modeTun"] = ("Whole PC (TUN)", "Tout le PC (TUN)"),
        ["modeProxy"] = ("Browsers (proxy)", "Navigateurs (proxy)"),
        ["modeTunHint"] = ("Every app and game goes through the VPN.", "Toutes les applis et les jeux passent par le VPN."),
        ["modeProxyHint"] = ("Only apps that follow the Windows proxy (browsers…).", "Seulement les applis qui suivent le proxy Windows (navigateurs…)."),
        ["adminText"] = ("TUN mode needs administrator rights to create the virtual network card.\n\nRestart VOID-RAY as administrator and connect?",
                         "Le mode TUN a besoin des droits administrateur pour créer la carte réseau virtuelle.\n\nRedémarrer VOID-RAY en administrateur et se connecter ?"),
        ["adminDeclined"] = ("Administrator rights refused. Use Browsers (proxy) mode or try again.",
                             "Droits administrateur refusés. Utilise le mode Navigateurs (proxy) ou réessaie."),

        // ---- tray
        ["trayOpen"] = ("Open VOID-RAY", "Ouvrir VOID-RAY"),
        ["trayConnect"] = ("Connect", "Se connecter"),
        ["trayDisconnect"] = ("Disconnect", "Se déconnecter"),
        ["trayQuit"] = ("Quit", "Quitter"),
        ["trayStillRunning"] = ("VOID-RAY keeps running in the notification area. Right-click the icon to quit.",
                                "VOID-RAY reste ouvert dans la zone de notification. Clic droit sur l'icône pour quitter."),
        ["closeToTray"] = ("Close to notification area", "Fermer dans la zone de notification"),
    };

    private string _mode = "auto";

    public event PropertyChangedEventHandler? PropertyChanged;

    public string this[string key] => T(key);

    /// <summary>"auto", "en" or "fr".</summary>
    public string Mode => _mode;

    /// <summary>Resolved language: "en" or "fr".</summary>
    public string Lang { get; private set; } = "en";

    public CultureInfo Culture => CultureInfo.GetCultureInfo(Lang == "fr" ? "fr-FR" : "en-GB");

    public event Action? Changed;

    public void SetMode(string mode)
    {
        _mode = mode is "en" or "fr" ? mode : "auto";
        Lang = _mode != "auto" ? _mode
            : CultureInfo.CurrentUICulture.TwoLetterISOLanguageName == "fr" ? "fr" : "en";
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs("Item[]"));
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(Mode)));
        Changed?.Invoke();
    }

    public static string T(string key, params object[] args)
    {
        if (!Strings.TryGetValue(key, out var pair))
            return key;
        var s = I.Lang == "fr" ? pair.Fr : pair.En;
        return args.Length == 0 ? s : string.Format(I.Culture, s, args);
    }
}
