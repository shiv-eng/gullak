plugins {
    alias(libs.plugins.gullak.android.library)
}

android {
    namespace = "com.gullak.core.testing"
}

dependencies {
    api(projects.core.domain)
    api(projects.core.model)
    api(libs.junit4)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
}
