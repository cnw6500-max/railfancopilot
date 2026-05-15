# 🚂 RailFan — Android App

A full-featured railfan companion app built with Kotlin + Jetpack Compose.

---

## Features

| Screen | What it does |
|--------|-------------|
| **Map** | Live train positions on Google Maps with grade crossings, signals, yards, photo spots. Filter by railroad. Tap any train for full detail sheet. |
| **Scanner** | Stream railroad radio channels (Broadcastify). Animated waveform, AI transcription per channel. Background playback via foreground service. |
| **Decoder** | AI-powered train symbol decoder using Claude API. Enter any symbol (e.g. `Q-CHISBD-11`) to get origin, destination, consist, schedule, notes. |
| **Photo Tools** | Sun angle predictor with interactive canvas dial. Camera tagging, train-length estimator, loco identifier (AI), AR overlay. Achievement badges. |
| **Community** | Crowd-sourced sightings feed with geofence safety warnings, weather alerts, RRPD non-emergency contacts. Submit your own sightings. |
| **Encyclopedia** | Locomotive roster browser with full specs, paint/railroad history, signal system glossary. Search by model, manufacturer, or railroad. |

---

## Project Structure

```
app/src/main/java/com/railfan/app/
├── MainActivity.kt                    # Entry point, navigation host, bottom bar
├── data/
│   ├── models/Models.kt               # All data classes & enums
│   ├── repository/
│   │   ├── Database.kt                # Room DB, DAOs (SavedLocations, Reports)
│   │   ├── NetworkApi.kt              # Retrofit services (TrainAPI, Anthropic)
│   │   └── RailFanRepository.kt       # Single source of truth, demo data fallback
│   └── service/
│       ├── ScannerService.kt          # Foreground service for radio streaming (ExoPlayer)
│       └── LocationTrackingService.kt # Foreground service for background location
├── viewmodel/
│   └── RailFanViewModel.kt            # All state: trains, scanner, decoder, community
└── ui/
    ├── theme/Theme.kt                 # Material3 dark color scheme
    ├── components/Components.kt       # Shared: TrainCard, FilterChip, AlertBanner, etc.
    └── screens/
        ├── MapScreen.kt               # Google Maps + train list + detail bottom sheet
        ├── ScannerScreen.kt           # Scanner channels + waveform + transcription
        ├── DecoderScreen.kt           # AI symbol decode + history chips
        ├── PhotoScreen.kt             # Sun dial + tool cards + achievements
        ├── CommunityScreen.kt         # Reports feed + submit dialog + safety alerts
        └── EncyclopediaScreen.kt      # Loco roster + signal glossary
```

---

## Setup

### 1. Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 26+
- Kotlin 2.0+

### 2. Clone and open
```bash
git clone <repo>
cd RailFanApp
# Open in Android Studio: File > Open > select this folder
```

### 3. API keys
Copy `local.properties.template` to `local.properties` and fill in your keys:

```properties
MAPS_API_KEY=your_google_maps_key
ANTHROPIC_API_KEY=your_anthropic_key
BROADCASTIFY_API_KEY=your_broadcastify_key
sdk.dir=/path/to/your/android/sdk
```

#### Getting API keys:
- **Google Maps**: [console.cloud.google.com](https://console.cloud.google.com) → Enable "Maps SDK for Android"
- **Anthropic (Claude)**: [console.anthropic.com](https://console.anthropic.com) → Create API key
- **Broadcastify**: [broadcastify.com/api](https://www.broadcastify.com/api/) → Apply for API access

### 4. Live train data
The app currently uses **demo/simulated train data**. To connect real live trains:

Replace `NetworkModule.trainApi` base URL in `NetworkApi.kt` with one of these providers:
- **Railstream** — [railstream.net](https://railstream.net) (best for North America freight)
- **Trains2** — community API, limited coverage
- **RailNav** — European focus
- **Amtrak API** — `https://api-v3.amtraker.com/v3/trains` (free, Amtrak only)

The `TrainApiItem` data class maps to a standard schema — you may need to adjust field names to match your chosen provider's response format.

### 5. Build & run
```bash
./gradlew assembleDebug
# Or press the Run button in Android Studio
```

---

## Architecture

- **Jetpack Compose** — 100% declarative UI, no XML layouts
- **ViewModel + StateFlow** — unidirectional data flow
- **Room** — local persistence for saved locations and community reports
- **Retrofit + OkHttp** — REST API calls with logging
- **ExoPlayer (Media3)** — radio stream playback in foreground service
- **Google Maps Compose** — map with custom markers
- **Accompanist Permissions** — runtime permission handling
- **Claude API** — AI symbol decoding and locomotive identification

---

## Extending the App

### Add a new railroad to the filter
In `Models.kt`, add to the `Railroad` enum:
```kotlin
CSXT("CSX Transportation", 0xFF0A2A0A),
```

### Add a locomotive to the encyclopedia
In `RailFanRepository.kt`, append to `getLocomotivedDatabase()`:
```kotlin
LocomotiveEntry("l7", "SD70M-2", "EMD", 2004, 4300, "DC traction", "C-C",
    listOf(Railroad.UP), "UP's modern DC locomotive.", null)
```

### Connect real scanner streams
In `getDemoChannels()`, replace `stream_url` values with real Broadcastify stream URLs. URLs follow the pattern: `https://broadcastify.cdnstream1.com/{feed_id}`

### Push notifications for approaching trains
Use `WorkManager` with a periodic task that calls `getLiveTrains()`, compares positions to saved spots, and fires a `NotificationCompat` when `etaMinutes < 5`.

---

## Screenshots

_Screenshots will appear here after first build._

---

## License

MIT License. See LICENSE for details.
