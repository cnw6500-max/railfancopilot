import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    // Applied unversioned: this plugin ships inside the Kotlin Gradle Plugin
    // itself (same artifact as kotlin-multiplatform above), so declaring a
    // separate version via the catalog conflicts with the one already on
    // the buildscript classpath.
    id("org.jetbrains.kotlin.native.cocoapods")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    val xcf = XCFramework("shared")
    iosX64 {
        binaries.framework { baseName = "shared"; xcf.add(this) }
    }
    iosArm64 {
        binaries.framework { baseName = "shared"; xcf.add(this) }
    }
    iosSimulatorArm64 {
        binaries.framework { baseName = "shared"; xcf.add(this) }
    }

    // The GitLive Firebase Kotlin SDK's iOS artifacts are Kotlin wrappers around
    // the native Firebase Apple SDK — that native SDK is not a transitive
    // dependency, so it has to be linked in ourselves via CocoaPods, one pod per
    // GitLive module actually used: common -> FirebaseCore, plus firestore/auth/storage.
    cocoapods {
        version = "1.0"
        summary = "Railfan Copilot shared KMP module"
        homepage = "https://railfancopilot.app"
        ios.deploymentTarget = "16.0"

        pod("FirebaseCore")
        pod("FirebaseFirestore")
        pod("FirebaseAuth")
        pod("FirebaseStorage")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.multiplatform.settings)
            implementation(libs.gitlive.firebase.common)
            implementation(libs.gitlive.firebase.firestore)
            implementation(libs.gitlive.firebase.auth)
            implementation(libs.gitlive.firebase.storage)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.android)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

// Configure the Android library extension via the new AGP 9.x DSL interface,
// bypassing the deprecated LibraryExtension function accessor.
extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    namespace = "com.railfancopilot.shared"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
