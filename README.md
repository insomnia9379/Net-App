# Marketplace Scanner (Android)

A personal Android app that watches Facebook Marketplace for you. Define
searches once and the app scans them on a schedule in the background, sending a
notification the moment a matching listing appears, its price drops, or it lands
under a price you set.

> **Personal-use tool.** It uses *your own* Facebook session to read Marketplace
> — there is no public Marketplace API. Use it responsibly and in line with
> Facebook's Terms of Service and the laws that apply to you.

## Features

- **Saved searches** — keyword, optional category, location + radius, and a
  min/max price range.
- **Background scanning** — `WorkManager` runs every 15 min – 6 h (configurable),
  surviving reboots and app death, with an optional Wi-Fi-only constraint.
- **Smart alerts**, per search:
  - new listings,
  - price drops on a listing you've already seen,
  - price at/under a threshold you choose,
  - keyword must-match within the title.
- **On-device only** — listings and your captured session are stored locally
  (Room + DataStore). The session cookie is sent only to `facebook.com`.

## How sign-in works

Tap **Sign in to Facebook** and log in on Facebook's own page inside an in-app
`WebView`. The app never sees your password; once Facebook sets the
authenticated session cookie it is captured from the system `CookieManager` and
reused by the background scanner. Sign out at any time from **Settings**, which
clears the stored session and cancels scans.

## Architecture

| Layer | Pieces |
|-------|--------|
| UI | Jetpack Compose, Navigation, `ScannerViewModel` |
| Data | Room (`SavedSearch`, `Listing`), `SessionStore` (DataStore) |
| Network | `MarketplaceClient` (OkHttp) + `MarketplaceParser` |
| Background | `ScanWorker` + `ScanScheduler` (WorkManager) |
| Alerts | `Notifications` (grouped notification channel) |

All the brittle, Facebook-specific scraping lives in **`MarketplaceParser`**.
Because Facebook's markup ordering is unstable, the parser anchors on each
listing's title and reads each field from the *nearest* occurrence rather than
assuming a fixed order. When Facebook changes its markup, this file is the only
one that needs updating.

## Build

Requires Android Studio (Koala+) or the Android SDK with the Gradle wrapper.

```bash
./gradlew :app:assembleDebug
# install on a connected device/emulator
./gradlew :app:installDebug
```

- `minSdk` 26, `targetSdk`/`compileSdk` 34, Kotlin 1.9, AGP 8.5.

## Project layout

```
app/src/main/java/com/netapp/marketplacescanner/
├── MarketplaceScannerApp.kt      // Application: channels + reschedules scans
├── MainActivity.kt
├── data/
│   ├── db/                       // Room entities + DAOs + database
│   ├── model/ScannedListing.kt
│   ├── net/                      // SessionStore, MarketplaceClient, MarketplaceParser
│   └── repo/ScannerRepository.kt // scan → match → persist → alerts
├── work/                         // ScanWorker + ScanScheduler
├── notify/Notifications.kt
└── ui/                           // Compose screens + ViewModel
```
