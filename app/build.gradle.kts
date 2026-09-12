import java.io.FileInputStream
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    // No kapt/KSP anymore - Room (the only thing that needed an annotation processor) was
    // replaced with plain SQLite specifically to unblock Kotlin 2.4 for multimodal support.
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(FileInputStream(file))
}

android {
    namespace = "com.studylens"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.studylens"
        minSdk = 31 // required by litertlm-android
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        buildConfigField(
            "String",
            "OPENROUTER_API_KEY",
            "\"${localProperties.getProperty("OPENROUTER_API_KEY", "")}\""
        )
        buildConfigField(
            "String",
            "GROQ_API_KEY",
            "\"${localProperties.getProperty("GROQ_API_KEY", "")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Replaces the deprecated android.kotlinOptions { jvmTarget = "17" } - required by
// the Kotlin 2.4.20 Gradle plugin.
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget("17")
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // Camera + OCR
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:text-recognition:16.0.0")

    // Local storage: plain SQLite now (input/data/AppDatabase.kt) - no Room, no annotation
    // processor, so nothing here needs a Kotlin-metadata-compatible compiler plugin.
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // On-device LLM - litertlm-android (Engine/Conversation/Session API) is what
    // LlmEngine.kt actually uses (Kotlin 2.4-compatible, supports multimodal .litertlm
    // models - confirmed working end-to-end with Qwen2-VL 2B, Gemma 4 E2B, FastVLM 0.5B).
    // tasks-genai/tasks-core/tasks-vision are kept only because MediaPipeEngineManager.kt
    // still references them - that class isn't wired into LlmEngine or any active screen.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.17.0")
    implementation("com.google.mediapipe:tasks-genai:0.10.24")
    implementation("com.google.mediapipe:tasks-core:0.10.14")
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    // Networking, for OpenRouter retrieval call
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Local Testing
    testImplementation("junit:junit:4.13.2")
}
