package com.railfancopilot.shared.platform

import io.ktor.client.HttpClient

// ── Logging ───────────────────────────────────────────────────────────────────

expect fun logDebug(tag: String, message: String)
expect fun logError(tag: String, message: String, throwable: Throwable? = null)
expect fun logWarn(tag: String, message: String)

// ── HTTP client engine ────────────────────────────────────────────────────────
// Each platform provides its own engine (Android → OkHttp, iOS → Darwin).

expect fun createHttpClient(block: io.ktor.client.HttpClientConfig<*>.() -> Unit): HttpClient

// ── Firebase Storage upload payload ───────────────────────────────────────────
// dev.gitlive.firebase.storage.Data has no common constructor ("every platform
// has its own constructor" per the SDK's own doc comment), so wrapping a
// ByteArray for upload needs a platform bridge. Android side is compile-verified
// (wraps com.google.firebase.storage.Data 1:1). iOS side (ByteArray -> NSData)
// could not be compiled or tested on this machine — verify on the Mac before
// relying on it.
expect fun dataOf(bytes: ByteArray): dev.gitlive.firebase.storage.Data
