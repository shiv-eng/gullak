import com.gullak.buildlogic.localProperties

plugins {
    alias(libs.plugins.gullak.android.library)
    alias(libs.plugins.gullak.hilt)
}

// Prototype only: a shipped app calls Sarvam through a backend and never holds the key in the APK.
val localProps = localProperties()

android {
    namespace = "com.gullak.core.speech"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "GEMINI_API_KEY", "\"${localProps.getProperty("GEMINI_API_KEY", "")}\"")
        buildConfigField(
            "String",
            "GEMINI_TTS_MODEL",
            "\"${localProps.getProperty("GEMINI_TTS_MODEL", "gemini-3.8-flash-lite-tts")}\"",
        )
        buildConfigField("String", "GEMINI_TTS_VOICE", "\"${localProps.getProperty("GEMINI_TTS_VOICE", "Sulafat")}\"")
        buildConfigField("String", "SARVAM_API_KEY", "\"${localProps.getProperty("SARVAM_API_KEY", "")}\"")
        buildConfigField("String", "SARVAM_STT_MODEL", "\"${localProps.getProperty("SARVAM_STT_MODEL", "saaras:v4")}\"")
        buildConfigField("String", "SARVAM_TTS_MODEL", "\"${localProps.getProperty("SARVAM_TTS_MODEL", "bulbul:v3")}\"")
        buildConfigField(
            "String",
            "SARVAM_TTS_SPEAKER",
            "\"${localProps.getProperty("SARVAM_TTS_SPEAKER", "simran")}\"",
        )
    }
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.androidx.core.ktx)

    testImplementation(projects.core.testing)
}
