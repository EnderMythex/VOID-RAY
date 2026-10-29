<p align="center">
  <img src="docs/media/hero.png" alt="VOID-RAY sur Windows et Android" width="100%">
</p>

<h1 align="center">VOID-RAY</h1>

<p align="center">
  <b>Le client VPN des abonnés EnderrVPN — Windows &amp; Android.</b><br>
  Un lien, un glissement, tu es protégé.
</p>

<p align="center">
  <a href="https://github.com/EnderMythex/VOID-RAY/releases/latest"><img alt="Release" src="https://img.shields.io/github/v/release/EnderMythex/VOID-RAY?style=for-the-badge&color=6d86ff&label=t%C3%A9l%C3%A9charger"></a>
  <img alt="Windows" src="https://img.shields.io/badge/Windows-10%20%2F%2011-52d6bd?style=for-the-badge&logo=windows&logoColor=white">
  <img alt="Android" src="https://img.shields.io/badge/Android-8%2B-52d6bd?style=for-the-badge&logo=android&logoColor=white">
</p>

---

## Présentation

<p align="center">
  <a href="docs/media/voidray.mp4"><img src="docs/media/video-poster.jpg" alt="Voir la vidéo de présentation" width="100%"></a>
  <br><sub>▶ Clique pour voir la vidéo (24 s)</sub>
</p>

## Télécharger

| Plateforme | Fichier |
| --- | --- |
| **Windows 10 / 11** | [`VoidRay-win-x64.exe`](https://github.com/EnderMythex/VOID-RAY/releases/latest/download/VoidRay-win-x64.exe) — un seul exe, rien à installer |
| **Android (presque tous les téléphones)** | `VoidRay-android-arm64-v8a.apk` dans la [dernière release](https://github.com/EnderMythex/VOID-RAY/releases/latest) |
| **Android (tous)** | `VoidRay-android-universal.apk` dans la [dernière release](https://github.com/EnderMythex/VOID-RAY/releases/latest) |

Au premier lancement, colle ton lien d'abonnement `https://sub.enderr.win/ender/…` :
seuls les liens EnderrVPN valides sont acceptés, et tout le reste se configure seul.

## Sur PC

<p align="center">
  <img src="docs/media/desktop.png" alt="VOID-RAY sur Windows, thèmes sombre et clair" width="100%">
</p>

- **Tout le PC passe par le VPN (mode TUN)** : jeux (Minecraft…), launchers, Discord.
  Une carte réseau virtuelle « VOID-RAY » est créée par
  [sing-box](https://github.com/SagerNet/sing-box), qui transmet tout à
  [Xray-core](https://github.com/XTLS/Xray-core). L'app se relance en administrateur
  quand il le faut et se connecte directement.
- **Mode navigateurs (proxy)** : ne règle que le proxy système, sans droits administrateur.
- **Zone de notification** : fermer la fenêtre ne coupe pas le VPN. Clic sur l'icône
  pour la rouvrir, clic droit pour se connecter / déconnecter ou quitter. Un point vert
  indique que le VPN est actif.
- Débit montant / descendant en direct, IP publique, pays et ping réel à travers le tunnel.
- Journal intégré pour comprendre un souci de connexion.

## Sur Android

<p align="center">
  <img src="docs/media/phones.png" alt="VOID-RAY sur Android" width="100%">
</p>

- **Glisse pour te connecter** autour du trou noir ; les serveurs et l'abonnement
  s'ouvrent dans des panneaux qui glissent.
- **Débit en direct dans la notification** (↑ / ↓ par seconde et total de la session),
  avec un bouton pour se déconnecter.
- **Tuile « Réglages rapides »** pour connecter ou déconnecter sans ouvrir l'app.
- Le VPN utilise le `VpnService` d'Android et Xray-core (`libv2ray.aar`) avec son entrée TUN.

## Sur les deux

- **Infos d'abonnement automatiques** : profil, nom d'utilisateur, statut, données
  consommées / quota, date d'expiration, annonce, liens de support et du site.
- **Liste des serveurs** avec test de ping.
- **Firewall Bypass automatique** : le serveur « Firewall Bypass » est reconfiguré pour
  passer par Cloudflare (`172.67.186.245:443`, WebSocket + TLS, SNI / Host
  `xray.enderr.win`, path `/x7k2p` ou celui de l'abonnement, empreinte `chrome`,
  ALPN `http/1.1`).
- **Thème sombre / clair / auto**, **6 couleurs d'accent**, français / anglais.
- **Mises à jour intégrées** : le bouton *Vérifier les mises à jour* lit la dernière
  release GitHub et l'installe (le PC remplace son exe et redémarre, Android ouvre
  l'installateur).

Protocoles : `VLESS` (REALITY, TLS, Vision), `VMess`, `Trojan`, `Shadowsocks` (y compris
2022), avec les transports TCP, WebSocket, gRPC, HTTPUpgrade, XHTTP et mKCP. Les
abonnements au format JSON Xray sont aussi pris en charge.

## Compiler

### Windows (Rust + Tauri v2)

Prérequis : Windows 10/11, [Rust](https://rustup.rs) et Node.js (pour la CLI Tauri).

```powershell
cd desktop
npx @tauri-apps/cli@2 build --no-bundle
```

L'exécutable se trouve dans `desktop\src-tauri\target\release\VoidRay.exe`.
`xray.exe` et `sing-box.exe` (versions épinglées) sont téléchargés au premier usage dans
`%AppData%\VoidRay\core`. Les réglages sont dans `%AppData%\VoidRay\settings.json`.

### Android (Kotlin + Jetpack Compose)

Prérequis : Android SDK et JDK 17.

```bash
curl -L -o android/app/libs/libv2ray.aar \
  https://github.com/2dust/AndroidLibXrayLite/releases/latest/download/libv2ray.aar
cd android && ./gradlew assembleRelease
```

## Publier une version

1. Mettre à jour `.github/release-notes.md` (texte affiché dans la release et dans l'app).
2. **Actions → Release → Run workflow** et entrer la version (ex. `1.4.0`), ou pousser
   un tag `v1.4.0`.

Le workflow construit `VoidRay-win-x64.exe` et les APK `VoidRay-android-*.apk`, puis
crée la release. La version doit être plus grande que la précédente (Android refuse
d'installer une version plus ancienne). Le repo doit être public pour que les
utilisateurs puissent télécharger les mises à jour.

## Structure

```
desktop/                 App Windows (Rust + Tauri v2)
├── src-tauri/src/       app (commandes), subscription, links (vless/vmess/trojan/ss),
│                        config (Xray / sing-box, Firewall Bypass), cores (processus),
│                        system (proxy, élévation), tray, net (ping, IP, mises à jour)
└── ui/                  index.html, style.css, app.js, i18n.js
android/                 App Android (Kotlin + Compose)
└── app/src/main/java/win/enderr/voidray/
    ├── core/            LinkParser, SubscriptionFetcher, XrayConfig, FirewallBypass
    ├── ui/              Home (trou noir, glisser pour se connecter), Screens, Theme
    └── vpn/             VoidRayVpnService, tuile Réglages rapides
src/VoidRay/             Ancienne app Windows (WPF / .NET 8), remplacée par desktop/
```
