package com.shivangi.gullak.core.ai.gemini

import com.shivangi.gullak.core.domain.ai.AiInfo
import javax.inject.Inject

internal class GeminiAiInfo @Inject constructor(private val config: GeminiConfig) : AiInfo {
    override val modelId: String get() = config.modelId

    override val isLiveModelConfigured: Boolean get() = config.isConfigured
}
