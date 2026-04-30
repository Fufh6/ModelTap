package com.wuyousheng.modeltap.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface MessagePart {
    @Serializable
    @SerialName("text")
    data class TextPart(val text: String) : MessagePart

    @Serializable
    @SerialName("remote_image")
    data class RemoteImagePart(val url: String) : MessagePart

    @Serializable
    @SerialName("local_image")
    data class LocalImagePart(val uri: String) : MessagePart

    @Serializable
    @SerialName("local_file")
    data class LocalFilePart(
        val uri: String,
        val name: String,
        val sizeBytes: Long,
        val mimeType: String? = null
    ) : MessagePart
}
