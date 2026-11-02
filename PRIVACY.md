# VOID-RAY Privacy Policy

_Last updated: November 2, 2026_

VOID-RAY ("the app") is the VPN app for subscribers of EnderrVPN ("the service"), on Android and
Windows. "We" means the EnderrVPN team, who develops the app and runs the service. This policy
explains what the app handles, why, and who receives it.

## In short

- No account to create, no ads, no analytics, no crash reporting and no trackers in the app.
- Your subscription link stays on your device and is only sent to EnderrVPN.
- While you're connected, your traffic travels through an encrypted tunnel to the EnderrVPN server
  you chose. The app doesn't record or sell what you do online.

## What the app handles, and why

### Your subscription link

The link you paste (`https://sub.enderr.win/ender/…`) contains your subscription identifier. The app
sends it over HTTPS to `sub.enderr.win` to get your server list and your plan details (status, data
used, quota, expiry date, last connection), and refreshes them every few minutes while the app is
running. The link is stored in the app's private storage on your device.

### Your internet traffic while connected

On Android, the app uses Android's `VpnService` to create an encrypted tunnel between your device
and the EnderrVPN server you selected (on Windows: a virtual network adapter, or the system proxy).
While you're connected, your internet traffic goes through that tunnel and the server forwards it to
its destination, so websites see the server's IP address instead of yours.

- The app only connects to servers that encrypt this tunnel (TLS, REALITY, or the protocol's own
  encryption) and refuses the others.
- The app does not log, inspect, store or share the content or the destinations of your traffic.
- EnderrVPN's servers count the amount of data each subscription uses and note the time of its last
  connection, to apply the limits of your plan. Both are shown to you in the app.

### Connection checks

- **Server latency:** the app opens a short TCP connection to the servers of your subscription to
  measure their latency. No personal data is sent.
- **Public IP check:** once connected, the app asks `ipwho.is` (or `api.ipify.org` as a fallback),
  through the tunnel, which public IP address and country it sees, to show you that the VPN works.
  These services see the VPN server's IP address, not your device's.

### Notifications

On Android, a notification shows the connection status and the upload and download speed, computed
on your device from the app's own traffic counters.

### Updates

- **Google Play version:** updates come from Google Play. The app never downloads code by itself.
- **GitHub version (Android and Windows):** the app asks `api.github.com` whether a newer release
  exists and downloads it from GitHub when you choose to install it. On first use, the Windows app
  also downloads the open-source Xray and sing-box engines from GitHub. GitHub receives your IP
  address and the app version with these requests (see
  [GitHub's privacy statement](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement)).

### Support

The Support link opens EnderrVPN's Discord server. What you share there is covered by
[Discord's privacy policy](https://discord.com/privacy).

## What stays on your device

Your subscription link, the cached plan details and server list, the server you chose, your daily
data usage history, and your theme, colour and language settings. On Android, this data is excluded
from cloud backups and device transfers.

It is deleted when you remove your link (Settings → **Use another link** on Android, **Change link**
on Windows) or uninstall the app. On Windows, the settings live in the `%AppData%\VoidRay` folder,
which you can delete.

## What we don't do

- No advertising, no advertising ID, no analytics or tracking SDKs.
- No access to your contacts, photos, files, microphone, camera or location.
- We don't sell or rent your data, and we don't use it for advertising.

## Who receives data

- **EnderrVPN:** your subscription identifier (to load your plan) and your VPN traffic (to deliver
  the service), as described above.
- **ipwho.is / api.ipify.org:** a request coming from the VPN server's IP address, while connected.
- **GitHub:** update checks and downloads, GitHub version only.

Nobody else.

## Security

Subscription requests use HTTPS and the VPN tunnel is always encrypted. Your link is stored in the
app's private storage, which other apps can't read.

## Keeping and deleting data

- **On your device:** until you remove your link or uninstall the app.
- **At EnderrVPN:** your subscription data (identifier, data usage, last connection) is kept while
  your subscription exists. To have it deleted, or for any question about your data, contact us.

## Children

VOID-RAY is not intended for children under 13, and we don't knowingly collect their data.

## Your rights

Depending on where you live (for example under the GDPR in the European Union), you can ask to
access, correct or delete your data, or object to its use. Contact us and we'll answer within one
month.

## Changes

We'll update this page when the app changes how it handles data, and change the date at the top.

## Contact

- Support (Discord): <https://discord.gg/BzG6FJz6zK>
- GitHub: <https://github.com/EnderMythex/VOID-RAY/issues>
