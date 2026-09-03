# Changelog

## 2.7.2 (iOS build 4) — unreleased
### STB Railroad Map Depot integration (Android + iOS)
- **Rail Lines overlay now uses the Surface Transportation Board / NTAD North American Rail Network** (`services3.arcgis.com/6rJKAjBRDRSfjCzV`) as the primary source, with OpenStreetMap/Overpass as a fallback. Lines are colored by owning railroad (AAR reporting mark → display name), double-track drawn heavier, and tapping a line shows owner · subdivision · yard · track count.
- **Auto-fill railroad & subdivision** on new Community Spots and Saved Locations from the nearest STB rail segment (≤ 800 m), with a "Nearest track: …" hint under the fields.
- **New "Abandoned" map overlay** — STB abandoned (grey dashed) and railbanked / rails-to-trails (green dashed) lines. Tap a line for railroad, docket number, county/state, length, filed/approved/completed dates and a link to STB docket records.
- iOS: "Rail Lines" and "Abandoned" chips added to the map (iOS previously had no rail-line overlay); `StbRailService.swift` mirrors Android's `StbRailFetcher.kt`.
- New Android models: `RailInfo`, `AbandonedRailLine`; `RailwaySegment` gains `ownerMark`, `subdivision`, `division`, `tracks`, `yardName`, `passenger`.

### Also in 2.7.2
- iOS Android-parity merge: Trip Log, Station Board, Log Transmission, yard detail sheet, Photo Enhancer, tagged photos, Community Spots, rebuilt Webcams, native MKMapView, voice dictation, per-agency settings, local persistence.
- Purchase-completion in-app review prompt (iOS + Android).
- iOS links native Firebase frameworks via CocoaPods for the shared XCFramework.
- Fix `Railroad.amtrak` enum case in `loadTimetable`.

## 2.7.1 (versionCode 40)
- Upgraded Google Play Billing Library from 7.1.1 to 9.1.0, meeting Google's Aug 31, 2026 requirement (v8+). No API changes were needed — existing code already used the current, non-deprecated Billing Library APIs.
- No user-facing changes.
