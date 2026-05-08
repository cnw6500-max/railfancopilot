package com.railfancopilot.shared.models

// Pure Kotlin data classes — no Android/Room annotations.
// Room @Entity annotations live in the Android :app module's Database.kt layer.

// ── Train tracking ───────────────────────────────────────────────────────────

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
    BNSF("BNSF",               0xFF1A3A6B, 30f),
    UP("Union Pacific",         0xFF4A2A00, 60f),
    CSX("CSX",                  0xFF0A2A0A, 210f),
    NS("Norfolk Southern",      0xFF1A1A1A, 270f),
    CN("Canadian National",     0xFF8B0000, 0f),
    CP("Canadian Pacific",      0xFF8B0000, 330f),
    AMTRAK("Amtrak",            0xFF3A1A4A, 240f),
    KCS("Kansas City Southern", 0xFF1A1A3A, 180f),
    OTHER("Other",              0xFF2A2A2A, 120f)
}

enum class TrainStatus { ON_TIME, DELAYED, STOPPED, UNKNOWN }

// ── Map features ─────────────────────────────────────────────────────────────

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

// ── Scanner ──────────────────────────────────────────────────────────────────

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
    val confidence: Float
)

// ── Photography ──────────────────────────────────────────────────────────────

data class SunInfo(
    val elevationDegrees: Double,
    val azimuthDegrees: Double,
    val goldenHourStart: String,
    val goldenHourEnd: String,
    val isGoldenHour: Boolean
)

data class PhotoMetadataShared(
    val id: String,
    val railroad: String?,
    val trainSymbol: String?,
    val latitude: Double,
    val longitude: Double,
    val locationName: String?,
    val timestampMs: Long,
    val locoModel: String?,
    val notes: String?,
    val localPath: String? = null
)

// ── Symbol decode ─────────────────────────────────────────────────────────────

data class SymbolDecodeEntryShared(
    val id: String,
    val symbol: String,
    val railroad: String,
    val type: String,
    val origin: String,
    val destination: String,
    val timestampMs: Long
)

data class LocoIdEntryShared(
    val id: String,
    val resultText: String,
    val thumbnailPath: String?,
    val timestampMs: Long
)

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

// ── Community ─────────────────────────────────────────────────────────────────

data class CommunityReportShared(
    val id: String,
    val userId: String,
    val userName: String,
    val latitude: Double,
    val longitude: Double,
    val text: String,
    val trainSymbol: String?,
    val railroad: String?,
    val tags: String,
    val timestampMs: Long,
    val upvotes: Int,
    val isVerified: Boolean,
    val localPhotoPath: String? = null
)

// ── Encyclopedia ──────────────────────────────────────────────────────────────

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

// ── Saved locations ───────────────────────────────────────────────────────────

data class SavedLocationShared(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val notes: String?,
    val subdivision: String?,
    val scannerFrequency: String?,
    val photoTips: String?,
    val createdMs: Long
)

// ── Achievements ──────────────────────────────────────────────────────────────

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val iconEmoji: String,
    val earned: Boolean,
    val earnedMs: Long?
)

// ── Safety ────────────────────────────────────────────────────────────────────

data class SafetyAlert(
    val type: SafetyAlertType,
    val message: String,
    val severity: AlertSeverity
)

enum class SafetyAlertType { GEOFENCE_VIOLATION, WEATHER, PRIVATE_PROPERTY, RESTRICTED_AREA }
enum class AlertSeverity { INFO, WARNING, DANGER }
