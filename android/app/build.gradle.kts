plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "win.enderr.voidray"
    compileSdk = 36

    defaultConfig {
        applicationId = "win.enderr.voidray"
        minSdk = 26
        // Google Play requires new apps and updates to target Android 16 (API 36).
        targetSdk = 36
        // Release builds pass VERSION_NAME (from the git tag); the code grows with it.
        val version = System.getenv("VERSION_NAME") ?: "1.5.0"
        val parts = version.split('.').map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0)
        versionName = version
        versionCode = parts[0] * 10000 + parts[1] * 100 + parts[2]
    }

    flavorDimensions += "store"
    productFlavors {
        // GitHub releases: the app checks the project's releases and installs newer APKs itself.
        create("github") {
            dimension = "store"
            buildConfigField("boolean", "PLAY_STORE", "false")
        }
        // Google Play: Play delivers the updates, so no self-update code and no install permission.
        create("play") {
            dimension = "store"
            buildConfigField("boolean", "PLAY_STORE", "true")
        }
    }

    signingConfigs {
        // The release key never lives in the repository: CI decodes it from the
        // VOIDRAY_KEYSTORE_B64 secret into a file and passes its path here.
        val ks = System.getenv("VOIDRAY_KEYSTORE")
        if (ks != null && file(ks).exists()) {
            create("release") {
                storeFile = file(ks)
                storePassword = System.getenv("VOIDRAY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("VOIDRAY_KEY_ALIAS") ?: "voidray"
                keyPassword = System.getenv("VOIDRAY_KEY_PASSWORD") ?: System.getenv("VOIDRAY_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Without the secret (forks, local builds) fall back to the debug key.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
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
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    lint {
        // CI keeps the full report (lintPlayRelease); only fatal issues stop a build.
        abortOnError = false
    }
}

dependencies {
    // Xray core for Android (gomobile build from 2dust/AndroidLibXrayLite), fetched by CI.
    implementation(files("libs/libv2ray.aar"))

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
