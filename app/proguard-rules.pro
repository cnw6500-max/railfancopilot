# ── App data models (Gson serialization — field names must not be renamed) ────
-keep class com.railfancopilot.app.data.** { *; }
-keepclassmembers class com.railfancopilot.app.data.** { *; }

# ── Repository internal Gson POJOs (private data classes used for JSON parsing) ─
-keep class com.railfancopilot.app.** { *; }
-keepclassmembers class com.railfancopilot.app.** {
    <fields>;
}

# ── BuildConfig (API keys must survive shrinking) ─────────────────────────────
-keep class com.railfancopilot.app.BuildConfig { *; }

# ── Gson ──────────────────────────────────────────────────────────────────────
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ── Retrofit / OkHttp ────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# ── Coroutines ────────────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ── Room ──────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }

# ── Google Maps SDK ───────────────────────────────────────────────────────────
-keep class com.google.android.gms.maps.** { *; }
-keep interface com.google.android.gms.maps.** { *; }
-keep class com.google.maps.android.** { *; }
-dontwarn com.google.android.gms.maps.**
-dontwarn com.google.maps.android.**

# ── Google Play Services (location, maps base) ────────────────────────────────
-keep class com.google.android.gms.common.** { *; }
-keep class com.google.android.gms.location.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-dontwarn com.google.android.gms.**

# ── Utils ─────────────────────────────────────────────────────────────────────
-keep class com.railfancopilot.app.utils.** { *; }
