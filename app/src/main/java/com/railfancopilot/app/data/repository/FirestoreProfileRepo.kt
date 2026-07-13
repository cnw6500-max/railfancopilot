package com.railfancopilot.app.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.railfancopilot.app.data.models.FollowEntry
import com.railfancopilot.app.data.models.UserProfile
import com.railfancopilot.app.data.models.UsernameClaimResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Reads and writes the shared Firestore "users/{uid}" profile document plus
 * the "usernames/{normalized}" collection used as a uniqueness index —
 * standard Firestore pattern: the normalized username IS the document ID,
 * so claiming one is an atomic create-if-absent inside a transaction.
 */
object FirestoreProfileRepo {

    private val db = FirebaseFirestore.getInstance()
    private const val USERS_COLLECTION = "users"
    private const val USERNAMES_COLLECTION = "usernames"
    private const val USERNAME_TAKEN_SENTINEL = "USERNAME_TAKEN"

    private val USERNAME_REGEX = Regex("^[a-z0-9_]{3,20}$")

    fun isValidUsernameFormat(username: String): Boolean =
        USERNAME_REGEX.matches(username.trim().lowercase())

    // ── Live profile listener ─────────────────────────────────────────────────

    fun getProfileFlow(uid: String): Flow<UserProfile?> = callbackFlow {
        val reg = db.collection(USERS_COLLECTION).document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null || !snap.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val username = snap.getString("username")
                trySend(
                    UserProfile(
                        uid = uid,
                        username = username ?: "",
                        displayName = snap.getString("displayName") ?: "",
                        joinedMs = snap.getLong("joinedMs") ?: 0L,
                        sightingCount = (snap.getLong("sightingCount") ?: 0L).toInt(),
                        reporterScore = (snap.getLong("reporterScore") ?: 0L).toInt(),
                        followerCount = (snap.getLong("followerCount") ?: 0L).toInt(),
                        followingCount = (snap.getLong("followingCount") ?: 0L).toInt()
                    )
                )
            }
        awaitClose { reg.remove() }
    }

    // ── Availability check (for live "is this taken" feedback while typing) ──

    suspend fun isUsernameAvailable(rawUsername: String, currentUid: String): Boolean {
        val normalized = rawUsername.trim().lowercase()
        if (!isValidUsernameFormat(normalized)) return false
        val doc = db.collection(USERNAMES_COLLECTION).document(normalized).get().await()
        return !doc.exists() || doc.getString("uid") == currentUid
    }

    // ── Claim / rename ─────────────────────────────────────────────────────────

    suspend fun claimUsername(
        uid: String,
        rawUsername: String,
        displayName: String
    ): UsernameClaimResult {
        val normalized = rawUsername.trim().lowercase()
        if (!isValidUsernameFormat(normalized)) return UsernameClaimResult.InvalidFormat

        return try {
            db.runTransaction { txn ->
                val usernameRef = db.collection(USERNAMES_COLLECTION).document(normalized)
                val userRef = db.collection(USERS_COLLECTION).document(uid)

                // All reads must happen before any writes in a Firestore transaction.
                val usernameDoc = txn.get(usernameRef)
                val userDoc = txn.get(userRef)

                if (usernameDoc.exists() && usernameDoc.getString("uid") != uid) {
                    throw IllegalStateException(USERNAME_TAKEN_SENTINEL)
                }

                val oldUsername = userDoc.getString("username")
                val oldRef = if (oldUsername != null && oldUsername != normalized) {
                    db.collection(USERNAMES_COLLECTION).document(oldUsername)
                } else null
                val oldDoc = oldRef?.let { txn.get(it) }

                // Release the previous handle only if it still points to this uid.
                if (oldRef != null && oldDoc != null && oldDoc.exists() && oldDoc.getString("uid") == uid) {
                    txn.delete(oldRef)
                }

                txn.set(usernameRef, hashMapOf("uid" to uid))
                txn.set(
                    userRef,
                    hashMapOf(
                        "username" to normalized,
                        "displayName" to displayName.trim().ifBlank { normalized },
                        "joinedMs" to (userDoc.getLong("joinedMs") ?: System.currentTimeMillis()),
                        "usernameUpdatedMs" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                )
                null
            }.await()
            UsernameClaimResult.Success
        } catch (e: Exception) {
            if (e.message == USERNAME_TAKEN_SENTINEL) {
                UsernameClaimResult.Taken
            } else {
                if (com.railfancopilot.app.BuildConfig.DEBUG)
                    android.util.Log.e("FirestoreProfileRepo", "claimUsername failed: ${e.message}", e)
                UsernameClaimResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    // ── Follow / unfollow ───────────────────────────────────────────────────────

    /**
     * Follower/following counters live on the two users' own `users/{uid}` docs
     * and are adjusted by exactly ±1 per call — mirrors the upvote-increment
     * pattern already trusted elsewhere in firestore.rules, so a non-owner can
     * bump a stranger's followerCount without full write access to their doc.
     */
    suspend fun followUser(currentUid: String, myUsername: String, myDisplayName: String, target: UserProfile) {
        val batch = db.batch()
        val now = System.currentTimeMillis()

        val followRef = db.collection(USERS_COLLECTION).document(currentUid)
            .collection("following").document(target.uid)
        batch.set(
            followRef,
            hashMapOf("username" to target.username, "displayName" to target.displayName, "followedMs" to now)
        )

        // Mirrored under the target's own doc so their followers list can be
        // read without a collectionGroup query across every user's "following".
        val followerRef = db.collection(USERS_COLLECTION).document(target.uid)
            .collection("followers").document(currentUid)
        batch.set(
            followerRef,
            hashMapOf("username" to myUsername, "displayName" to myDisplayName, "followedMs" to now)
        )

        batch.update(db.collection(USERS_COLLECTION).document(currentUid), "followingCount", FieldValue.increment(1))
        batch.update(db.collection(USERS_COLLECTION).document(target.uid), "followerCount", FieldValue.increment(1))
        batch.commit().await()
    }

    suspend fun unfollowUser(currentUid: String, targetUid: String) {
        val batch = db.batch()
        batch.delete(db.collection(USERS_COLLECTION).document(currentUid).collection("following").document(targetUid))
        batch.delete(db.collection(USERS_COLLECTION).document(targetUid).collection("followers").document(currentUid))
        batch.update(db.collection(USERS_COLLECTION).document(currentUid), "followingCount", FieldValue.increment(-1))
        batch.update(db.collection(USERS_COLLECTION).document(targetUid), "followerCount", FieldValue.increment(-1))
        batch.commit().await()
    }

    // ── Nearby-sighting alert prefs + location (read server-side by the
    //    nearbySightingAlert Cloud Function — this is why it's mirrored to
    //    Firestore instead of staying in local DataStore like other alerts) ────

    fun updateNearbyAlertPrefs(uid: String, enabled: Boolean, radiusMiles: Double, railroads: List<String>) {
        db.collection(USERS_COLLECTION).document(uid).set(
            hashMapOf(
                "nearbyAlertsEnabled" to enabled,
                "nearbyAlertRadiusMiles" to radiusMiles,
                "nearbyAlertRailroads" to railroads
            ),
            SetOptions.merge()
        )
    }

    fun updateLastKnownLocation(uid: String, lat: Double, lon: Double) {
        db.collection(USERS_COLLECTION).document(uid).set(
            hashMapOf(
                "lastKnownLat" to lat,
                "lastKnownLon" to lon
            ),
            SetOptions.merge()
        )
    }

    fun getFollowingFlow(uid: String): Flow<List<FollowEntry>> = callbackFlow {
        val reg = db.collection(USERS_COLLECTION).document(uid).collection("following")
            .orderBy("followedMs", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    try {
                        FollowEntry(
                            uid = doc.id,
                            username = doc.getString("username") ?: "",
                            displayName = doc.getString("displayName") ?: "",
                            followedMs = doc.getLong("followedMs") ?: 0L
                        )
                    } catch (_: Exception) { null }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun getFollowersFlow(uid: String): Flow<List<FollowEntry>> = callbackFlow {
        val reg = db.collection(USERS_COLLECTION).document(uid).collection("followers")
            .orderBy("followedMs", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    try {
                        FollowEntry(
                            uid = doc.id,
                            username = doc.getString("username") ?: "",
                            displayName = doc.getString("displayName") ?: "",
                            followedMs = doc.getLong("followedMs") ?: 0L
                        )
                    } catch (_: Exception) { null }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }
}
