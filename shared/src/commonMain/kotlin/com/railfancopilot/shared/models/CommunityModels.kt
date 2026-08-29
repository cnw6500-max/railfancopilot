package com.railfancopilot.shared.models

import kotlinx.serialization.Serializable

// Models for the crowdsourced-community features (profiles, follows, roster,
// nearby alerts) — ported from the Android-only versions in the :app module's
// Models.kt so the same Firestore logic can run on iOS too. These are read
// field-by-field via DocumentSnapshot.get<T>(field) (not full-document
// deserialization), so plain `data class` is enough for reads. Write-payload
// classes below are @Serializable because GitLive's .set() goes through
// kotlinx.serialization.

// ── Read-side domain models (mirror the Android app's data classes) ──────────

data class UserProfile(
    val uid: String,
    val username: String,
    val displayName: String,
    val joinedMs: Long,
    val sightingCount: Int = 0,
    val reporterScore: Int = 0,
    val followerCount: Int = 0,
    val followingCount: Int = 0
)

sealed class UsernameClaimResult {
    data object Success : UsernameClaimResult()
    data object Taken : UsernameClaimResult()
    data object InvalidFormat : UsernameClaimResult()
    data class Error(val message: String) : UsernameClaimResult()
}

data class FollowEntry(
    val uid: String,
    val username: String,
    val displayName: String,
    val followedMs: Long
)

data class RosterEntry(
    val id: String,
    val railroad: String,
    val number: String,
    val model: String = "",
    val notes: String = "",
    val photoUrl: String? = null,
    val submittedBy: String = "Railfan",
    val submittedMs: Long = 0L,
    val lastSeenMs: Long = 0L,
    val upvotes: Int = 0
)

// ── Write payloads (kotlinx.serialization needs a concrete serializable type
//    for Transaction/WriteBatch .set() — these are never read back, only used
//    as the argument to .set(ref, payload, merge = true)) ────────────────────

@Serializable
internal data class UsernameIndexPayload(val uid: String)

@Serializable
internal data class UserProfileFieldsPayload(
    val username: String,
    val displayName: String,
    val joinedMs: Long,
    val usernameUpdatedMs: Long
)

@Serializable
internal data class FollowEntryPayload(
    val username: String,
    val displayName: String,
    val followedMs: Long
)

// Nullable fields default to null and are omitted from the write when
// encodeDefaults = false is passed to .set() — that's what gives this the
// same "only write what's actually provided" upsert semantics as the
// Android-only version's conditional hashMapOf(...) construction.
@Serializable
internal data class RosterUpsertPayload(
    val railroad: String,
    val number: String,
    val lastSeenMs: Long,
    val model: String? = null,
    val notes: String? = null,
    val submittedBy: String? = null,
    val submittedMs: Long? = null,
    val upvotes: Long? = null
)
