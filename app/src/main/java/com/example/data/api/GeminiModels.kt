package com.example.data.api

import com.google.gson.annotations.SerializedName

// --- Common Data Classes for Gemini API via Gson ---

data class GenerateContentRequest(
    @SerializedName("contents") val contents: List<Content>,
    @SerializedName("generationConfig") val generationConfig: GenerationConfig? = null,
    @SerializedName("systemInstruction") val systemInstruction: Content? = null
)

data class Content(
    @SerializedName("parts") val parts: List<Part>
)

data class Part(
    @SerializedName("text") val text: String? = null,
    @SerializedName("inlineData") val inlineData: InlineData? = null
)

data class InlineData(
    @SerializedName("mimeType") val mimeType: String,
    @SerializedName("data") val data: String
)

data class GenerationConfig(
    @SerializedName("responseMimeType") val responseMimeType: String? = null,
    @SerializedName("temperature") val temperature: Double? = null,
    @SerializedName("maxOutputTokens") val maxOutputTokens: Int? = null
)

data class GenerateContentResponse(
    @SerializedName("candidates") val candidates: List<Candidate>? = null
)

data class Candidate(
    @SerializedName("content") val content: Content? = null
)
