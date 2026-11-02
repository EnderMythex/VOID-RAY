## Downloads

| Platform | File |
|---|---|
| Windows 10/11 | `VoidRay-win-x64.exe` |
| Android (most phones), recommended | `VoidRay-android-arm64-v8a.apk` |
| Android (all devices) | `VoidRay-android-universal.apk` |
| Google Play version, for Play Console only (can't be installed directly) | `VoidRay-android-play-store.aab` |

## What's new

- **Windows: no more "ghost" VPN after VOID-RAY is closed by force.** If the app was killed (crash, Task Manager, an antivirus quarantine) while connected, Xray and sing-box kept running: Windows still showed the VOID-RAY network as connected, and the next connection failed with "a file that already exists". They now stop together with the app, and leftovers from an older session are ended when VOID-RAY starts or connects.
- Windows: the filtered-network check only tests Roblox and Minecraft over HTTPS (no more SSH test).
- Android: no change since 1.5.2.
