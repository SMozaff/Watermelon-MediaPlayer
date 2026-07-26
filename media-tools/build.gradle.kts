plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.watermelon.mediatools"
    compileSdk = 35
    defaultConfig {
        minSdk = 23
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
        // NATIVE BUILD DISABLED: media-tools/src/main/cpp/lame/ has no vendored
        // libmp3lame source yet (see that directory's README.md), so CMake would
        // hard-fail configuration ("does not contain a CMakeLists.txt file") on
        // every CI run -- confirmed by a real build failure. Re-enable the two
        // externalNativeBuild blocks below (this one and the android{} one) once
        // libmp3lame is actually added under cpp/lame/. Mp3Encoder.kt's
        // System.loadLibrary("mp3encoder") will throw UnsatisfiedLinkError at
        // runtime until then -- AudioExtractor is not usable end-to-end without this.
        // externalNativeBuild {
        //     cmake {
        //         cppFlags += ""
        //         arguments += "-DANDROID_STL=c++_shared"
        //     }
        // }
    }
    // externalNativeBuild {
    //     cmake {
    //         path = file("src/main/cpp/CMakeLists.txt")
    //         version = "3.22.1"
    //     }
    // }
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

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
