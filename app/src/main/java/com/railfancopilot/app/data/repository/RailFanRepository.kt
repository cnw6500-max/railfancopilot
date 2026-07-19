package com.railfancopilot.app.data.repository

import android.content.Context
import android.location.Location
import com.railfancopilot.app.BuildConfig
import com.railfancopilot.app.data.models.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

class RailFanRepository(private val context: Context) {

    private val db = RailFanDatabase.getInstance(context)
    private val gson = Gson()

    // ── ETA helper ────────────────────────────────────────────────────────────

    // Returns minutes until train reaches the user, or null if moving away / too far / stopped.
    private fun computeEtaMinutes(
        userLat: Double, userLon: Double,
        trainLat: Double, trainLon: Double,
        speedMph: Int, headingDeg: Int
    ): Int? {
        if (speedMph < 5) return null
        val results = FloatArray(2) // [distance, bearing]
        Location.distanceBetween(trainLat, trainLon, userLat, userLon, results)
        val distMeters = results[0]
        val bearingToUser = results[1]
        // Ignore trains heading away (heading differs from bearing-to-user by > 90°)
        val headingDiff = Math.abs(((headingDeg - bearingToUser + 540) % 360) - 180)
        if (headingDiff > 90) return null
        val distMiles = distMeters / 1609.34
        if (distMiles > 150) return null
        return (distMiles / speedMph * 60).toInt().coerceAtLeast(0)
    }

    // ── Live trains ────────────────────────────────────────────────────────────

    // Real Amtrak positions from api.amtraker.com.
    // Only trains within NEARBY_RADIUS_MILES of the user are returned.
    suspend fun getLiveTrains(lat: Double, lon: Double, railroad: String? = null, radiusMiles: Double = NEARBY_RADIUS_MILES): List<TrainLocation> {
        return try {
            val response = NetworkModule.amtrakApi.getTrains()
            response.values.flatten()
                .filter { it.lat != null && it.lon != null && it.lat != 0.0 && it.lon != 0.0 }
                .map { train ->
                    val timely = train.trainTimely.uppercase()
                    TrainLocation(
                        id = "amtrak-${train.trainNum}",
                        symbol = "${train.routeName} #${train.trainNum}",
                        railroad = Railroad.AMTRAK,
                        latitude = train.lat!!,
                        longitude = train.lon!!,
                        speedMph = train.velocity?.toInt() ?: 0,
                        headingDegrees = GtfsRtFetcher.cardinalToDegrees(train.heading ?: ""),
                        etaMinutes = computeEtaMinutes(lat, lon, train.lat!!, train.lon!!, train.velocity?.toInt() ?: 0, GtfsRtFetcher.cardinalToDegrees(train.heading ?: "")),
                        status = when {
                            timely == "ON TIME" || timely.contains("EARLY") -> TrainStatus.ON_TIME
                            timely.contains("LATE") -> TrainStatus.DELAYED
                            else -> TrainStatus.UNKNOWN
                        },
                        consist = listOf("P42DC"),
                        origin = train.stations.firstOrNull()?.code ?: "Origin",
                        destination = train.stations.lastOrNull()?.code ?: "Destination",
                        milepost = null,
                        subdivision = train.routeName
                    )
                }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) android.util.Log.e("RailFanRepo", "Amtrak API failed: ${e.message}", e)
            emptyList<TrainLocation>()
        }
    }

    // ── Amtrak station departures board ──────────────────────────────────────

    /**
     * Returns trains currently calling at [stationCode] with their full stop list.
     * Each entry is (trainSymbol, routeName, stops).
     */
    suspend fun getStationDepartures(stationCode: String): List<Triple<String, String, List<TimetableStop>>> {
        return try {
            val response = NetworkModule.amtrakApi.getStationTrains(stationCode.uppercase())
            response.values.flatten().map { train ->
                Triple(
                    "Amtrak #${train.trainNum}",
                    train.routeName,
                    train.stations.map { it.toTimetableStop() }
                )
            }.sortedBy { (_, _, stops) ->
                // Sort by scheduled departure at this station
                stops.firstOrNull { it.code.equals(stationCode, ignoreCase = true) }
                    ?.scheduledDeparture ?: "99:99"
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) android.util.Log.e("RailFanRepo", "Station departures failed for $stationCode: ${e.message}")
            emptyList()
        }
    }

    // ── Amtrak timetable (per-train station stops) ────────────────────────────

    /**
     * Fetch the full station stop list for a single Amtrak train by its number
     * (e.g. "20" for the Empire Builder).  Returns an empty list on any failure.
     *
     * Times from the API are ISO-8601 strings; we parse them and format as
     * "h:mm a" in the device's local timezone for display.
     */
    suspend fun getTrainTimetable(trainNum: String): List<TimetableStop> {
        return try {
            val response = NetworkModule.amtrakApi.getTrain(trainNum)
            val train = response.values.flatten().firstOrNull() ?: return emptyList()
            train.stations.map { s -> s.toTimetableStop() }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) android.util.Log.e("RailFanRepo", "Timetable fetch failed for $trainNum: ${e.message}", e)
            emptyList()
        }
    }

    // ── SEPTA Regional Rail (free JSON, no key) ──────────────────────────────

    /**
     * SEPTA Regional Rail positions from www3.septa.org (free, no auth).
     * NB: SEPTA's API returns compass headings ("SW") and no speed — ETAs are not computed.
     */
    suspend fun getSeptaTrains(lat: Double, lon: Double, radiusMiles: Double = NEARBY_RADIUS_MILES): List<TrainLocation> {
        return try {
            val response = NetworkModule.septaApi.getTrainView()
            val distBuf = FloatArray(1)
            response.mapNotNull { train ->
                val tLat = train.lat.toDoubleOrNull() ?: return@mapNotNull null
                val tLon = train.lon.toDoubleOrNull() ?: return@mapNotNull null
                if (tLat == 0.0 && tLon == 0.0) return@mapNotNull null
                Location.distanceBetween(lat, lon, tLat, tLon, distBuf)
                if (distBuf[0] / 1609.34 > radiusMiles) return@mapNotNull null

                val heading = GtfsRtFetcher.cardinalToDegrees(train.heading)
                // Strip " Line" suffix for concise display (e.g. "Wilmington/Newark")
                val lineLabel = train.line
                    .removeSuffix(" Line")
                    .removeSuffix(" line")
                    .trim()
                    .ifBlank { train.dest }

                TrainLocation(
                    id            = "septa-${train.trainno}",
                    symbol        = "SEPTA $lineLabel",
                    railroad      = Railroad.OTHER,
                    latitude      = tLat,
                    longitude     = tLon,
                    speedMph      = 0,          // SEPTA API does not expose speed
                    headingDegrees = heading,
                    etaMinutes    = null,
                    status        = if (train.late > 0) TrainStatus.DELAYED else TrainStatus.ON_TIME,
                    consist       = train.consist.split(",").map { it.trim() }.filter { it.isNotBlank() },
                    origin        = train.currentstop,
                    destination   = train.dest,
                    milepost      = null,
                    subdivision   = train.service
                )
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) android.util.Log.e("RailFanRepo", "SEPTA API failed: ${e.message}", e)
            emptyList()
        }
    }

    // ── Metra (Chicago area — GTFS-RT via Cloud Functions proxy) ─────────────

    suspend fun getMetraTrains(lat: Double, lon: Double, radiusMiles: Double = NEARBY_RADIUS_MILES): List<TrainLocation> {
        return try {
            val bytes = BackendFunctionsClient.getMetraPositions()
            GtfsRtFetcher.parseAndFilter(bytes, Railroad.OTHER, "Metra", lat, lon, radiusMiles, ::computeEtaMinutes)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) android.util.Log.e("RailFanRepo", "Metra failed: ${e.message}", e)
            emptyList()
        }
    }

    // ── MTA LIRR + Metro-North (New York — GTFS-RT via Cloud Functions proxy) ──

    suspend fun getMtaLirrTrains(lat: Double, lon: Double, radiusMiles: Double = NEARBY_RADIUS_MILES): List<TrainLocation> {
        return try {
            val bytes = BackendFunctionsClient.getMtaLirrPositions()
            GtfsRtFetcher.parseAndFilter(bytes, Railroad.OTHER, "LIRR", lat, lon, radiusMiles, ::computeEtaMinutes)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) android.util.Log.e("RailFanRepo", "LIRR failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getMtaMetroNorthTrains(lat: Double, lon: Double, radiusMiles: Double = NEARBY_RADIUS_MILES): List<TrainLocation> {
        return try {
            val bytes = BackendFunctionsClient.getMtaMetroNorthPositions()
            GtfsRtFetcher.parseAndFilter(bytes, Railroad.OTHER, "Metro-North", lat, lon, radiusMiles, ::computeEtaMinutes)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) android.util.Log.e("RailFanRepo", "Metro-North failed: ${e.message}", e)
            emptyList()
        }
    }

    // ── Caltrain (Bay Area — GTFS-RT via Cloud Functions proxy) ───────────────

    suspend fun getCaltrainTrains(lat: Double, lon: Double, radiusMiles: Double = NEARBY_RADIUS_MILES): List<TrainLocation> {
        return try {
            val bytes = BackendFunctionsClient.getCaltrainPositions()
            GtfsRtFetcher.parseAndFilter(bytes, Railroad.OTHER, "Caltrain", lat, lon, radiusMiles, ::computeEtaMinutes)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) android.util.Log.e("RailFanRepo", "Caltrain failed: ${e.message}", e)
            emptyList()
        }
    }

    // ── VRE (Virginia Railway Express — GTFS-RT, open feed) ──────────────────

    suspend fun getVreTrains(lat: Double, lon: Double, radiusMiles: Double = NEARBY_RADIUS_MILES): List<TrainLocation> =
        GtfsRtFetcher.fetch(
            tag         = "VRE",
            url         = "https://gtfs.vre.org/GTFS-RT/VehiclePositions.pb",
            railroad    = Railroad.OTHER,
            agencyLabel = "VRE",
            userLat = lat, userLon = lon, radiusMiles = radiusMiles,
            etaFn   = ::computeEtaMinutes
        )

    // ── MARC (Maryland Area Regional Commuter — GTFS-RT, open feed) ──────────

    suspend fun getMarcTrains(lat: Double, lon: Double, radiusMiles: Double = NEARBY_RADIUS_MILES): List<TrainLocation> =
        GtfsRtFetcher.fetch(
            tag         = "MARC",
            url         = "https://gtfs.mta.maryland.gov/gtfs-rt/VehiclePositions",
            railroad    = Railroad.OTHER,
            agencyLabel = "MARC",
            userLat = lat, userLon = lon, radiusMiles = radiusMiles,
            etaFn   = ::computeEtaMinutes
        )

    // ── Metrolink (Southern California — GTFS-RT, open feed) ─────────────────

    suspend fun getMetrolinkTrains(lat: Double, lon: Double, radiusMiles: Double = NEARBY_RADIUS_MILES): List<TrainLocation> =
        GtfsRtFetcher.fetch(
            tag         = "Metrolink",
            url         = "https://metrolinktrains.com/gtfs-rt/vehiclepositions.pb",
            railroad    = Railroad.OTHER,
            agencyLabel = "Metrolink",
            userLat = lat, userLon = lon, radiusMiles = radiusMiles,
            etaFn   = ::computeEtaMinutes
        )

    // ── NJ Transit Rail (New Jersey — GTFS-RT, open feed) ────────────────────

    suspend fun getNjTransitTrains(lat: Double, lon: Double, radiusMiles: Double = NEARBY_RADIUS_MILES): List<TrainLocation> =
        GtfsRtFetcher.fetch(
            tag         = "NJTransit",
            url         = "https://www.njtransit.com/rss/VehicleData.xml",
            railroad    = Railroad.OTHER,
            agencyLabel = "NJ Transit",
            userLat = lat, userLon = lon, radiusMiles = radiusMiles,
            etaFn   = ::computeEtaMinutes
        )

    // ── Sound Transit Sounder (Seattle — GTFS-RT, open feed) ──────────────────

    suspend fun getSoundTransitTrains(lat: Double, lon: Double, radiusMiles: Double = NEARBY_RADIUS_MILES): List<TrainLocation> =
        GtfsRtFetcher.fetch(
            tag         = "SoundTransit",
            url         = "https://www.soundtransit.org/gtfs-rt/GTFS-Sounder-VehiclePositions.pb",
            railroad    = Railroad.OTHER,
            agencyLabel = "Sounder",
            userLat = lat, userLon = lon, radiusMiles = radiusMiles,
            etaFn   = ::computeEtaMinutes
        )

    // ── MBTA commuter rail (free JSON, no key) ────────────────────────────────

    /** Fetches MBTA commuter rail vehicle positions and filters by [radiusMiles] from the user. */
    suspend fun getMbtaTrains(lat: Double, lon: Double, radiusMiles: Double = NEARBY_RADIUS_MILES): List<TrainLocation> {
        return try {
            val response = NetworkModule.mbtaApi.getVehicles()
            val distBuf = FloatArray(1)
            response.data
                .filter { it.attributes.latitude != 0.0 && it.attributes.longitude != 0.0 }
                .filter { vehicle ->
                    Location.distanceBetween(lat, lon, vehicle.attributes.latitude, vehicle.attributes.longitude, distBuf)
                    distBuf[0] / 1609.34 <= radiusMiles
                }
                .map { vehicle ->
                    val attr = vehicle.attributes
                    // MBTA speed comes back as m/s — convert to mph
                    val speedMph = ((attr.speed ?: 0.0) * 2.23694).toInt()
                    val heading  = attr.bearing ?: 0
                    val routeId  = vehicle.relationships.route.data?.id ?: "MBTA"
                    val tripId   = vehicle.relationships.trip.data?.id ?: ""
                    // Route IDs look like "CR-Fitchburg" — strip "CR-" prefix
                    val lineName = routeId.removePrefix("CR-").replace("-", " ")
                    val status = when {
                        attr.current_status == "STOPPED_AT" -> TrainStatus.STOPPED
                        speedMph > 0 -> TrainStatus.ON_TIME
                        else -> TrainStatus.UNKNOWN
                    }
                    TrainLocation(
                        id = "mbta-${vehicle.id}",
                        symbol = "MBTA $lineName",
                        railroad = Railroad.OTHER,    // groups with other commuter feeds under the Commuter chip
                        latitude = attr.latitude,
                        longitude = attr.longitude,
                        speedMph = speedMph,
                        headingDegrees = heading,
                        etaMinutes = computeEtaMinutes(lat, lon, attr.latitude, attr.longitude, speedMph, heading),
                        status = status,
                        consist = if (attr.label.isNotBlank()) listOf(attr.label) else emptyList(),
                        origin = "Boston",
                        destination = lineName,
                        milepost = null,
                        subdivision = routeId
                    )
                }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) android.util.Log.e("RailFanRepo", "MBTA API failed: ${e.message}", e)
            emptyList()
        }
    }

    companion object {
        /** Radius (miles) within which trains are considered "nearby". */
        const val NEARBY_RADIUS_MILES = 500.0
    }

    // ── AI symbol decoder (via Cloud Functions proxy) ─────────────────────────

    suspend fun decodeTrainSymbol(symbol: String): Result<TrainSymbolDecodeResult> {
        return try {
            if (BuildConfig.DEBUG) android.util.Log.d("RailFanDecoder", "Decoding: $symbol")
            val localContext = com.railfancopilot.app.utils.SymbolDatabase.buildContext(symbol)
            val rawText = BackendFunctionsClient.decodeTrainSymbol(symbol, localContext)
            if (BuildConfig.DEBUG) android.util.Log.d("RailFanDecoder", "Raw response: $rawText")
            val jsonText = rawText.replace("```json", "").replace("```", "").trim()
            val parsed = gson.fromJson(jsonText, DecodedSymbolJson::class.java)
            Result.success(TrainSymbolDecodeResult(
                symbol = parsed.symbol ?: symbol,
                type = parsed.type ?: "Unknown",
                origin = parsed.origin ?: "Unknown",
                destination = parsed.destination ?: "Unknown",
                schedule = parsed.schedule ?: "Unknown",
                typicalConsist = parsed.typicalConsist ?: emptyList(),
                railroad = Railroad.values().find { it.name == (parsed.railroad ?: "") } ?: Railroad.OTHER,
                notes = parsed.notes ?: "",
                priority = parsed.priority ?: "Unknown"
            ))
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) android.util.Log.e("RailFanDecoder", "Decode failed: ${e.javaClass.simpleName}: ${e.message}", e)
            Result.failure(e)
        }
    }

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

    // ── Locomotive identification (via Cloud Functions proxy) ─────────────────

    suspend fun identifyLocomotive(base64Image: String): Result<String> {
        return try {
            val text = BackendFunctionsClient.identifyLocomotive(base64Image)
            Result.success(text)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) android.util.Log.e("LocoIdentifier", "Identify failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ── Community reports — backed by shared Firestore "sightings" collection ──
    // Sightings are shared cross-platform with the iOS app in real time.

    fun getRecentReportsFlow(): Flow<List<CommunityReport>> =
        FirestoreCommunityRepo.getAllSightingsFlow()

    /** Live Firestore feed filtered client-side to [radiusMiles] around the user. */
    fun getNearbyReportsFlow(lat: Double, lon: Double, radiusMiles: Double): Flow<List<CommunityReport>> =
        FirestoreCommunityRepo.getSightingsFlow(lat, lon, radiusMiles)

    fun getAllReportsFlow(): Flow<List<CommunityReport>> =
        FirestoreCommunityRepo.getAllSightingsFlow()

    suspend fun deleteCommunityReport(reportId: String) =
        FirestoreCommunityRepo.deleteSighting(reportId)

    suspend fun addReport(
        lat: Double, lon: Double, text: String,
        trainSymbol: String?, railroad: String?, tags: List<String>,
        localPhotoPath: String? = null,
        reporterName: String = "Railfan",
        consist: String? = null,
        weather: String? = null,
        locationName: String = ""
    ) {
        val location = locationName.ifBlank { reverseGeocode(lat, lon) ?: "Unknown location" }
        FirestoreCommunityRepo.submitSighting(
            lat = lat, lon = lon,
            text = text,
            trainSymbol = trainSymbol,
            railroad = railroad,
            reporterName = reporterName,
            location = location,
            consist = consist,
            weather = weather,
            photoPath = localPhotoPath
        )
    }

    suspend fun fetchWeather(lat: Double, lon: Double): String? = try {
        val resp = NetworkModule.openMeteoApi.getCurrent(lat, lon)
        val c = resp.current
        val condition = when (c.weather_code) {
            0          -> "Clear"
            1          -> "Mainly Clear"
            2          -> "Partly Cloudy"
            3          -> "Overcast"
            45, 48     -> "Foggy"
            51, 53, 55 -> "Drizzle"
            61, 63, 65 -> "Rain"
            66, 67     -> "Freezing Rain"
            71, 73, 75 -> "Snow"
            77         -> "Snow Grains"
            80, 81, 82 -> "Showers"
            85, 86     -> "Snow Showers"
            95         -> "Thunderstorm"
            96, 99     -> "Severe Thunderstorm"
            else       -> "Unknown"
        }
        "${c.temperature_2m.toInt()}°F · $condition · ${c.wind_speed_10m.toInt()} mph wind"
    } catch (_: Exception) { null }

    suspend fun analyzeConsist(base64Image: String): Result<String> = try {
        Result.success(BackendFunctionsClient.analyzeConsist(base64Image))
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ── AAR frequency reference ───────────────────────────────────────────────
    // Source: RadioReference.com aid/7747 — AAR standard railroad band (160–161 MHz)
    // Frequencies are national standards; actual subdivision assignments vary by region.

    fun getAARFrequencies(): List<RadioChannel> = listOf(

        // ── BNSF ──────────────────────────────────────────────────────────────
        RadioChannel("bnsf-1",  "Dispatcher (AAR Ch 6)",          160.410, Railroad.BNSF, null, "", false, 0),
        RadioChannel("bnsf-2",  "Dispatcher (AAR Ch 12)",         160.515, Railroad.BNSF, null, "", false, 0),
        RadioChannel("bnsf-3",  "Dispatcher (AAR Ch 8)",          160.230, Railroad.BNSF, null, "", false, 0),
        RadioChannel("bnsf-4",  "Yardmaster (AAR Ch 10)",         160.260, Railroad.BNSF, null, "", false, 0),
        RadioChannel("bnsf-5",  "Maintenance of Way (AAR Ch 90)", 161.385, Railroad.BNSF, null, "", false, 0),
        RadioChannel("bnsf-6",  "EOT / HOT link (AAR Ch 72)",     161.100, Railroad.BNSF, null, "", false, 0),
        RadioChannel("bnsf-7",  "Dispatcher (AAR Ch 97)",         161.565, Railroad.BNSF, null, "", false, 0),

        // ── Union Pacific ─────────────────────────────────────────────────────
        RadioChannel("up-1",    "Dispatcher (AAR Ch 12)",         160.515, Railroad.UP, null, "", false, 0),
        RadioChannel("up-2",    "Dispatcher (AAR Ch 18)",         160.590, Railroad.UP, null, "", false, 0),
        RadioChannel("up-3",    "Dispatcher (AAR Ch 52)",         161.010, Railroad.UP, null, "", false, 0),
        RadioChannel("up-4",    "Dispatcher (AAR Ch 63)",         161.130, Railroad.UP, null, "", false, 0),
        RadioChannel("up-5",    "Dispatcher (AAR Ch 56)",         161.070, Railroad.UP, null, "", false, 0),
        RadioChannel("up-6",    "Car Department (AAR Ch 78)",     161.190, Railroad.UP, null, "", false, 0),
        RadioChannel("up-7",    "Yardmaster (AAR Ch 36)",         160.755, Railroad.UP, null, "", false, 0),

        // ── CSX ───────────────────────────────────────────────────────────────
        RadioChannel("csx-1",   "Dispatcher (AAR Ch 8)",          160.230, Railroad.CSX, null, "", false, 0),
        RadioChannel("csx-2",   "Dispatcher (AAR Ch 6)",          160.410, Railroad.CSX, null, "", false, 0),
        RadioChannel("csx-3",   "Dispatcher (AAR Ch 16)",         160.560, Railroad.CSX, null, "", false, 0),
        RadioChannel("csx-4",   "Dispatcher (AAR Ch 42)",         160.800, Railroad.CSX, null, "", false, 0),
        RadioChannel("csx-5",   "Dispatcher (AAR Ch 56)",         161.070, Railroad.CSX, null, "", false, 0),
        RadioChannel("csx-6",   "EOT Device (AAR Ch 88)",         161.370, Railroad.CSX, null, "", false, 0),

        // ── Norfolk Southern ──────────────────────────────────────────────────
        RadioChannel("ns-1",    "Dispatcher (AAR Ch 6)",          160.410, Railroad.NS, null, "", false, 0),
        RadioChannel("ns-2",    "Dispatcher (AAR Ch 12)",         160.515, Railroad.NS, null, "", false, 0),
        RadioChannel("ns-3",    "Dispatcher (AAR Ch 56)",         161.070, Railroad.NS, null, "", false, 0),
        RadioChannel("ns-4",    "Mechanical / Car Dept (AAR Ch 46)", 160.920, Railroad.NS, null, "", false, 0),
        RadioChannel("ns-5",    "Car Department (AAR Ch 78)",     161.190, Railroad.NS, null, "", false, 0),

        // ── Amtrak ────────────────────────────────────────────────────────────
        RadioChannel("amt-1",   "Operations (AAR Ch 7)",          160.215, Railroad.AMTRAK, null, "", false, 0),
        RadioChannel("amt-2",   "Operations / NEC (AAR Ch 16)",   160.560, Railroad.AMTRAK, null, "", false, 0),
        RadioChannel("amt-3",   "Mechanical (AAR Ch 46)",         160.920, Railroad.AMTRAK, null, "", false, 0),
        RadioChannel("amt-4",   "Car Department (AAR Ch 78)",     161.190, Railroad.AMTRAK, null, "", false, 0),
        RadioChannel("amt-5",   "Operations (AAR Ch 85)",         161.385, Railroad.AMTRAK, null, "", false, 0),

        // ── Canadian National ─────────────────────────────────────────────────
        RadioChannel("cn-1",    "Dispatcher (AAR Ch 6)",          160.410, Railroad.CN, null, "", false, 0),
        RadioChannel("cn-2",    "Dispatcher (AAR Ch 56)",         161.070, Railroad.CN, null, "", false, 0),
        RadioChannel("cn-3",    "Yardmaster (AAR Ch 78)",         161.190, Railroad.CN, null, "", false, 0),

        // ── Canadian Pacific / CPKC ───────────────────────────────────────────
        RadioChannel("cp-1",    "Dispatcher (AAR Ch 6)",          160.410, Railroad.CP, null, "", false, 0),
        RadioChannel("cp-2",    "Dispatcher (AAR Ch 12)",         160.515, Railroad.CP, null, "", false, 0),
        RadioChannel("cp-3",    "Dispatcher (AAR Ch 52)",         161.010, Railroad.CP, null, "", false, 0),

        // ── All railroads — telemetry & safety ────────────────────────────────
        RadioChannel("all-1",   "Head of Train (HOT) Device",     452.9375, Railroad.OTHER, "All railroads", "", false, 0),
        RadioChannel("all-2",   "End of Train (EOT) Device",      457.9375, Railroad.OTHER, "All railroads", "", false, 0),
        RadioChannel("all-3",   "Defect Detector / Dragging Equipment", 161.550, Railroad.OTHER, "All railroads", "", false, 0),
        RadioChannel("all-4",   "Emergency Calling (AAR Ch 97A)", 160.290, Railroad.OTHER, "All railroads", "", false, 0),
        RadioChannel("all-5",   "Distributed Power Unit (DPU)",   452.9250, Railroad.OTHER, "All railroads", "", false, 0),
        RadioChannel("all-6",   "PTC Base Station (lower band)",  220.1025, Railroad.OTHER, "All railroads", "", false, 0)
    )

    // ── Reverse geocoding ─────────────────────────────────────────────────────

    suspend fun reverseGeocode(lat: Double, lon: Double): String? = try {
        val result = NetworkModule.nominatimApi.reverse(lat, lon)
        val addr = result.address
        // Build a short "City, State" label; fall back gracefully
        val place = addr.city ?: addr.town ?: addr.village ?: addr.county
        when {
            place != null && addr.state != null -> "$place, ${addr.state}"
            place != null -> place
            else -> result.display_name.split(",").take(2).joinToString(",").trim().takeIf { it.isNotBlank() }
        }
    } catch (_: Exception) { null }

    // ── Loco ID history ───────────────────────────────────────────────────────

    fun getLocoIdHistoryFlow(): Flow<List<LocoIdEntry>> =
        db.locoIdEntryDao().getAllFlow()

    suspend fun saveLocoIdEntry(entry: LocoIdEntry) =
        db.locoIdEntryDao().insert(entry)

    suspend fun deleteLocoIdEntry(entry: LocoIdEntry) =
        db.locoIdEntryDao().delete(entry)

    // ── Symbol decode history ─────────────────────────────────────────────────

    fun getSymbolDecodeHistoryFlow(): Flow<List<SymbolDecodeEntry>> =
        db.symbolDecodeEntryDao().getAllFlow()

    suspend fun saveSymbolDecodeEntry(entry: SymbolDecodeEntry) {
        db.symbolDecodeEntryDao().insert(entry)
        db.symbolDecodeEntryDao().prune()
    }

    suspend fun deleteSymbolDecodeEntry(entry: SymbolDecodeEntry) =
        db.symbolDecodeEntryDao().delete(entry)

    suspend fun clearSymbolDecodeHistory() =
        db.symbolDecodeEntryDao().deleteAll()

    // ── Tagged photos ─────────────────────────────────────────────────────────

    fun getTaggedPhotosFlow(): Flow<List<PhotoMetadata>> =
        db.photoMetadataDao().getAllFlow()

    suspend fun saveTaggedPhoto(photo: PhotoMetadata) =
        db.photoMetadataDao().insert(photo)

    suspend fun deleteTaggedPhoto(photo: PhotoMetadata) =
        db.photoMetadataDao().delete(photo)

    // ── Saved locations ───────────────────────────────────────────────────────

    fun getSavedLocationsFlow(): Flow<List<SavedLocation>> =
        db.savedLocationDao().getAllFlow()

    suspend fun saveLocation(location: SavedLocation) =
        db.savedLocationDao().insert(location)

    suspend fun deleteLocation(location: SavedLocation) =
        db.savedLocationDao().delete(location)

    // ── Train trail waypoints ─────────────────────────────────────────────────

    suspend fun insertTrailWaypoint(waypoint: TrainTrailWaypoint) =
        db.trainTrailWaypointDao().insert(waypoint)

    suspend fun getTrailWaypointsSince(trainId: String, sinceMs: Long): List<TrainTrailWaypoint> =
        db.trainTrailWaypointDao().getTrailSince(trainId, sinceMs)

    suspend fun getLatestTrailWaypoint(trainId: String): TrainTrailWaypoint? =
        db.trainTrailWaypointDao().getLatestWaypoint(trainId)

    suspend fun lookupLocoNumber(roadNumber: String): Result<String> = try {
        Result.success(BackendFunctionsClient.lookupLocoNumber(roadNumber))
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) android.util.Log.e("RailFanRepo", "Loco lookup failed: ${e.message}")
        Result.failure(e)
    }

    suspend fun pruneTrailWaypointsOlderThan(beforeMs: Long) =
        db.trainTrailWaypointDao().pruneOlderThan(beforeMs)

    suspend fun getAllTrailWaypointsSince(sinceMs: Long): List<TrainTrailWaypoint> =
        db.trainTrailWaypointDao().getAllSince(sinceMs)

    // ── Timetable cache ───────────────────────────────────────────────────────

    suspend fun getTimetableCache(trainId: String): TimetableCacheEntry? =
        db.timetableCacheDao().get(trainId)

    suspend fun saveTimetableCache(entry: TimetableCacheEntry) =
        db.timetableCacheDao().insert(entry)

    suspend fun pruneTimetableCache(beforeMs: Long) =
        db.timetableCacheDao().pruneOlderThan(beforeMs)

    // ── Trip logs ─────────────────────────────────────────────────────────────

    fun getTripLogsFlow(): kotlinx.coroutines.flow.Flow<List<TripLog>> =
        db.tripLogDao().getAllFlow()

    suspend fun insertTrip(trip: TripLog) =
        db.tripLogDao().insert(trip)

    suspend fun updateTrip(trip: TripLog) =
        db.tripLogDao().update(trip)

    suspend fun deleteTrip(trip: TripLog) =
        db.tripLogDao().delete(trip)

    suspend fun getActiveTrip(): TripLog? =
        db.tripLogDao().getActiveTrip()

    suspend fun totalTripMiles(): Double =
        db.tripLogDao().totalMiles()

    suspend fun completedTripCount(): Int =
        db.tripLogDao().completedTripCount()

    // ── Encyclopedia ──────────────────────────────────────────────────────────

    fun getLocomotivedDatabase(): List<LocomotiveEntry> = listOf(

        // ── GE / Wabtec GEVO family ──────────────────────────────────────────
        LocomotiveEntry("l1", "ES44AC", "GE Transportation", 2004, 4400,
            "AC traction", "C-C", listOf(Railroad.BNSF, Railroad.UP, Railroad.CSX),
            "GEVO series flagship. BNSF's primary Transcon power; UP and CSX also operate large fleets.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/BNSF_7452_Barstow.jpg?width=480"),
        LocomotiveEntry("l2", "ES44C4", "GE Transportation", 2012, 4400,
            "AC traction", "C-C", listOf(Railroad.BNSF),
            "Controlled Tractive Effort variant — two of six axle motors cut out at speed to reduce wheel slip. Externally identical to ES44AC.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/BNSF_6071_Victorville.jpg?width=480"),
        LocomotiveEntry("l3", "ES44DC", "GE Transportation", 2003, 4400,
            "DC traction", "C-C", listOf(Railroad.NS, Railroad.CSX, Railroad.KCS),
            "DC-traction GEVO. NS and CSX preferred DC for maintenance simplicity. Visually nearly identical to the AC version.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/NS_7614_ES44DC.jpg?width=480"),
        LocomotiveEntry("l4", "ET44AC", "GE Transportation", 2015, 4400,
            "AC traction", "C-C", listOf(Railroad.BNSF, Railroad.CSX),
            "Tier 4 emission-compliant GEVO with exhaust aftertreatment. The 'Evolution Series Tier 4'. BNSF and CSX are primary operators.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/BNSF_3890_ET44AC.jpg?width=480"),
        LocomotiveEntry("l5", "ET44C4", "GE Transportation", 2015, 4400,
            "AC traction", "C-C", listOf(Railroad.BNSF),
            "Tier 4 Controlled Tractive Effort variant. BNSF ordered these exclusively for heavy mountain helper service.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/BNSF_3820_ET44C4.jpg?width=480"),

        // ── GE legacy / high-horsepower ─────────────────────────────────────
        LocomotiveEntry("l6", "AC4400CW", "GE Transportation", 1993, 4400,
            "AC traction", "C-C", listOf(Railroad.UP, Railroad.CSX, Railroad.CN),
            "GE's first widely successful AC traction locomotive. The wide-nose 'C' cab became the template for all modern GE road units.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/UP_AC4400CW_6761.jpg?width=480"),
        LocomotiveEntry("l7", "AC6000CW", "GE Transportation", 1995, 6000,
            "AC traction", "C-C", listOf(Railroad.UP, Railroad.CSX),
            "World's most powerful diesel when introduced. Engine reliability issues led to most being downrated to 4400 hp in service.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/UP_AC6000CW_7558.jpg?width=480"),
        LocomotiveEntry("l8", "C44-9W", "GE Transportation", 1994, 4400,
            "DC traction", "C-C", listOf(Railroad.UP, Railroad.NS, Railroad.CSX, Railroad.CN),
            "Dash 9-44CW. GE's bestseller of the 1990s; the wide-nose design that set the template for modern GE units. Thousands still in daily service.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/NS_9176_C44-9W.jpg?width=480"),
        LocomotiveEntry("l9", "C40-8W", "GE Transportation", 1989, 4000,
            "DC traction", "C-C", listOf(Railroad.UP, Railroad.CSX, Railroad.NS),
            "Dash 8-40CW. GE's first wide-nose locomotive. Introduced the microprocessor control systems now standard across the industry.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/CSX_7513_C40-8W.jpg?width=480"),

        // ── GE Passenger ────────────────────────────────────────────────────
        LocomotiveEntry("l10", "P42DC", "GE Transportation", 1996, 4200,
            "DC traction", "B-B", listOf(Railroad.AMTRAK),
            "Amtrak's Genesis series workhorse. Leads the California Zephyr, Southwest Chief, and most long-distance trains. Low-profile cab clears Eastern loading gauges.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/Amtrak_P42DC_822.jpg?width=480"),
        LocomotiveEntry("l11", "ALC-42 Charger", "Siemens", 2021, 4200,
            "AC traction", "B-B", listOf(Railroad.AMTRAK),
            "Amtrak's newest passenger locomotive replacing aging P42s. Tier 4 compliant, 125 mph capable. Siemens Charger platform shared with state-supported corridor services.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/Amtrak_ALC-42_301.jpg?width=480"),
        LocomotiveEntry("l12", "SC-44 Charger", "Siemens", 2016, 4400,
            "AC traction", "B-B", listOf(Railroad.AMTRAK, Railroad.OTHER),
            "State-corridor Charger variant. Operates Amtrak's Pacific Surfliner, Hiawatha, and other state-supported routes. 110 mph top speed.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/Amtrak_SC44_2101.jpg?width=480"),

        // ── EMD SD70 family ─────────────────────────────────────────────────
        LocomotiveEntry("l13", "SD70ACe", "EMD", 2004, 4300,
            "AC traction", "C-C", listOf(Railroad.UP, Railroad.NS, Railroad.CSX),
            "EMD's answer to the GEVO. UP's primary road locomotive. The wide-nose 'safety cab' and 16-710 prime mover define modern EMD power.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/UP_8444_SD70ACe.jpg?width=480"),
        LocomotiveEntry("l14", "SD70ACe-T4", "EMD / Progress Rail", 2015, 4300,
            "AC traction", "C-C", listOf(Railroad.NS, Railroad.UP),
            "Tier 4 compliant SD70ACe with exhaust gas recirculation. NS was the launch customer. Distinguishable by the revised hood vents.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/NS_SD70ACe-T4.jpg?width=480"),
        LocomotiveEntry("l15", "SD70MAC", "EMD", 1993, 4300,
            "AC traction", "C-C", listOf(Railroad.BNSF, Railroad.CSX),
            "EMD's first production AC traction locomotive. BNSF predecessors BN and Santa Fe were launch customers. The 3-phase AC inverter package became the industry standard.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/BNSF_9616_SD70MAC.jpg?width=480"),
        LocomotiveEntry("l16", "SD70M", "EMD", 1993, 4000,
            "DC traction", "C-C", listOf(Railroad.UP),
            "UP's DC-traction workhorse of the 1990s. The 'Whisker' cab variant features full-width nose with angled side windows. UP operated over 1,000 units.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/UP_4141_SD70M.jpg?width=480"),
        LocomotiveEntry("l17", "SD70M-2", "EMD", 2010, 4300,
            "DC traction", "C-C", listOf(Railroad.UP, Railroad.BNSF),
            "Updated DC-traction SD70 with isolated cab and enhanced microprocessors. UP's last large DC purchase before transitioning fully to AC.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/UP_SD70M-2.jpg?width=480"),

        // ── EMD high-horsepower ──────────────────────────────────────────────
        LocomotiveEntry("l18", "SD90MAC", "EMD", 1995, 6000,
            "AC traction", "C-C", listOf(Railroad.UP, Railroad.CP),
            "EMD's 6000-hp contender using the new 265H engine — which proved troublesome. Many units were rebuilt to 4300-hp SD70ACe spec. The 265H engine program was eventually abandoned.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/UP_SD90MAC_8102.jpg?width=480"),
        LocomotiveEntry("l19", "SD80MAC", "EMD", 1995, 5000,
            "AC traction", "C-C", listOf(Railroad.OTHER),
            "Conrail's unique 5000-hp AC units, eventually absorbed by NS and CSX at the Conrail split. One of the most powerful 16-cylinder diesels built.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/Conrail_SD80MAC.jpg?width=480"),

        // ── EMD SD60 / SD50 family ──────────────────────────────────────────
        LocomotiveEntry("l20", "SD60M", "EMD", 1989, 3800,
            "DC traction", "C-C", listOf(Railroad.UP, Railroad.CN, Railroad.BNSF),
            "First EMD locomotive with the wide-nose safety cab in North America. UP, BN, and CN were primary buyers. Still widespread in secondary service.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/UP_SD60M_2290.jpg?width=480"),
        LocomotiveEntry("l21", "SD60MAC", "EMD", 1994, 4000,
            "AC traction", "C-C", listOf(Railroad.BNSF),
            "AC traction retrofit of the SD60 platform. Burlington Northern ordered 250 units for Powder River Basin coal service.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/BNSF_SD60MAC.jpg?width=480"),

        // ── EMD SD40 family ──────────────────────────────────────────────────
        LocomotiveEntry("l22", "SD40-2", "EMD", 1972, 3000,
            "DC traction", "C-C", listOf(Railroad.BNSF, Railroad.UP, Railroad.NS, Railroad.CSX, Railroad.CN, Railroad.CP),
            "The most successful diesel locomotive ever built — over 4,000 produced. The 16-645E3 engine and HT-C truck set the standard for an entire generation of freight power.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/BNSF_6851_SD40-2.jpg?width=480"),
        LocomotiveEntry("l23", "SD40-2T", "EMD", 1974, 3000,
            "DC traction", "C-C", listOf(Railroad.CN, Railroad.OTHER),
            "Tunnel Motor variant with under-frame cooling for low-clearance tunnels. Built for Southern Pacific's mountain operations; CN acquired many.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/CN_SD40-2T_5377.jpg?width=480"),
        LocomotiveEntry("l24", "SD45", "EMD", 1965, 3600,
            "DC traction", "C-C", listOf(Railroad.BNSF, Railroad.NS, Railroad.OTHER),
            "20-cylinder 645 prime mover delivering 3600 hp — EMD's most powerful of its era. Famous 'flared radiator' fins. High maintenance led most roads to favor the SD40-2.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/SP_SD45.jpg?width=480"),

        // ── EMD four-axle road units ─────────────────────────────────────────
        LocomotiveEntry("l25", "GP38-2", "EMD", 1972, 2000,
            "DC traction", "B-B", listOf(Railroad.BNSF, Railroad.UP, Railroad.CSX, Railroad.NS, Railroad.CN, Railroad.CP),
            "The standard four-axle branch line locomotive of the 1970s–80s. Low-adhesion 8-645E engine makes it ideal for light rail and industrial switching.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/NS_GP38-2_5083.jpg?width=480"),
        LocomotiveEntry("l26", "GP60", "EMD", 1985, 3800,
            "DC traction", "B-B", listOf(Railroad.BNSF, Railroad.UP, Railroad.OTHER),
            "EMD's high-horsepower four-axle response to GE's Dash 8. Santa Fe and Southern Pacific were primary buyers. 3800 hp from 16-710G prime mover.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/BNSF_GP60_4017.jpg?width=480"),
        LocomotiveEntry("l27", "GP9", "EMD", 1954, 1750,
            "DC traction", "B-B", listOf(Railroad.BNSF, Railroad.CN, Railroad.CP, Railroad.OTHER),
            "The locomotive that modernized American railroading. Over 4,000 built. Many survive today in industrial, commuter, and tourist railroad service.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/CN_GP9_4102.jpg?width=480"),

        // ── EMD Passenger ────────────────────────────────────────────────────
        LocomotiveEntry("l28", "F59PHI", "EMD", 1994, 3000,
            "AC traction", "B-B", listOf(Railroad.AMTRAK, Railroad.OTHER),
            "Intercity passenger locomotive for state corridor services. Low-profile hood and isolated cab. Operates Amtrak California services and Cascades.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/Amtrak_F59PHI_456.jpg?width=480"),
        LocomotiveEntry("l29", "F40PH", "EMD", 1976, 3000,
            "DC traction", "B-B", listOf(Railroad.AMTRAK, Railroad.OTHER),
            "Amtrak's dominant power from the late 1970s through the 1990s. Head-end power (HEP) generator replaced the steam heat boiler. Many survive on commuter railroads.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/Amtrak_F40PH_349.jpg?width=480"),

        // ── EMD Switchers ────────────────────────────────────────────────────
        LocomotiveEntry("l30", "MP15AC", "EMD", 1975, 1500,
            "AC traction", "B-B", listOf(Railroad.BNSF, Railroad.UP, Railroad.CSX, Railroad.NS, Railroad.OTHER),
            "Multi-Purpose 15 — EMD's primary switcher of the 1970s. Used for yard, local, and industrial service. Still common in major classification yards.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/UP_MP15AC_1362.jpg?width=480"),
        LocomotiveEntry("l31", "SW1500", "EMD", 1966, 1500,
            "DC traction", "B-B", listOf(Railroad.BNSF, Railroad.UP, Railroad.CSX, Railroad.NS, Railroad.OTHER),
            "Standard yard switcher of the 1960s–70s. The 8-645E prime mover and trademark short hood silhouette made it ubiquitous in American yards.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/UP_SW1500_1254.jpg?width=480"),

        // ── GE Dash 7 / U-series legacy ──────────────────────────────────────
        LocomotiveEntry("l32", "C36-7", "GE Transportation", 1983, 3600,
            "DC traction", "C-C", listOf(Railroad.CSX, Railroad.NS, Railroad.OTHER),
            "GE's 3600-hp Dash 7. CSX (former Seaboard System and Family Lines) and NS (former Southern) were primary buyers. Wide nose was optional; most were narrow-cab units.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/CSX_C36-7_5517.jpg?width=480"),
        LocomotiveEntry("l33", "C30-7", "GE Transportation", 1976, 3000,
            "DC traction", "C-C", listOf(Railroad.BNSF, Railroad.CSX, Railroad.UP, Railroad.OTHER),
            "GE's 3000-hp Dash 7 workhorse of the late 1970s and 1980s. Conrail, Santa Fe, and SP were among the largest operators. Many survived into the 2010s.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/BN_C30-7_5501.jpg?width=480"),
        LocomotiveEntry("l34", "B39-8", "GE Transportation", 1984, 3900,
            "DC traction", "B-B", listOf(Railroad.BNSF, Railroad.OTHER),
            "GE's first Dash 8 four-axle locomotive. Santa Fe (now BNSF) used them on high-speed intermodal service. The B-B frame limited tractive effort vs. six-axle units.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/ATSF_B39-8_7400.jpg?width=480"),
        LocomotiveEntry("l35", "U30C", "GE Transportation", 1967, 3000,
            "DC traction", "C-C", listOf(Railroad.UP, Railroad.CSX, Railroad.OTHER),
            "GE's 3000-hp U-boat (Universal series) six-axle flagship. UP and Penn Central were the largest buyers. The U-series established GE as a genuine competitor to EMD.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/UP_U30C_2853.jpg?width=480"),
        LocomotiveEntry("l36", "U23B", "GE Transportation", 1968, 2250,
            "DC traction", "B-B", listOf(Railroad.CSX, Railroad.NS, Railroad.OTHER),
            "Four-axle U-boat widely used on the Eastern roads. Penn Central, Conrail, and Southern were major operators. Recognizable by the short hood and rounded long-hood profile.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/Conrail_U23B_2721.jpg?width=480"),

        // ── Additional EMD freight units ──────────────────────────────────────
        LocomotiveEntry("l37", "SD75I", "EMD", 1995, 4300,
            "AC traction", "C-C", listOf(Railroad.BNSF),
            "BNSF-specific high-adhesion variant ordered exclusively by Burlington Northern before the merger. The 'I' denotes isolated cab for improved crew comfort on long transcontinental runs.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/BNSF_SD75I_8270.jpg?width=480"),
        LocomotiveEntry("l38", "SD75M", "EMD", 1994, 4300,
            "DC traction", "C-C", listOf(Railroad.BNSF, Railroad.UP),
            "BNSF and UP's DC-traction variant of the SD75 platform. Distinguished by the large dynamic brake grids and 'whisker' nose treatment on some BNSF units.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/BNSF_SD75M_9002.jpg?width=480"),
        LocomotiveEntry("l39", "SD60I", "EMD", 1989, 3800,
            "DC traction", "C-C", listOf(Railroad.NS),
            "Norfolk Southern's exclusive isolated-cab SD60. NS ordered 103 units — the only railroad to specify the full-width isolated cab on the SD60 platform. Easily recognized by the distinctive cab shape.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/NS_SD60I_6641.jpg?width=480"),
        LocomotiveEntry("l40", "GP60M", "EMD", 1989, 3800,
            "DC traction", "B-B", listOf(Railroad.BNSF, Railroad.OTHER),
            "Santa Fe's wide-nose GP60 variant. The distinctive low-profile cab was designed for Santa Fe's Warbonnet passenger scheme before becoming the standard for the Transcon intermodal fleet.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/BNSF_GP60M_133.jpg?width=480"),
        LocomotiveEntry("l41", "GP50", "EMD", 1980, 3500,
            "DC traction", "B-B", listOf(Railroad.BNSF, Railroad.UP, Railroad.NS, Railroad.OTHER),
            "EMD's 3500-hp four-axle locomotive bridging the GP40 and GP60 eras. Used on high-speed merchandise and intermodal service across multiple Class I railroads.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/NS_GP50_7062.jpg?width=480"),
        LocomotiveEntry("l42", "GP40-2", "EMD", 1972, 3000,
            "DC traction", "B-B", listOf(Railroad.BNSF, Railroad.UP, Railroad.CSX, Railroad.NS, Railroad.CN, Railroad.OTHER),
            "The 3000-hp four-axle workhorse of the 1970s. Widely used on fast merchandise and intermodal trains where six-axle units were not required. Thousands built across all Class I railroads.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/Conrail_GP40-2_3373.jpg?width=480"),
        LocomotiveEntry("l43", "SD45-2", "EMD", 1972, 3600,
            "DC traction", "C-C", listOf(Railroad.NS, Railroad.OTHER),
            "Improved SD45 with Dash 2 modular electronics. Conrail inherited a large fleet at its formation; most passed to NS at the 1999 split. The 20-cylinder 645 engine remained thirsty but powerful.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/Conrail_SD45-2_6657.jpg?width=480"),

        // ── Classic EMD cab units (F and E series) ────────────────────────────
        LocomotiveEntry("l44", "F7", "EMD", 1949, 1500,
            "DC traction", "B-B", listOf(Railroad.BNSF, Railroad.CN, Railroad.CP, Railroad.OTHER),
            "The locomotive that won the dieselization of America. Over 4,000 A and B units built. Thousands survive in museums, tourist railroads, and heritage excursion service. Santa Fe Warbonnet F7s are among the most iconic.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/ATSF_F7_347.jpg?width=480"),
        LocomotiveEntry("l45", "E8/E9", "EMD", 1949, 2250,
            "DC traction", "A1A-A1A", listOf(Railroad.AMTRAK, Railroad.OTHER),
            "EMD's passenger A-unit with the distinctive rounded nose. The E9 (1954) was the final production version. Amtrak inherited hundreds at its 1971 formation; most were retired by the 1980s but several survive in excursion service.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/Amtrak_E8_403.jpg?width=480"),
        LocomotiveEntry("l46", "FP45", "EMD", 1967, 3600,
            "DC traction", "B-B", listOf(Railroad.BNSF, Railroad.OTHER),
            "A full-width cowl-body passenger locomotive built for Santa Fe and Milwaukee Road. Six BNSF heritage units retain the red-and-silver Warbonnet scheme and operate on special excursions, making them among the most photographed locomotives in North America.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/BNSF_FP45_97.jpg?width=480"),
        LocomotiveEntry("l47", "F40PH-2", "EMD", 1985, 3000,
            "DC traction", "B-B", listOf(Railroad.AMTRAK, Railroad.OTHER),
            "Improved F40PH with HEP upgrades for Amtrak's Superliner fleet. The workhorse of Amtrak's long-distance network through the 1990s. Many survive on commuter railroads including Metra and NJ Transit.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/Metra_F40PH-2_109.jpg?width=480"),

        // ── ALCO heritage units ───────────────────────────────────────────────
        LocomotiveEntry("l48", "C-630", "ALCO / MLW", 1965, 3000,
            "DC traction", "C-C", listOf(Railroad.CN, Railroad.CP, Railroad.OTHER),
            "ALCO's 3000-hp six-axle Century series flagship. Penn Central and Canadian roads were primary buyers. MLW (Montreal Locomotive Works) built licensed copies for CN and CP that outlasted US examples by decades.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/CN_C630_2033.jpg?width=480"),
        LocomotiveEntry("l49", "C-628", "ALCO", 1963, 2750,
            "DC traction", "C-C", listOf(Railroad.OTHER),
            "ALCO's first Century six-axle locomotive. Southern Pacific, Union Pacific, and Penn Central were major buyers. The distinctive angular hood and ALCO 251 prime mover made these immediately identifiable.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/SP_C628_4805.jpg?width=480"),
        LocomotiveEntry("l50", "RS-3", "ALCO", 1950, 1600,
            "DC traction", "B-B", listOf(Railroad.OTHER),
            "ALCO's best-selling Road Switcher. Over 1,400 built for US roads and export. Many survive on tourist and heritage railroads. The RS-3 established the hood-unit layout that all subsequent American diesels would follow.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/Reading_RS-3_462.jpg?width=480"),
        LocomotiveEntry("l51", "PA-1", "ALCO", 1946, 2000,
            "DC traction", "A1A-A1A", listOf(Railroad.OTHER),
            "ALCO's answer to EMD's E series — arguably more beautiful. Santa Fe, Pennsylvania Railroad, and Delaware & Hudson operated PAs. Only a handful survive; two restored PA-1s operate on excursions in Argentina.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/ATSF_PA1_51.jpg?width=480"),
        LocomotiveEntry("l52", "C-420", "ALCO", 1963, 2000,
            "DC traction", "B-B", listOf(Railroad.OTHER),
            "Four-axle Century series unit operated by Long Island Rail Road, Reading, and others. A handful survive on tourist railroads. ALCO's 251C engine gave the C-420 a distinctive exhaust note beloved by railfans.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/LIRR_C420_210.jpg?width=480"),

        // ── Passenger electric / EMU ──────────────────────────────────────────
        LocomotiveEntry("l53", "ACS-64", "Siemens", 2014, 8600,
            "Electric (25 kV AC)", "B-B", listOf(Railroad.AMTRAK),
            "Amtrak's Cities Sprinter — the primary electric locomotive for Northeast Corridor services including Acela support and Northeast Regional trains. 125 mph top speed; replaces the AEM-7 and HHP-8 on most NEC runs.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/Amtrak_ACS-64_601.jpg?width=480"),
        LocomotiveEntry("l54", "AEM-7", "ASEA / EMD", 1980, 7000,
            "Electric (25 kV AC / 12 kV AC)", "B-B", listOf(Railroad.AMTRAK, Railroad.OTHER),
            "Amtrak's dominant NEC electric locomotive for three decades. Swedish ASEA design built under license. SEPTA and NJ Transit also operated examples. Nicknamed 'Toaster' for its boxy shape.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/Amtrak_AEM-7_904.jpg?width=480"),
        LocomotiveEntry("l55", "HHP-8", "Bombardier / ALSTOM", 2000, 8000,
            "Electric (25 kV AC)", "B-B", listOf(Railroad.AMTRAK),
            "High-Horsepower 8000 — Amtrak's troubled electric locomotive intended to pair with Acela Express sets. Chronic reliability issues led to early retirement. Replaced by the ACS-64.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/Amtrak_HHP-8_650.jpg?width=480"),
        LocomotiveEntry("l56", "Bombardier M7", "Bombardier", 2002, 0,
            "Electric (750 V DC third rail)", "B-B", listOf(Railroad.OTHER),
            "Electric multiple-unit cars operated by Long Island Rail Road and Metro-North. Over 800 units in service — the backbone of both fleets. Top speed 100 mph; regenerative braking returns power to the third rail.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/LIRR_M7_7262.jpg?width=480"),
        LocomotiveEntry("l57", "Silverliner V", "Hyundai Rotem", 2010, 0,
            "Electric (25 kV AC / 12 kV AC)", "B-B", listOf(Railroad.OTHER),
            "SEPTA's current electric multiple-unit fleet on the Main Line and Chestnut Hill branches. Dual-voltage capability for both SEPTA's 25 kV and legacy 12 kV catenary sections.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/SEPTA_Silverliner_V_826.jpg?width=480"),

        // ── Commuter diesel power ─────────────────────────────────────────────
        LocomotiveEntry("l58", "MP36PH-3C", "Motive Power Industries", 2002, 3600,
            "DC traction", "B-B", listOf(Railroad.OTHER),
            "MPI's primary commuter locomotive. Metra, Virginia Railway Express, and Sound Transit operate large fleets. The 3600-hp 16-265H engine and push-pull capability make it ideal for stub-end commuter terminals.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/Metra_MP36PH-3C_428.jpg?width=480"),
        LocomotiveEntry("l59", "MP36PH-3S", "Motive Power Industries", 2005, 3600,
            "DC traction", "B-B", listOf(Railroad.OTHER),
            "Caltrain's variant of the MP36, built for the San Francisco Peninsula corridor. Designed with cab signaling for the busy commuter corridor south from San Francisco to San Jose and Gilroy.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/Caltrain_MP36PH-3S_922.jpg?width=480"),
        LocomotiveEntry("l60", "MP40PH-3C", "Motive Power Industries", 2007, 4000,
            "DC traction", "B-B", listOf(Railroad.OTHER),
            "MBTA's commuter rail workhorse on the greater Boston network. The 4000-hp engine handles the demanding grades of the Framingham/Worcester and Providence/Stoughton lines.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/MBTA_MP40PH-3C_027.jpg?width=480"),
        LocomotiveEntry("l61", "F59PH", "EMD", 1988, 3000,
            "DC traction", "B-B", listOf(Railroad.OTHER),
            "Earlier version of the F59PHI (l28) with a conventional cab. Operated by Caltrain, MBTA, and GO Transit. The distinctive rounded nose predated the full F59PHI low-profile design.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/Caltrain_F59PH_913.jpg?width=480"),
        LocomotiveEntry("l62", "NJT ALP-46", "Bombardier", 2002, 7000,
            "Electric (25 kV AC)", "B-B", listOf(Railroad.OTHER),
            "New Jersey Transit's primary electric locomotive, based on the German DB Class 101. Operates across NJT's electrified Northeast Corridor and Morris & Essex lines. 125 mph capable.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/NJT_ALP-46_4600.jpg?width=480"),
        LocomotiveEntry("l63", "NJT ALP-45DP", "Bombardier", 2012, 5700,
            "Dual-mode (electric + diesel)", "B-B", listOf(Railroad.OTHER),
            "Dual-mode locomotive operating under 25 kV catenary and on diesel on unelectrified lines. NJ Transit uses them on through-service from the Northeast Corridor to non-electrified branches in New Jersey.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/NJT_ALP-45DP_4500.jpg?width=480"),

        // ── Wabtec / GE Tier 4 and recent ────────────────────────────────────
        LocomotiveEntry("l64", "ET44AH", "GE Transportation", 2016, 4400,
            "AC traction", "C-C", listOf(Railroad.BNSF),
            "Evolution Series Tier 4 with Aggressive Hybrid braking for BNSF's Powder River Basin coal trains. The extended dynamic brake grids are the most visible external difference from the standard ET44AC.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/BNSF_ET44AH_3944.jpg?width=480"),
        LocomotiveEntry("l65", "ES44BH", "GE Transportation", 2007, 4400,
            "AC traction", "C-C", listOf(Railroad.BNSF),
            "BNSF-specific variant with an auxiliary power unit instead of dynamic brakes — designed for yard idling reduction in cold-weather yards. Visually similar to ES44AC but with a different rear hood configuration.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/BNSF_ES44BH_5959.jpg?width=480"),

        // ── EMD / Progress Rail Tier 4 ────────────────────────────────────────
        LocomotiveEntry("l66", "SD70ACe-T4", "Progress Rail", 2015, 4300,
            "AC traction", "C-C", listOf(Railroad.NS, Railroad.UP, Railroad.CSX),
            "Tier 4 emissions-compliant SD70ACe using exhaust gas recirculation rather than selective catalytic reduction. NS was the launch customer with 200 units; UP followed. Distinguishable by revised hood vent pattern.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/NS_SD70ACe-T4_1097.jpg?width=480"),

        // ── Canadian motive power ─────────────────────────────────────────────
        LocomotiveEntry("l67", "CN SD75I", "EMD", 1995, 4300,
            "AC traction", "C-C", listOf(Railroad.CN),
            "Canadian National's high-adhesion SD75 fleet. CN operates hundreds on their transcontinental main lines between Halifax and Vancouver. The 'I' isolated cab reduces vibration on CN's long-haul crews.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/CN_SD75I_5608.jpg?width=480"),
        LocomotiveEntry("l68", "CP ES44AC", "GE Transportation", 2005, 4400,
            "AC traction", "C-C", listOf(Railroad.CP),
            "Canadian Pacific's GEVO fleet forms the backbone of their transcontinental power. Post-merger CPKC units now work KCS corridors as well. CP's multimark logo on the nose makes them instantly recognizable.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/CP_ES44AC_8748.jpg?width=480"),
        LocomotiveEntry("l69", "CPKC SD70ACu", "Progress Rail", 2023, 4300,
            "AC traction", "C-C", listOf(Railroad.CP),
            "CPKC's newest freight power, ordered post-merger for the combined Canadian Pacific Kansas City network. The 'u' designation denotes updated emissions and control systems. First deliveries carry both CP and CPKC markings.",
            null),

        // ── Special / mixed-use ───────────────────────────────────────────────
        LocomotiveEntry("l70", "EMD SD70AH-T4", "Progress Rail", 2017, 4300,
            "AC traction", "C-C", listOf(Railroad.NS, Railroad.CSX),
            "Hybrid-braking Tier 4 variant. NS ordered a small fleet for mountain helper service; the enhanced dynamic braking assists on heavy coal drags over mountain subdivisions.",
            null),
        LocomotiveEntry("l71", "GE P32AC-DM", "GE Transportation", 1995, 3200,
            "Dual-mode (electric + diesel)", "B-B", listOf(Railroad.AMTRAK, Railroad.OTHER),
            "Dual-mode locomotive powering Metro-North's Hudson Line service between Grand Central Terminal (third rail) and upstate New York (diesel). Nicknamed 'Genesis DM' and 'Pepsi Can' for the curved bodyshell.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/Metro-North_P32AC-DM_222.jpg?width=480"),
        LocomotiveEntry("l72", "GP7", "EMD", 1949, 1500,
            "DC traction", "B-B", listOf(Railroad.BNSF, Railroad.UP, Railroad.OTHER),
            "The first of the famous GP (General Purpose) line — the locomotive that proved the hood unit could replace the cab unit for road freight service. Thousands were built; many survive in tourist railroad and industrial service.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/ATSF_GP7_2790.jpg?width=480"),
        LocomotiveEntry("l73", "GP35", "EMD", 1963, 2500,
            "DC traction", "B-B", listOf(Railroad.BNSF, Railroad.UP, Railroad.NS, Railroad.OTHER),
            "A transitional EMD unit between the GP30 and GP40 eras. Over 1,300 built. The 16-567D prime mover and external oil cooler make it easily identifiable. Many survive in industrial and tourist service.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/SP_GP35_6328.jpg?width=480"),
        LocomotiveEntry("l74", "SD24", "EMD", 1958, 2400,
            "DC traction", "C-C", listOf(Railroad.UP, Railroad.OTHER),
            "EMD's first six-axle road locomotive — introduced the C-C truck arrangement to American freight railroading. Union Pacific and Southern Pacific were primary buyers. Most were retired by the 1980s.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/UP_SD24_435.jpg?width=480"),
        LocomotiveEntry("l75", "NJT GP40PH-2", "EMD", 1974, 3000,
            "DC traction", "B-B", listOf(Railroad.OTHER),
            "Rebuilt GP40 with head-end power for New Jersey Transit push-pull commuter service. A common sight on NJT's Erie lines. The HEP blower housing atop the long hood distinguishes them from standard GP40s.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/NJT_GP40PH-2_4105.jpg?width=480"),
        LocomotiveEntry("l76", "Siemens Charger SC-44 (Midwest)", "Siemens", 2017, 4400,
            "AC traction", "B-B", listOf(Railroad.OTHER),
            "Midwest corridor variant of the Charger platform. Operates Amtrak's Hiawatha, Illinois Zephyr, and Carl Sandburg services under state contract. Identical mechanically to the SC-44 but finished in state-specific liveries.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/Illinois_Amtrak_SC44_4600.jpg?width=480"),
        LocomotiveEntry("l77", "Baldwin AS-616", "Baldwin Locomotive Works", 1950, 1600,
            "DC traction", "C-C", listOf(Railroad.OTHER),
            "Baldwin's last successful road switcher design before the company exited locomotive production. A handful survive on tourist and heritage railroads. The distinctive cast-frame trucks and rounded hood are Baldwin hallmarks.",
            "https://commons.wikimedia.org/wiki/Special:FilePath/SP_AS-616_5228.jpg?width=480"),
        LocomotiveEntry("l78", "Lima-Hamilton LS-1200", "Lima-Hamilton", 1949, 1200,
            "DC traction", "B-B", listOf(Railroad.OTHER),
            "The only road locomotive produced by Lima-Hamilton before the company merged into Baldwin. Fewer than 30 were built for New York Central and Monon. Extreme rarity makes any surviving example a prized museum piece.",
            null),
        LocomotiveEntry("l79", "Alstom Prima II", "Alstom", 2018, 5600,
            "Electric (25 kV AC)", "Bo-Bo", listOf(Railroad.OTHER),
            "Modern electric locomotive used by Amtrak's state partners in the Pacific Northwest on shared corridors with freight railroads. High-speed capable with advanced regenerative braking for environmentally efficient operation.",
            null),
        LocomotiveEntry("l80", "BNSF C44-9W Heritage", "GE Transportation", 1994, 4400,
            "DC traction", "C-C", listOf(Railroad.BNSF),
            "Preserved Dash 9-44CW units maintained in BNSF's heritage locomotive program, repainted in predecessor roads' liveries — Burlington Northern green, Santa Fe Warbonnet, and Frisco orange. Assigned to priority intermodal trains for promotional runs.",
            null)
    )

    // ── Sun calculator ────────────────────────────────────────────────────────

    fun calculateSunInfo(lat: Double, lon: Double, timeMs: Long = System.currentTimeMillis()): SunInfo {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = timeMs }
        val hour      = cal.get(java.util.Calendar.HOUR_OF_DAY) +
                        cal.get(java.util.Calendar.MINUTE) / 60.0
        val dayOfYear = cal.get(java.util.Calendar.DAY_OF_YEAR)

        // Solar declination (degrees)
        val decl = 23.45 * Math.sin(Math.toRadians(360.0 / 365.0 * (dayOfYear - 81)))

        // Equation of time (minutes) — accounts for Earth's elliptical orbit and axial tilt.
        // Shifts solar noon by up to ±16 min vs. clock noon; worst around Feb 12 and Nov 3.
        val B         = Math.toRadians(360.0 / 365.0 * (dayOfYear - 81))
        val eqTimeMin = 9.87 * Math.sin(2 * B) - 7.53 * Math.cos(B) - 1.5 * Math.sin(B)

        // Solar noon in local clock time, corrected for longitude offset within the timezone
        // and for the equation of time.
        val tzOffsetHours = cal.timeZone.getOffset(timeMs) / 3_600_000.0
        val solarNoonLCT  = 12.0 - lon / 15.0 + tzOffsetHours - eqTimeMin / 60.0

        // Current hour angle (0° at solar noon, negative in AM, positive in PM).
        // Must use solarNoonLCT — not hardcoded 12:00 — so azimuth/elevation are correct
        // regardless of how far the user is from their timezone's central meridian.
        val hourAngle = (hour - solarNoonLCT) * 15.0

        // Sun elevation and azimuth for the current moment
        val elevRad = Math.asin(
            Math.sin(Math.toRadians(lat)) * Math.sin(Math.toRadians(decl)) +
            Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(decl)) *
            Math.cos(Math.toRadians(hourAngle))
        )
        val elevation = Math.toDegrees(elevRad)
        val azimuth = (Math.toDegrees(Math.atan2(
            -Math.sin(Math.toRadians(hourAngle)),
            Math.tan(Math.toRadians(decl)) * Math.cos(Math.toRadians(lat)) -
            Math.sin(Math.toRadians(lat)) * Math.cos(Math.toRadians(hourAngle))
        )) + 360) % 360

        // Returns the symmetric hour angle (degrees) at which the sun reaches
        // targetElevDeg. Null when the sun never reaches that elevation (polar regions).
        fun hourAngleAt(targetElevDeg: Double): Double? {
            val cosH = (Math.sin(Math.toRadians(targetElevDeg)) -
                        Math.sin(Math.toRadians(lat)) * Math.sin(Math.toRadians(decl))) /
                       (Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(decl)))
            if (cosH < -1.0 || cosH > 1.0) return null
            return Math.toDegrees(Math.acos(cosH))
        }

        // Hour angles at horizon (-0.833° accounts for atmospheric refraction)
        // and at the 6° golden-hour boundary
        val haHorizon = hourAngleAt(-0.833)
        val ha6Deg    = hourAngleAt(6.0)

        val goldenHourStart: String
        val goldenHourEnd:   String
        val isGoldenHour:    Boolean

        if (haHorizon == null) {
            // Polar day or polar night — sun never crosses the horizon
            isGoldenHour    = false
            goldenHourStart = "--:--"
            goldenHourEnd   = "--:--"
        } else {
            val sunriseLCT = solarNoonLCT - haHorizon / 15.0
            val sunsetLCT  = solarNoonLCT + haHorizon / 15.0

            // If the sun never reaches 6° (deep winter at high latitude),
            // the entire daylight window is golden hour
            val morningGoldEndLCT   = if (ha6Deg != null) solarNoonLCT - ha6Deg / 15.0 else sunsetLCT
            val eveningGoldStartLCT = if (ha6Deg != null) solarNoonLCT + ha6Deg / 15.0 else sunriseLCT

            // Show morning window before solar noon, evening window from solar noon onward
            val (ghStart, ghEnd) = if (hour < solarNoonLCT)
                sunriseLCT to morningGoldEndLCT
            else
                eveningGoldStartLCT to sunsetLCT

            isGoldenHour    = elevation in -0.833..6.0
            goldenHourStart = formatLocalHour(ghStart)
            goldenHourEnd   = formatLocalHour(ghEnd)
        }

        return SunInfo(
            elevationDegrees = elevation,
            azimuthDegrees   = azimuth,
            goldenHourStart  = goldenHourStart,
            goldenHourEnd    = goldenHourEnd,
            isGoldenHour     = isGoldenHour
        )
    }

    /** Formats a decimal local hour (may be outside 0–24) as e.g. "6:42 AM". */
    private fun formatLocalHour(decimalHour: Double): String {
        val h24  = ((decimalHour % 24.0) + 24.0) % 24.0   // normalise to 0–24
        val hInt = h24.toInt()
        val min  = ((h24 - hInt) * 60.0).toInt().coerceIn(0, 59)
        val isPm = hInt >= 12
        val h12  = when { hInt == 0 -> 12; hInt > 12 -> hInt - 12; else -> hInt }
        return "$h12:${min.toString().padStart(2, '0')} ${if (isPm) "PM" else "AM"}"
    }
}

// ── AmtrakStationJson → TimetableStop ────────────────────────────────────────

private val ISO_FMT = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
private val DISPLAY_FMT = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)

private fun String.parseAmtrakTime(): String? = try {
    DISPLAY_FMT.format(ISO_FMT.parse(this)!!)
} catch (_: Exception) { null }

internal fun AmtrakStationJson.toTimetableStop() = TimetableStop(
    code               = code,
    scheduledArrival   = schArr?.parseAmtrakTime(),
    scheduledDeparture = schDep?.parseAmtrakTime(),
    actualArrival      = arr?.parseAmtrakTime(),
    actualDeparture    = dep?.parseAmtrakTime(),
    arrivalStatus      = arrCmnt?.takeIf { it.isNotBlank() },
    departureStatus    = depCmnt?.takeIf { it.isNotBlank() },
    isBusThruway       = bus,
    hasDeparted        = postdep,
    hasArrived         = postarr
)
