plugins {
    alias(libs.plugins.gullak.jvm.library)
    `java-test-fixtures`
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)

    // Fakes and builders shared by domain tests and, through :core:testing, by the Android modules.
    testFixturesApi(projects.core.model)
    testFixturesApi(libs.kotlinx.coroutines.core)
}
