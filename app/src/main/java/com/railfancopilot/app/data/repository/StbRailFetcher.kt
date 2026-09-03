package com.railfancopilot.app.data.repository

import com.google.android.gms.maps.model.LatLng
import com.railfancopilot.app.data.models.AbandonedRailLine
import com.railfancopilot.app.data.models.RailInfo
import com.railfancopilot.app.data.models.RailwaySegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Fetches rail-network geometry from the Surface Transportation Board's public
 * ArcGIS "Railroad Map Depot" (services3.arcgis.com/6rJKAjBRDRSfjCzV).
 *
 *  • NTAD North American Rail Network lines — owner (reporting mark), subdivision,
 *    track count, yard name.  Primary source for the "Rail Lines" overlay and for
 *    auto-filling railroad/subdivision on new spots & saved locations.
 *  • STB abandoned + railbanked (rails-to-trails) lines with docket metadata.
 *
 * All layers are public, Query-only, no auth.  ArcGIS caps a single response at
 * 2000 features, so bbox queries page with resultOffset.
 *
 * STB disclaimer: data is supplied by railroad applicants and is not verified —
 * it is informational only and does not establish the legal status of any line.
 */
object StbRailFetcher {

    private const val BASE = "https://services3.arcgis.com/6rJKAjBRDRSfjCzV/arcgis/rest/services"
    private const val NARN_LAYER      = "$BASE/NTAD_NARN_Other_Rail_Lines/FeatureServer/0/query"
    private const val ABANDONED_LAYER = "$BASE/Abandoned_rail_lines/FeatureServer/0/query"
    private const val RAILBANKED_LAYER = "$BASE/Railbanked_rail_lines/FeatureServer/0/query"

    private const val PAGE_SIZE = 2000
    private const val MAX_PAGES = 3          // ≤ 6000 segments per viewport

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "RailfanCopilot/1.0 (Android)")
                    .build()
            )
        }
        .build()

    private val segmentCache   = LinkedHashMap<String, List<RailwaySegment>>()
    private val abandonedCache = LinkedHashMap<String, List<AbandonedRailLine>>()

    // ── Reporting mark → display name ──────────────────────────────────────────

    /** NTAD RROWNER codes are AAR reporting marks. Expand the common ones. */
    fun ownerDisplayName(mark: String): String = when (mark.trim().uppercase()) {
        "BNSF"          -> "BNSF Railway"
        "UP"            -> "Union Pacific"
        "CSXT", "CSX"   -> "CSX Transportation"
        "NS"            -> "Norfolk Southern"
        "CN", "GTW", "IC", "WC" -> "Canadian National"
        "CPRS", "CP", "CPKC", "SOO", "DME" -> "CPKC"
        "KCS", "KCSM"   -> "CPKC (ex-KCS)"
        "AMTK"          -> "Amtrak"
        "NIRC", "METX"  -> "Metra"
        "IAIS"          -> "Iowa Interstate"
        "FXE"           -> "Ferromex"
        "MRL"           -> "Montana Rail Link"
        "PAS", "PAR"    -> "Pan Am / CSX"
        "GWR"           -> "Genesee & Wyoming"
        "WSOR"          -> "Wisconsin & Southern"
        "CFE"           -> "Chicago, Fort Wayne & Eastern"
        "BPRR"          -> "Buffalo & Pittsburgh"
        "FEC"           -> "Florida East Coast"
        "LIRR"          -> "Long Island Rail Road"
        "MNCW", "MNCR"  -> "Metro-North"
        "NJTR"          -> "NJ Transit"
        "SEPA", "SEPTA" -> "SEPTA"
        "CALTRAIN", "PCJPB" -> "Caltrain"
        "SCAX"          -> "Metrolink"
        "MBTA"          -> "MBTA"
        "VREX"          -> "Virginia Railway Express"
        "TRE"           -> "Trinity Railway Express"
        "UTAH", "UTA"   -> "UTA FrontRunner"
        "SDIY"          -> "San Diego & Imperial Valley"
        "BCOL"          -> "BC Rail (CN)"
        "VIA"           -> "VIA Rail"
        "", " "         -> ""
        else            -> mark.trim()
    }

    // ── Rail lines (NTAD) ─────────────────────────────────────────────────────

    suspend fun fetchRailSegments(
        south: Double, west: Double, north: Double, east: Double
    ): List<RailwaySegment> = withContext(Dispatchers.IO) {
        val key = "%.2f,%.2f,%.2f,%.2f".format(south, west, north, east)
        segmentCache[key]?.let { return@withContext it }

        // Generalize geometry relative to viewport so wide views stay light.
        val offset = max(0.00005, (east - west) / 2500.0)
        val out = mutableListOf<RailwaySegment>()
        try {
            var page = 0
            while (page < MAX_PAGES) {
                val url = buildString {
                    append(NARN_LAYER)
                    append("?f=json&where=1%3D1")
                    append("&geometry=").append(enc("$west,$south,$east,$north"))
                    append("&geometryType=esriGeometryEnvelope&inSR=4326&outSR=4326")
                    append("&spatialRel=esriSpatialRelIntersects")
                    append("&outFields=").append(enc("FRAARCID,RROWNER1,RROWNER2,SUBDIV,DIVISION,TRACKS,YARDNAME,PASSNGR"))
                    append("&geometryPrecision=5&maxAllowableOffset=").append(offset)
                    append("&resultRecordCount=").append(PAGE_SIZE)
                    append("&resultOffset=").append(page * PAGE_SIZE)
                }
                val body = get(url) ?: break
                val json = JSONObject(body)
                if (json.has("error")) {
                    android.util.Log.w("StbRailFetcher", "ArcGIS error: ${json.optJSONObject("error")}")
                    break
                }
                val feats = json.optJSONArray("features") ?: break
                for (i in 0 until feats.length()) parseNarnFeature(feats.getJSONObject(i))?.let(out::add)
                if (!json.optBoolean("exceededTransferLimit", false) || feats.length() < PAGE_SIZE) break
                page++
            }
        } catch (e: Exception) {
            android.util.Log.w("StbRailFetcher", "fetchRailSegments failed: ${e.message}")
        }
        if (out.isNotEmpty()) {
            segmentCache[key] = out
            while (segmentCache.size > 20) segmentCache.remove(segmentCache.keys.first())
        }
        out
    }

    private fun parseNarnFeature(f: JSONObject): RailwaySegment? {
        val attrs = f.optJSONObject("attributes") ?: return null
        val points = parsePaths(f.optJSONObject("geometry")) ?: return null
        val mark   = attrs.optString("RROWNER1", "").trim()
        val subdiv = attrs.optString("SUBDIV", "").trim().titleCase()
        return RailwaySegment(
            id          = attrs.optLong("FRAARCID", attrs.optLong("FID", 0L)),
            points      = points,
            operator    = ownerDisplayName(mark),
            name        = subdiv,
            ownerMark   = mark,
            subdivision = subdiv,
            division    = attrs.optString("DIVISION", "").trim().titleCase(),
            tracks      = attrs.optInt("TRACKS", 0),
            yardName    = attrs.optString("YARDNAME", "").trim().titleCase(),
            passenger   = attrs.optString("PASSNGR", "").trim().isNotEmpty()
        )
    }

    /**
     * Nearest rail segment to a point (≤ [radiusM]).  Used to auto-fill the
     * railroad / subdivision fields when a user creates a spot or saved location.
     */
    suspend fun lookupRailInfo(lat: Double, lon: Double, radiusM: Int = 800): RailInfo? =
        withContext(Dispatchers.IO) {
            try {
                val url = buildString {
                    append(NARN_LAYER)
                    append("?f=json&where=1%3D1")
                    append("&geometry=").append(enc("$lon,$lat"))
                    append("&geometryType=esriGeometryPoint&inSR=4326&outSR=4326")
                    append("&distance=").append(radiusM).append("&units=esriSRUnit_Meter")
                    append("&spatialRel=esriSpatialRelIntersects")
                    append("&outFields=").append(enc("FRAARCID,RROWNER1,RROWNER2,SUBDIV,DIVISION,TRACKS,YARDNAME,PASSNGR"))
                    append("&geometryPrecision=6&resultRecordCount=50")
                }
                val json = JSONObject(get(url) ?: return@withContext null)
                val feats = json.optJSONArray("features") ?: return@withContext null
                val segs = (0 until feats.length()).mapNotNull { parseNarnFeature(feats.getJSONObject(it)) }
                if (segs.isEmpty()) return@withContext null

                val here = LatLng(lat, lon)
                val scored = segs.map { it to distanceToPolylineM(here, it.points) }.sortedBy { it.second }
                val nearest = scored.first()
                // Prefer a named subdivision if one is nearly as close as the nearest segment.
                val withSub = scored.firstOrNull { it.first.subdivision.isNotBlank() && it.second <= nearest.second * 1.5 + 50 }
                RailInfo(
                    ownerMark   = nearest.first.ownerMark,
                    ownerName   = nearest.first.operator,
                    subdivision = withSub?.first?.subdivision ?: "",
                    division    = withSub?.first?.division ?: nearest.first.division,
                    yardName    = scored.firstOrNull { it.first.yardName.isNotBlank() }?.first?.yardName ?: "",
                    tracks      = nearest.first.tracks,
                    distanceM   = nearest.second
                )
            } catch (e: Exception) {
                android.util.Log.w("StbRailFetcher", "lookupRailInfo failed: ${e.message}")
                null
            }
        }

    // ── Abandoned / railbanked lines (STB) ───────────────────────────────────

    suspend fun fetchAbandonedLines(
        south: Double, west: Double, north: Double, east: Double
    ): List<AbandonedRailLine> = withContext(Dispatchers.IO) {
        val key = "%.1f,%.1f,%.1f,%.1f".format(south, west, north, east)
        abandonedCache[key]?.let { return@withContext it }
        val offset = max(0.00005, (east - west) / 2500.0)
        val out = mutableListOf<AbandonedRailLine>()
        for ((layer, railbanked) in listOf(ABANDONED_LAYER to false, RAILBANKED_LAYER to true)) {
            try {
                val url = buildString {
                    append(layer)
                    append("?f=json&where=1%3D1")
                    append("&geometry=").append(enc("$west,$south,$east,$north"))
                    append("&geometryType=esriGeometryEnvelope&inSR=4326&outSR=4326")
                    append("&spatialRel=esriSpatialRelIntersects")
                    append("&outFields=").append(enc("FID,ID,Docket,Railroad,State,County,Filed,Approved,Completed,Length,More_Info,Link"))
                    append("&geometryPrecision=5&maxAllowableOffset=").append(offset)
                    append("&resultRecordCount=").append(PAGE_SIZE)
                }
                val json = JSONObject(get(url) ?: continue)
                val feats = json.optJSONArray("features") ?: continue
                for (i in 0 until feats.length()) {
                    val f = feats.getJSONObject(i)
                    val a = f.optJSONObject("attributes") ?: continue
                    val pts = parsePaths(f.optJSONObject("geometry")) ?: continue
                    out.add(
                        AbandonedRailLine(
                            id         = (if (railbanked) "RB_" else "AB_") + a.optString("ID", a.optString("FID")),
                            points     = pts,
                            railbanked = railbanked,
                            docket     = a.optString("Docket", "").trim(),
                            railroad   = a.optString("Railroad", "").trim(),
                            state      = a.optString("State", "").trim(),
                            county     = a.optString("County", "").trim(),
                            filed      = a.optString("Filed", "").trim(),
                            approved   = a.optString("Approved", "").trim(),
                            completed  = a.optString("Completed", "").trim(),
                            lengthMiles = a.optDouble("Length", 0.0),
                            moreInfo   = a.optString("More_Info", "").trim(),
                            link       = a.optString("Link", "").trim()
                        )
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("StbRailFetcher", "fetchAbandonedLines failed: ${e.message}")
            }
        }
        if (out.isNotEmpty()) {
            abandonedCache[key] = out
            while (abandonedCache.size > 20) abandonedCache.remove(abandonedCache.keys.first())
        }
        out
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun get(url: String): String? =
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.string()
        }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Flattens an esriGeometryPolyline's `paths` ([[x,y],…],…) into one point list. */
    private fun parsePaths(geom: JSONObject?): List<LatLng>? {
        val paths: JSONArray = geom?.optJSONArray("paths") ?: return null
        val pts = mutableListOf<LatLng>()
        for (p in 0 until paths.length()) {
            val path = paths.getJSONArray(p)
            for (i in 0 until path.length()) {
                val xy = path.getJSONArray(i)
                pts.add(LatLng(xy.getDouble(1), xy.getDouble(0)))
            }
        }
        return if (pts.size >= 2) pts else null
    }

    private fun String.titleCase(): String =
        if (isBlank()) "" else lowercase().split(' ').joinToString(" ") { w ->
            if (w.length <= 2 && w.uppercase() in setOf("UP", "NS", "CN", "CP", "OF")) w.uppercase()
            else w.replaceFirstChar { it.uppercase() }
        }

    /** Approximate point→polyline distance in metres (equirectangular, fine for < 5 km). */
    fun distanceToPolylineM(p: LatLng, line: List<LatLng>): Double {
        val kLat = 111_320.0
        val kLon = 111_320.0 * cos(Math.toRadians(p.latitude))
        fun toXY(l: LatLng) = (l.longitude - p.longitude) * kLon to (l.latitude - p.latitude) * kLat
        var best = Double.MAX_VALUE
        for (i in 0 until line.size - 1) {
            val (ax, ay) = toXY(line[i]); val (bx, by) = toXY(line[i + 1])
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else ((-ax) * dx + (-ay) * dy) / len2
            val tc = t.coerceIn(0.0, 1.0)
            val cx = ax + tc * dx; val cy = ay + tc * dy
            val d = sqrt(cx * cx + cy * cy)
            if (d < best) best = d
        }
        return best
    }
}
