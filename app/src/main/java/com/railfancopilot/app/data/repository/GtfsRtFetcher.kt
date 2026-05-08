package com.railfancopilot.app.data.repository

import android.location.Location
import android.util.Log
import com.railfancopilot.app.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Generic GTFS-Realtime VehiclePositions fetcher.
 *
 * Downloads the protobuf binary with OkHttp, hands the raw bytes to
 * [GtfsRtProtoParser] (zero external dependencies), applies a distance
 * filter, and maps each vehicle to a [TrainLocation].
 */
object GtfsRtFetcher {

    /**
     * @param tag          Log tag / agency name used in logcat.
     * @param url          VehiclePositions feed URL.
     * @param headers      Optional HTTP headers (Basic auth, API key, etc.).
     * @param railroad     [Railroad] to tag results with.
     * @param agencyLabel  Short prefix for [TrainLocation.id] and [TrainLocation.symbol].
     * @param userLat      User latitude — used for distance filter and ETA.
     * @param userLon      User longitude.
     * @param radiusMiles  Drop vehicles farther than this from the user.
     * @param etaFn        (userLat, userLon, trainLat, trainLon, speedMph, heading) → ETA minutes.
     */
    suspend fun fetch(
        tag:          String,
        url:          String,
        headers:      Map<String, String> = emptyMap(),
        railroad:     Railroad,
        agencyLabel:  String,
        userLat:      Double,
        userLon:      Double,
        radiusMiles:  Double,
        etaFn:        (Double, Double, Double, Double, Int, Int) -> Int?
    ): List<TrainLocation> = withContext(Dispatchers.IO) {

        try {
            val reqBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

            val bytes = NetworkModule.gtfsRtClient
                .newCall(reqBuilder.build())
                .execute()
                .use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w("GtfsRtFetcher", "$tag HTTP ${resp.code}")
                        return@withContext emptyList()
                    }
                    resp.body?.bytes() ?: return@withContext emptyList()
                }

            val vehicles = GtfsRtProtoParser.parse(bytes)
            val distBuf  = FloatArray(1)

            vehicles.mapNotNull { v ->
                if (v.latitude == 0f && v.longitude == 0f) return@mapNotNull null

                val lat = v.latitude.toDouble()
                val lon = v.longitude.toDouble()

                // Distance filter
                Location.distanceBetween(userLat, userLon, lat, lon, distBuf)
                if (distBuf[0] / 1609.34 > radiusMiles) return@mapNotNull null

                val speedMph = (v.speedMs * 2.23694f).toInt()   // m/s → mph
                val heading  = v.bearing.toInt()

                // Strip agency prefix for a tidy display label
                val lineLabel = v.routeId
                    .removePrefix("$agencyLabel-")
                    .removePrefix("${agencyLabel}_")
                    .replace("-", " ")
                    .replace("_", " ")
                    .trim()
                    .ifBlank { v.entityId }

                val status = when {
                    v.currentStatus == 1 -> TrainStatus.STOPPED     // STOPPED_AT
                    speedMph > 0         -> TrainStatus.ON_TIME
                    else                 -> TrainStatus.UNKNOWN
                }

                TrainLocation(
                    id             = "$agencyLabel-${v.entityId}",
                    symbol         = "$agencyLabel $lineLabel".trim(),
                    railroad       = railroad,
                    latitude       = lat,
                    longitude      = lon,
                    speedMph       = speedMph,
                    headingDegrees = heading,
                    etaMinutes     = if (speedMph > 0) etaFn(userLat, userLon, lat, lon, speedMph, heading) else null,
                    status         = status,
                    consist        = emptyList(),
                    origin         = "",
                    destination    = lineLabel,
                    milepost       = null,
                    subdivision    = v.routeId
                )
            }

        } catch (e: Exception) {
            Log.e("GtfsRtFetcher", "$tag failed: ${e.javaClass.simpleName}: ${e.message}")
            emptyList()
        }
    }

    /** Convert a compass direction string (SEPTA format) to degrees. */
    fun cardinalToDegrees(cardinal: String): Int = when (cardinal.uppercase().trim()) {
        "N"  ->   0; "NNE" ->  22; "NE"  ->  45; "ENE" ->  67
        "E"  ->  90; "ESE" -> 112; "SE"  -> 135; "SSE" -> 157
        "S"  -> 180; "SSW" -> 202; "SW"  -> 225; "WSW" -> 247
        "W"  -> 270; "WNW" -> 292; "NW"  -> 315; "NNW" -> 337
        else ->   0
    }
}
