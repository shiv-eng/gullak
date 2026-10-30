package com.gullak.core.speech.di

import com.gullak.core.domain.ai.SpeechInput
import com.gullak.core.domain.ai.VoiceOutput
import com.gullak.core.speech.BuildConfig
import com.gullak.core.speech.FallbackSpeechInput
import com.gullak.core.speech.FallbackVoiceOutput
import com.gullak.core.speech.gemini.GeminiVoiceConfig
import com.gullak.core.speech.sarvam.SarvamConfig
import com.gullak.core.speech.sarvam.SarvamHttp
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SpeechModule {
    @Binds
    abstract fun voiceOutput(impl: FallbackVoiceOutput): VoiceOutput

    @Binds
    abstract fun speechInput(impl: FallbackSpeechInput): SpeechInput
}

@Module
@InstallIn(SingletonComponent::class)
internal object SarvamModule {
    @Provides
    fun config(): SarvamConfig = SarvamConfig(
        apiKey = BuildConfig.SARVAM_API_KEY,
        sttModel = BuildConfig.SARVAM_STT_MODEL,
        ttsModel = BuildConfig.SARVAM_TTS_MODEL,
        ttsSpeaker = BuildConfig.SARVAM_TTS_SPEAKER,
    )

    @Provides
    fun geminiVoice(): GeminiVoiceConfig = GeminiVoiceConfig(
        apiKey = BuildConfig.GEMINI_API_KEY,
        model = BuildConfig.GEMINI_TTS_MODEL,
        voice = BuildConfig.GEMINI_TTS_VOICE,
    )

    @Provides
    @Singleton
    @SarvamHttp
    fun client(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(CONNECT_SECONDS))
        .readTimeout(Duration.ofSeconds(READ_SECONDS))
        .pingInterval(Duration.ofSeconds(PING_SECONDS))
        .build()

    private const val CONNECT_SECONDS = 5L
    private const val READ_SECONDS = 15L
    private const val PING_SECONDS = 20L
}
