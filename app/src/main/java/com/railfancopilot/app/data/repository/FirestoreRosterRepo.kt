package com.railfancopilot.app.data.repository

import com.railfancopilot.app.data.models.RosterEntry
import com.railfancopilot.shared.repository.SharedRosterRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Thin Android-side wrapper around [SharedRosterRepo] (the real, ported-to-
 * :shared implementation — see that file for the actual Firestore/Storage
 * logic). Kept as a separate object, with the same function signatures the
 * rest of the app already calls, so nothing in RailFanViewModel/
 * EncyclopediaScreen/PhotoScreen had to change when this moved to the shared
 * module.
 */
object FirestoreRosterRepo {

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun normalizeId(railroad: String, number: String): String =
        SharedRosterRepo.normalizeId(railroad, number)

    fun getRosterFlow(): Flow<List<RosterEntry>> =
        SharedRosterRepo.getRosterFlow()

    suspend fun submitRosterEntry(
        railroad: String,
        number: String,
        model: String,
        notes: String,
        submittedBy: String
    ): String = SharedRosterRepo.submitRosterEntry(railroad, number, model, notes, submittedBy)

    suspend fun addRosterPhoto(id: String, photoBytes: ByteArray): String =
        SharedRosterRepo.addRosterPhoto(id, photoBytes)

    // Fire-and-forget, matching the original non-suspend call site in
    // RailFanViewModel.upvoteRosterEntry (not inside a launch{}).
    fun upvoteRosterEntry(id: String) {
        repoScope.launch { SharedRosterRepo.upvoteRosterEntry(id) }
    }
}
