# Publishing VOID-RAY on Google Play

Everything the app itself needs is done; this page lists what is left to do in
[Play Console](https://play.google.com/console), with the texts to paste.

## Already handled in the app

| Play requirement | How VOID-RAY meets it |
| --- | --- |
| Target API level (Android 16 / API 36 since Aug 31, 2026) | `targetSdk = 36` |
| Android App Bundle | The `play` flavor is built as a signed `.aab` by CI |
| No self-updating, no `REQUEST_INSTALL_PACKAGES` | Only the `github` flavor has the updater and that permission; CI fails if the Play build requests it |
| 16 KB memory pages | 64-bit native libraries checked by CI (Xray core pinned to a 16 KB aligned release) |
| VpnService: VPN is the core feature, traffic encrypted | Servers without TLS / REALITY / protocol encryption are refused and tagged "Not encrypted" |
| Privacy policy in the app | Link on the link screen, in Settings and in the VPN disclosure |
| Prominent disclosure and consent | "Before you connect" dialog before Android's VPN request; closing it isn't consent |
| Foreground service type | `specialUse` (subtype `vpn`), declared in the manifest |
| Notifications permission | Asked at runtime on Android 13+ |
| No ads, analytics or advertising ID | None in the app |
| Payments (anti-steering) | The Play build points to support only, never to buying a subscription |
| Backups | The subscription link is excluded from cloud backups and device transfers |
| Edge to edge, predictive back | Safe-drawing insets, system bar icons follow the theme, back closes sheets |

## 1. Get the bundle

GitHub → **Actions** → **Android** (or **Release** for a versioned build) → latest run →
**Artifacts** → `VoidRay-play-bundle` → the `.aab` inside is what you upload.

It is signed with the key from the `VOIDRAY_KEYSTORE_B64` secret, which becomes your
**upload key** in Play Console. The `versionCode` comes from the version: `1.5.0` → `10500`.
Each upload needs a higher one, so publish a new version (Release workflow) before each upload.

## 2. Developer account

- One-time fee of 25 USD and an identity check.
- **Personal accounts created after November 13, 2023 must run a closed test with at least
  12 testers for 14 days in a row before they can publish to production.** Plan for it (friends,
  your Discord community). Organization accounts (D-U-N-S number) don't have this step.
- A public contact email is required for the store listing.

## 3. Create the app

- App name: `VOID-RAY for EnderrVPN`, default language: English (United States) — en-US.
- App or game: **App**. Free or paid: **Free**.
- Accept the declarations (Developer Program Policies, US export laws).

### App signing

When you upload the first bundle, Play Console asks how to handle the app signing key:

- **Use your existing key (recommended):** "Use a different key" → "Export and upload a key from
  Java keystore", with Google's PEPK tool and the keystore behind `VOIDRAY_KEYSTORE_B64`. Play and
  GitHub builds then share the same signature, and people can switch between them without
  uninstalling.
- **Let Google create the key:** simpler, but Play and GitHub installs become incompatible (switching
  requires uninstalling, which removes the saved link).

## 4. Store listing

Texts and graphics are ready in [`fastlane/metadata/android`](../fastlane/metadata/android):

| Field | File |
| --- | --- |
| App name (30) | `en-US/title.txt` |
| Short description (80) | `en-US/short_description.txt` |
| Full description (4000) | `en-US/full_description.txt` (mentions VpnService, as the VPN policy requires) |
| App icon 512 × 512 | `en-US/images/icon.png` |
| Feature graphic 1024 × 500 | `en-US/images/featureGraphic.png` |
| Phone screenshots 1080 × 1920 | `en-US/images/phoneScreenshots/` |
| French translation | `fr-FR/` (Store presence → Translations → add French, France) |
| What's new | `en-US/changelogs/<versionCode>.txt` |

- Category: **Tools**. Tags: VPN, privacy.
- Privacy policy URL: `https://github.com/EnderMythex/VOID-RAY/blob/HEAD/PRIVACY.md`

The screenshots are rendered from a faithful replica of the Android screens with sample data;
you can replace them with captures from a phone (same size: 1080 × 1920 or any 9:16).

## 5. App content (Policy and programs → App content)

### Privacy policy

`https://github.com/EnderMythex/VOID-RAY/blob/HEAD/PRIVACY.md`

### Ads

**No, my app does not contain ads.**

### App access

**All or some functionality is restricted.** Add one instruction set:

> VOID-RAY requires an EnderrVPN subscription.
> 1. Open the app and paste this link in the "Subscription link" field: `https://sub.enderr.win/ender/<test id>`
> 2. Tap Continue.
> 3. Slide "Slide to turn on" to the right, tap "Accept and connect", then allow Android's VPN request.
>
> The link has no data limit and stays valid while the app is on Google Play.

Create a **dedicated test subscription** for Google's reviewers (no data cap, far expiry date) —
never your own link — and keep it active: Google reviews every update.

### Content rating

Questionnaire category: **Utility, Productivity, Communication, or Other**. Answer **No** to the
violence, sexuality, language, controlled substances and gambling questions, **No** to "users can
interact or exchange content" and to "shares the user's location", and **No** to "allows purchases of
digital goods". Expected rating: Everyone / PEGI 3.

### Target audience

**18 and over** (keeps the app out of the Families policy).

### Data safety

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | Yes |
| Is all of the user data collected by your app encrypted in transit? | Yes |
| Account creation | My app does not allow users to create an account (subscriptions are created by EnderrVPN) |
| Do you provide a way for users to request that their data is deleted? | Yes (support link, see the privacy policy) |

Data types:

| Data type | Collected | Shared | Processed ephemerally | Required | Purposes |
| --- | --- | --- | --- | --- | --- |
| Personal info → User IDs (subscription identifier) | Yes | No | No | Required | App functionality, Account management |
| App activity → Other actions (data volume used, last connection) | Yes | No | No | Required | App functionality, Account management |

Nothing else is collected by the app: no location, contacts, files, browsing history, device IDs,
crash logs or analytics. VPN traffic is forwarded in real time and not stored (ephemeral).

> **Check your servers before answering.** The answers above assume the EnderrVPN servers only keep
> per-subscription data usage and the last connection time. If your Xray / 3x-ui servers keep
> **access logs** (visited sites) or store **client IP addresses**, also declare *Web browsing history*
> and/or *Approximate location*, and add it to `PRIVACY.md` — or turn those logs off.

### Government apps, financial features, health, news

**No** to all.

### Advertising ID

**No** — the app doesn't use it.

### VpnService declaration

Shown under *Sensitive permissions and APIs*. Core functionality: **VPN**. Description:

> VOID-RAY is a VPN client for subscribers of the EnderrVPN service; providing the VPN connection is
> its core functionality. When the user slides to connect, the app uses VpnService to create a TUN
> interface and runs the open-source Xray core, which sends all device traffic through an encrypted
> tunnel (VLESS with TLS or REALITY, VMess, Trojan over TLS, Shadowsocks) to the EnderrVPN server the
> user selected. Servers that aren't encrypted are refused. The app collects no personal data beyond
> the subscription identifier sent to EnderrVPN, doesn't inspect, log, redirect or monetize user
> traffic, and contains no ads or analytics. A disclosure explains all this before the first
> connection, then Android's own VPN consent dialog is shown.

### Foreground service permissions

Declare `FOREGROUND_SERVICE_SPECIAL_USE`. Description:

> The VPN runs in a foreground service of type specialUse (subtype "vpn") for as long as the user keeps
> the VPN connected. It keeps the encrypted tunnel alive while the user uses other apps and shows a
> persistent notification with the connection status, the live upload and download speed and a
> Disconnect button. It only starts when the user connects (in the app or from the Quick Settings tile)
> and stops as soon as the user disconnects.

Play also asks for a **video link**: record the phone screen (connect in the app, pull down the
notification, tap Disconnect), upload it to YouTube as *unlisted*, and paste the link.

## 6. Test, then release

1. **Internal testing** → upload the `.aab` → check the **pre-launch report** (Play tests the app on
   real devices).
2. **Closed testing** with your 12+ testers for 14 days (personal accounts).
3. Apply for production access, then **Production** → create a release with the same bundle or a
   newer one. Google's review of a VPN app can take several days.

## Updating later

Run **Actions → Release** with the new version: the GitHub release gets the APKs as before, and the
run's `VoidRay-play-bundle` artifact is the bundle to upload in Play Console. Add a
`changelogs/<versionCode>.txt` for the "What's new" text.
