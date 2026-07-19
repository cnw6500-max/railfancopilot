package com.railfancopilot.app.data.repository

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.railfancopilot.app.data.models.TrainTrailWaypoint
import com.railfancopilot.app.data.models.TRAIL_RETENTION_MS
import kotlinx.coroutines.tasks.await

/**
 * Persists train position waypoints to Firestore for cross-device 14-day trail history.
 *
 * Firestore structure:
 *   train_trails/{trainId}/waypoints/{auto-id}
 *     → { lat, lon, speedMph, timestampMs, trainSymbol, railroad }
 *
 * Cost controls:
 *  - Writes are throttled by the caller: only when a train moves > MIN_CLOUD_WRITE_METERS.
 *  - Reads are lazy: only triggered when a user taps on a specific train.
 *  - Pruning is handled by a Firestore TTL policy on the `timestampMs` field
 *    (configure in Firebase console: Database → TTL → collection train_trails/{id}/waypoints,
 *    field timestampMs, unit milliseconds, duration 14 days).
 *    The client-side prune in [pruneStaleWaypoints] is a belt-and-suspenders fallback.
 */
object FirestoreTrailsRepo {

    private val db by lazy { FirebaseFirestore.getInstance() }

    /** Minimum distance (metres) a train must move before we write a cloud waypoint. */
    const val MIN_CLOUD_WRITE_METERS = 3_218.0   // ~2 miles

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Write a single waypoint for [trainId].  Safe to call from any coroutine;
     * failures are silently swallowed so a Firestore blip never breaks the UI.
     */
    suspend fun writeWaypoint(waypoint: TrainTrailWaypoint) {
        runCatching {
            db.collection("train_trails")
                .document(waypoint.trainId)
                .collection("waypoints")
                .add(
                    mapOf(
                        "lat"         to waypoint.latitude,
                        "lon"         to waypoint.longitude,
                        "speedMph"    to waypoint.speedMph,
                        "timestampMs" to waypoint.timestampMs,
                        "symbol"      to waypoint.trainSymbol,
                        "railroad"    to waypoint.railroad
                    )
                )
                .await()
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Fetch up to [limit] waypoints for [trainId] from the last 14 days,
     * sorted oldest-first so they can be rendered as a polyline.
     * Returns an empty list on any error.
     */
    suspend fun getTrail(trainId: String, limit: Long = 500): List<LatLng> {
        val sinceMs = System.currentTimeMillis() - TRAIL_RETENTION_MS
        return runCatching {
            db.collection("train_trails")
                .document(trainId)
                .collection("waypoints")
                .whereGreaterThan("timestampMs", sinceMs)
                .orderBy("timestampMs")
                .limit(limit)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    val lat = doc.getDouble("lat") ?: return@mapNotNull null
                    val lon = doc.getDouble("lon") ?: return@mapNotNull null
                    LatLng(lat, lon)
                }
        }.getOrDefault(emptyList())
    }

    // ── Prune ─────────────────────────────────────────────────────────────────

    /**
     * Belt-and-suspenders client-side prune for [trainId].
     * Deletes waypoints older than 14 days.  Called lazily when a trail is fetched.
     * In production the Firestore TTL policy handles this automatically.
     */
    suspend fun pruneStaleWaypoints(trainId: String) {
        val cutoffMs = System.currentTimeMillis() - TRAIL_RETENTION_MS
        runCatching {
            val stale = db.collection("train_trails")
                .document(trainId)
                .collection("waypoints")
                .whereLessThan("timestampMs", cutoffMs)
                .limit(100)
                .get()
                .await()
            val batch = db.batch()
            stale.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    // ── Trip log sync ─────────────────────────────────────────────────────────

    /**
     * Write a completed trip to Firestore under the user's anonymous UID,
     * so it survives app reinstalls.
     */
    suspend fun syncCompletedTrip(uid: String, tripId: String, data: Map<String, Any>) {
        runCatching {
            db.collection("users")
                .document(uid)
                .collection("trip_logs")
                .document(tripId)
                .set(data, SetOptions.merge())
                .await()
        }
    }
}
