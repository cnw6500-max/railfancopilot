# iOS handoff (from the Windows session, Nov 3, 2026)

Pick-up notes for finishing the iOS release on the Mac. Everything below is
committed on `main` in `cnw6500-max/railfancopilot`. On the Mac:

```bash
cd ~/path/to/railfancopilot   # or: git clone https://github.com/cnw6500-max/railfancopilot.git
git pull origin main
```

## Where things stand

| | Android | iOS |
|---|---|---|
| Version | 2.7.6 | 2.7.6 (`iosApp/project.yml`) |
| Build | versionCode 53 | CFBundleVersion 53 (kept equal to Android's versionCode) |
| Store | 50 (2.7.4) in production; 52/53 built locally, not yet uploaded | Last store build 2.7.3 (6), built by hand on the Mac |

iOS 2.7.6 (53) contains, relative to the last App Store build:

- **Firestore TLS security fix**: Firebase Apple SDK requirement raised from `from: 11.0.0`
  (which could never resolve the patch) to `from: 12.19.3` in `iosApp/project.yml`.
  Firebase emailed that iOS Firestore SDKs trusted compromised eMudhra CAs. **Ship this.**
- **Rare/heritage fix**: Heritage Spotter (`a1`) only unlocks on a real heritage ID
  (`isHeritageLocoID()` in `RailFanViewModel.swift`, reads the backend's `**Special Status:**` line).
- **One-time badge re-check**: on first launch, `recheckHeritageBadge()` revokes `a1` unless a
  saved loco ID still qualifies (flag `locoBadgeRecheckV1` in UserDefaults).
- **Saved-spot arrival alerts** (parity with Android's geofencing): regions are 300 m, capped at the
  20 nearest (iOS limit); on entry `checkTrainsOnArrival(at:)` fetches trains within 25 mi and
  posts `sendSpotArrivalNotification` (in `NotificationManager.swift`). Still gated on
  `approachNotificationsEnabled && isPremium` (Android is not Pro-gated: open product decision).

None of the Swift above has been compiled yet. It was written on Windows.

## iOS CI (`.github/workflows/ios-appstore.yml`)

All 19 runs before this handoff failed. Fixes so far:

1. **Shared framework (fixed, `7d62f3a`)**: the CocoaPods plugin generated cinterop for the
   Firebase pods and failed (`-fmodules`, then duplicate symbols with GitLive's own bindings).
   `shared` is now a **static** XCFramework with no pods; the app links Firebase once via SPM.
   `project.yml` no longer embeds `shared.xcframework` (static frameworks must not be embedded).
   Run #19 got past this and the XcodeGen step for the first time.
2. **Signing certificate (BLOCKED on secrets)**: run #19 failed at "Import signing certificate":
   `SecKeychainItemImport: Unable to decode the provided data`. The `APPLE_CERTIFICATE_BASE64`
   secret isn't a valid base64 `.p12`.
3. **Provisioning (fixed ahead, `a8b725a`, untested)**: Archive and Export now pass the App
   Store Connect API key with `-allowProvisioningUpdates` so automatic signing works on CI.

### Fix the secrets (needs this Mac)

```bash
./iosApp/scripts/export-signing-secrets.sh ~/Desktop/cert.p12 ~/Downloads/AuthKey_XXXXXXXXXX.p8
```

The script walks through it. In short:
1. Keychain Access → My Certificates → **Apple Distribution: … (7K7CYUNXTT)** (must have a
   private key under it) → Export as `.p12` with a password.
2. GitHub → repo Settings → Secrets and variables → Actions:
   - `APPLE_CERTIFICATE_BASE64`: one-line base64 of the `.p12`
   - `APPLE_CERTIFICATE_PASSWORD`: the export password
   - `KEYCHAIN_PASSWORD`: any random string
   - `APP_STORE_CONNECT_KEY_BASE64`: one-line base64 of the `.p8` API key
   - `APP_STORE_CONNECT_KEY_ID`, `APP_STORE_CONNECT_ISSUER_ID`: from App Store Connect → Users and Access → Integrations
3. Actions → **iOS App Store Release** → Run workflow.

Note: pushes to `main` trigger this workflow; commits with `[skip ci]` in the message don't.

## To do on the Mac, in order

1. Fix the signing secrets (above) and re-run the workflow. Likely next failure points:
   Swift compile errors in the new code, or GitLive 2.1.0 (built against Firebase 11) vs
   Firebase 12 at link time / runtime.
2. If CI keeps failing, build locally to iterate faster:
   ```bash
   ./iosApp/setup.sh          # builds shared.xcframework + runs xcodegen
   open iosApp/iosApp.xcodeproj
   ```
   Then Product → Archive. Same steps CI runs.
3. Smoke-test on a device: Firestore community feed loads (Firebase 12), photo ID,
   achievements, and arrival alerts (Settings → enable approach notifications → "Always" location;
   simulate arrival with Xcode's Debug → Simulate Location or a GPX file).
4. Submit **2.7.6 (53)** in App Store Connect. Builds 7 and 8 never uploaded; skip them.
   Review notes: "Security update (Firebase SDK) and bug fixes."
5. Decide whether iOS arrival alerts stay Pro-only (Android's aren't).

## Other context from this session

- Android 2.7.6 (53) adds geofence arrival alerts (`SpotGeofences.kt`); Play needs the
  background-location declaration + video. See `play-policy-prep-2026-10.md` (Draft B).
- Cloud Functions upgraded to firebase-functions 7.4 / firebase-admin 13 and deployed;
  `identifyLocomotive` returns the `**Special Status:**` line both apps read.
- Firestore index for `collectionGroup('watchlist').where('type', ...)` added and deployed
  (watched-loco alerts had been failing since Oct 28).
