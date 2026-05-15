package com.railfancopilot.shared.network

import com.railfancopilot.shared.platform.createHttpClient
import com.railfancopilot.shared.platform.logError
import com.railfancopilot.shared.platform.logWarn
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private val railfanJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

object RailFanApi {

    private val client = createHttpClient {
        install(ContentNegotiation) { json(railfanJson) }
        install(Logging) { level = LogLevel.NONE }
    }

    // ── Anthropic Claude ──────────────────────────────────────────────────────

    suspend fun sendAnthropicMessage(apiKey: String, request: AnthropicRequest): AnthropicResponse =
        client.post("https://api.anthropic.com/v1/messages") {
            contentType(ContentType.Application.Json)
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
            setBody(request)
        }.body()

    // ── Amtrak ────────────────────────────────────────────────────────────────

    suspend fun getAmtrakTrains(): Map<String, List<AmtrakTrainJson>> =
        client.get("https://api.amtraker.com/v3/trains").body()

    // ── Nominatim ─────────────────────────────────────────────────────────────

    suspend fun reverseGeocode(lat: Double, lon: Double): NominatimReverseResult =
        client.get("https://nominatim.openstreetmap.org/reverse") {
            header("User-Agent", "RailfanCopilot/2.0 (KMP)")
            parameter("lat", lat)
            parameter("lon", lon)
            parameter("format", "json")
            parameter("addressdetails", 1)
            parameter("zoom", 10)
        }.body()

    suspend fun searchLocation(query: String): List<NominatimResult> =
        client.get("https://nominatim.openstreetmap.org/search") {
            header("User-Agent", "RailfanCopilot/2.0 (KMP)")
            parameter("q", query)
            parameter("format", "json")
            parameter("limit", 6)
        }.body()

    // ── SEPTA Regional Rail ───────────────────────────────────────────────────

    suspend fun getSeptaTrains(): List<SeptaTrainJson> =
        client.get("https://www3.septa.org/api/TrainView/") {
            header("User-Agent", "RailfanCopilot/2.0 (KMP)")
        }.body()

    // ── MBTA ──────────────────────────────────────────────────────────────────

    suspend fun getMbtaVehicles(): MbtaVehicleResponse =
        client.get("https://api-v3.mbta.com/vehicles") {
            header("User-Agent", "RailfanCopilot/2.0 (KMP)")
            parameter("filter[route_type]", 2)
            parameter("fields[vehicle]", "latitude,longitude,speed,bearing,label,current_status,updated_at")
        }.body()

    // ── GTFS-RT binary feeds (Metra, MTA LIRR, Metro-North, Caltrain, SoundTransit) ──

    suspend fun fetchGtfsRtBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray? =
        try {
            client.get(url) {
                header("User-Agent", "RailfanCopilot/2.0 (KMP)")
                headers.forEach { (k, v) -> header(k, v) }
            }.body()
        } catch (e: Exception) {
            logError("GtfsRtApi", "Fetch failed for $url: ${e.message}", e)
            null
        }
}
