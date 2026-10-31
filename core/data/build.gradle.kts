plugins {
    alias(libs.plugins.gullak.android.library)
    alias(libs.plugins.gullak.hilt)
    alias(libs.plugins.gullak.room)
}

android {
    namespace = "com.shivangi.gullak.core.data"
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
