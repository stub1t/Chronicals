plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release builds are versioned and signed from the environment (see .github/workflows/android-release.yml).
// Without these, local builds get a dev version and an unsigned release APK.
val env: Map<String, String> = System.getenv()
val releaseKeystore = env["CHRONICALS_KEYSTORE_FILE"]

android {
    namespace = "io.github.stub1t.chronicals"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.stub1t.chronicals"
        minSdk = 24
        targetSdk = 36
        versionCode = (env["CHRONICALS_VERSION_CODE"] ?: "1").toInt()
        versionName = env["CHRONICALS_VERSION_NAME"] ?: "0.0.0-dev"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = env["CHRONICALS_KEYSTORE_PASSWORD"]
                keyAlias = env["CHRONICALS_KEY_ALIAS"]
                keyPassword = env["CHRONICALS_KEY_PASSWORD"]
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Optimised like a release but signed with the debug key, for trying the real speed on a test phone or emulator.
        create("staging") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        // java.time on Android 7 (API 24 and 25).
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // No dependency metadata blob in the APK: it is only readable by Google Play.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    val composeBom = platform("androidx.compose:compose-bom:2026.05.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.8")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
