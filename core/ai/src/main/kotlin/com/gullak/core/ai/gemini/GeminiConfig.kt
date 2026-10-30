package com.gullak.core.ai.gemini

/**
 * Prototype configuration read from `local.properties` into BuildConfig. A shipped app would call the model
 * through Paytm's backend and never hold a key in the APK.
 */
data class GeminiConfig(
    val apiKey: String,
    val modelId: String,
    /** For the spoken conversation, where the quality of the Hindi matters most. */
    val talkModelId: String = modelId,
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank() && modelId.isNotBlank()

    /**
     * Gemini 3 and later: Google strongly recommends leaving temperature at its default (1.0), and thinking
     * can be turned down. Older models take an explicit temperature and have no thinking level.
     */
    internal val isGemini3OrLater: Boolean
        get() = GENERATION.find(modelId)?.groupValues?.get(1)?.toIntOrNull()?.let { it >= GEMINI_3 } ?: false

    private companion object {
        val GENERATION = Regex("""gemini-(\d+)""")
        const val GEMINI_3 = 3
    }
}
