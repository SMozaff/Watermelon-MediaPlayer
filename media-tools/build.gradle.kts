plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.watermelon.mediatools"
    compileSdk = 35
    defaultConfig {
        minSdk = 23
    }
    buildFeatures { buildConfig = false }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // Transformer/Effect APIs are annotated @UnstableApi, same rationale as playback-engine.
        freeCompilerArgs += "-opt-in=androidx.media3.common.util.UnstableApi"
    }
}

dependencies {
    implementation(project(":common-interfaces"))
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.muxer)
    implementation(libs.androidx.media3.common)
    implementation(libs.kotlinx.coroutines.android)
    // Pure-Java MP3 encoder (LGPL, port of LAME) -- no NDK/JNI needed, replacing the
    // previous libmp3lame-via-JNI plan that was blocked on this sandbox having no
    // network access to fetch and vendor libmp3lame's C source. Coordinates found via
    // web search (mvnrepository.com/artifact/com.cloudburst/java-lame/3.98.4) --
    // NOT independently verified by fetching the POM directly (network-blocked here
    // too). Double check this resolves correctly on your first real build; if it
    // doesn't, the underlying project is https://github.com/nwaldispuehl/java-lame
    // and the jar can be built from source or grabbed from its GitHub Releases instead.
    implementation(libs.java.lame)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
