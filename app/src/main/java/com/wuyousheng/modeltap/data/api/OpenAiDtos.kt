package com.wuyousheng.modeltap.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class ModelsResponse(
    val data: List<ModelDto> = emptyList()
)

@Serializable
data class ModelDto(
    val id: String? = null,
    @SerialName("owned_by") val ownedBy: String? = null
)

@Serializable
data class ChatCompletionsRequest(
    val model: String,
    val messages: List<ChatMessagePayload>,
    val temperature: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("reasoning_effort") val reasoningEffort: String? = null,
    val stream: Boolean? = null,
    @SerialName("stream_options") val streamOptions: StreamOptionsDto? = null
)

@Serializable
data class StreamOptionsDto(
    @SerialName("include_usage") val includeUsage: Boolean = true
)

@Serializable
data class ChatMessagePayload(
    val role: String,
    val content: List<ContentPartDto>
)

@Serializable
sealed interface ContentPartDto {
    val type: String
}

@Serializable
@SerialName("text")
data class TextContentPartDto(
    val text: String,
    @Transient override val type: String = "text"
) : ContentPartDto

@Serializable
@SerialName("image_url")
data class ImageUrlContentPartDto(
    @SerialName("image_url") val imageUrl: ImageUrlDto,
    @Transient override val type: String = "image_url"
) : ContentPartDto

@Serializable
data class ImageUrlDto(
    val url: String
)

@Serializable
data class ChatCompletionsResponse(
    val choices: List<ChoiceDto> = emptyList(),
    val usage: UsageDto? = null
)

@Serializable
data class ChoiceDto(
    val message: AssistantMessageDto? = null,
    val delta: AssistantMessageDto? = null
)

@Serializable
data class AssistantMessageDto(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class UsageDto(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null
)
