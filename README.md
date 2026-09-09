
# Telemetry — LAN + Cloud Push-to-Talk

Android walkie-talkie with two transports behind one TalkNet UI:

* **LAN Direct** — phone-to-phone voice over local WiFi (UDP, no internet, no account)
* **Cloud Channels** — internet rooms via [LiveKit Cloud](https://cloud.livekit.io) (SFU), any word is a room

Everything audio runs in a foreground service, so it keeps talking with the screen off.

## Features

**Talk**
* Hold-to-talk everywhere (big round PTT), volume-down PTT, Live monitor mode (mic stays open)
* LAN: 16 kHz PCM over UDP `:50005`; Cloud: Opus over LiveKit WebRTC, mic muted by default

**Channels**
* Home lists pinned rooms (Office / Warehouse / Remote / Security), recent rooms, and JOIN BY WORD — any word is a channel, no creation step
* Room names validated like the server (`letters, numbers, space, _ or -, max 64`)

**Discovery & presence (LAN)**
* UDP broadcast beacons (`:50006`, every 2s): name + IP + battery, 10s expiry
* NEARBY lists with avatars, battery %, staleness, tap-to-dial; manual IP fallback
* Multi-homed phones beacon per interface; hotspot-aware own-IP picking

**Messaging & health**
* Quick texts (presets, one per line, max 8): LiveKit data messages on Cloud, `TXT|` sidecar packets on LAN
* Connection-quality dots on cloud avatars (mint / amber / red)

**Platform**
* Foreground service (`microphone` type) with sticky notification + Stop action
* Jetpack Compose + Material 3, dark TalkNet theme, `@Preview` per screen
* App icon: mint mic + telemetry waves adaptive icon on `#14262A` (incl. monochrome themed-icon variant)

## Using the app

1. Install `app-release.apk` from [Releases](../../releases) (release-signed).
2. Grant **Microphone** (and Notifications on Android 13+) when asked; accept the battery-optimization prompt so it works screen-off.
3. **LAN:** open the LAN tab, set your name, tap a NEARBY peer (or type an IP), open LAN Talk, hold to talk. Both phones must be on the same WiFi (or one phone's hotspot). Guest WiFis with client isolation block device-to-device traffic — use the main SSID or hotspot mode.
4. **Cloud:** open the CLOUD tab → `+ Join` → name + channel → Join. First run needs the LiveKit URL + token-server URL (Server settings). Hold to talk.

## Token server (Cloud mode only)

The app never holds the LiveKit API secret. A tiny Cloudflare Worker mints short-lived join tokens. Full setup: [`token-server/README.md`](token-server/README.md).

```bash
cd token-server
wrangler secret put LIVEKIT_API_KEY
wrangler secret put LIVEKIT_API_SECRET
wrangler deploy
```

## Build from source

**Android Studio** (recommended): open the `telemetry` folder → Trust Project → wait for Gradle sync → Run (`Shift+F10`) on an emulator or USB device. `@Preview` functions (`HomePreview`, `JoinPreview`, `ActivePreview`, `LanScreenPreview`) render without a device.

**Command line:** needs AGP 9.x-compatible Gradle (9.5+) and the Studio-bundled JDK:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
& "$env:USERPROFILE\.gradle\wrapper\dists\gradle-9.5.0-bin\<hash>\gradle-9.5.0\bin\gradle.bat" :app:assembleDebug
```

APK lands at `app/build/outputs/apk/debug/app-debug.apk` (debug-signed).

### Official release build (signed)

Release builds are signed with `app/telemetry-release.jks` (4096-bit RSA, 30-year
validity). Credentials live in `keystore.properties` at the repo root, which is
gitignored and never committed:

```properties
storeFile=app/telemetry-release.jks
storePassword=...
keyAlias=telemetry
keyPassword=...
```

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
& "$env:USERPROFILE\.gradle\wrapper\dists\gradle-9.5.0-bin\<hash>\gradle-9.5.0\bin\gradle.bat" :app:assembleRelease :app:bundleRelease
```

This produces a release-signed APK at `app/build/outputs/apk/release/app-release.apk`
and an upload bundle at `app/build/outputs/bundle/release/app-release.aab`
(APK Signature Scheme v2; verify with
`apksigner verify --verbose --print-certs`). The build fails fast with a clear
message if `keystore.properties` is missing, so a release can never silently
ship with a debug signature. Back up `app/telemetry-release.jks` plus
`keystore.properties` somewhere safe (password manager + offline copy) — lose
the key and Play Store updates for this app id become impossible.

## Architecture

```
Activities (UI only, Compose)
   │ intents (commands) / StateFlow (state)
   ▼
TelemetryService (foreground, START_STICKY)
   ├── LAN  UDP rx/tx (:50005) + beacons (:50006)
   └── Cloud  LiveKit room (token → connect → publish/mute/data)
                   ▲
Cloudflare Worker ─┘  POST /token {room, identity} → {token, url}
```

Key files:

| Path | Role |
|---|---|
| `app/…/MainActivity.kt` | LAN Direct screen (delegates to service) |
| `app/…/LiveKitActivity.kt` | Home / Join / Active cloud flow (delegates to service) |
| `app/…/service/TelemetryService.kt` | All audio + network + notification |
| `app/…/service/SessionState.kt` | Shared flows (status, peers, speakers, texts, quality) |
| `app/…/lan/Discovery.kt` | UDP presence beacons + listener |
| `app/…/ui/talknet/` | TalkNet design system + channel screens |
| `token-server/` | Cloudflare Worker token minter |

## Permissions (and why)

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | Mic for voice |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` | Keep voice alive screen-off |
| `POST_NOTIFICATIONS` | Foreground-service notification (Android 13+) |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Cloud voice + token fetch |
| `ACCESS_WIFI_STATE` / `CHANGE_WIFI_MULTICAST_STATE` | LAN discovery broadcasts |
| Battery-optimization exemption (prompted once) | Survive Doze while listening |

## Known limits

* No end-to-end encryption (LiveKit transport security only; LAN is raw PCM).
* Guest/isolated WiFis block LAN entirely — hotspot mode is the fallback.
* No background-service exemption handling beyond the first-run prompt.
