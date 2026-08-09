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

// :core = pure-Kotlin logic ported test-for-test from iOS. :app = Android UI (Compose + MapLibre)
// that consumes :core.
include(":core")
include(":app")
