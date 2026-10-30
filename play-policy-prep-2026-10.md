# RailFan Copilot: Play Policy Prep (October 2026 update)

App: `com.railfancopilot.app` · versionCode 45 · targetSdk 36

| # | Item | Deadline | Who submits |
|---|------|----------|-------------|
| 1 | Trader status declaration | November 2026 (see note) | You, in Play Console |
| 2 | Japan payment collection authorization | December 1, 2026, *or the date in your invitation* | You, in Payments Center |
| 3 | Location declarations (fine + background) | January 27, 2027 | You, in Play Console → App content |

---

## 1. Trader status declaration

**Where:** Play Console → *Policy and programs* → *App content* (or the account-level *Developer account* → *Account details* → *Trader status* prompt).

**Your likely answer: Trader.** RailFan Copilot sells a paid in-app product (`railfan_copilot_pro`), and any app that earns money is generally treated as trade activity under the EU Digital Services Act.

**Have these ready:**
- [ ] Legal name (personal name if you're an individual developer)
- [ ] Postal address. **This is shown publicly on the store listing.** If you don't want your home address published, set up a business address or virtual mailbox first.
- [ ] Phone number (verified, also shown publicly)
- [ ] Email address (verified, also shown publicly). A dedicated support address is a good idea.
- [ ] Self-certification that you only offer products that comply with EU law
- [ ] If you're registered as an organization: D-U-N-S number / business registration details

**What happens if you skip it:** if Play has no verified declaration on file, the app can be pulled from all 27 EU countries.

> Note: I couldn't find Google's own announcement for the "November 2026" trader requirement in the email. What I did find covers the existing EU DSA trader declaration. Check Play Console → *Policy status* / *Inbox* for the exact form and date. If you already declared trader status earlier, you may only need to re-confirm it.

---

## 2. Japan payment collection authorization

**Does it apply?** Yes, **if Japan is among your app's countries** (Play Console → *Production* → *Countries/regions*). It applies to developers outside Japan who sell to users in Japan. If Japan isn't enabled and you don't want it, you can turn Japan off instead. If Japan is enabled, completing the form is the safer choice.

**Where:** Google Payments Center (payments.google.com) → follow the invitation sent to your developer account. Help page: *How to verify as a developer selling into or out of Japan*.

**The two parts:**
- [ ] **Payment Collection Authorization:** you authorize Google to act as your payment collection agent for sales to Japanese users.
- [ ] **Installment Sales Act (ISA) disclosures:** business information and legal declarations required by Japanese law.

**Have these ready:**
- [ ] Legal name / business name matching your payments profile
- [ ] Address and contact details
- [ ] Authority to sign the declarations (you, as account owner)

**Risk if missed:** Google **holds your earnings in every region**, not only Japan.

> Use the deadline in your Payments Center invitation if it differs from December 1.

---

## 3. Location declarations

### ⚠️ Fix first: the background location claim doesn't match the code

The background disclosure in `MainActivity.kt` (`BackgroundLocationDisclosureDialog`) tells users:

> "uses your location in the background to send you proximity alerts when trains approach your saved locations — even when the app is closed"

What the code actually does:
- Location updates come from `RailFanViewModel.startLocationTracking()` (FusedLocationProvider, high accuracy, every 10 s / 50 m). They stop when the ViewModel is cleared, so they live and die with the app's UI process.
- `LocationTrackingService` (the `foregroundServiceType="location"` service) **is never started anywhere.** Its comment says "Location updates are handled in the ViewModel".
- No `GeofencingClient` is used. `triggerGeofenceCheck()` runs only when the ViewModel gets a location update.
- "Nearby sighting" alerts are sent by the server (FCM) based on the last location synced to Firestore, so **they don't need background location.**

The result: you ask for "Allow all the time" for a feature that only half works (it keeps going while the process happens to stay alive, and stops once the app is closed). Play reviewers test the feature you declare. A declaration that doesn't match what the app does is a common reason for rejection, and it can count as misleading under the new "Deceptive Behavior" clarification.

**Pick one:**

**Option A: Drop background location (recommended, least work)**
- Remove `ACCESS_BACKGROUND_LOCATION` and `FOREGROUND_SERVICE_LOCATION` from the manifest
- Delete `LocationTrackingService` and its `<service>` entry
- Remove the background permission request and `BackgroundLocationDisclosureDialog` from `MainActivity.kt`
- Saved-location proximity alerts then work while the app is open. Server-side nearby-sighting alerts keep working when it's closed.
- No background declaration needed. Update `PRIVACY_POLICY.md` and the Data safety form if they mention background collection.

**Option B: Make background alerts real**
- Register each saved location with `GeofencingClient` (Android handles the background monitoring with very little battery cost, and no foreground service is needed)
- Fire the "train approaching" check from a geofence `BroadcastReceiver` / WorkManager job
- Keep the background permission and use the declaration text below
- Pro free-tier limit (3 saved locations) fits easily within the 100-geofence cap. For Pro users, cap it or register only the nearest 100.

### Draft A: ACCESS_FINE_LOCATION declaration

*(Required either way. Paste into Play Console → App content → Location permissions.)*

**Core feature that needs precise location:**
> RailFan Copilot is a railfan (train-spotting) app. Its core feature is a live map showing the user's position relative to moving trains and railroad tracks, with alerts when a train approaches the user's position or a saved trackside spot.

**Why approximate location or the location button isn't enough:**
> Railfan spots are specific trackside locations, often within a few hundred feet of each other along the same line. Approximate location (about 1–3 km) can't tell which side of a junction, which grade crossing, or which of two parallel lines the user is at, so it can't decide whether a given train will pass the user. Precise location is also used continuously while the map is open to update the user's position as they drive between spots and to measure the distance to approaching trains, which a one-time location button can't do. Users can also add their precise location to community sighting reports and spot entries.

**User-facing features that use it:**
> Live train map centered on the user · approaching-train alerts for the user's position and saved spots · scanner feeds sorted by distance · nearby rail yards · sun/golden-hour times for the user's spot · geotagging community sightings and saved spots.

### Draft B: ACCESS_BACKGROUND_LOCATION declaration

*(Use only with Option B, after geofencing is implemented. Play also requires a short video showing the disclosure, the permission prompt, and the feature working with the app closed.)*

**Feature:**
> Saved-spot proximity alerts: users save trackside locations (saved spots). When the user arrives at a saved spot, the app checks live train positions and notifies them if a train will pass soon, even if the app isn't open.

**Why it needs background access:**
> Railfans usually drive to a spot with the phone in a pocket or mount and the app closed. The alert is only useful if it fires when they arrive, without them having to open the app. Android geofences on the saved spots trigger the check; the app doesn't track a continuous location history.

**User controls:**
> The feature is opt-in. A prominent in-app disclosure is shown before the system prompt. Users can turn alerts off in Settings, and removing a saved spot removes its geofence.

**Video checklist:** disclosure dialog → "Allow all the time" prompt → saved spot list → app swiped away → arrival at spot (or mock location) → notification appears.

### Later: Android 17 (targetSdk 37)
When you raise targetSdk to 37, any one-time "use my location" action (for example, centering the map once or filling in location search) must use the system **location button**. Continuous map tracking stays covered by the fine location declaration above.

---

**Sources:**
- [How to verify as a developer selling into or out of Japan (Google Payments Center Help)](https://support.google.com/paymentscenter/answer/17595997?hl=en)
- [Requirements for distributing apps in specific countries/regions (Play Console Help)](https://support.google.com/googleplay/android-developer/answer/6223646?hl=en)
- [Enrolling in the external payments program (Play Console Help)](https://support.google.com/googleplay/android-developer/answer/16787536?hl=en)
- [Trader Status for Developer: DSA of EU (makaka.org)](https://makaka.org/unity-tutorials/trader-status)
