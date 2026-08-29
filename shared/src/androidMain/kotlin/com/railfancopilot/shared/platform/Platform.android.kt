package com.railfancopilot.shared.platform

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.android.Android

actual fun logDebug(tag: String, message: String) {
    Log.d(tag, message)
}

actual fun logError(tag: String, message: String, throwable: Throwable?) {
    if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
}

actual fun logWarn(tag: String, message: String) {
    Log.w(tag, message)
}

actual fun createHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(Android) { block() }

actual fun dataOf(bytes: ByteArray): dev.gitlive.firebase.storage.Data =
    dev.gitlive.firebase.storage.Data(bytes)
