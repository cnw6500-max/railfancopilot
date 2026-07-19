package com.railfancopilot.app.data.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// ── Train tracking ──────────────────────────────────────────────────────────

data class TrainLocation(
    val id: String,
    val symbol: String,
    val railroad: Railroad,
    val latitude: Double,
    val longitude: Double,
    val speedMph: Int,
    val headingDegrees: Int,
    val etaMinutes: Int?,
    val status: TrainStatus,
    val consist: List<String>,
    val origin: String,
    val destination: String,
    val milepost: Double?,
    val subdivision: String?
)

enum class Railroad(val displayName: String, val color: Long, val markerHue: Float) {
    BNSF("BNSF",               0xFF1A3A6B, 30f),   // orange
    UP("Union Pacific",         0xFF4A2A00, 60f),   // yellow
    CSX("CSX",                  0xFF0A2A0A, 210f),  // azure
    NS("Norfolk Southern",      0xFF1A1A1A, 270f),  // violet
    CN("Canadian National",     0xFF8B0000, 0f),    // red
    CP("Canadian Pacific",      0xFF8B0000, 330f),  // rose
    AMTRAK("Amtrak",            0xFF3A1A4A, 240f),  // blue
    KCS("Kansas City Southern", 0xFF1A1A3A, 180f),  // cyan
    OTHER("Other",              0xFF2A2A2A, 120f)   // green
}

enum class TrainStatus { ON_TIME, DELAYED, STOPPED, UNKNOWN }

// ── Map features ────────────────────────────────────────────────────────────

data class MapFeature(
    val id: String,
    val type: MapFeatureType,
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val description: String?,
    val safetyNotes: String?,
    val scannerFrequency: String?,
    val isVerified: Boolean
)

enum class MapFeatureType {
    GRADE_CROSSING, SIGNAL, YARD, SIDING, CREW_CHANGE,
    PHOTO_SPOT, BRIDGE, TUNNEL
}

// ── Scanner ─────────────────────────────────────────────────────────────────

data class RadioChannel(
    val id: String,
    val name: String,
    val frequencyMhz: Double,
    val railroad: Railroad,
    val subdivision: String?,
    val streamUrl: String,
    val isActive: Boolean,
    val listenerCount: Int
)

data class Transcript(
    val id: String,
    val channelId: String,
    val text: String,
    val timestampMs: Long,
    val confidence: Float,
    val taggedTrainSymbol: String? = null
)

// ── Photography ─────────────────────────────────────────────────────────────

data class SunInfo(
    val elevationDegrees: Double,
    val azimuthDegrees: Double,
    val goldenHourStart: String,
    val goldenHourEnd: String,
    val isGoldenHour: Boolean
)

@Entity(tableName = "tagged_photos")
data class PhotoMetadata(
    @PrimaryKey val id: String,
    val railroad: String?,        // stored as name string — converted via RailroadConverter
    val trainSymbol: String?,
    val latitude: Double,
    val longitude: Double,
    val locationName: String?,
    val timestampMs: Long,
    val locoModel: String?,
    val notes: String?,
    val localPath: String? = null  // absolute path to JPEG on device storage
)

// ── Symbol decode history ─────────────────────────────────────────────────────

@Entity(tableName = "symbol_decode_history")
data class SymbolDecodeEntry(
    @PrimaryKey val id: String,
    val symbol:      String,
    val railroad:    String,
    val type:        String,
    val origin:      String,
    val destination: String,
    val timestampMs: Long
)

// ── Loco ID history ──────────────────────────────────────────────────────────

@Entity(tableName = "loco_id_history")
data class LocoIdEntry(
    @PrimaryKey val id: String,
    val resultText: String,
    val thumbnailPath: String?,   // absolute path to saved JPEG, or null
    val timestampMs: Long
)

// ── AI Decoder ───────────────────────────────────────────────────────────────

data class TrainSymbolDecodeResult(
    val symbol: String,
    val type: String,
    val origin: String,
    val destination: String,
    val schedule: String,
    val typicalConsist: List<String>,
    val railroad: Railroad,
    val notes: String,
    val priority: String
)

// ── Community ────────────────────────────────────────────────────────────────

enum class ConsistEntryType(val label: String) {
    LOCOMOTIVE("Locomotive"),
    BOXCAR("Boxcar"),
    TANK_CAR("Tank Car"),
    FLAT_CAR("Flatcar"),
    HOPPER("Hopper"),
    GONDOLA("Gondola"),
    INTERMODAL("Intermodal"),
    CABOOSE("Caboose"),
    OTHER("Other")
}

data class ConsistEntry(
    val type: ConsistEntryType,
    val model: String,
    val roadNumber: String,
    val railroad: String
)

@Entity(tableName = "community_reports")
data class CommunityReport(
    @PrimaryKey val id: String,
    val userId: String,
    val userName: String,
    val reporterUid: String? = null,   // real Firebase uid, for profile links / follow — userId above is actually the display name
    val latitude: Double,
    val longitude: Double,
    val text: String,
    val trainSymbol: String?,
    val railroad: String?,
    val tags: String,           // JSON list
    val timestampMs: Long,
    val upvotes: Int,
    val isVerified: Boolean,
    val localPhotoPath: String? = null,
    val consist: String? = null,        // JSON-serialized List<ConsistEntry>
    val weather: String? = null,        // e.g. "72°F · Partly Cloudy · 8 mph wind"
    val locationName: String = ""       // reverse-geocoded place name
)

// ── User profiles (crowdsourced identity) ─────────────────────────────────────

/**
 * Mirrors the Firestore `users/{uid}` document. Not a Room entity — read live
 * from the cloud, same pattern as CommunityReport.
 */
data class UserProfile(
    val uid: String,
    val username: String,        // unique handle, lowercase [a-z0-9_]{3,20}, backs follows/roster attribution
    val displayName: String,     // free-text label shown on reports, may differ in case/spacing from username
    val joinedMs: Long,
    val sightingCount: Int = 0,
    val reporterScore: Int = 0,
    val followerCount: Int = 0,
    val followingCount: Int = 0
)

sealed class UsernameClaimResult {
    data object Success : UsernameClaimResult()
    data object Taken : UsernameClaimResult()
    data object InvalidFormat : UsernameClaimResult()
    data class Error(val message: String) : UsernameClaimResult()
}

/** One row of `users/{uid}/following/{targetUid}` — denormalized so lists render with no extra fetches. */
data class FollowEntry(
    val uid: String,
    val username: String,
    val displayName: String,
    val followedMs: Long
)

/**
 * One document in the community-maintained `roster` collection — a specific
 * numbered locomotive (not a model/class; see LocomotiveEntry for that).
 * Doc ID is a normalized "{railroad}_{number}" key, so repeat sightings of
 * the same unit update one shared entry instead of creating duplicates.
 */
data class RosterEntry(
    val id: String,
    val railroad: String,
    val number: String,
    val model: String = "",       // optional, e.g. "ES44AC"
    val notes: String = "",       // paint scheme, special markings, corrections
    val photoUrl: String? = null,
    val submittedBy: String = "Railfan",
    val submittedMs: Long = 0L,
    val lastSeenMs: Long = 0L,
    val upvotes: Int = 0
)

// ── Encyclopedia ─────────────────────────────────────────────────────────────

data class LocomotiveEntry(
    val id: String,
    val model: String,
    val manufacturer: String,
    val introduced: Int,
    val horsepower: Int,
    val tractionMotors: String,
    val wheelArrangement: String,
    val railroads: List<Railroad>,
    val notes: String,
    val imageUrl: String?
)

// ── Train trail waypoints (14-day local + cloud retention) ───────────────────

/**
 * One position sample for a live train, persisted to Room and mirrored to
 * Firestore (train_trails/{trainId}/waypoints).
 * Waypoints older than TRAIL_RETENTION_MS are pruned on startup.
 */
@Entity(
    tableName = "train_trail_waypoints",
    indices = [
        Index("trainId"),
        Index("timestampMs")
    ]
)
data class TrainTrailWaypoint(
    @PrimaryKey val id: String,
    val trainId: String,
    val trainSymbol: String,
    val railroad: String,         // Railroad enum name
    val latitude: Double,
    val longitude: Double,
    val speedMph: Int,
    val timestampMs: Long
)

const val TRAIL_RETENTION_MS = 14L * 24 * 60 * 60 * 1_000L   // 14 days

// ── Trip logs ─────────────────────────────────────────────────────────────────

/**
 * A single user-initiated train ride.  endMs == 0L means the trip is still
 * in progress.  distanceMiles is accumulated in real-time from GPS updates.
 */
@Entity(tableName = "trip_logs")
data class TripLog(
    @PrimaryKey val id: String,
    val trainId: String,
    val trainSymbol: String,
    val railroad: String,             // Railroad enum name
    val startMs: Long,
    val endMs: Long = 0L,             // 0 = active
    val distanceMiles: Double = 0.0,
    val boardingStation: String? = null,
    val alightingStation: String? = null,
    val notes: String? = null
) {
    val isActive get() = endMs == 0L
    val durationMinutes get() = if (endMs > startMs) ((endMs - startMs) / 60_000).toInt() else 0
}

// ── Timetable cache ───────────────────────────────────────────────────────────

/**
 * Persists a fetched timetable (JSON-serialised stop list) so it survives
 * app restarts and works offline for up to [TIMETABLE_CACHE_TTL_MS].
 */
@Entity(tableName = "timetable_cache")
data class TimetableCacheEntry(
    @PrimaryKey val trainId: String,
    /** Gson-serialised List<TimetableStop> */
    val stopsJson: String,
    val fetchedMs: Long
)

const val TIMETABLE_CACHE_TTL_MS = 24L * 60 * 60 * 1_000L   // 24 hours

// ── Timetable ────────────────────────────────────────────────────────────────

/**
 * One station stop in an Amtrak train's timetable.
 * Times are pre-formatted strings (e.g. "3:42 PM") ready for display.
 */
data class TimetableStop(
    val code: String,
    val scheduledArrival: String?,
    val scheduledDeparture: String?,
    val actualArrival: String?,
    val actualDeparture: String?,
    /** e.g. "On Time", "4 Min Late", "Cancelled" — null if no info */
    val arrivalStatus: String?,
    val departureStatus: String?,
    val isBusThruway: Boolean,
    /** Train has already departed this stop */
    val hasDeparted: Boolean,
    /** Train has already arrived at this stop */
    val hasArrived: Boolean
) {
    /** True when this is a bus substitution segment, not rail. */
    val isRail get() = !isBusThruway
}

// ── Saved locations ──────────────────────────────────────────────────────────

@Entity(tableName = "saved_locations")
data class SavedLocation(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val notes: String?,
    val subdivision: String?,
    val scannerFrequency: String?,
    val photoTips: String?,
    val createdMs: Long
)

// ── Achievements ─────────────────────────────────────────────────────────────

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val iconEmoji: String,
    val earned: Boolean,
    val earnedMs: Long?
)

// ── Safety ───────────────────────────────────────────────────────────────────

data class SafetyAlert(
    val type: SafetyAlertType,
    val message: String,
    val severity: AlertSeverity
)

enum class SafetyAlertType { GEOFENCE_VIOLATION, WEATHER, PRIVATE_PROPERTY, RESTRICTED_AREA }
enum class AlertSeverity { INFO, WARNING, DANGER }

// ── Sighting comments ─────────────────────────────────────────────────────────

data class SightingComment(
    val id: String,
    val userName: String,
    val text: String,
    val timestampMs: Long
)

// ── Community Railfan Spots ───────────────────────────────────────────────────

enum class TrainFrequency(val label: String) {
    LIGHT("Light · <5 trains/day"),
    MODERATE("Moderate · 5–20 trains/day"),
    HEAVY("Heavy · 20+ trains/day"),
    UNKNOWN("Unknown")
}

data class RailfanSpot(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val submittedBy: String,
    val submittedMs: Long,
    val submittedByUid: String? = null,   // owner check for edit — submittedBy is just a display name
    val railroad: String = "",
    val subdivision: String = "",
    val notes: String = "",
    val photoAngles: String = "",
    val safetyNotes: String = "",
    val parkingNotes: String = "",
    val scannerFrequency: String = "",
    val seasonalNotes: String = "",
    val trainFrequency: TrainFrequency = TrainFrequency.UNKNOWN,
    val isPublicProperty: Boolean = true,
    val hasParking: Boolean = false,
    val hasRestrooms: Boolean = false,
    val hasFood: Boolean = false,
    val hasShade: Boolean = false,
    val upvotes: Int = 0,
    val photoUrls: List<String> = emptyList()
)

// ── Watchlist ─────────────────────────────────────────────────────────────────

enum class WatchlistType { SYMBOL, LOCO }

data class WatchlistEntry(
    val id: String,
    val type: WatchlistType,
    val value: String,          // train symbol (e.g. "QCHLA") or road number (e.g. "4030")
    val railroad: String = "",  // optional filter for LOCO type
    val label: String = "",     // display label, auto-generated if blank
    val addedMs: Long = System.currentTimeMillis()
)

// ── Railfan Alerts ────────────────────────────────────────────────────────────

enum class RailAlertType(val label: String, val emoji: String) {
    RARE_LOCO("Rare Locomotive", "⭐"),
    HOT_TRAIN("Hot Train", "🔥"),
    HIGH_SPEED("High Speed", "⚡"),
    SCANNER_ACTIVITY("Scanner Activity", "📻"),
    TRAIN_APPROACHING("Train Approaching", "🚂"),
    HERITAGE_UNIT("Heritage Unit", "🏆"),
    SPECIAL_MOVE("Special Movement", "🌟")
}

// ── Railway map lines (from Overpass API) ────────────────────────────────────
data class RailwaySegment(
    val id: Long,
    val points: List<com.google.android.gms.maps.model.LatLng>,
    val operator: String,   // e.g. "BNSF Railway"
    val name: String        // subdivision name from OSM tags
)

data class RailAlert(
    val id: String,
    val type: RailAlertType,
    val title: String,
    val message: String,
    val timestampMs: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val trainSymbol: String? = null,
    val isRead: Boolean = false,
    val isVerified: Boolean = false,   // reporter score >= threshold at time of alert
    val reporterScore: Int = 0,
    val locoNumber: String? = null,    // specific road number, e.g. "4014"
    val heritageName: String? = null   // scheme name, e.g. "UP Big Boy"
)

