package com.railfancopilot.shared.repository

import com.railfancopilot.shared.models.FollowEntry
import com.railfancopilot.shared.models.FollowEntryPayload
import com.railfancopilot.shared.models.UserProfile
import com.railfancopilot.shared.models.UserProfileFieldsPayload
import com.railfancopilot.shared.models.UsernameClaimResult
import com.railfancopilot.shared.models.UsernameIndexPayload
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

/**
 * KMP port of the Android app's FirestoreProfileRepo — same Firestore schema
 * (users/{uid}, usernames/{normalized}, users/{uid}/following, .../followers),
 * same transaction/batch semantics, rewritten against the GitLive Firebase
 * Kotlin SDK so this logic can run on iOS as well as Android from one
 * implementation. Ported 2026-07-13 — Android-compile-verified only; the iOS
 * target could not be built or tested on this (Windows) machine.
 */
object SharedProfileRepo {

    private val db get() = Firebase.firestore
    private const val USERS_COLLECTION = "users"
    private const val USERNAMES_COLLECTION = "usernames"
    private const val USERNAME_TAKEN_SENTINEL = "USERNAME_TAKEN"

    private val USERNAME_REGEX = Regex("^[a-z0-9_]{3,20}$")

    private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

    fun isValidUsernameFormat(username: String): Boolean =
        USERNAME_REGEX.matches(username.trim().lowercase())

    private inline fun <reified T> DocumentSnapshot.getOrNull(field: String): T? =
        if (contains(field)) get<T>(field) else null

    // ── Live profile listener ─────────────────────────────────────────────────

    fun getProfileFlow(uid: String): Flow<UserProfile?> =
        db.collection(USERS_COLLECTION).document(uid).snapshots()
            .map { snap ->
                if (!snap.exists) return@map null
                UserProfile(
                    uid = uid,
                    username = snap.getOrNull<String>("username") ?: "",
                    displayName = snap.getOrNull<String>("displayName") ?: "",
                    joinedMs = snap.getOrNull<Long>("joinedMs") ?: 0L,
                    sightingCount = (snap.getOrNull<Long>("sightingCount") ?: 0L).toInt(),
                    reporterScore = (snap.getOrNull<Long>("reporterScore") ?: 0L).toInt(),
                    followerCount = (snap.getOrNull<Long>("followerCount") ?: 0L).toInt(),
                    followingCount = (snap.getOrNull<Long>("followingCount") ?: 0L).toInt()
                )
            }
            .catch { emit(null) }

    // ── Availability check ─────────────────────────────────────────────────────

    suspend fun isUsernameAvailable(rawUsername: String, currentUid: String): Boolean {
        val normalized = rawUsername.trim().lowercase()
        if (!isValidUsernameFormat(normalized)) return false
        val doc = db.collection(USERNAMES_COLLECTION).document(normalized).get()
        return !doc.exists || doc.getOrNull<String>("uid") == currentUid
    }

    // ── Claim / rename ─────────────────────────────────────────────────────────

    suspend fun claimUsername(uid: String, rawUsername: String, displayName: String): UsernameClaimResult {
        val normalized = rawUsername.trim().lowercase()
        if (!isValidUsernameFormat(normalized)) return UsernameClaimResult.InvalidFormat

        return try {
            db.runTransaction {
                val usernameRef = db.collection(USERNAMES_COLLECTION).document(normalized)
                val userRef = db.collection(USERS_COLLECTION).document(uid)

                // All reads before any writes, per Firestore transaction rules.
                val usernameDoc = get(usernameRef)
                val userDoc = get(userRef)

                if (usernameDoc.exists && usernameDoc.getOrNull<String>("uid") != uid) {
                    throw IllegalStateException(USERNAME_TAKEN_SENTINEL)
                }

                val oldUsername = userDoc.getOrNull<String>("username")
                if (oldUsername != null && oldUsername != normalized) {
                    val oldRef = db.collection(USERNAMES_COLLECTION).document(oldUsername)
                    val oldDoc = get(oldRef)
                    if (oldDoc.exists && oldDoc.getOrNull<String>("uid") == uid) {
                        delete(oldRef)
                    }
                }

                set(usernameRef, UsernameIndexPayload(uid), merge = false)
                set(
                    userRef,
                    UserProfileFieldsPayload(
                        username = normalized,
                        displayName = displayName.trim().ifBlank { normalized },
                        joinedMs = userDoc.getOrNull<Long>("joinedMs") ?: nowMs(),
                        usernameUpdatedMs = nowMs()
                    ),
                    merge = true
                )
            }
            UsernameClaimResult.Success
        } catch (e: Exception) {
            if (e.message == USERNAME_TAKEN_SENTINEL) {
                UsernameClaimResult.Taken
            } else {
                UsernameClaimResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    // ── Follow / unfollow ───────────────────────────────────────────────────────

    suspend fun followUser(currentUid: String, myUsername: String, myDisplayName: String, target: UserProfile) {
        val now = nowMs()
        val batch = db.batch()

        val followRef = db.collection(USERS_COLLECTION).document(currentUid)
            .collection("following").document(target.uid)
        batch.set(followRef, FollowEntryPayload(target.username, target.displayName, now), merge = false)

        // Mirrored under the target's own doc so their followers list is a
        // single-collection read, same as the Android FirestoreProfileRepo.
        val followerRef = db.collection(USERS_COLLECTION).document(target.uid)
            .collection("followers").document(currentUid)
        batch.set(followerRef, FollowEntryPayload(myUsername, myDisplayName, now), merge = false)

        @Suppress("DEPRECATION")
        batch.update(db.collection(USERS_COLLECTION).document(currentUid), "followingCount" to FieldValue.increment(1))
        @Suppress("DEPRECATION")
        batch.update(db.collection(USERS_COLLECTION).document(target.uid), "followerCount" to FieldValue.increment(1))

        batch.commit()
    }

    suspend fun unfollowUser(currentUid: String, targetUid: String) {
        val batch = db.batch()
        batch.delete(db.collection(USERS_COLLECTION).document(currentUid).collection("following").document(targetUid))
        batch.delete(db.collection(USERS_COLLECTION).document(targetUid).collection("followers").document(currentUid))
        @Suppress("DEPRECATION")
        batch.update(db.collection(USERS_COLLECTION).document(currentUid), "followingCount" to FieldValue.increment(-1))
        @Suppress("DEPRECATION")
        batch.update(db.collection(USERS_COLLECTION).document(targetUid), "followerCount" to FieldValue.increment(-1))
        batch.commit()
    }

    private fun parseFollowEntry(doc: DocumentSnapshot): FollowEntry? = try {
        FollowEntry(
            uid = doc.id,
            username = doc.getOrNull<String>("username") ?: "",
            displayName = doc.getOrNull<String>("displayName") ?: "",
            followedMs = doc.getOrNull<Long>("followedMs") ?: 0L
        )
    } catch (_: Exception) {
        null
    }

    fun getFollowingFlow(uid: String): Flow<List<FollowEntry>> =
        db.collection(USERS_COLLECTION).document(uid).collection("following")
            .orderBy("followedMs", Direction.DESCENDING)
            .snapshots()
            .map { qs -> qs.documents.mapNotNull(::parseFollowEntry) }
            .catch { emit(emptyList()) }

    fun getFollowersFlow(uid: String): Flow<List<FollowEntry>> =
        db.collection(USERS_COLLECTION).document(uid).collection("followers")
            .orderBy("followedMs", Direction.DESCENDING)
            .snapshots()
            .map { qs -> qs.documents.mapNotNull(::parseFollowEntry) }
            .catch { emit(emptyList()) }

    // ── Nearby-sighting alert prefs + location (read server-side by the
    //    nearbySightingAlert Cloud Function) ───────────────────────────────────

    suspend fun updateNearbyAlertPrefs(uid: String, enabled: Boolean, radiusMiles: Double, railroads: List<String>) {
        @Suppress("DEPRECATION")
        db.collection(USERS_COLLECTION).document(uid).update(
            "nearbyAlertsEnabled" to enabled,
            "nearbyAlertRadiusMiles" to radiusMiles,
            "nearbyAlertRailroads" to railroads
        )
    }

    suspend fun updateLastKnownLocation(uid: String, lat: Double, lon: Double) {
        @Suppress("DEPRECATION")
        db.collection(USERS_COLLECTION).document(uid).update(
            "lastKnownLat" to lat,
            "lastKnownLon" to lon
        )
    }
}
