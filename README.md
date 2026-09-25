# VOID-RAY

Client VPN pour Windows (WPF / C# / .NET 8) réservé aux abonnés **EnderrVPN**.

Au lancement, l'app demande le lien d'abonnement : seuls les liens
`https://sub.enderr.win/ender/…` valides sont acceptés (format vérifié, puis
l'abonnement est interrogé sur le serveur). Si l'abonnement est supprimé plus tard,
l'app revient à cet écran.

L'interface reprend la page d'abonnement EnderrVPN : thème sombre / clair / auto,
6 couleurs d'accent, français / anglais (ou automatique).

Une fois connecté, l'app récupère automatiquement :

- le **nom du profil** et le **nom d'utilisateur**
- le **statut** (actif, expiré, quota atteint…)
- les **données consommées / le quota** (envoyé, reçu, restant, barre de progression)
- la **date d'expiration** et les jours restants
- l'**annonce**, le lien de **support** et le **site web** du fournisseur
- la **liste des serveurs**, avec un test de ping

Compatible avec les panels courants : Marzban, Marzneshin, Remnawave, 3x-ui, Hiddify, etc.
Ces infos viennent de l'en-tête standard `subscription-userinfo`, des en-têtes `profile-title`,
`support-url`, `announce`, et de l'endpoint `/info` quand il existe.

## Firewall Bypass automatique

Quand un serveur nommé « Firewall Bypass » est détecté à l'import, il est reconfiguré
pour passer par Cloudflare : adresse `172.67.186.245:443`, WebSocket + TLS,
SNI / Host `xray.enderr.win`, path `/x7k2p` (ou celui de l'abonnement),
empreinte `chrome`, ALPN `http/1.1`. Les valeurs sont dans
`src/VoidRay/Services/FirewallBypass.cs`.

## Protocoles supportés

`VLESS` (REALITY, TLS, Vision), `VMess`, `Trojan` et `Shadowsocks` (y compris 2022),
avec les transports TCP, WebSocket, gRPC, HTTPUpgrade, XHTTP et mKCP.
Les abonnements au format JSON Xray sont aussi pris en charge.

## Fonctionnement

VOID-RAY pilote [Xray-core](https://github.com/XTLS/Xray-core) :

1. Au premier clic sur **Connexion**, `xray.exe` est téléchargé automatiquement dans
   `%AppData%\VoidRay\core` (tu peux aussi le placer à côté de `VoidRay.exe` ou dans un dossier `core\`).
2. Une configuration est générée pour le serveur choisi, puis Xray démarre en local
   (HTTP `127.0.0.1:10809`, SOCKS5 `127.0.0.1:10808`).
3. Le **proxy système Windows** est activé : navigateurs et la plupart des applis passent par le VPN.
4. L'IP publique, le pays et la latence réelle sont vérifiés à travers le tunnel.

À la déconnexion ou à la fermeture, les réglages proxy d'origine sont restaurés,
même après un plantage (au prochain lancement).

## Compiler

Prérequis : Windows 10/11 et le [SDK .NET 8](https://dotnet.microsoft.com/download/dotnet/8.0)
(ou Visual Studio 2022 avec la charge de travail « Développement .NET Desktop »).

```powershell
dotnet run --project src/VoidRay
```

Pour un exécutable unique à distribuer :

```powershell
dotnet publish src/VoidRay -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -o publish
```

L'exécutable se trouve ensuite dans `publish\VoidRay.exe`.

## Structure

```
src/VoidRay/
├── Models/        ServerProfile, SubscriptionInfo, AppSettings
├── Services/      SubscriptionService (récupération + infos), LinkParser (vless/vmess/trojan/ss),
│                  XrayConfigBuilder, XrayCore (processus), SystemProxy (registre), NetTools (ping, IP),
│                  Brand (lien sub.enderr.win), FirewallBypass, ThemeManager, Loc (FR/EN), UsageHistory
├── ViewModels/    MainViewModel (MVVM), ServerItemViewModel
├── Controls/      TickGauge (jauge), UsageBars (conso par jour), Icon
├── Themes/        Theme.xaml (styles) — couleurs appliquées par ThemeManager
└── MainWindow.xaml
```

Les paramètres (lien, serveur choisi, dernier abonnement en cache) sont enregistrés dans
`%AppData%\VoidRay\settings.json`.
