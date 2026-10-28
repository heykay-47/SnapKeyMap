plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")

android {
    namespace = "com.heykay.snapkeymap"
    compileSdk = 36
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.heykay.snapkeymap"
        minSdk = 29
        targetSdk = 36
        // F-Droid reads both as literals from this file.
        versionCode = 10191
        versionName = "1.2.0-beta.1"
    }

    // F-Droid can't inspect the dependency list that Google encrypts into the signing block.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storeType = "pkcs12"
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val releaseSigning = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            signingConfig = releaseSigning
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(17)
}

// Releases up to v1.0.14 used the CI run number (at most 67), so this scheme always installs over them.
android.defaultConfig.let { config ->
    val version = checkNotNull(Regex("""([0-9]+)\.([0-9]+)\.([0-9]+)(?:-beta\.([1-9]))?""").matchEntire(config.versionName!!)) {
        "versionName must be X.Y.Z or X.Y.Z-beta.N with N in 1..9, not ${config.versionName}"
    }
    val (major, minor, patch) = version.groupValues.drop(1).take(3).map(String::toInt)
    val base = major * 10000 + minor * 100 + patch
    val beta = version.groupValues[4].toIntOrNull()
    val expected = if (beta == null) base else base - 10 + beta
    check(config.versionCode == expected) {
        "versionCode for ${config.versionName} must be $expected, not ${config.versionCode}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
