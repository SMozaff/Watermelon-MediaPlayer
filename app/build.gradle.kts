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
        // This is the first distributable release. Subsequent public releases must increase it.
        versionCode = 1
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

    // Signing inputs are deliberately read only from Gradle properties. No keystore path,
    // password, or alias is stored in version control. When any input is absent, the release
    // variant remains unsigned so ordinary CI can still exercise R8/resource shrinking without
    // granting pull-request jobs access to release credentials.
    val releaseSigningProperties = listOf(
        "RELEASE_STORE_FILE",
        "RELEASE_STORE_PASSWORD",
        "RELEASE_KEY_ALIAS",
        "RELEASE_KEY_PASSWORD"
    )
    val hasReleaseSigningProperties = releaseSigningProperties.all(project::hasProperty)

    if (hasReleaseSigningProperties) {
        signingConfigs {
            create("release") {
                storeFile = file(project.property("RELEASE_STORE_FILE") as String)
                storePassword = project.property("RELEASE_STORE_PASSWORD") as String
                keyAlias = project.property("RELEASE_KEY_ALIAS") as String
                keyPassword = project.property("RELEASE_KEY_PASSWORD") as String
            }
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
            if (hasReleaseSigningProperties) {
                signingConfig = signingConfigs.getByName("release")
            }
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
