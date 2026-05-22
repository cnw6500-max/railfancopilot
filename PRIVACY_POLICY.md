# Privacy Policy — Railfan Copilot

**Last updated: May 22, 2026**

---

## 1. Overview

Railfan Copilot ("the app", "we", "us") is a railfan utility application for Android. This policy explains what data we collect, how we use it, and your rights regarding that data.

---

## 2. Data We Collect

### 2.1 Location
- **What:** Precise GPS coordinates (latitude/longitude)
- **Why:** To show nearby trains on the map, sort scanner feeds by proximity, tag community sighting reports, and send proximity alerts when trains approach your saved locations
- **When:** While the app is in use (foreground). Background location is used only to send approach alerts when the app is closed, and only if you grant "Allow all the time" permission
- **Stored:** Your current location is never permanently stored by us. Sighting reports you submit include your GPS coordinates and are stored in Google Firebase

### 2.2 Photos
- **What:** Photos you choose to submit for AI analysis
- **Why:** To identify locomotives, analyze consists, and enhance photos using Anthropic's Claude AI
- **Stored:** Photos sent for AI analysis are transmitted to Anthropic's API and are not stored by us. Photos you attach to community sighting reports or railfan spots are stored in Google Firebase Storage

### 2.3 Anonymous Device ID
- **What:** A randomly generated anonymous identifier assigned by Firebase Authentication on first launch
- **Why:** To associate your watchlist entries with your device so we can deliver push notifications when a watched locomotive or train symbol is spotted
- **Stored:** This ID is stored in Google Firebase. It contains no personal information — no name, email, or account
- **Reset:** Uninstalling and reinstalling the app generates a new ID and clears your watchlist

### 2.4 Push Notification Token (FCM Token)
- **What:** A device token issued by Google Firebase Cloud Messaging
- **Why:** To deliver watchlist alerts and approach notifications to your device
- **Stored:** Stored in Google Firebase alongside your anonymous device ID

### 2.5 Community Content
- **What:** Sighting reports, comments, railfan spot submissions, and upvotes you submit
- **Why:** To power the community feed visible to all app users
- **Stored:** Stored in Google Firebase Firestore and Firebase Storage. Community content is public and visible to all users of the app

### 2.6 In-App Purchases
- **What:** Purchase records for Railfan Copilot Pro
- **Why:** To verify Pro status and unlock Pro features
- **Stored:** Purchase history is managed by Google Play Billing. We receive only a confirmation of purchase — we do not receive your payment details

### 2.7 App Preferences
- **What:** Settings such as refresh interval, railroad toggles, display name, and alert preferences
- **Why:** To persist your configuration between sessions
- **Stored:** Locally on your device using Android DataStore. Not transmitted to any server

---

## 3. Third-Party Services

| Service | Purpose | Privacy Policy |
|---------|---------|----------------|
| Google Firebase (Firestore, Storage, Auth, Cloud Messaging, Cloud Functions) | Community data storage, anonymous auth, push notifications, AI API proxy | https://firebase.google.com/support/privacy |
| Anthropic Claude API | AI locomotive identification, consist analysis, train symbol decoding | https://www.anthropic.com/privacy |
| Google Maps SDK | Interactive map display | https://policies.google.com/privacy |
| Google Play Billing | In-app purchases | https://policies.google.com/privacy |
| OpenRailwayMap / OpenStreetMap | Railroad infrastructure map overlay tiles | https://wiki.osmfoundation.org/wiki/Privacy_Policy |
| Nominatim (OpenStreetMap) | Reverse geocoding for city names in approach alerts | https://nominatim.org/release-docs/latest/api/Reverse/ |
| Railroad scanner streams (railroadradio.net and others) | Live railroad radio audio | Streamed over standard HTTP audio — no personal data transmitted |

---

## 4. Data We Do NOT Collect

- Your name, email address, or any account credentials
- Browsing history or activity outside the app
- Contacts or call logs
- Payment card or financial information
- Persistent device identifiers (IMEI, IDFA, etc.)

---

## 5. Scanner Audio

Live railroad scanner audio is streamed directly from public radio sources (such as railroadradio.net) over standard HTTP connections. This is an industry-standard protocol for public scanner audio feeds. No personal data is transmitted over these connections. Audio is not recorded or stored by the app.

---

## 6. Data Retention

| Data type | Retention |
|-----------|-----------|
| Community sightings and comments | Stored indefinitely unless deleted by us for policy violations |
| Railfan spot submissions | Stored indefinitely |
| Spot and sighting photos | Stored indefinitely in Firebase Storage |
| Anonymous device ID and FCM token | Retained until you uninstall the app or we delete inactive records |
| App preferences | Stored locally; deleted when app is uninstalled |
| AI analysis photos | Not retained — transmitted to Anthropic and discarded |

---

## 7. Data Security

- All communication between the app and Firebase/Anthropic uses HTTPS
- Railroad scanner audio streams use HTTP (required by source providers — no personal data involved)
- Firebase security rules restrict access so users can only read/write their own watchlist data
- Community content is publicly readable but write-protected by Firebase authentication rules
- App backup is disabled — your local preferences and history are not backed up to Google or transferred to other devices

---

## 8. Children's Privacy

Railfan Copilot is not directed at children under 13. We do not knowingly collect personal information from children under 13. If you believe a child has submitted personal information through the app, please contact us.

---

## 9. Your Rights

You may:
- **Delete community content** — contact us to request removal of sightings, comments, or spot submissions you posted
- **Reset your anonymous ID** — uninstall and reinstall the app
- **Disable location** — revoke location permission in Android Settings at any time. Core map and alert features will not function without it
- **Disable notifications** — revoke notification permission in Android Settings
- **Request data deletion** — contact us at the address below

---

## 10. Refunds

In-app purchases can be refunded through Google Play within 48 hours of purchase. Open Google Play → profile icon → Payments & subscriptions → Order history → Request a refund.

---

## 11. Changes to This Policy

We may update this policy as new features are added. The "Last updated" date at the top will reflect any changes. Continued use of the app after changes constitutes acceptance of the updated policy.

---

## 12. Contact

For privacy questions or data deletion requests:

**Email:** privacy@railfancopilot.com

---

*Railfan Copilot is an independent app and is not affiliated with any railroad company, Amtrak, or transit agency.*
