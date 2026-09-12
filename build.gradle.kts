buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP bumped from 8.4.2 - Kotlin 2.4.20 requires AGP >= 8.5.2. AGP 8.5.x's own
        // minimum Gradle requirement is 8.7, which we already have - no cascading changes.
        classpath("com.android.tools.build:gradle:8.5.2")
        // Bumped to 2.4.20 because litertlm-android:0.17.0 (multimodal support) was
        // compiled with Kotlin 2.4.0 metadata and won't load under an older compiler.
        // KSP is gone entirely now (Room was removed - see input/data/AppDatabase.kt for
        // why: no Room release, stable or alpha, supports Kotlin 2.4 metadata yet).
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.4.20")
    }
}
