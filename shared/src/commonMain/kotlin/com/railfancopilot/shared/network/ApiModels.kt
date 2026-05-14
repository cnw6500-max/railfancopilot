package com.railfancopilot.shared.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ── Anthropic Claude API ──────────────────────────────────────────────────────

@Serializable
data class AnthropicRequest(
    val model: String = "claude-sonnet-4-6",
    @SerialName("max_tokens") val maxTokens: Int = 1024,
    val system: String? = null,
    val messages: List<AnthropicMessage>
)

@Serializable
data class AnthropicMessage(
    val role: String,
    val content: JsonElement   // String for text-only; JsonArray for multimodal
)

@Serializable
data class AnthropicResponse(
    val content: List<AnthropicContent> = emptyList()
)

@Serializable
data class AnthropicContent(
    val type: String = "",
    val text: String = ""
)

// ── Amtrak API (api.amtraker.com) ─────────────────────────────────────────────

@Serializable
data class AmtrakTrainJson(
    val trainNum: String = "",      // API sometimes returns alphanumeric e.g. "b5150"
    val routeName: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val heading: String? = null,    // cardinal direction e.g. "N", "NE", or numeric string
    val velocity: Double? = null,   // API returns float mph e.g. 72.899
    val trainTimely: String = "",
    val stations: List<AmtrakStationJson> = emptyList(),
    val trainState: String = ""
)

@Serializable
data class AmtrakStationJson(
    val code: String = "",
    val status: String = ""
)

// ── Nominatim (OpenStreetMap geocoding) ───────────────────────────────────────

@Serializable
data class NominatimResult(
    @SerialName("place_id")    val placeId: Long = 0L,
    @SerialName("display_name") val displayName: String = "",
    val lat: String = "0",
    val lon: String = "0",
    val type: String? = null
)

@Serializable
data class NominatimReverseResult(
    @SerialName("display_name") val displayName: String = "",
    val address: NominatimAddress = NominatimAddress()
)

@Serializable
data class NominatimAddress(
    val road: String? = null,
    val city: String? = null,
    val town: String? = null,
    val village: String? = null,
    val county: String? = null,
    val state: String? = null,
    @SerialName("country_code") val countryCode: String? = null
)

// ── SEPTA Regional Rail ───────────────────────────────────────────────────────

@Serializable
data class SeptaTrainJson(
    val lat: String = "0",
    val lon: String = "0",
    val trainno: String = "",
    val service: String = "",
    val dest: String = "",
    val currentstop: String = "",
    val nextstop: String = "",
    val line: String = "",
    val consist: String = "",
    val heading: String = "",
    val late: Int = 0,
    @SerialName("SOURCE") val source: String = ""
)

// ── MBTA v3 API ───────────────────────────────────────────────────────────────

@Serializable
data class MbtaVehicleResponse(
    val data: List<MbtaVehicleData> = emptyList()
)

@Serializable
data class MbtaVehicleData(
    val id: String = "",
    val attributes: MbtaVehicleAttributes = MbtaVehicleAttributes(),
    val relationships: MbtaVehicleRelationships = MbtaVehicleRelationships()
)

@Serializable
data class MbtaVehicleAttributes(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val speed: Double? = null,
    val bearing: Int? = null,
    val label: String = "",
    @SerialName("current_status") val currentStatus: String = "",
    @SerialName("updated_at") val updatedAt: String = ""
)

@Serializable
data class MbtaVehicleRelationships(
    val trip: MbtaRelationshipWrapper = MbtaRelationshipWrapper(),
    val route: MbtaRelationshipWrapper = MbtaRelationshipWrapper()
)

@Serializable
data class MbtaRelationshipWrapper(
    val data: MbtaRelationshipData? = null
)

@Serializable
data class MbtaRelationshipData(
    val id: String = ""
)
