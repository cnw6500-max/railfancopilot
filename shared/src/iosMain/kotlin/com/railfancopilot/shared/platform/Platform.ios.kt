package com.railfancopilot.shared.platform

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin

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
