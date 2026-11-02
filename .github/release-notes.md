## Downloads

| Platform | File |
|---|---|
| Windows 10/11 | `VoidRay-win-x64.exe` |
| Android (most phones), recommended | `VoidRay-android-arm64-v8a.apk` |
| Android (all devices) | `VoidRay-android-universal.apk` |
| Google Play version, for Play Console only (can't be installed directly) | `VoidRay-android-play-store.aab` |

## What's new

- **Windows: the selected option always shows its dark pill.** In Routing (and the other switches) the highlight could vanish until you clicked again, when the panel had been drawn while hidden.
- **Windows: more reliable TUN mode.** VOID-RAY now waits for Windows to bring the VOID-RAY network card up (it can take more than 10 seconds) before saying it's connected, instead of announcing it too early. If a card left behind by a killed session blocks the name, it retries under another name instead of failing.
- Android: no change since 1.5.2.
