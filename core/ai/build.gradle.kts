import com.gullak.buildlogic.localProperties

plugins {
    alias(libs.plugins.gullak.android.library)
    alias(libs.plugins.gullak.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Prototype only: a shipped app calls the model through a backend and never holds the key in the APK.
val localProps = localProperties()

android {
    namespace = "com.gullak.core.ai"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "GEMINI_API_KEY", "\"${localProps.getProperty("GEMINI_API_KEY", "")}\"")
        buildConfigField("String", "GEMINI_MODEL_ID", "\"${localProps.getProperty("GEMINI_MODEL_ID", "")}\"")
        buildConfigField(
            "String",
            "GEMINI_TALK_MODEL_ID",
            "\"${localProps.getProperty("GEMINI_TALK_MODEL_ID", "gemini-3.5-flash")}\"",
        )
    }
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    debugImplementation(libs.okhttp.logging.interceptor)

    testImplementation(projects.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
