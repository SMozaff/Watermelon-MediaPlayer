plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.watermelon.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.watermelon.mediaplayer"
        minSdk = 23
        targetSdk = 35
        // First version bump off the initial 1 / "1.0" placeholders (see remediation plan
        // item 2) -- still a debug-signed build until real release signing is provisioned.
        versionCode = 2
        versionName = "1.0.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // MediaController / SessionToken in MainActivity are @UnstableApi.
        freeCompilerArgs += "-opt-in=androidx.media3.common.util.UnstableApi"
    }
    // Reads from Gradle properties (gradle.properties, -P flags, or ~/.gradle/gradle.properties)
    // rather than hardcoding any credential in source control. None of these properties are
    // defined in this repo -- a human must supply them locally or as CI secrets before
    // assembleRelease can actually produce a signed APK; until then the property lookups
    // below resolve to null and Gradle will fail signing at build time with a clear error,
    // which is the correct behavior rather than silently falling back to debug signing.
    signingConfigs {
        create("release") {
            val storeFilePath = project.findProperty("RELEASE_STORE_FILE") as String?
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
            }
            storePassword = project.findProperty("RELEASE_STORE_PASSWORD") as String?
            keyAlias = project.findProperty("RELEASE_KEY_ALIAS") as String?
            keyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as String?
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation(project(":ui-presentation"))
    implementation(project(":playback-engine"))
    implementation(project(":library-storage"))
    implementation(project(":subtitle-engine"))
    implementation(project(":media-tools"))
    implementation(project(":common-interfaces"))

    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.kotlinx.coroutines.android)
}
