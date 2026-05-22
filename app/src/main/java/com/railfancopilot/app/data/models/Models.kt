package com.railfancopilot.app.data.models

import androidx.room.Entity
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
    TRAIN_APPROACHING("Train Approaching", "🚂")
}

data class RailAlert(
    val id: String,
    val type: RailAlertType,
    val title: String,
    val message: String,
    val timestampMs: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val trainSymbol: String? = null,
    val isRead: Boolean = false
)

