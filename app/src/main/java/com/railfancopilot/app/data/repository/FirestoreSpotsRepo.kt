package com.railfancopilot.app.data.repository

import android.location.Location
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import com.railfancopilot.app.data.models.RailfanSpot
import com.railfancopilot.app.data.models.TrainFrequency
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

object FirestoreSpotsRepo {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private const val COLLECTION = "railfan_spots"

    // ── Real-time flow of spots within radius ─────────────────────────────────

    fun getSpotsFlow(
        userLat: Double,
        userLon: Double,
        radiusMiles: Double = 150.0
    ): Flow<List<RailfanSpot>> = callbackFlow {
        val reg: ListenerRegistration = db.collection(COLLECTION)
            .orderBy("upvotes", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(200)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                val spots = snap?.documents?.mapNotNull { doc ->
                    try {
                        val lat = doc.getDouble("latitude") ?: return@mapNotNull null
                        val lon = doc.getDouble("longitude") ?: return@mapNotNull null
                        val dist = FloatArray(1)
                        Location.distanceBetween(userLat, userLon, lat, lon, dist)
                        if (dist[0] / 1609.34 > radiusMiles) return@mapNotNull null

                        @Suppress("UNCHECKED_CAST")
                        val photoUrls = (doc.get("photoUrls") as? List<String>) ?: emptyList()

                        RailfanSpot(
                            id              = doc.id,
                            name            = doc.getString("name")            ?: "Unnamed Spot",
                            latitude        = lat,
                            longitude       = lon,
                            submittedBy     = doc.getString("submittedBy")     ?: "Railfan",
                            submittedMs     = doc.getLong("submittedMs")       ?: 0L,
                            submittedByUid  = doc.getString("submittedByUid"),
                            railroad        = doc.getString("railroad")        ?: "",
                            subdivision     = doc.getString("subdivision")     ?: "",
                            notes           = doc.getString("notes")           ?: "",
                            photoAngles     = doc.getString("photoAngles")     ?: "",
                            safetyNotes     = doc.getString("safetyNotes")     ?: "",
                            parkingNotes    = doc.getString("parkingNotes")    ?: "",
                            scannerFrequency= doc.getString("scannerFrequency")?: "",
                            seasonalNotes   = doc.getString("seasonalNotes")   ?: "",
                            trainFrequency  = TrainFrequency.entries.find {
                                                it.name == doc.getString("trainFrequency")
                                              } ?: TrainFrequency.UNKNOWN,
                            isPublicProperty= doc.getBoolean("isPublicProperty") ?: true,
                            hasParking      = doc.getBoolean("hasParking")     ?: false,
                            hasRestrooms    = doc.getBoolean("hasRestrooms")   ?: false,
                            hasFood         = doc.getBoolean("hasFood")        ?: false,
                            hasShade        = doc.getBoolean("hasShade")       ?: false,
                            upvotes         = (doc.getLong("upvotes") ?: 0L).toInt(),
                            photoUrls       = photoUrls
                        )
                    } catch (_: Exception) { null }
                } ?: emptyList()
                trySend(spots)
            }
        awaitClose { reg.remove() }
    }

    // ── Submit a new spot ─────────────────────────────────────────────────────

    suspend fun submitSpot(spot: RailfanSpot, photoBytes: ByteArray?): String {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
        val uid = auth.currentUser?.uid

        val docRef = db.collection(COLLECTION).document(spot.id)

        // Upload photo first if provided
        val photoUrls = mutableListOf<String>()
        if (photoBytes != null) {
            val photoId = UUID.randomUUID().toString()
            val ref = storage.reference.child("spots/${spot.id}/$photoId.jpg")
            ref.putBytes(photoBytes).await()
            val url = ref.downloadUrl.await().toString()
            photoUrls.add(url)
        }

        // Use SetOptions.merge() so that upvotes are not reset if the document
        // already exists (e.g. duplicate submission or edit). Upvotes are managed
        // exclusively via FieldValue.increment in upvoteSpot().
        docRef.set(hashMapOf(
            "name"             to spot.name,
            "latitude"         to spot.latitude,
            "longitude"        to spot.longitude,
            "submittedBy"      to spot.submittedBy,
            "submittedMs"      to spot.submittedMs,
            "submittedByUid"   to uid,
            "railroad"         to spot.railroad,
            "subdivision"      to spot.subdivision,
            "notes"            to spot.notes,
            "photoAngles"      to spot.photoAngles,
            "safetyNotes"      to spot.safetyNotes,
            "parkingNotes"     to spot.parkingNotes,
            "scannerFrequency" to spot.scannerFrequency,
            "seasonalNotes"    to spot.seasonalNotes,
            "trainFrequency"   to spot.trainFrequency.name,
            "isPublicProperty" to spot.isPublicProperty,
            "hasParking"       to spot.hasParking,
            "hasRestrooms"     to spot.hasRestrooms,
            "hasFood"          to spot.hasFood,
            "hasShade"         to spot.hasShade,
            "photoUrls"        to photoUrls
        ), com.google.firebase.firestore.SetOptions.merge()).await()

        // Only initialise upvotes on first write (field absent → set to 0)
        val snap = docRef.get().await()
        if (!snap.contains("upvotes")) {
            docRef.update("upvotes", 0L).await()
        }

        return spot.id
    }

    // ── Edit an existing spot (owner only, enforced server-side too) ──────────
    // Deliberately touches only content fields via .update(), never
    // latitude/longitude/submittedBy/submittedMs/submittedByUid/upvotes — so
    // the Firestore rule's affectedKeys() check can allowlist exactly this set.
    // Preserves existing photoUrls unless a new photo is picked (append, not
    // replace — submitSpot's merge-write would otherwise wipe them on an edit
    // that doesn't touch photos, since it always writes photoUrls from scratch).

    suspend fun editSpot(spot: RailfanSpot, newPhotoBytes: ByteArray?): String {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) auth.signInAnonymously().await()

        val photoUrls = spot.photoUrls.toMutableList()
        if (newPhotoBytes != null) {
            val photoId = UUID.randomUUID().toString()
            val ref = storage.reference.child("spots/${spot.id}/$photoId.jpg")
            ref.putBytes(newPhotoBytes).await()
            photoUrls.add(ref.downloadUrl.await().toString())
        }

        db.collection(COLLECTION).document(spot.id).update(
            hashMapOf<String, Any?>(
                "name" to spot.name,
                "railroad" to spot.railroad,
                "subdivision" to spot.subdivision,
                "notes" to spot.notes,
                "photoAngles" to spot.photoAngles,
                "safetyNotes" to spot.safetyNotes,
                "parkingNotes" to spot.parkingNotes,
                "scannerFrequency" to spot.scannerFrequency,
                "seasonalNotes" to spot.seasonalNotes,
                "trainFrequency" to spot.trainFrequency.name,
                "isPublicProperty" to spot.isPublicProperty,
                "hasParking" to spot.hasParking,
                "hasRestrooms" to spot.hasRestrooms,
                "hasFood" to spot.hasFood,
                "hasShade" to spot.hasShade,
                "photoUrls" to photoUrls
            )
        ).await()

        return spot.id
    }

    // ── Upvote ────────────────────────────────────────────────────────────────

    fun upvoteSpot(spotId: String) {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) return
        db.collection(COLLECTION).document(spotId)
            .update("upvotes", com.google.firebase.firestore.FieldValue.increment(1))
    }

    // ── Add photo to existing spot ────────────────────────────────────────────

    suspend fun addPhoto(spotId: String, photoBytes: ByteArray) {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
        val photoId = UUID.randomUUID().toString()
        val ref = storage.reference.child("spots/$spotId/$photoId.jpg")
        ref.putBytes(photoBytes).await()
        val url = ref.downloadUrl.await().toString()
        db.collection(COLLECTION).document(spotId)
            .update("photoUrls", com.google.firebase.firestore.FieldValue.arrayUnion(url))
    }
}
