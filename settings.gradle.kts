pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "EVFastRoute"

// Pure-Kotlin logic shared conceptually with the iOS app (ported, test-for-test).
// The Android `:app` module (Compose + MapLibre) is added next.
include(":core")
