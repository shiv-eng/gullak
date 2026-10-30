plugins {
    alias(libs.plugins.gullak.android.feature)
}

android {
    namespace = "com.gullak.feature.settings"
}

dependencies {
    // Per-app language (AppCompatDelegate.setApplicationLocales), back to API 26.
    implementation(libs.androidx.appcompat)
}
