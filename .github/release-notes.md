## Downloads

| Platform | File |
|---|---|
| Windows 10/11 | `VoidRay-win-x64.exe` |
| Android (most phones), recommended | `VoidRay-android-arm64-v8a.apk` |
| Android (all devices) | `VoidRay-android-universal.apk` |
| Google Play version, for Play Console only (can't be installed directly) | `VoidRay-android-play-store.aab` |

## What's new

- **More reliable ping.** Each server is measured three times and the best result is kept, so one slow packet no longer spoils the number (Windows and Android).
- **Android: real ping.** The list now shows the delay of a real request through each server (TLS, REALITY and the Cloudflare hop included), and falls back to a simple connection test when that fails. Expect higher but more honest numbers: green under 500 ms, orange under 1 s.
- **Lighter traffic.** Finished connections are released after 1 s instead of up to 5 s (Windows and Android).
- **Windows (TUN mode): faster first loads.** DNS goes over HTTPS through the tunnel and reuses one connection instead of opening a new one for every lookup.
