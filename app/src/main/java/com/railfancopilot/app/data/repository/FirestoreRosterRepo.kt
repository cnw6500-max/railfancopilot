package com.railfancopilot.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.railfancopilot.app.data.models.RosterEntry
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Community-maintained roster of specific numbered locomotives, shared with
 * the "railfan_spots" upvote pattern this file mirrors. One doc per real
 * locomotive (id = normalized "railroad_number") — repeat sightings update
 * the same doc rather than creating duplicates, so the roster stays a single
 * collaboratively-corrected record per unit.
 */
object FirestoreRosterRepo {

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private const val COLLECTION = "roster"

    fun normalizeId(railroad: String, number: String): String {
        val rr = railroad.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
        val num = number.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
        return "${rr}_$num"
    }

    // ── Live roster flow, best-known units first ──────────────────────────────

    fun getRosterFlow(): Flow<List<RosterEntry>> = callbackFlow {
        val reg = db.collection(COLLECTION)
            .orderBy("upvotes", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(300)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val entries = snap?.documents?.mapNotNull { doc ->
                    try {
                        RosterEntry(
                            id = doc.id,
                            railroad = doc.getString("railroad") ?: "",
                            number = doc.getString("number") ?: "",
                            model = doc.getString("model") ?: "",
                            notes = doc.getString("notes") ?: "",
                            photoUrl = doc.getString("photoUrl"),
                            submittedBy = doc.getString("submittedBy") ?: "Railfan",
                            submittedMs = doc.getLong("submittedMs") ?: 0L,
                            lastSeenMs = doc.getLong("lastSeenMs") ?: 0L,
                            upvotes = (doc.getLong("upvotes") ?: 0L).toInt()
                        )
                    } catch (_: Exception) { null }
                } ?: emptyList()
                trySend(entries)
            }
        awaitClose { reg.remove() }
    }

    // ── Submit / correct a roster entry ───────────────────────────────────────

    suspend fun submitRosterEntry(
        railroad: String,
        number: String,
        model: String,
        notes: String,
        submittedBy: String
    ): String {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) auth.signInAnonymously().await()

        val id = normalizeId(railroad, number)
        val docRef = db.collection(COLLECTION).document(id)
        val existing = docRef.get().await()

        val fields = hashMapOf<String, Any?>(
            "railroad" to railroad.trim(),
            "number" to number.trim(),
            "lastSeenMs" to System.currentTimeMillis()
        )
        // Only overwrite model/notes when the submitter actually provided something —
        // a bare "I saw this unit too" submission shouldn't blank out prior corrections.
        if (model.isNotBlank()) fields["model"] = model.trim()
        if (notes.isNotBlank()) fields["notes"] = notes.trim()
        if (!existing.exists()) {
            fields["submittedBy"] = submittedBy
            fields["submittedMs"] = System.currentTimeMillis()
            fields["upvotes"] = 0L
        }

        docRef.set(fields, SetOptions.merge()).await()
        return id
    }

    // ── Photo (submit-time or added later from the detail view) ──────────────

    suspend fun addRosterPhoto(id: String, photoBytes: ByteArray): String {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) auth.signInAnonymously().await()

        val ref = storage.reference.child("roster/$id/${UUID.randomUUID()}.jpg")
        ref.putBytes(photoBytes).await()
        val url = ref.downloadUrl.await().toString()
        db.collection(COLLECTION).document(id).update("photoUrl", url).await()
        return url
    }

    // ── Upvote ────────────────────────────────────────────────────────────────

    fun upvoteRosterEntry(id: String) {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) return
        db.collection(COLLECTION).document(id)
            .update("upvotes", FieldValue.increment(1))
    }
}
