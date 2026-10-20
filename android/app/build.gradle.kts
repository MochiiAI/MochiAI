plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Escape a string for embedding inside a generated Java/Kotlin string literal.
private fun String.escaped(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.mochi.agent"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mochi.agent"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables.useSupportLibrary = true

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    // The fallback GitHub token for the internal MochiiAI service account is
    // injected at build time (e.g. from local.properties or an env build server).
    // Never commit a live PAT — GitHub push protection blocks it.
    //   local.properties:  mochiDefaultGitHubToken=github_pat_<…>
    val defaultGitHubToken: String = providers.gradleProperty("mochiDefaultGitHubToken")
        .orElse("")
        .get()
        .trim()

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            buildConfigField("String", "MOCHI_CLOUD_WS_URL", "\"ws://10.0.2.2:8000/ws\"")
            buildConfigField("String", "MOCHI_CLOUD_HTTP_URL", "\"http://10.0.2.2:8000/\"")
            buildConfigField("String", "MOCHI_DEFAULT_GITHUB_TOKEN", "\"${defaultGitHubToken.escaped()}\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField("String", "MOCHI_CLOUD_WS_URL", "\"wss://api.mochi.ai/ws\"")
            buildConfigField("String", "MOCHI_CLOUD_HTTP_URL", "\"https://api.mochi.ai/\"")
            buildConfigField("String", "MOCHI_DEFAULT_GITHUB_TOKEN", "\"${defaultGitHubToken.escaped()}\"")
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
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.datastore)

    implementation(libs.lottie.compose)
    implementation(libs.rive.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso)
    debugImplementation(libs.androidx.compose.ui.tooling)
}