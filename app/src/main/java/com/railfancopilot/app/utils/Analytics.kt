package com.railfancopilot.app.utils

import android.content.Context
import androidx.core.os.bundleOf
import com.google.firebase.analytics.FirebaseAnalytics

object Analytics {
    private var fa: FirebaseAnalytics? = null

    fun init(context: Context) {
        fa = FirebaseAnalytics.getInstance(context)
    }

    fun setProStatus(isPro: Boolean) {
        fa?.setUserProperty("subscription_status", if (isPro) "pro" else "free")
    }

    // ── Screen views ─────────────────────────────────────────────────────────
    fun screenView(name: String) {
        fa?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundleOf(
            FirebaseAnalytics.Param.SCREEN_NAME to name
        ))
    }

    // ── Map ──────────────────────────────────────────────────────────────────
    fun trainSelected(trainId: String, railroad: String) {
        fa?.logEvent("train_selected", bundleOf(
            "train_id" to trainId,
            "railroad" to railroad
        ))
    }

    // ── Scanner ──────────────────────────────────────────────────────────────
    fun scanStarted() {
        fa?.logEvent("scan_started", null)
    }

    fun scanResult(railroad: String) {
        fa?.logEvent("scan_result", bundleOf("railroad" to railroad))
    }

    // ── Decoder ──────────────────────────────────────────────────────────────
    fun decoderUsed(reportingMark: String) {
        fa?.logEvent("decoder_used", bundleOf("reporting_mark" to reportingMark))
    }

    // ── Photo ────────────────────────────────────────────────────────────────
    fun photoTaken() {
        fa?.logEvent("photo_taken", null)
    }

    fun photoShared() {
        fa?.logEvent("photo_shared", null)
    }

    // ── Community / Spots ────────────────────────────────────────────────────
    fun sightingPosted() {
        fa?.logEvent("sighting_posted", null)
    }

    fun communityFeedViewed() {
        fa?.logEvent("community_feed_viewed", null)
    }

    // ── Spots ────────────────────────────────────────────────────────────────
    fun spotSaved(name: String) {
        fa?.logEvent("spot_saved", bundleOf("spot_name" to name))
    }

    // ── Watchlist ────────────────────────────────────────────────────────────
    fun watchlistItemAdded(railroad: String) {
        fa?.logEvent("watchlist_item_added", bundleOf("railroad" to railroad))
    }

    // ── Webcams ──────────────────────────────────────────────────────────────
    fun webcamViewed(camName: String) {
        fa?.logEvent("webcam_viewed", bundleOf("cam_name" to camName))
    }

    // ── Encyclopedia ─────────────────────────────────────────────────────────
    fun encyclopediaViewed(entry: String) {
        fa?.logEvent("encyclopedia_viewed", bundleOf("entry" to entry))
    }

    // ── Trips ────────────────────────────────────────────────────────────────
    fun tripCreated() {
        fa?.logEvent("trip_created", null)
    }

    // ── Upgrade ──────────────────────────────────────────────────────────────
    fun upgradeScreenViewed() {
        fa?.logEvent("upgrade_screen_viewed", null)
    }

    fun upgradePurchased() {
        fa?.logEvent("upgrade_purchased", null)
    }

    // ── Alerts ───────────────────────────────────────────────────────────────
    fun alertViewed(alertId: String) {
        fa?.logEvent("alert_viewed", bundleOf("alert_id" to alertId))
    }
}
