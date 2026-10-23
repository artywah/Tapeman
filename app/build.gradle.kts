import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Apple Music developer token (a JWT made by tools/make_apple_token.py), read from the
// git-ignored apple-music.properties; without it the token can be pasted in the app.
val appleProps = Properties().apply {
    rootProject.file("apple-music.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
val appleDeveloperToken: String = appleProps.getProperty("apple.developerToken", "").trim()

// Release signing, from the git-ignored signing.properties (storeFile, storePassword, keyAlias,
// keyPassword). Without it, release builds fall back to the debug key.
val signingProps = Properties().apply {
    rootProject.file("signing.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

// The app version; versionCode must go up by one with every release.
val appVersionName = "0.2.1"
val appVersionCode = 12

base {
    archivesName = "Tapeman-$appVersionName"
}

android {
    namespace = "dev.foldman"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.foldman"
        minSdk = 30
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "APPLE_DEVELOPER_TOKEN", "\"$appleDeveloperToken\"")
        // Every current foldable is 64-bit ARM; leaving out the rest halves the APK.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        if (signingProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    implementation("androidx.window:window:1.5.1")
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Apple's MusicKit for Android AARs (musickitauth-release-*.aar, mediaplayback-release-*.aar),
    // downloaded from developer.apple.com. Optional: the app finds them at runtime.
    implementation(fileTree("libs") { include("*.aar", "*.jar") })
    // MusicKit's sign-in screen uses an AppCompat theme.
    implementation("androidx.appcompat:appcompat:1.8.0")
}
