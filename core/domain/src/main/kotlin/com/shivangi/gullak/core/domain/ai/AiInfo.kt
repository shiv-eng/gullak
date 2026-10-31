package com.shivangi.gullak.core.domain.ai

/** What Settings shows about the live model (the API key itself is never exposed). */
interface AiInfo {
    val modelId: String

    /** False when the key or model ID is missing; Gullak then reads goals with the offline rules. */
    val isLiveModelConfigured: Boolean
}
