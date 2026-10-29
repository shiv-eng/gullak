plugins {
    alias(libs.plugins.gullak.android.feature)
}

android {
    namespace = "com.gullak.feature.plan"
}

dependencies {
    implementation(projects.core.speech)
}
