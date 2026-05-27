package com.railfancopilot.app.data.repository

import com.google.android.gms.maps.model.LatLng
import com.railfancopilot.app.data.models.RailwaySegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object OverpassFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "RailfanCopilot/1.0 (Android)")
                    .build()
            )
        }
        .build()

    // Simple in-memory cache keyed by rounded bounding box
    private val cache = mutableMapOf<String, List<RailwaySegment>>()

    suspend fun fetchRailwaySegments(
        south: Double, west: Double, north: Double, east: Double
    ): List<RailwaySegment> = withContext(Dispatchers.IO) {

        val cacheKey = "%.2f,%.2f,%.2f,%.2f".format(south, west, north, east)
        cache[cacheKey]?.let { return@withContext it }

        val query = """
            [out:json][timeout:20];
            (way["railway"="rail"]($south,$west,$north,$east););
            out geom qt;
        """.trimIndent()

        return@withContext try {
            val request = Request.Builder()
                .url("https://overpass-api.de/api/interpreter")
                .post(query.toRequestBody("text/plain".toMediaType()))
                .build()

            val body = client.newCall(request).execute().use { it.body?.string() ?: return@withContext emptyList() }
            val segments = parseResponse(body)
            cache[cacheKey] = segments
            // Evict old cache entries to avoid unbounded growth
            if (cache.size > 20) {
                val oldest = cache.keys.first()
                cache.remove(oldest)
            }
            segments
        } catch (e: Exception) {
            android.util.Log.w("OverpassFetcher", "Fetch failed: ${e.message}")
            emptyList()
        }
    }

    private fun parseResponse(json: String): List<RailwaySegment> {
        val elements = JSONObject(json).getJSONArray("elements")
        val segments = mutableListOf<RailwaySegment>()

        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            if (el.optString("type") != "way") continue

            val geometry = el.optJSONArray("geometry") ?: continue
            if (geometry.length() < 2) continue

            val tags = el.optJSONObject("tags") ?: JSONObject()
            val operator = tags.optString("operator", tags.optString("network", ""))
            val name = tags.optString("name", tags.optString("ref", ""))

            val points = (0 until geometry.length()).map { j ->
                val node = geometry.getJSONObject(j)
                LatLng(node.getDouble("lat"), node.getDouble("lon"))
            }

            segments.add(
                RailwaySegment(
                    id = el.getLong("id"),
                    points = points,
                    operator = operator,
                    name = name
                )
            )
        }
        return segments
    }
}
