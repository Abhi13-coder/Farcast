plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.farcast"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.farcast"
        minSdk = 29          // Android 10, per Section 6's compatibility floor
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            // armeabi-v7a explicitly included per Section 6 — don't let a
            // dependency silently restrict this to arm64 only. Verify EVERY
            // native dependency below (onnxruntime-android, netcipher's
            // bundled bits, nanohttpd is pure Java so it's fine) actually
            // ships an armeabi-v7a artifact before shipping to ARM32 devices —
            // several flagged throughout this build still need that check.
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
    }

    buildFeatures { compose = true }

    // RE/EMU/MEDIA LAB (see src/main/cpp/RE_EMU_LAB.md for the full story).
    // Commented out rather than active: the capstone/keystone/unicorn git
    // submodules this CMakeLists.txt expects aren't checked out yet, so
    // leaving this active would just fail the build for everyone until that
    // follow-up work is done. Uncomment once the submodules exist.
    // externalNativeBuild {
    //     cmake {
    //         path = file("src/main/cpp/CMakeLists.txt")
    //         version = "3.22.1"
    //     }
    // }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources {
            // Room/Rhino/OkHttp all ship META-INF license/notice files that
            // collide across dependencies — exclude rather than let the
            // build fail on the first duplicate it hits.
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
        }
    }
}

dependencies {
    // Compose
    implementation(platform(libs.compose.bom))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Core / networking / data (DarkMixer, features/, privacy/)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.netcipher)

    // Case DB + Moment Finder + plugin registry
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // SecureKeyVault
    implementation(libs.security.crypto)

    // YOLO / Moment Finder
    implementation(libs.onnxruntime.android)

    // MCP server
    implementation(libs.nanohttpd)

    // SCRIPT plugins
    implementation(libs.rhino)

    // Location Picker — osmdroid's Configuration needs androidx.preference's
    // PreferenceManager (its own docs use this, not the deprecated android.preference one).
    implementation(libs.osmdroid)
    implementation("androidx.preference:preference-ktx:1.2.1")

    // Unit tests
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.kotlinx.coroutines.core) {
        // coroutines-test pulls in the same coroutines version; kept separate
        // below rather than assumed transitively.
    }
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
