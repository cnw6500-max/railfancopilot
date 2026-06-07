import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localPropsFile.inputStream().use { localProps.load(it) }
}

android {
    namespace = "com.railfancopilot.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.railfancopilot.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 36
        versionName = "2.6.0"

        val mapsKey = localProps.getProperty("MAPS_API_KEY") ?: ""
        manifestPlaceholders["MAPS_API_KEY"] = mapsKey
    }

    signingConfigs {
        create("release") {
            // Fill these in from local.properties or environment variables — never hard-code credentials.
            // Add to local.properties:
            //   KEYSTORE_FILE=../railfan-release.jks
            //   KEYSTORE_PASSWORD=your_password
            //   KEY_ALIAS=railfan
            //   KEY_PASSWORD=your_key_password
            storeFile     = localProps.getProperty("KEYSTORE_FILE")?.let { rootProject.file(it) }
            storePassword = localProps.getProperty("KEYSTORE_PASSWORD") ?: ""
            keyAlias      = localProps.getProperty("KEY_ALIAS")         ?: ""
            keyPassword   = localProps.getProperty("KEY_PASSWORD")      ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Force fragment to latest to resolve Play Console SDK warning
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.coroutines.android)
    implementation(libs.viewmodel.compose)
    implementation(libs.coil.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.accompanist.permissions)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.gson)
    implementation(libs.play.review)
    implementation(libs.play.billing.ktx)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    // Firebase — Firestore (shared community feed with iOS) + Functions (API proxy)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.storage)
    implementation(libs.coroutines.play.services)
    // GTFS-Realtime vehicle positions are parsed with a built-in zero-dependency
    // protobuf reader (GtfsRtProtoParser) — no external bindings library needed.
    debugImplementation(libs.androidx.ui.tooling)
}

// Workaround: AGP 9+ removed these tasks that Android Studio still references.
tasks.register("unitTestClasses")
tasks.register("androidTestClasses")