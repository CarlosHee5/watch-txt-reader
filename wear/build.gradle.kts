plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Same keystore for :mobile and :wear (Data Layer requires same applicationId + same signature).
// Set WATCHTXT_KEYSTORE / WATCHTXT_KEYSTORE_PASSWORD / WATCHTXT_KEY_ALIAS / WATCHTXT_KEY_PASSWORD
// to sign with your own key; otherwise the standard debug keystore is used for both modules.
val sharedKeystore: String? = System.getenv("WATCHTXT_KEYSTORE")?.takeIf { it.isNotBlank() && file(it).exists() }

android {
    namespace = "com.carloshee.watchtxt.wear"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.carloshee.watchtxt"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (sharedKeystore != null) {
            create("shared") {
                storeFile = file(sharedKeystore)
                storePassword = System.getenv("WATCHTXT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("WATCHTXT_KEY_ALIAS")
                keyPassword = System.getenv("WATCHTXT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        val shared = signingConfigs.findByName("shared")
        getByName("debug") {
            if (shared != null) signingConfig = shared
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = shared ?: signingConfigs.getByName("debug")
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
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")

    // Compose for Wear OS
    implementation("androidx.wear.compose:compose-material:1.3.1")
    implementation("androidx.wear.compose:compose-foundation:1.3.1")
    implementation("androidx.wear.compose:compose-navigation:1.3.1")
    implementation("androidx.navigation:navigation-runtime-ktx:2.7.7")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")

    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
}
