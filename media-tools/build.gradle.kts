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
    // network access to fetch and vendor libmp3lame's C source.
    //
    // CORRECTED after a real CI build failure: this was originally declared as
    // com.cloudburst:java-lame:3.98.4 (from an unreliable web search hit that turned out
    // not to exist on Maven Central -- confirmed by "Could not find
    // com.cloudburst:java-lame:3.98.4" in a real Gradle sync). The project
    // (github.com/nwaldispuehl/java-lame) is NOT published to Maven Central at all --
    // confirmed directly from its own README, which only documents building it into a
    // local ~/.m2 repo. Now served via JitPack instead (see settings.gradle.kts for the
    // repository declaration), using JitPack's group:artifact:tag convention against the
    // project's real v3.98.4 GitHub Release.
    //
    // FALLBACK if JitPack fails to build this on first resolve (it builds repos live,
    // rather than serving a pre-existing artifact, so there's some risk here too -- not
    // eliminated, just a real, verified mechanism rather than a guess): download the
    // pre-built jar directly from https://github.com/nwaldispuehl/java-lame/releases
    // (asset attached to the v3.98.4 release) and drop it in as a local file dependency
    // instead, e.g. `implementation(files("libs/java-lame-3.98.4.jar"))`.
    implementation(libs.java.lame)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
