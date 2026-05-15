package com.railfancopilot.shared.repository

import com.railfancopilot.shared.models.*
import com.railfancopilot.shared.network.*
import com.railfancopilot.shared.platform.logError
import kotlinx.datetime.*
import kotlinx.serialization.json.*
import kotlin.math.*

class RailFanRepository(
    private val anthropicApiKey: String = "",
    private val metraUser: String = "",
    private val metraPassword: String = "",
    private val mtaApiKey: String = "",
    private val fiveElevenKey: String = ""
) {

    // ── Geometry ──────────────────────────────────────────────────────────────

    // Haversine formula — pure Kotlin, no Android Location import needed.
    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6_371_000.0
        val dLat = (lat2 - lat1) * PI / 180.0
        val dLon = (lon2 - lon1) * PI / 180.0
        val a = sin(dLat / 2).pow(2) +
                cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) * sin(dLon / 2).pow(2)
        return R * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }

    private fun distanceMiles(lat1: Double, lon1: Double, lat2: Double, lon2: Double) =
        distanceMeters(lat1, lon1, lat2, lon2) / 1609.34

    private fun bearingDegrees(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val dLon = (toLon - fromLon) * PI / 180.0
        val y = sin(dLon) * cos(toLat * PI / 180.0)
        val x = cos(fromLat * PI / 180.0) * sin(toLat * PI / 180.0) -
                sin(fromLat * PI / 180.0) * cos(toLat * PI / 180.0) * cos(dLon)
        return ((atan2(y, x) * 180.0 / PI) + 360.0) % 360.0
    }

    private fun computeEtaMinutes(
        userLat: Double, userLon: Double,
        trainLat: Double, trainLon: Double,
        speedMph: Int, headingDeg: Int
    ): Int? {
        if (speedMph < 5) return null
        val bearingToUser = bearingDegrees(trainLat, trainLon, userLat, userLon)
        val headingDiff = abs(((headingDeg - bearingToUser + 540.0) % 360.0) - 180.0)
        if (headingDiff > 90.0) return null
        val distMiles = distanceMiles(trainLat, trainLon, userLat, userLon)
        if (distMiles > 150.0) return null
        return (distMiles / speedMph * 60.0).toInt().coerceAtLeast(0)
    }

    // ── Live trains ───────────────────────────────────────────────────────────

    suspend fun getLiveTrains(lat: Double, lon: Double, radiusMiles: Double = NEARBY_RADIUS_MILES): List<TrainLocation> =
        try {
            RailFanApi.getAmtrakTrains().values.flatten()
                .filter { it.lat != null && it.lon != null && it.lat != 0.0 && it.lon != 0.0 }
                .map { train ->
                    val timely = train.trainTimely.uppercase()
                    val speedMph = train.velocity?.toInt() ?: 0
                    val heading = cardinalToDegrees(train.heading ?: "")
                    TrainLocation(
                        id            = "amtrak-${train.trainNum}",
                        symbol        = "${train.routeName} #${train.trainNum}",
                        railroad      = Railroad.AMTRAK,
                        latitude      = train.lat!!,
                        longitude     = train.lon!!,
                        speedMph      = speedMph,
                        headingDegrees = heading,
                        etaMinutes    = computeEtaMinutes(lat, lon, train.lat!!, train.lon!!, speedMph, heading),
                        status = when {
                            timely == "ON TIME" || timely.contains("EARLY") -> TrainStatus.ON_TIME
                            timely.contains("LATE") -> TrainStatus.DELAYED
                            else -> TrainStatus.UNKNOWN
                        },
                        consist       = listOf("P42DC"),
                        origin        = train.stations.firstOrNull()?.code ?: "Origin",
                        destination   = train.stations.lastOrNull()?.code ?: "Destination",
                        milepost      = null,
                        subdivision   = train.routeName
                    )
                }
        } catch (e: Exception) {
            logError("RailFanRepo", "Amtrak API failed: ${e.message}", e)
            emptyList()
        }

    suspend fun getSeptaTrains(lat: Double, lon: Double, radiusMiles: Double = NEARBY_RADIUS_MILES): List<TrainLocation> =
        try {
            RailFanApi.getSeptaTrains()
                .mapNotNull { train ->
                    val tLat = train.lat.toDoubleOrNull() ?: return@mapNotNull null
                    val tLon = train.lon.toDoubleOrNull() ?: return@mapNotNull null
                    if (tLat == 0.0 && tLon == 0.0) return@mapNotNull null
                    if (distanceMiles(lat, lon, tLat, tLon) > radiusMiles) return@mapNotNull null
                    val heading = cardinalToDegrees(train.heading)
                    val lineLabel = train.line.removeSuffix(" Line").removeSuffix(" line").trim().ifBlank { train.dest }
                    TrainLocation(
                        id             = "septa-${train.trainno}",
                        symbol         = "SEPTA $lineLabel",
                        railroad       = Railroad.OTHER,
                        latitude       = tLat,
                        longitude      = tLon,
                        speedMph       = 0,
                        headingDegrees = heading,
                        etaMinutes     = null,
                        status         = if (train.late > 0) TrainStatus.DELAYED else TrainStatus.ON_TIME,
                        consist        = train.consist.split(",").map { it.trim() }.filter { it.isNotBlank() },
                        origin         = train.currentstop,
                        destination    = train.dest,
                        milepost       = null,
                        subdivision    = train.service
                    )
                }
        } catch (e: Exception) {
            logError("RailFanRepo", "SEPTA API failed: ${e.message}", e)
            emptyList()
        }

    suspend fun getMbtaTrains(lat: Double, lon: Double, radiusMiles: Double = NEARBY_RADIUS_MILES): List<TrainLocation> =
        try {
            RailFanApi.getMbtaVehicles().data
                .filter { it.attributes.latitude != 0.0 && it.attributes.longitude != 0.0 }
                .filter { distanceMiles(lat, lon, it.attributes.latitude, it.attributes.longitude) <= radiusMiles }
                .map { vehicle ->
                    val attr = vehicle.attributes
                    val speedMph = ((attr.speed ?: 0.0) * 2.23694).toInt()
                    val heading  = attr.bearing ?: 0
                    val routeId  = vehicle.relationships.route.data?.id ?: "MBTA"
                    val lineName = routeId.removePrefix("CR-").replace("-", " ")
                    TrainLocation(
                        id             = "mbta-${vehicle.id}",
                        symbol         = "MBTA $lineName",
                        railroad       = Railroad.OTHER,
                        latitude       = attr.latitude,
                        longitude      = attr.longitude,
                        speedMph       = speedMph,
                        headingDegrees = heading,
                        etaMinutes     = computeEtaMinutes(lat, lon, attr.latitude, attr.longitude, speedMph, heading),
                        status = when {
                            attr.currentStatus == "STOPPED_AT" -> TrainStatus.STOPPED
                            speedMph > 0 -> TrainStatus.ON_TIME
                            else -> TrainStatus.UNKNOWN
                        },
                        consist        = if (attr.label.isNotBlank()) listOf(attr.label) else emptyList(),
                        origin         = "Boston",
                        destination    = lineName,
                        milepost       = null,
                        subdivision    = routeId
                    )
                }
        } catch (e: Exception) {
            logError("RailFanRepo", "MBTA API failed: ${e.message}", e)
            emptyList()
        }

    // ── GTFS-RT feeds ─────────────────────────────────────────────────────────

    suspend fun getMetraTrains(lat: Double, lon: Double, radiusMiles: Double = NEARBY_RADIUS_MILES): List<TrainLocation> {
        if (metraUser.isBlank() || metraPassword.isBlank()) return emptyList()
        val credentials = encodeBasicAuth(metraUser, metraPassword)
        return fetchGtfsRt(
            tag = "Metra",
            url = "https://gtfs.metra.com/gtfs-rt/VehiclePositions.pb",
            headers = mapOf("Authorization" to "Basic $credentials"),
            railroad = Railroad.OTHER, agencyLabel = "Metra",
            userLat = lat, userLon = lon, radiusMiles = radiusMiles
        )
    }

    suspend fun getMtaLirrTrains(lat: Double, lon: Double, radiusMiles: Double = NEARBY_RADIUS_MILES): List<TrainLocation> {
        if (mtaApiKey.isBlank()) return emptyList()
        return fetchGtfsRt(
            tag = "LIRR",
            url = "https://api-endpoint.mta.info/Datastore/Publish/20160908/2be4ea32-d106-401d-83f3-404f3e7f2c30/VehiclePositions",
            headers = mapOf("x-api-key" to mtaApiKey),
            railroad = Railroad.OTHER, agencyLabel = "LIRR",
            userLat = lat, userLon = lon, radiusMiles = radiusMiles
        )
    }

    suspend fun getMtaMetroNorthTrains(lat: Double, lon: Double, radiusMiles: Double = NEARBY_RADIUS_MILES): List<TrainLocation> {
        if (mtaApiKey.isBlank()) return emptyList()
        return fetchGtfsRt(
            tag = "Metro-North",
            url = "https://api-endpoint.mta.info/Datastore/Publish/20160908/2be4ea32-d106-401d-83f3-404f3e7f2c30/MetroNorthVehiclePositions",
            headers = mapOf("x-api-key" to mtaApiKey),
            railroad = Railroad.OTHER, agencyLabel = "Metro-North",
            userLat = lat, userLon = lon, radiusMiles = radiusMiles
        )
    }

    suspend fun getCaltrainTrains(lat: Double, lon: Double, radiusMiles: Double = NEARBY_RADIUS_MILES): List<TrainLocation> {
        if (fiveElevenKey.isBlank()) return emptyList()
        return fetchGtfsRt(
            tag = "Caltrain",
            url = "https://api.511.org/Transit/VehiclePositions?api_key=$fiveElevenKey&agency=CT",
            railroad = Railroad.OTHER, agencyLabel = "Caltrain",
            userLat = lat, userLon = lon, radiusMiles = radiusMiles
        )
    }

    suspend fun getSoundTransitTrains(lat: Double, lon: Double, radiusMiles: Double = NEARBY_RADIUS_MILES): List<TrainLocation> =
        fetchGtfsRt(
            tag = "SoundTransit",
            url = "https://www.soundtransit.org/gtfs-rt/GTFS-Sounder-VehiclePositions.pb",
            railroad = Railroad.OTHER, agencyLabel = "Sounder",
            userLat = lat, userLon = lon, radiusMiles = radiusMiles
        )

    private suspend fun fetchGtfsRt(
        tag: String, url: String,
        headers: Map<String, String> = emptyMap(),
        railroad: Railroad, agencyLabel: String,
        userLat: Double, userLon: Double, radiusMiles: Double
    ): List<TrainLocation> {
        val bytes = RailFanApi.fetchGtfsRtBytes(url, headers) ?: return emptyList()
        return try {
            GtfsRtProtoParser.parse(bytes).mapNotNull { v ->
                if (v.latitude == 0f && v.longitude == 0f) return@mapNotNull null
                val vLat = v.latitude.toDouble()
                val vLon = v.longitude.toDouble()
                if (distanceMiles(userLat, userLon, vLat, vLon) > radiusMiles) return@mapNotNull null
                val speedMph = (v.speedMs * 2.23694f).toInt()
                val heading  = v.bearing.toInt()
                val lineLabel = v.routeId
                    .removePrefix("$agencyLabel-").removePrefix("${agencyLabel}_")
                    .replace("-", " ").replace("_", " ").trim().ifBlank { v.entityId }
                TrainLocation(
                    id             = "$agencyLabel-${v.entityId}",
                    symbol         = "$agencyLabel $lineLabel".trim(),
                    railroad       = railroad,
                    latitude       = vLat,
                    longitude      = vLon,
                    speedMph       = speedMph,
                    headingDegrees = heading,
                    etaMinutes     = if (speedMph > 0) computeEtaMinutes(userLat, userLon, vLat, vLon, speedMph, heading) else null,
                    status         = if (v.currentStatus == 1) TrainStatus.STOPPED else if (speedMph > 0) TrainStatus.ON_TIME else TrainStatus.UNKNOWN,
                    consist        = emptyList(),
                    origin         = "",
                    destination    = lineLabel,
                    milepost       = null,
                    subdivision    = v.routeId
                )
            }
        } catch (e: Exception) {
            logError("GtfsRtFetcher", "$tag parse failed: ${e.message}", e)
            emptyList()
        }
    }

    // ── AI symbol decoder ──────────────────────────────────────────────────────

    suspend fun decodeTrainSymbol(symbol: String): Result<TrainSymbolDecodeResult> {
        if (anthropicApiKey.isBlank())
            return Result.failure(Exception("AI Decoder requires an Anthropic API key."))
        return try {
            val systemPrompt = buildSymbolDecoderSystemPrompt()
            val response = RailFanApi.sendAnthropicMessage(
                apiKey = anthropicApiKey,
                request = AnthropicRequest(
                    system = systemPrompt,
                    messages = listOf(
                        AnthropicMessage(
                            role    = "user",
                            content = JsonPrimitive("Decode this train symbol: ${symbol.trim().uppercase()}")
                        )
                    )
                )
            )
            val rawText = response.content.firstOrNull()?.text ?: "{}"
            val jsonText = rawText.replace("```json", "").replace("```", "").trim()
            val parsed = railfanJson.decodeFromString<DecodedSymbolJson>(jsonText)
            Result.success(TrainSymbolDecodeResult(
                symbol    = parsed.symbol ?: symbol,
                type      = parsed.type ?: "Unknown",
                origin    = parsed.origin ?: "Unknown",
                destination = parsed.destination ?: "Unknown",
                schedule  = parsed.schedule ?: "Unknown",
                typicalConsist = parsed.typicalConsist ?: emptyList(),
                railroad  = Railroad.entries.find { it.name == (parsed.railroad ?: "") } ?: Railroad.OTHER,
                notes     = parsed.notes ?: "",
                priority  = parsed.priority ?: "Unknown"
            ))
        } catch (e: Exception) {
            logError("RailFanDecoder", "Decode failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun identifyLocomotive(base64ImageJpeg: String): Result<String> {
        if (anthropicApiKey.isBlank())
            return Result.failure(Exception("Loco Identifier requires an Anthropic API key."))
        return try {
            val content = buildJsonArray {
                addJsonObject {
                    put("type", "image")
                    putJsonObject("source") {
                        put("type", "base64")
                        put("media_type", "image/jpeg")
                        put("data", base64ImageJpeg)
                    }
                }
                addJsonObject {
                    put("type", "text")
                    put("text", "Identify this locomotive. Include: model (e.g. ES44AC, SD70ACe), railroad, road number if visible, and 1-2 notable features. Be concise — 2-3 sentences.")
                }
            }
            val response = RailFanApi.sendAnthropicMessage(
                apiKey = anthropicApiKey,
                request = AnthropicRequest(
                    system = "You are an expert railfan and locomotive identifier specializing in North American diesel and electric locomotives. Identify locomotives from photos accurately and concisely.",
                    messages = listOf(AnthropicMessage(role = "user", content = content))
                )
            )
            Result.success(response.content.firstOrNull()?.text ?: "Unable to identify locomotive.")
        } catch (e: Exception) {
            logError("LocoIdentifier", "Identify failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ── Geocoding ─────────────────────────────────────────────────────────────

    suspend fun reverseGeocode(lat: Double, lon: Double): String? =
        try {
            val result = RailFanApi.reverseGeocode(lat, lon)
            val addr = result.address
            val place = addr.city ?: addr.town ?: addr.village ?: addr.county
            when {
                place != null && addr.state != null -> "$place, ${addr.state}"
                place != null -> place
                else -> result.displayName.split(",").take(2).joinToString(",").trim().takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) { null }

    suspend fun searchLocation(query: String): List<NominatimResult> =
        try { RailFanApi.searchLocation(query) }
        catch (_: Exception) { emptyList() }

    // ── Sun calculator (kotlinx-datetime — no java.util.Calendar) ────────────

    fun calculateSunInfo(lat: Double, lon: Double, timeMs: Long = Clock.System.now().toEpochMilliseconds()): SunInfo {
        val instant  = Instant.fromEpochMilliseconds(timeMs)
        val tz       = TimeZone.currentSystemDefault()
        val localDT  = instant.toLocalDateTime(tz)
        val hour     = localDT.hour + localDT.minute / 60.0
        val dayOfYear = localDT.date.dayOfYear
        val tzOffsetHours = tz.offsetAt(instant).totalSeconds / 3600.0

        val decl = 23.45 * sin((360.0 / 365.0 * (dayOfYear - 81)) * PI / 180.0)

        val B = (360.0 / 365.0 * (dayOfYear - 81)) * PI / 180.0
        val eqTimeMin = 9.87 * sin(2 * B) - 7.53 * cos(B) - 1.5 * sin(B)

        val solarNoonLCT = 12.0 - lon / 15.0 + tzOffsetHours - eqTimeMin / 60.0
        val hourAngle    = (hour - solarNoonLCT) * 15.0

        val elevRad = asin(
            sin(lat * PI / 180.0) * sin(decl * PI / 180.0) +
            cos(lat * PI / 180.0) * cos(decl * PI / 180.0) * cos(hourAngle * PI / 180.0)
        )
        val elevation = elevRad * 180.0 / PI
        val azimuth = ((atan2(
            -sin(hourAngle * PI / 180.0),
            tan(decl * PI / 180.0) * cos(lat * PI / 180.0) -
            sin(lat * PI / 180.0) * cos(hourAngle * PI / 180.0)
        ) * 180.0 / PI) + 360.0) % 360.0

        fun hourAngleAt(targetElevDeg: Double): Double? {
            val cosH = (sin(targetElevDeg * PI / 180.0) -
                        sin(lat * PI / 180.0) * sin(decl * PI / 180.0)) /
                       (cos(lat * PI / 180.0) * cos(decl * PI / 180.0))
            if (cosH < -1.0 || cosH > 1.0) return null
            return acos(cosH) * 180.0 / PI
        }

        val haHorizon = hourAngleAt(-0.833)
        val ha6Deg    = hourAngleAt(6.0)

        if (haHorizon == null) {
            return SunInfo(elevation, azimuth, "--:--", "--:--", false)
        }

        val sunriseLCT = solarNoonLCT - haHorizon / 15.0
        val sunsetLCT  = solarNoonLCT + haHorizon / 15.0
        val morningGoldEndLCT   = if (ha6Deg != null) solarNoonLCT - ha6Deg / 15.0 else sunsetLCT
        val eveningGoldStartLCT = if (ha6Deg != null) solarNoonLCT + ha6Deg / 15.0 else sunriseLCT

        val (ghStart, ghEnd) = if (hour < solarNoonLCT)
            sunriseLCT to morningGoldEndLCT
        else
            eveningGoldStartLCT to sunsetLCT

        return SunInfo(
            elevationDegrees = elevation,
            azimuthDegrees   = azimuth,
            goldenHourStart  = formatLocalHour(ghStart),
            goldenHourEnd    = formatLocalHour(ghEnd),
            isGoldenHour     = elevation in -0.833..6.0
        )
    }

    private fun formatLocalHour(h: Double): String {
        val h24  = ((h % 24.0) + 24.0) % 24.0
        val hInt = h24.toInt()
        val min  = ((h24 - hInt) * 60.0).toInt().coerceIn(0, 59)
        val isPm = hInt >= 12
        val h12  = when { hInt == 0 -> 12; hInt > 12 -> hInt - 12; else -> hInt }
        return "$h12:${min.toString().padStart(2, '0')} ${if (isPm) "PM" else "AM"}"
    }

    // ── AAR frequencies ───────────────────────────────────────────────────────

    fun getAARFrequencies(): List<RadioChannel> = listOf(
        RadioChannel("bnsf-1",  "Dispatcher (AAR Ch 6)",          160.410, Railroad.BNSF, null, "", false, 0),
        RadioChannel("bnsf-2",  "Dispatcher (AAR Ch 12)",         160.515, Railroad.BNSF, null, "", false, 0),
        RadioChannel("bnsf-3",  "Dispatcher (AAR Ch 8)",          160.230, Railroad.BNSF, null, "", false, 0),
        RadioChannel("bnsf-4",  "Yardmaster (AAR Ch 10)",         160.260, Railroad.BNSF, null, "", false, 0),
        RadioChannel("bnsf-5",  "Maintenance of Way (AAR Ch 90)", 161.385, Railroad.BNSF, null, "", false, 0),
        RadioChannel("bnsf-6",  "EOT / HOT link (AAR Ch 72)",     161.100, Railroad.BNSF, null, "", false, 0),
        RadioChannel("bnsf-7",  "Dispatcher (AAR Ch 97)",         161.565, Railroad.BNSF, null, "", false, 0),
        RadioChannel("up-1",    "Dispatcher (AAR Ch 12)",         160.515, Railroad.UP, null, "", false, 0),
        RadioChannel("up-2",    "Dispatcher (AAR Ch 18)",         160.590, Railroad.UP, null, "", false, 0),
        RadioChannel("up-3",    "Dispatcher (AAR Ch 52)",         161.010, Railroad.UP, null, "", false, 0),
        RadioChannel("up-4",    "Dispatcher (AAR Ch 63)",         161.130, Railroad.UP, null, "", false, 0),
        RadioChannel("up-5",    "Dispatcher (AAR Ch 56)",         161.070, Railroad.UP, null, "", false, 0),
        RadioChannel("up-6",    "Car Department (AAR Ch 78)",     161.190, Railroad.UP, null, "", false, 0),
        RadioChannel("up-7",    "Yardmaster (AAR Ch 36)",         160.755, Railroad.UP, null, "", false, 0),
        RadioChannel("csx-1",   "Dispatcher (AAR Ch 8)",          160.230, Railroad.CSX, null, "", false, 0),
        RadioChannel("csx-2",   "Dispatcher (AAR Ch 6)",          160.410, Railroad.CSX, null, "", false, 0),
        RadioChannel("csx-3",   "Dispatcher (AAR Ch 16)",         160.560, Railroad.CSX, null, "", false, 0),
        RadioChannel("csx-4",   "Dispatcher (AAR Ch 42)",         160.800, Railroad.CSX, null, "", false, 0),
        RadioChannel("csx-5",   "Dispatcher (AAR Ch 56)",         161.070, Railroad.CSX, null, "", false, 0),
        RadioChannel("csx-6",   "EOT Device (AAR Ch 88)",         161.370, Railroad.CSX, null, "", false, 0),
        RadioChannel("ns-1",    "Dispatcher (AAR Ch 6)",          160.410, Railroad.NS, null, "", false, 0),
        RadioChannel("ns-2",    "Dispatcher (AAR Ch 12)",         160.515, Railroad.NS, null, "", false, 0),
        RadioChannel("ns-3",    "Dispatcher (AAR Ch 56)",         161.070, Railroad.NS, null, "", false, 0),
        RadioChannel("ns-4",    "Mechanical / Car Dept (AAR Ch 46)", 160.920, Railroad.NS, null, "", false, 0),
        RadioChannel("ns-5",    "Car Department (AAR Ch 78)",     161.190, Railroad.NS, null, "", false, 0),
        RadioChannel("amt-1",   "Operations (AAR Ch 7)",          160.215, Railroad.AMTRAK, null, "", false, 0),
        RadioChannel("amt-2",   "Operations / NEC (AAR Ch 16)",   160.560, Railroad.AMTRAK, null, "", false, 0),
        RadioChannel("amt-3",   "Mechanical (AAR Ch 46)",         160.920, Railroad.AMTRAK, null, "", false, 0),
        RadioChannel("amt-4",   "Car Department (AAR Ch 78)",     161.190, Railroad.AMTRAK, null, "", false, 0),
        RadioChannel("amt-5",   "Operations (AAR Ch 85)",         161.385, Railroad.AMTRAK, null, "", false, 0),
        RadioChannel("cn-1",    "Dispatcher (AAR Ch 6)",          160.410, Railroad.CN, null, "", false, 0),
        RadioChannel("cn-2",    "Dispatcher (AAR Ch 56)",         161.070, Railroad.CN, null, "", false, 0),
        RadioChannel("cn-3",    "Yardmaster (AAR Ch 78)",         161.190, Railroad.CN, null, "", false, 0),
        RadioChannel("cp-1",    "Dispatcher (AAR Ch 6)",          160.410, Railroad.CP, null, "", false, 0),
        RadioChannel("cp-2",    "Dispatcher (AAR Ch 12)",         160.515, Railroad.CP, null, "", false, 0),
        RadioChannel("cp-3",    "Dispatcher (AAR Ch 52)",         161.010, Railroad.CP, null, "", false, 0),
        RadioChannel("all-1",   "Head of Train (HOT) Device",     452.9375, Railroad.OTHER, "All railroads", "", false, 0),
        RadioChannel("all-2",   "End of Train (EOT) Device",      457.9375, Railroad.OTHER, "All railroads", "", false, 0),
        RadioChannel("all-3",   "Defect Detector / Dragging Equipment", 161.550, Railroad.OTHER, "All railroads", "", false, 0),
        RadioChannel("all-4",   "Emergency Calling (AAR Ch 97A)", 160.290, Railroad.OTHER, "All railroads", "", false, 0),
        RadioChannel("all-5",   "Distributed Power Unit (DPU)",   452.9250, Railroad.OTHER, "All railroads", "", false, 0),
        RadioChannel("all-6",   "PTC Base Station (lower band)",  220.1025, Railroad.OTHER, "All railroads", "", false, 0)
    )

    // ── Utilities ──────────────────────────────────────────────────────────────

    private fun cardinalToDegrees(cardinal: String): Int = when (cardinal.uppercase().trim()) {
        "N"  ->   0; "NNE" ->  22; "NE"  ->  45; "ENE" ->  67
        "E"  ->  90; "ESE" -> 112; "SE"  -> 135; "SSE" -> 157
        "S"  -> 180; "SSW" -> 202; "SW"  -> 225; "WSW" -> 247
        "W"  -> 270; "WNW" -> 292; "NW"  -> 315; "NNW" -> 337
        else ->   0
    }

    // Base64 encode "user:pass" for GTFS-RT Basic Auth — pure Kotlin, no Android API.
    private fun encodeBasicAuth(user: String, pass: String): String {
        val input = "$user:$pass".encodeToByteArray()
        val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val sb = StringBuilder()
        var i = 0
        while (i < input.size) {
            val b0 = input[i].toInt() and 0xFF
            val b1 = if (i + 1 < input.size) input[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < input.size) input[i + 2].toInt() and 0xFF else 0
            sb.append(table[b0 shr 2])
            sb.append(table[((b0 and 0x3) shl 4) or (b1 shr 4)])
            sb.append(if (i + 1 < input.size) table[((b1 and 0xF) shl 2) or (b2 shr 6)] else '=')
            sb.append(if (i + 2 < input.size) table[b2 and 0x3F] else '=')
            i += 3
        }
        return sb.toString()
    }

    private fun buildSymbolDecoderSystemPrompt() = """
You are an expert railfan and railroad operations specialist with deep knowledge of North American freight railroad train symbols.

SYMBOL TYPE GUIDE (first letter):
A=Automotive, C=Coal, E=Equipment/Power Move, G=Grain, I=Intermodal,
M=Manifest, Z=Priority Intermodal, U=Bulk/Unit, Q=Tank/Liquid,
J=BNSF Interchange, L=Local, S=Special

UP CITY CODES: CH=Chicago, LA=Los Angeles, KC=Kansas City, NO=New Orleans,
PO=Portland, SE=Seattle, DN=Denver, SL=Salt Lake City, HO=Houston

BNSF: Q/Z=Intermodal, H=Grain, M=Manifest
NS: Numbers only (11N, 24Q)
AMTRAK: Named trains + number

Return ONLY valid JSON, no markdown, no code blocks:
{
  "symbol": "string",
  "type": "string",
  "origin": "string",
  "destination": "string",
  "schedule": "string",
  "typicalConsist": ["string"],
  "railroad": "string (BNSF/UP/CSX/NS/CN/CP/AMTRAK/KCS/OTHER)",
  "notes": "string",
  "priority": "string (High/Medium/Low)"
}""".trimIndent()

    companion object {
        const val NEARBY_RADIUS_MILES = 500.0
    }
}

// ── Internal JSON model for Anthropic response parsing ────────────────────────

@kotlinx.serialization.Serializable
private data class DecodedSymbolJson(
    val symbol: String? = null,
    val type: String? = null,
    val origin: String? = null,
    val destination: String? = null,
    val schedule: String? = null,
    val typicalConsist: List<String>? = null,
    val railroad: String? = null,
    val notes: String? = null,
    val priority: String? = null
)

private val railfanJson = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
