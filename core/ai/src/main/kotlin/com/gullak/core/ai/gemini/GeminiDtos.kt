package com.gullak.core.ai.gemini

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/*
 * Wire types for the Gemini API `models.generateContent` REST method (v1beta). Only the fields Gullak uses;
 * unknown response fields are ignored.
 */

@Serializable
internal data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null,
    val generationConfig: GenerationConfig? = null,
)

@Serializable
internal data class Content(
    val parts: List<Part>,
    val role: String? = null,
)

@Serializable
internal data class Part(
    val text: String? = null,
    /** Set on thinking parts of Gemini 3 replies; those are not part of the answer. */
    val thought: Boolean? = null,
)

@Serializable
internal data class GenerationConfig(
    val temperature: Double? = null,
    val responseMimeType: String? = null,
    /** An OpenAPI-subset `Schema` object (types STRING, INTEGER, OBJECT; `nullable`; `enum`). */
    val responseSchema: JsonObject? = null,
    val thinkingConfig: ThinkingConfig? = null,
)

@Serializable
internal data class ThinkingConfig(val thinkingLevel: String)

@Serializable
internal data class GenerateContentResponse(
    val candidates: List<Candidate> = emptyList(),
    val promptFeedback: PromptFeedback? = null,
)

@Serializable
internal data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null,
)

@Serializable
internal data class PromptFeedback(val blockReason: String? = null)
