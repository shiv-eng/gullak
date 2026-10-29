plugins {
    alias(libs.plugins.gullak.android.feature)
}

android {
    namespace = "com.gullak.feature.creategoal"
}

dependencies {
    implementation(projects.core.speech)
}
