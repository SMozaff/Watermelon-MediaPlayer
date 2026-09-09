plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.watermelon.playback"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
        // targetSdk lives on the app module for libraries; kept for parity with the blueprint.
    }
    buildFeatures { buildConfig = false }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Built-in Kotlin inherits JVM 17 from android.compileOptions. Keep the Media3
// unstable-API opt-in at the compiler level rather than as a raw freeCompilerArg.
kotlin {
    compilerOptions {
        optIn.add("androidx.media3.common.util.UnstableApi")
    }
}

dependencies {
    implementation(project(":common-interfaces"))
    implementation(libs.androidx.core)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
