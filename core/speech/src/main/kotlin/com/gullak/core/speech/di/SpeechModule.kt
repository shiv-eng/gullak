package com.gullak.core.speech.di

import com.gullak.core.domain.ai.VoiceOutput
import com.gullak.core.speech.TextToSpeechVoiceOutput
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SpeechModule {
    @Binds
    abstract fun voiceOutput(impl: TextToSpeechVoiceOutput): VoiceOutput
}
