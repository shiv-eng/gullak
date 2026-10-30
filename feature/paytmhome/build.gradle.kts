plugins {
    alias(libs.plugins.gullak.android.feature)
}

// A stand-in for the Paytm app's home screen, so Gullak can be shown as an extension of it.
android {
    namespace = "com.gullak.feature.paytmhome"
}
