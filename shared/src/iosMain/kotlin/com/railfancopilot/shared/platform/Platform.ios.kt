package com.railfancopilot.shared.platform

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create

actual fun logDebug(tag: String, message: String) {
    println("D/$tag: $message")
}

actual fun logError(tag: String, message: String, throwable: Throwable?) {
    println("E/$tag: $message${throwable?.let { " — ${it.message}" } ?: ""}")
}

actual fun logWarn(tag: String, message: String) {
    println("W/$tag: $message")
}

actual fun createHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(Darwin) { block() }

// NOT VERIFIED — could not be compiled or run on this (Windows) machine.
// Standard Kotlin/Native ByteArray -> NSData bridging pattern (pin the byte
// array, hand its address + length to NSData.create), wrapped in GitLive's
// Data(NSData) actual constructor. Double-check this builds and uploads a
// real photo correctly the first time this is compiled on the Mac.
@OptIn(ExperimentalForeignApi::class)
actual fun dataOf(bytes: ByteArray): dev.gitlive.firebase.storage.Data {
    val nsData = if (bytes.isEmpty()) {
        NSData()
    } else {
        bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
    }
    return dev.gitlive.firebase.storage.Data(nsData)
}
