plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

fun configuredValue(name: String, default: String = ""): String =
    (project.findProperty(name) as String?)?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }
        ?: default

fun quotedBuildConfig(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

fun firstConfigured(vararg names: String): String =
    names.firstNotNullOfOrNull { configuredValue(it).takeIf(String::isNotBlank) }.orEmpty()

val releaseStoreFile = firstConfigured("ANDROID_KEYSTORE_PATH", "CM_KEYSTORE_PATH")
val releaseStorePassword = firstConfigured("ANDROID_KEYSTORE_PASSWORD", "CM_KEYSTORE_PASSWORD")
val releaseKeyAlias = firstConfigured("ANDROID_KEY_ALIAS", "CM_KEY_ALIAS")
val releaseKeyPassword = firstConfigured("ANDROID_KEY_PASSWORD", "CM_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { it.isNotBlank() }

android {
    namespace = "com.evfastroute.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kendennis.evfastroute"
        minSdk = 26
        targetSdk = 36
        versionCode = configuredValue("VERSION_CODE", "2").toIntOrNull()?.coerceAtLeast(1) ?: 2
        versionName = configuredValue("VERSION_NAME", "0.2.0")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Provider credentials are accepted for private beta builds. Public production builds
        // should point at a controlled proxy instead because mobile BuildConfig values can be
        // extracted from an APK/AAB. See DISTRIBUTION.md.
        buildConfigField("String", "OCM_API_KEY", quotedBuildConfig(configuredValue("OCM_API_KEY")))
        buildConfigField("String", "ORS_API_KEY", quotedBuildConfig(configuredValue("ORS_API_KEY")))
        buildConfigField(
            "String",
            "ORS_BASE_URL",
            quotedBuildConfig(configuredValue("ORS_BASE_URL", "https://api.heigit.org/openrouteservice")),
        )
        buildConfigField(
            "String",
            "OCM_BASE_URL",
            quotedBuildConfig(configuredValue("OCM_BASE_URL", "https://api.openchargemap.io/v3")),
        )
        buildConfigField(
            "String",
            "PHOTON_BASE_URL",
            quotedBuildConfig(configuredValue("PHOTON_BASE_URL", "https://photon.komoot.io")),
        )
        buildConfigField(
            "String",
            "PRIVACY_POLICY_URL",
            quotedBuildConfig(
                configuredValue(
                    "PRIVACY_POLICY_URL",
                    "https://github.com/ken012/App-ev-fastroute-android/blob/main/PRIVACY.md",
                ),
            ),
        )
        buildConfigField(
            "String",
            "THIRD_PARTY_NOTICES_URL",
            quotedBuildConfig(
                configuredValue(
                    "THIRD_PARTY_NOTICES_URL",
                    "https://github.com/ken012/App-ev-fastroute-android/blob/main/THIRD_PARTY_NOTICES.md",
                ),
            ),
        )
        buildConfigField(
            "String",
            "SUPPORT_URL",
            quotedBuildConfig(
                configuredValue(
                    "SUPPORT_URL",
                    "https://github.com/ken012/App-ev-fastroute-android/issues",
                ),
            ),
        )
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    packaging {
        jniLibs.useLegacyPackaging = false
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.maplibre.gl:android-sdk:13.4.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
