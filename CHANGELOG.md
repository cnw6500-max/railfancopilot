# Changelog

## 2.7.6 (Android versionCode 53 · iOS build 53) — unreleased
iOS version and build number now track Android's versionName and versionCode.

### Saved-spot arrival alerts
- iOS: saved-spot regions shrink from 1 mile to 300 m and are capped at the 20 nearest (iOS's per-app limit). Arriving now fetches trains within 25 miles and reports the nearest, instead of a generic "you're near" message; still opt-in via approach notifications for Pro users.
- Each saved location is registered as an Android geofence (300 m, up to 100). Arriving at a spot, even with the app closed, runs a WorkManager job that fetches trains within 25 miles from the enabled feeds and notifies "You're at {spot}" with the nearest train. Geofences are re-registered on reboot and app update.
- Removed the unused `LocationTrackingService` and the `FOREGROUND_SERVICE_LOCATION` permission; the background location disclosure now describes what the app actually does.

## 2.7.3 (iOS build 6) — unreleased
### STB Railroad Map Depot integration (Android + iOS)
- **Rail Lines overlay now uses the Surface Transportation Board / NTAD North American Rail Network** (`services3.arcgis.com/6rJKAjBRDRSfjCzV`) as the primary source, with OpenStreetMap/Overpass as a fallback. Lines are colored by owning railroad (AAR reporting mark → display name), double-track drawn heavier, and tapping a line shows owner · subdivision · yard · track count.
- **Auto-fill railroad & subdivision** on new Community Spots and Saved Locations from the nearest STB rail segment (≤ 800 m), with a "Nearest track: …" hint under the fields.
- **New "Abandoned" map overlay** — STB abandoned (grey dashed) and railbanked / rails-to-trails (green dashed) lines. Tap a line for railroad, docket number, county/state, length, filed/approved/completed dates and a link to STB docket records.
- iOS: "Rail Lines" and "Abandoned" chips added to the map (iOS previously had no rail-line overlay); `StbRailService.swift` mirrors Android's `StbRailFetcher.kt`.
- New Android models: `RailInfo`, `AbandonedRailLine`; `RailwaySegment` gains `ownerMark`, `subdivision`, `division`, `tracks`, `yardName`, `passenger`.

### Rare / heritage false positives (Android 2.7.5, versionCode 52; iOS build 8)
- Fixed nearly every locomotive being flagged as heritage or rare: photo ID now reads an explicit Special Status line from `identifyLocomotive`, Rare Locomotive alerts no longer treat CN/CP/CPKC/VIA as foreign power or fire on your own sightings, and the community heritage tag requires the railroad to match.
- One-time badge re-check on first launch: Heritage Spotter (and Foreign Power on Android) stay earned only if a saved locomotive ID still qualifies under the new rules.

### Also in 2.7.2
- iOS Android-parity merge: Trip Log, Station Board, Log Transmission, yard detail sheet, Photo Enhancer, tagged photos, Community Spots, rebuilt Webcams, native MKMapView, voice dictation, per-agency settings, local persistence.
- Purchase-completion in-app review prompt (iOS + Android).
- iOS links native Firebase frameworks via CocoaPods for the shared XCFramework.
- Fix `Railroad.amtrak` enum case in `loadTimetable`.

## 2.7.1 (versionCode 40)
- Upgraded Google Play Billing Library from 7.1.1 to 9.1.0, meeting Google's Aug 31, 2026 requirement (v8+). No API changes were needed — existing code already used the current, non-deprecated Billing Library APIs.
- No user-facing changes.
