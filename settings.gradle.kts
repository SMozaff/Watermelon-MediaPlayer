pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // java-lame (media-tools' pure-Java MP3 encoder) isn't published to Maven Central --
        // confirmed directly from the project's own README this session, after an earlier,
        // wrong guess (com.cloudburst:java-lame) failed in a real CI build. JitPack builds
        // public GitHub repos on demand and serves them as Maven artifacts; this is the
        // correct, verified mechanism for this specific library. Coordinates:
        // com.github.nwaldispuehl:java-lame:v3.98.4 (JitPack's group:artifact:tag convention,
        // confirmed against the repo's actual GitHub Releases page).
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "watermelon-mediaplayer"

include(
    ":common-interfaces",
    ":playback-engine",
    ":library-storage",
    ":subtitle-engine",
    ":media-tools",
    ":ui-presentation",
    ":app",
    ":benchmarks"
)
