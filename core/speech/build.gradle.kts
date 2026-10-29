plugins {
    alias(libs.plugins.gullak.android.library)
    alias(libs.plugins.gullak.hilt)
}

android {
    namespace = "com.gullak.core.speech"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
