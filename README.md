# Stock Watchdog

A minimal, battery-friendly personal stock watchdog for Android. Built with
Kotlin + Jetpack Compose. Designed for private, single-user installation on
your phone — not public release.

## Features

- **Watchlist**: add, remove, and reorder tickers; latest price, daily change,
  percent change, and market-open indicator when available.
- **Ticker detail**: prominent price, daily change, light-weight Canvas line
  chart with 1D / 5D / 1M / 3M ranges, and open/high/low/prev close/volume.
- **Alerts**: price above, price below, or absolute day-% change.
  - Multiple alerts per ticker.
  - Enable/disable/edit/delete.
  - "Notify once per crossing" logic prevents spam.
- **Notifications**: high-priority channel; tapping a notification deep-links
  straight to the ticker's detail page.
- **Background checks**: WorkManager periodic work (15 / 30 / 60 min).
- **Local-only storage**: Room + DataStore (no cloud, no account).
- **Samsung battery tips**: shown directly in Settings.

## Screens

1. Watchlist
2. Ticker Detail
3. Alerts
4. Settings

## Architecture

- **Kotlin + Jetpack Compose + Material 3**
- **MVVM** with `ViewModel` + `StateFlow`
- **Room** for persistence (`watchlist`, `alerts`, `price_cache`)
- **Retrofit + OkHttp + kotlinx.serialization** for API calls
- **WorkManager** for periodic alert checks
- **DataStore (Preferences)** for user settings
- Manual DI (`AppContainer`) — intentionally no Hilt to keep cold-start fast
  and build times short.

## API providers

Supports either:

- **Twelve Data** (default) — `https://api.twelvedata.com`
- **Alpha Vantage** — `https://www.alphavantage.co`

Switch providers in Settings. Both free tiers are heavily rate-limited, so
the app:
- Caches quotes in Room for 60s between foreground refreshes.
- Fetches quotes sequentially, not in parallel.
- Respects 15-minute periodic background intervals (Android minimum).

## Setup

### 1. Prerequisites

- Android Studio Koala or newer
- JDK 17
- Android SDK 34

### 2. Clone and configure keys

```
git clone <your repo>
cd StockWatchdog
copy local.properties.example local.properties
```

Edit `local.properties` and paste your keys:

```
TWELVE_DATA_API_KEY=your_twelve_data_key
ALPHA_VANTAGE_API_KEY=your_alpha_vantage_key
```

`local.properties` is gitignored, so keys never reach GitHub.

Alternatively, leave `local.properties` blank and enter the keys in
**Settings → Data provider** inside the app. Keys entered in-app are stored
in DataStore on the device only.

### 3a. Build via GitHub Actions (easiest — no tools needed on your PC)

If you don't want to install Android Studio locally, just push this repo to
GitHub. The included workflow at `.github/workflows/build-apk.yml` builds
`app-debug.apk` for you on GitHub's runners in ~4 minutes.

Steps:

1. Create a new empty repository on github.com.
2. From the project folder:
   ```
   git init
   git add .
   git commit -m "initial"
   git branch -M main
   git remote add origin https://github.com/<YOUR_USER>/<YOUR_REPO>.git
   git push -u origin main
   ```
3. Open the repo on GitHub → **Actions** tab → wait for **Build APK** to finish (green check).
4. Click the finished run → scroll to **Artifacts** → download `stock-watchdog-debug-apk`.
5. Transfer the APK to your Samsung (email, Drive, direct browser download) and install.

Optional — bake API keys into CI builds via GitHub Secrets:

- Repo → Settings → Secrets and variables → Actions → New repository secret
- Add `TWELVE_DATA_API_KEY` and/or `ALPHA_VANTAGE_API_KEY`

If you skip the secrets, the built APK will just ship with empty keys and
you can paste them into the in-app Settings screen on first launch.

For versioned builds with a proper GitHub Release attached, push a tag:

```
git tag v1.0.0
git push origin v1.0.0
```

### 3b. Build locally with Android Studio

From the project root:

```
./gradlew assembleDebug
```

Install the resulting APK (`app/build/outputs/apk/debug/app-debug.apk`) on
your phone via `adb install` or by copying it over. For a private signed
release build, the Gradle config reuses the debug signing key by default:

```
./gradlew assembleRelease
```

### 4. First run

- Grant the notification permission when prompted (Android 13+).
- Add tickers from the Watchlist `+` button.
- Open a ticker, tap **New alert** to add price / percent rules.
- In Settings, pick an interval (15/30/60 min) and follow the Samsung
  battery tip so alerts fire reliably.

## Notification deep-link

Notifications use a custom scheme:

```
stockwatchdog://ticker/AAPL
```

Tapping a notification reopens the app directly to that detail screen.

## Project layout

```
app/src/main/java/com/stockwatchdog/app/
├── MainActivity.kt
├── StockWatchdogApp.kt
├── di/                 AppContainer (manual DI)
├── data/
│   ├── api/            Retrofit services + MarketDataRepository
│   ├── db/             Room DAOs, entities, DB
│   └── prefs/          SettingsRepository (DataStore)
├── domain/             Normalized Quote/PricePoint/AlertEvaluator
├── notifications/      Channel + NotificationHelper
├── work/               WorkManager worker + scheduler
└── ui/
    ├── navigation/     NavHost + bottom-bar graph
    ├── theme/          Material 3 theme + dynamic colors
    ├── components/     PriceLineChart + formatting helpers
    ├── watchlist/      Watchlist VM + screen
    ├── detail/         Ticker detail VM + screen
    ├── alerts/         Alerts VM + screen
    └── settings/       Settings VM + screen
```

## Security notes

- API keys are never hardcoded in source.
- `local.properties` and `*.keystore` are gitignored.
- Keys entered in Settings live in the app's private DataStore file
  (app-sandboxed; backups restricted by `data_extraction_rules.xml`).

## Non-goals

No login, no cloud sync, no brokerage, no trading execution, no news feed.
This is a personal watchdog, on purpose.
