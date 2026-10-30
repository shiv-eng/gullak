package com.gullak.core.ai.di

import com.gullak.core.ai.BuildConfig
import com.gullak.core.ai.FallbackGoalUnderstandingService
import com.gullak.core.ai.explanation.GeminiPlanExplanationService
import com.gullak.core.ai.gemini.GeminiAiInfo
import com.gullak.core.ai.gemini.GeminiApi
import com.gullak.core.ai.gemini.GeminiConfig
import com.gullak.core.ai.gemini.GeminiHttp
import com.gullak.core.domain.ai.AiInfo
import com.gullak.core.domain.ai.GoalUnderstandingService
import com.gullak.core.domain.ai.PlanExplanationService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AiBindingsModule {
    /** Demo answers → Gemini → rule-based parser. */
    @Binds
    abstract fun goalUnderstandingService(impl: FallbackGoalUnderstandingService): GoalUnderstandingService

    @Binds
    abstract fun planExplanationService(impl: GeminiPlanExplanationService): PlanExplanationService

    @Binds
    abstract fun aiInfo(impl: GeminiAiInfo): AiInfo
}

@Module
@InstallIn(SingletonComponent::class)
internal object AiProvidersModule {
    @Provides
    fun json(): Json = GeminiHttp.json

    /** Prototype only: a shipped app would call the model through a backend, never with a key in the APK. */
    @Provides
    fun geminiConfig(): GeminiConfig = GeminiConfig(BuildConfig.GEMINI_API_KEY, BuildConfig.GEMINI_MODEL_ID)

    @Provides
    @Singleton
    fun geminiApi(config: GeminiConfig): GeminiApi = GeminiHttp.api(GeminiHttp.client(config))
}
