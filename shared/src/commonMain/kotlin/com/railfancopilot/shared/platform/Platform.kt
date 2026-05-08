package com.railfancopilot.shared.platform

import io.ktor.client.HttpClient

// ── Logging ───────────────────────────────────────────────────────────────────

expect fun logDebug(tag: String, message: String)
expect fun logError(tag: String, message: String, throwable: Throwable? = null)
expect fun logWarn(tag: String, message: String)

// ── HTTP client engine ────────────────────────────────────────────────────────
// Each platform provides its own engine (Android → OkHttp, iOS → Darwin).

expect fun createHttpClient(block: io.ktor.client.HttpClientConfig<*>.() -> Unit): HttpClient
