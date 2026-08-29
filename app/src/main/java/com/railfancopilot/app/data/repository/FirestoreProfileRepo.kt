package com.railfancopilot.app.data.repository

import com.railfancopilot.app.data.models.FollowEntry
import com.railfancopilot.app.data.models.UserProfile
import com.railfancopilot.app.data.models.UsernameClaimResult
import com.railfancopilot.shared.repository.SharedProfileRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Thin Android-side wrapper around [SharedProfileRepo] (the real, ported-to-
 * :shared implementation — see that file for the actual Firestore logic).
 * Kept as a separate object, with the same function signatures the rest of
 * the app already calls, so nothing in RailFanViewModel/ProfileScreen/
 * SettingsScreen had to change when this moved to the shared module.
 */
object FirestoreProfileRepo {

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun isValidUsernameFormat(username: String): Boolean =
        SharedProfileRepo.isValidUsernameFormat(username)

    fun getProfileFlow(uid: String): Flow<UserProfile?> =
        SharedProfileRepo.getProfileFlow(uid)

    suspend fun isUsernameAvailable(rawUsername: String, currentUid: String): Boolean =
        SharedProfileRepo.isUsernameAvailable(rawUsername, currentUid)

    suspend fun claimUsername(uid: String, rawUsername: String, displayName: String): UsernameClaimResult =
        SharedProfileRepo.claimUsername(uid, rawUsername, displayName)

    suspend fun followUser(currentUid: String, myUsername: String, myDisplayName: String, target: UserProfile) =
        SharedProfileRepo.followUser(currentUid, myUsername, myDisplayName, target)

    suspend fun unfollowUser(currentUid: String, targetUid: String) =
        SharedProfileRepo.unfollowUser(currentUid, targetUid)

    fun getFollowingFlow(uid: String): Flow<List<FollowEntry>> =
        SharedProfileRepo.getFollowingFlow(uid)

    fun getFollowersFlow(uid: String): Flow<List<FollowEntry>> =
        SharedProfileRepo.getFollowersFlow(uid)

    // Fire-and-forget, matching the original non-suspend call sites in
    // RailFanViewModel (setNearbyAlertsEnabled etc. aren't inside a launch{}).
    fun updateNearbyAlertPrefs(uid: String, enabled: Boolean, radiusMiles: Double, railroads: List<String>) {
        repoScope.launch { SharedProfileRepo.updateNearbyAlertPrefs(uid, enabled, radiusMiles, railroads) }
    }

    fun updateLastKnownLocation(uid: String, lat: Double, lon: Double) {
        repoScope.launch { SharedProfileRepo.updateLastKnownLocation(uid, lat, lon) }
    }
}
