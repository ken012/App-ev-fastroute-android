// Plugin versions are declared once here (apply false) and applied without versions in each
// module — the required pattern so the shared Kotlin Gradle plugin isn't put on the classpath
// with conflicting versions across :core (JVM) and :app (Android).
plugins {
    kotlin("jvm") version "2.1.21" apply false
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.21" apply false
}
