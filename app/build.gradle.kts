import com.shivangi.gullak.buildlogic.localProperties

plugins {
    alias(libs.plugins.gullak.android.application)
    alias(libs.plugins.gullak.compose)
    alias(libs.plugins.gullak.hilt)
    alias(libs.plugins.kotlin.serialization)
}

val localProps = localProperties()
val releaseStoreFile: String? = localProps.getProperty("RELEASE_STORE_FILE")?.takeIf { it.isNotBlank() }

android {
    namespace = "com.shivangi.gullak.app"

    defaultConfig {
        applicationId = "com.shivangi.gullak.app"
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            if (releaseStoreFile != null) {
                storeFile = file(releaseStoreFile)
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without a release keystore in local.properties, sign with the debug key so the demo APK installs.
            signingConfig = signingConfigs.getByName(if (releaseStoreFile != null) "release" else "debug")
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.ai)
    implementation(projects.core.speech)
    implementation(projects.core.designsystem)
    implementation(projects.feature.paytmhome)
    implementation(projects.feature.home)
    implementation(projects.feature.creategoal)
    implementation(projects.feature.goaldetail)
    implementation(projects.feature.settings)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.androidx.datastore.preferences)
    kspTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
