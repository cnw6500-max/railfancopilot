package com.railfancopilot.app.data.repository

import android.location.Location
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.railfancopilot.app.data.models.CommunityReport
import com.railfancopilot.app.data.models.SightingComment
import com.railfancopilot.app.data.models.WatchlistEntry
import com.railfancopilot.app.data.models.WatchlistType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

/**
 * Reads and writes community sightings from the shared Firestore "sightings" collection.
 * This is the same collection the iOS app uses, so sightings are cross-platform.
 *
 * Document schema (mirrors iOS FirestoreSighting):
 *   railroad      : String
 *   trainSymbol   : String
 *   location      : String  (human-readable location name)
 *   notes         : String
 *   latitude      : Double
 *   longitude     : Double
 *   reporterName  : String
 *   timestampMs   : Double (millis since epoch)
 *   upvotes       : Long
 */
object FirestoreCommunityRepo {

    private val db = FirebaseFirestore.getInstance()
    private const val COLLECTION = "sightings"

    // ── Real-time flow of sightings filtered by distance ─────────────────────

    fun getSightingsFlow(
        userLat: Double,
        userLon: Double,
        radiusMiles: Double = 100.0
    ): Flow<List<CommunityReport>> = callbackFlow {

        var registration: ListenerRegistration? = null

        registration = db.collection(COLLECTION)
            .orderBy("timestampMs", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (com.railfancopilot.app.BuildConfig.DEBUG)
                        android.util.Log.e("FirestoreRepo", "Listen failed: ${error.message}", error)
                    return@addSnapshotListener
                }

                val results = mutableListOf<CommunityReport>()
                snapshot?.documents?.forEach { doc ->
                    try {
                        val railroad     = doc.getString("railroad")     ?: "Unknown"
                        val trainSymbol  = doc.getString("trainSymbol")  ?: ""
                        val location     = doc.getString("location")     ?: "Unknown location"
                        val notes        = doc.getString("notes")        ?: ""
                        val lat          = doc.getDouble("latitude")     ?: 0.0
                        val lon          = doc.getDouble("longitude")    ?: 0.0
                        val reporter     = doc.getString("reporterName") ?: "Railfan"
                        val tsMs         = doc.getDouble("timestampMs")?.toLong()
                                         ?: doc.getLong("timestampMs")
                                         ?: System.currentTimeMillis()
                        val upvotes      = (doc.getLong("upvotes") ?: 0L).toInt()

                        // Client-side distance filter
                        val distResults  = FloatArray(1)
                        Location.distanceBetween(userLat, userLon, lat, lon, distResults)
                        val distMiles    = distResults[0] / 1609.34

                        if (distMiles <= radiusMiles) {
                            // Build the display text: combine symbol, location, notes
                            val text = buildString {
                                if (trainSymbol.isNotBlank() && trainSymbol != "Unknown") {
                                    append(trainSymbol)
                                    append(" spotted at ")
                                }
                                append(location)
                                if (notes.isNotBlank()) {
                                    append(" — ")
                                    append(notes)
                                }
                            }

                            val photoUrl = doc.getString("photoUrl")
                            results += CommunityReport(
                                id              = doc.id,
                                userId          = reporter,
                                userName        = reporter,
                                latitude        = lat,
                                longitude       = lon,
                                text            = text,
                                trainSymbol     = trainSymbol.ifBlank { null },
                                railroad        = railroad,
                                tags            = "[]",
                                timestampMs     = tsMs,
                                upvotes         = upvotes,
                                isVerified      = false,
                                localPhotoPath  = photoUrl
                            )
                        }
                    } catch (e: Exception) {
                        if (com.railfancopilot.app.BuildConfig.DEBUG)
                            android.util.Log.e("FirestoreRepo", "Parse error on ${doc.id}: ${e.message}")
                    }
                }

                trySend(results.sortedByDescending { it.timestampMs })
            }

        awaitClose { registration?.remove() }
    }

    // ── Flow with no location filter (show all, sorted by time) ──────────────

    fun getAllSightingsFlow(): Flow<List<CommunityReport>> = callbackFlow {
        val registration = db.collection(COLLECTION)
            .orderBy("timestampMs", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val results = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val railroad     = doc.getString("railroad")     ?: "Unknown"
                        val trainSymbol  = doc.getString("trainSymbol")  ?: ""
                        val location     = doc.getString("location")     ?: "Unknown location"
                        val notes        = doc.getString("notes")        ?: ""
                        val lat          = doc.getDouble("latitude")     ?: 0.0
                        val lon          = doc.getDouble("longitude")    ?: 0.0
                        val reporter     = doc.getString("reporterName") ?: "Railfan"
                        val tsMs         = doc.getDouble("timestampMs")?.toLong()
                                         ?: doc.getLong("timestampMs")
                                         ?: System.currentTimeMillis()
                        val upvotes      = (doc.getLong("upvotes") ?: 0L).toInt()

                        val text = buildString {
                            if (trainSymbol.isNotBlank() && trainSymbol != "Unknown") {
                                append(trainSymbol); append(" spotted at ")
                            }
                            append(location)
                            if (notes.isNotBlank()) { append(" — "); append(notes) }
                        }

                        CommunityReport(
                            id = doc.id, userId = reporter, userName = reporter,
                            latitude = lat, longitude = lon, text = text,
                            trainSymbol = trainSymbol.ifBlank { null }, railroad = railroad,
                            tags = "[]", timestampMs = tsMs, upvotes = upvotes,
                            isVerified = false, localPhotoPath = doc.getString("photoUrl")
                        )
                    } catch (_: Exception) { null }
                } ?: emptyList()
                trySend(results)
            }
        awaitClose { registration.remove() }
    }

    // ── Submit a new sighting ─────────────────────────────────────────────────

    suspend fun submitSighting(
        lat: Double,
        lon: Double,
        text: String,
        trainSymbol: String?,
        railroad: String?,
        reporterName: String,
        location: String = "Unknown location",
        consist: String? = null,
        weather: String? = null,
        photoPath: String? = null
    ) {
        val photoUrl: String? = photoPath?.let { path ->
            try {
                val file = File(path)
                if (file.exists()) {
                    val ref = FirebaseStorage.getInstance().reference
                        .child("sightings/${UUID.randomUUID()}/photo.jpg")
                    ref.putFile(Uri.fromFile(file)).await()
                    ref.downloadUrl.await().toString()
                } else null
            } catch (e: Exception) {
                if (com.railfancopilot.app.BuildConfig.DEBUG)
                    android.util.Log.e("FirestoreRepo", "Photo upload failed: ${e.message}", e)
                null
            }
        }

        val doc = hashMapOf<String, Any?>(
            "railroad"     to (railroad ?: "Unknown"),
            "trainSymbol"  to (trainSymbol ?: "Unknown"),
            "location"     to location,
            "notes"        to text,
            "latitude"     to lat,
            "longitude"    to lon,
            "reporterName" to reporterName,
            "timestampMs"  to System.currentTimeMillis().toDouble(),
            "upvotes"      to 0L,
            "consist"      to (consist ?: ""),
            "weather"      to (weather ?: ""),
            "photoUrl"     to photoUrl
        )
        try {
            db.collection(COLLECTION).add(doc).await()
        } catch (e: Exception) {
            if (com.railfancopilot.app.BuildConfig.DEBUG)
                android.util.Log.e("FirestoreRepo", "submitSighting failed: ${e.message}", e)
        }
    }

    // ── Upvote a sighting ─────────────────────────────────────────────────────

    fun upvote(sightingId: String) {
        db.collection(COLLECTION).document(sightingId)
            .update("upvotes", com.google.firebase.firestore.FieldValue.increment(1))
    }

    // ── Comments subcollection ────────────────────────────────────────────────

    fun getCommentsFlow(sightingId: String): Flow<List<SightingComment>> = callbackFlow {
        val reg = db.collection(COLLECTION).document(sightingId)
            .collection("comments")
            .orderBy("timestampMs", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                val comments = snap?.documents?.mapNotNull { doc ->
                    try {
                        SightingComment(
                            id          = doc.id,
                            userName    = doc.getString("userName") ?: "Railfan",
                            text        = doc.getString("text") ?: "",
                            timestampMs = doc.getDouble("timestampMs")?.toLong()
                                          ?: doc.getLong("timestampMs")
                                          ?: System.currentTimeMillis()
                        )
                    } catch (_: Exception) { null }
                } ?: emptyList()
                trySend(comments)
            }
        awaitClose { reg.remove() }
    }

    fun addComment(sightingId: String, text: String, userName: String) {
        db.collection(COLLECTION).document(sightingId).collection("comments").add(
            hashMapOf(
                "userName"    to userName,
                "text"        to text,
                "timestampMs" to System.currentTimeMillis().toDouble()
            )
        ).addOnFailureListener { e ->
            if (com.railfancopilot.app.BuildConfig.DEBUG)
                android.util.Log.e("FirestoreRepo", "addComment failed: ${e.message}", e)
        }
    }

    // ── Delete a sighting ─────────────────────────────────────────────────────

    fun deleteSighting(sightingId: String) {
        db.collection(COLLECTION).document(sightingId).delete()
            .addOnFailureListener { e ->
                if (com.railfancopilot.app.BuildConfig.DEBUG)
                    android.util.Log.e("FirestoreRepo", "deleteSighting failed: ${e.message}", e)
            }
    }

    // ── Anonymous auth + FCM token registration ───────────────────────────────

    suspend fun ensureAnonymousAuth(): String {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
        val uid = auth.currentUser!!.uid
        // Register / refresh FCM token so the watchlist fan-out function can reach this device
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            db.collection("users").document(uid).set(
                hashMapOf("fcmToken" to token),
                com.google.firebase.firestore.SetOptions.merge()
            )
        } catch (_: Exception) { }
        return uid
    }

    // ── Watchlist CRUD ────────────────────────────────────────────────────────

    fun getWatchlistFlow(uid: String): Flow<List<WatchlistEntry>> = callbackFlow {
        val reg = db.collection("users").document(uid)
            .collection("watchlist")
            .orderBy("addedMs", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                val entries = snap?.documents?.mapNotNull { doc ->
                    try {
                        WatchlistEntry(
                            id       = doc.id,
                            type     = if (doc.getString("type") == "SYMBOL") WatchlistType.SYMBOL
                                       else WatchlistType.LOCO,
                            value    = doc.getString("value") ?: "",
                            railroad = doc.getString("railroad") ?: "",
                            label    = doc.getString("label") ?: "",
                            addedMs  = doc.getLong("addedMs") ?: System.currentTimeMillis()
                        )
                    } catch (_: Exception) { null }
                } ?: emptyList()
                trySend(entries)
            }
        awaitClose { reg.remove() }
    }

    fun addWatchlistEntry(uid: String, entry: WatchlistEntry) {
        db.collection("users").document(uid)
            .collection("watchlist")
            .document(entry.id)
            .set(hashMapOf(
                "type"     to entry.type.name,
                "value"    to entry.value.uppercase().trim(),
                "railroad" to entry.railroad.trim(),
                "label"    to entry.label.trim(),
                "addedMs"  to entry.addedMs
            ))
    }

    fun removeWatchlistEntry(uid: String, entryId: String) {
        db.collection("users").document(uid)
            .collection("watchlist")
            .document(entryId)
            .delete()
    }
}
