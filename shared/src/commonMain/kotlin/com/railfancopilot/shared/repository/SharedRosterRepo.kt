package com.railfancopilot.shared.repository

import com.railfancopilot.shared.models.RosterEntry
import com.railfancopilot.shared.models.RosterUpsertPayload
import com.railfancopilot.shared.platform.dataOf
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.storage.storage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * KMP port of the Android app's FirestoreRosterRepo — same "roster" collection
 * and merge-upsert-by-normalized-id scheme, rewritten against the GitLive
 * Firebase Kotlin SDK. Ported 2026-07-13 — Android-compile-verified only;
 * the iOS target (including the photo-upload path, which depends on the
 * unverified ByteArray->NSData bridge in Platform.ios.kt) could not be built
 * or tested on this (Windows) machine.
 */
object SharedRosterRepo {

    private val db get() = Firebase.firestore
    private const val COLLECTION = "roster"

    private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

    fun normalizeId(railroad: String, number: String): String {
        val rr = railroad.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
        val num = number.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
        return "${rr}_$num"
    }

    private inline fun <reified T> DocumentSnapshot.getOrNull(field: String): T? =
        if (contains(field)) get<T>(field) else null

    private fun parseRosterEntry(doc: DocumentSnapshot): RosterEntry? = try {
        RosterEntry(
            id = doc.id,
            railroad = doc.getOrNull<String>("railroad") ?: "",
            number = doc.getOrNull<String>("number") ?: "",
            model = doc.getOrNull<String>("model") ?: "",
            notes = doc.getOrNull<String>("notes") ?: "",
            photoUrl = doc.getOrNull<String>("photoUrl"),
            submittedBy = doc.getOrNull<String>("submittedBy") ?: "Railfan",
            submittedMs = doc.getOrNull<Long>("submittedMs") ?: 0L,
            lastSeenMs = doc.getOrNull<Long>("lastSeenMs") ?: 0L,
            upvotes = (doc.getOrNull<Long>("upvotes") ?: 0L).toInt()
        )
    } catch (_: Exception) {
        null
    }

    // ── Live roster flow, best-known units first ──────────────────────────────

    fun getRosterFlow(): Flow<List<RosterEntry>> =
        db.collection(COLLECTION)
            .orderBy("upvotes", Direction.DESCENDING)
            .limit(300)
            .snapshots()
            .map { qs -> qs.documents.mapNotNull(::parseRosterEntry) }
            .catch { emit(emptyList()) }

    // ── Submit / correct a roster entry ───────────────────────────────────────

    suspend fun submitRosterEntry(
        railroad: String,
        number: String,
        model: String,
        notes: String,
        submittedBy: String
    ): String {
        if (Firebase.auth.currentUser == null) Firebase.auth.signInAnonymously()

        val id = normalizeId(railroad, number)
        val docRef = db.collection(COLLECTION).document(id)
        val existing = docRef.get()

        docRef.set(
            RosterUpsertPayload(
                railroad = railroad.trim(),
                number = number.trim(),
                lastSeenMs = nowMs(),
                model = model.trim().ifBlank { null },
                notes = notes.trim().ifBlank { null },
                submittedBy = if (!existing.exists) submittedBy else null,
                submittedMs = if (!existing.exists) nowMs() else null,
                upvotes = if (!existing.exists) 0L else null
            ),
            merge = true
        ) { encodeDefaults = false }

        return id
    }

    // ── Photo (submit-time or added later from the detail view) ──────────────

    @OptIn(ExperimentalUuidApi::class)
    suspend fun addRosterPhoto(id: String, photoBytes: ByteArray): String {
        if (Firebase.auth.currentUser == null) Firebase.auth.signInAnonymously()

        val ref = Firebase.storage.reference("roster/$id/${Uuid.random()}.jpg")
        ref.putData(dataOf(photoBytes))
        val url = ref.getDownloadUrl()

        @Suppress("DEPRECATION")
        db.collection(COLLECTION).document(id).update("photoUrl" to url)
        return url
    }

    // ── Upvote ────────────────────────────────────────────────────────────────

    suspend fun upvoteRosterEntry(id: String) {
        if (Firebase.auth.currentUser == null) return
        @Suppress("DEPRECATION")
        db.collection(COLLECTION).document(id).update("upvotes" to FieldValue.increment(1))
    }
}
