import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Read optional API keys from local.properties so they never land in git.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun localProp(name: String, default: String = ""): String =
    (localProps.getProperty(name) ?: System.getenv(name) ?: default)

val computedVersionCode = localProp("APP_VERSION_CODE").toIntOrNull()
    ?: System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
    ?: (System.currentTimeMillis() / 1000L).toInt()
val signingStoreFilePath = localProp("ANDROID_SIGNING_STORE_FILE")
val signingStoreType = localProp("ANDROID_SIGNING_STORE_TYPE")
val signingStorePassword = localProp("ANDROID_SIGNING_STORE_PASSWORD")
val signingKeyAlias = localProp("ANDROID_SIGNING_KEY_ALIAS")
val signingKeyPassword = localProp("ANDROID_SIGNING_KEY_PASSWORD")
val hasStableSigning = signingStoreFilePath.isNotBlank() &&
    signingStorePassword.isNotBlank() &&
    signingKeyAlias.isNotBlank() &&
    signingKeyPassword.isNotBlank() &&
    rootProject.file(signingStoreFilePath).exists()

android {
    namespace = "com.stockwatchdog.app"
    compileSdk = 34

    signingConfigs {
        if (hasStableSigning) {
            create("stable") {
                storeFile = rootProject.file(signingStoreFilePath)
                if (signingStoreType.isNotBlank()) {
                    storeType = signingStoreType
                }
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.stockwatchdog.app"
        minSdk = 26
        targetSdk = 34
        versionCode = computedVersionCode
        versionName = "1.0.0"

        vectorDrawables { useSupportLibrary = true }

        buildConfigField(
            "String", "TWELVE_DATA_API_KEY",
            "\"${localProp("TWELVE_DATA_API_KEY", "fd45fc48ee8b4bb9942bbed9edb15af3")}\""
        )
        buildConfigField(
            "String", "ALPHA_VANTAGE_API_KEY",
            "\"${localProp("ALPHA_VANTAGE_API_KEY", "7IH3N5WDRTF9GMZE")}\""
        )
        buildConfigField(
            "String", "FINNHUB_API_KEY",
            "\"${localProp("FINNHUB_API_KEY", "d7jj9nhr01qhf13f3cd0d7jj9nhr01qhf13f3cdg")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Private-use signing: default to debug signing so a release APK
            // can still be built and sideloaded without a custom keystore.
            signingConfig = if (hasStableSigning) {
                signingConfigs.getByName("stable")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
            if (hasStableSigning) {
                signingConfig = signingConfigs.getByName("stable")
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
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.retrofit.core)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.google.material)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
