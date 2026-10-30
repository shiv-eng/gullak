package com.gullak.core.speech.sarvam

import javax.inject.Qualifier

/**
 * Prototype configuration read from `local.properties` into BuildConfig. A shipped app would call Sarvam through
 * Paytm's backend and never hold a key in the APK. Without a key, the phone's own speech and voice are used.
 */
internal data class SarvamConfig(
    val apiKey: String,
    val sttModel: String,
    val ttsModel: String,
    val ttsSpeaker: String,
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class SarvamHttp
