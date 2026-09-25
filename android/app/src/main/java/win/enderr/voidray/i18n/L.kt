package win.enderr.voidray.i18n

import java.util.Locale

/** French / English strings, switchable at runtime ("auto" follows the phone). */
object L {
    @Volatile var lang: String = "en"
        private set
    var mode: String = "auto"
        private set

    val locale: Locale get() = if (lang == "fr") Locale.FRANCE else Locale.UK

    fun setMode(value: String) {
        mode = if (value == "en" || value == "fr") value else "auto"
        lang = if (mode != "auto") mode else if (Locale.getDefault().language == "fr") "fr" else "en"
    }

    fun t(key: String, vararg args: Any): String {
        val pair = strings[key] ?: return key
        val s = if (lang == "fr") pair.second else pair.first
        return if (args.isEmpty()) s else String.format(locale, s, *args)
    }

    private val strings: Map<String, Pair<String, String>> = mapOf(
        // gate
        "gateTitle" to ("Connect your subscription" to "Connecte ton abonnement"),
        "gateText" to ("Paste the subscription link you received from EnderrVPN. Only sub.enderr.win links are accepted." to
            "Colle le lien d'abonnement reçu d'EnderrVPN. Seuls les liens sub.enderr.win sont acceptés."),
        "gateLabel" to ("Subscription link" to "Lien d'abonnement"),
        "gateButton" to ("Continue" to "Continuer"),
        "gateChecking" to ("Checking…" to "Vérification…"),
        "gateHelp" to ("No subscription yet?" to "Pas encore d'abonnement ?"),
        "gateContact" to ("Contact support" to "Contacter l'assistance"),
        "paste" to ("Paste" to "Coller"),
        "errFormat" to ("This is not an EnderrVPN link. It should look like https://sub.enderr.win/ender/…" to
            "Ce n'est pas un lien EnderrVPN. Il doit ressembler à https://sub.enderr.win/ender/…"),
        "errNotFound" to ("Subscription not found. Check your link." to "Abonnement introuvable. Vérifie ton lien."),
        "errNetwork" to ("Can't reach sub.enderr.win. Check your internet connection." to
            "Impossible de joindre sub.enderr.win. Vérifie ta connexion internet."),
        "errEmpty" to ("This subscription has no server." to "Cet abonnement ne contient aucun serveur."),
        "errRevoked" to ("Your subscription is no longer valid. Enter a new link." to
            "Ton abonnement n'est plus valide. Entre un nouveau lien."),
        // top bar
        "themeAuto" to ("System theme" to "Thème automatique"),
        "themeDark" to ("Dark theme" to "Thème sombre"),
        "themeLight" to ("Light theme" to "Thème clair"),
        "langLabel" to ("Language" to "Langue"),
        "accentLabel" to ("Accent colour" to "Couleur d'accent"),
        // gauge
        "upload" to ("upload" to "envoyé"),
        "download" to ("download" to "reçu"),
        "history" to ("Daily usage" to "Consommation par jour"),
        "histWait" to ("Your daily usage will appear here." to "Ta consommation quotidienne apparaîtra ici."),
        "histAvg" to ("%s/day" to "%s/jour"),
        "capUsed" to ("used · no limit" to "consommés · sans limite"),
        "capLeft" to ("left of %s" to "restants sur %s"),
        "stActive" to ("Active" to "Actif"),
        "stUnlimited" to ("Unlimited" to "Illimité"),
        "stAlmost" to ("Almost out" to "Presque épuisé"),
        "stQuota" to ("Quota reached" to "Quota atteint"),
        "stExpired" to ("Expired" to "Expiré"),
        "stDisabled" to ("Disabled" to "Désactivé"),
        // details
        "details" to ("Subscription details" to "Détails de l'abonnement"),
        "rowId" to ("Subscription ID" to "Identifiant"),
        "rowAccount" to ("Account" to "Compte"),
        "rowAccounts" to ("Accounts" to "Comptes"),
        "rowStatus" to ("Status" to "État"),
        "rowDown" to ("Downloaded" to "Reçu"),
        "rowUp" to ("Uploaded" to "Envoyé"),
        "rowUsed" to ("Data used" to "Trafic utilisé"),
        "rowQuota" to ("Total quota" to "Quota total"),
        "rowLeft" to ("Data left" to "Trafic restant"),
        "rowOnline" to ("Last online" to "Dernière connexion"),
        "rowExpiry" to ("Expiry" to "Expiration"),
        "noExpiry" to ("No expiry" to "Aucune expiration"),
        "expired" to ("expired" to "expiré"),
        "today" to ("today" to "aujourd'hui"),
        "inDay" to ("in %d day" to "dans %d jour"),
        "inDays" to ("in %d days" to "dans %d jours"),
        "neverSeen" to ("Never seen" to "Jamais vu"),
        "justNow" to ("just now" to "à l'instant"),
        "minsAgo" to ("%d min ago" to "il y a %d min"),
        "hoursAgo" to ("%d h ago" to "il y a %d h"),
        "daysAgo" to ("%d days ago" to "il y a %d jours"),
        "copyId" to ("Copy subscription ID" to "Copier l'identifiant"),
        // connection
        "connection" to ("Connection" to "Connexion"),
        "stateOff" to ("Not protected" to "Non protégé"),
        "stateConnecting" to ("Connecting…" to "Connexion…"),
        "stateOn" to ("Protected" to "Protégé"),
        "stateDisconnecting" to ("Disconnecting…" to "Déconnexion…"),
        "hintOff" to ("Tap the button to connect" to "Appuie sur le bouton pour te connecter"),
        "hintOn" to ("Tap the button to disconnect" to "Appuie sur le bouton pour te déconnecter"),
        "hintWait" to ("Hold on a second…" to "Patiente un instant…"),
        "noServer" to ("No server selected" to "Aucun serveur sélectionné"),
        "duration" to ("duration" to "durée"),
        "publicIp" to ("public IP" to "IP publique"),
        "location" to ("location" to "localisation"),
        "realDelay" to ("latency" to "latence"),
        // link / configs
        "link" to ("Subscription link" to "Lien d'abonnement"),
        "copy" to ("Copy" to "Copier"),
        "refresh" to ("Refresh" to "Actualiser"),
        "changeLink" to ("Use another link" to "Changer de lien"),
        "configs" to ("Configurations" to "Configurations"),
        "testPing" to ("Ping" to "Ping"),
        "noConfigs" to ("No configurations yet." to "Aucune configuration pour le moment."),
        // footer / misc
        "support" to ("Support" to "Assistance"),
        "freshNow" to ("updated just now" to "mis à jour à l'instant"),
        "freshMin" to ("updated %d min ago" to "mis à jour il y a %d min"),
        "freshHour" to ("updated %d h ago" to "mis à jour il y a %d h"),
        "freshFail" to ("update failed" to "mise à jour impossible"),
        "copied" to ("%s copied" to "%s copié"),
        "lblLink" to ("Link" to "Lien"),
        "lblConfig" to ("Configuration" to "Configuration"),
        "lblId" to ("Subscription ID" to "Identifiant"),
        "loaded" to ("Subscription loaded · %d server(s)" to "Abonnement chargé · %d serveur(s)"),
        "bypassApplied" to ("Firewall Bypass configured automatically" to "Firewall Bypass configuré automatiquement"),
        "pickServer" to ("Pick a server in the list." to "Choisis un serveur dans la liste."),
        "errConnect" to ("Connection failed: %s" to "Échec de connexion : %s"),
        "errNoTraffic" to ("Connected, but no traffic gets through. Try another server." to
            "Connecté, mais aucun trafic ne passe. Essaie un autre serveur."),
        "errPermission" to ("VPN permission was refused." to "L'autorisation VPN a été refusée."),
        "failed" to ("failed" to "échec"),
        // notification
        "notifChannel" to ("VPN status" to "État du VPN"),
        "notifTitle" to ("VOID-RAY · Protected" to "VOID-RAY · Protégé"),
        "notifDisconnect" to ("Disconnect" to "Se déconnecter"),
        "tileLabel" to ("VOID-RAY" to "VOID-RAY"),
    )
}
