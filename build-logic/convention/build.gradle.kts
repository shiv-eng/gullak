plugins {
    `kotlin-dsl`
}

group = "com.gullak.buildlogic"

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    compileOnly(libs.ktlint.gradlePlugin)
    compileOnly(libs.kover.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "gullak.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "gullak.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "gullak.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("compose") {
            id = "gullak.compose"
            implementationClass = "ComposeConventionPlugin"
        }
        register("hilt") {
            id = "gullak.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("jvmLibrary") {
            id = "gullak.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("room") {
            id = "gullak.room"
            implementationClass = "RoomConventionPlugin"
        }
        register("test") {
            id = "gullak.test"
            implementationClass = "TestConventionPlugin"
        }
    }
}
