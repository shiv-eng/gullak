plugins {
    alias(libs.plugins.gullak.android.library)
    alias(libs.plugins.gullak.compose)
}

android {
    namespace = "com.shivangi.gullak.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material.icons.extended)
}
