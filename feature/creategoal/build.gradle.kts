plugins {
    alias(libs.plugins.gullak.android.feature)
}

android {
    namespace = "com.gullak.feature.creategoal"
}

dependencies {
    implementation(projects.core.speech)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
