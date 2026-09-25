# VOID-RAY

Client VPN pour Windows (WPF / C# / .NET 8) avec une interface noire.
L'utilisateur colle **son lien d'abonnement** et l'app récupère automatiquement :

- le **nom du profil** et le **nom d'utilisateur**
- le **statut** (actif, expiré, quota atteint…)
- les **données consommées / le quota** (envoyé, reçu, restant, barre de progression)
- la **date d'expiration** et les jours restants
- l'**annonce**, le lien de **support** et le **site web** du fournisseur
- la **liste des serveurs**, avec un test de ping

Compatible avec les panels courants : Marzban, Marzneshin, Remnawave, 3x-ui, Hiddify, etc.
Ces infos viennent de l'en-tête standard `subscription-userinfo`, des en-têtes `profile-title`,
`support-url`, `announce`, et de l'endpoint `/info` quand il existe.

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
│                  XrayConfigBuilder, XrayCore (processus), SystemProxy (registre), NetTools (ping, IP)
├── ViewModels/    MainViewModel (MVVM), ServerItemViewModel
├── Themes/        Theme.xaml (palette noire, styles)
└── MainWindow.xaml
```

Les paramètres (lien, serveur choisi, dernier abonnement en cache) sont enregistrés dans
`%AppData%\VoidRay\settings.json`.
