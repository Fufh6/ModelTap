package com.wuyousheng.modeltap.data.repository

import com.wuyousheng.modeltap.domain.model.MessagePart
import com.wuyousheng.modeltap.domain.model.ApiConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepositoryTest {
    @Test
    fun buildSessionTitle_keepsCustomTitle() {
        val title = buildSessionTitle(
            currentTitle = "自定义标题",
            parts = listOf(MessagePart.TextPart("新的用户消息"))
        )

        assertEquals("自定义标题", title)
    }

    @Test
    fun buildSessionTitle_usesFirstTextPartForNewSession() {
        val title = buildSessionTitle(
            currentTitle = "新会话",
            parts = listOf(MessagePart.TextPart("  第一行\n第二行  "))
        )

        assertEquals("第一行 第二行", title)
    }

    @Test
    fun buildSessionTitle_usesImageTitleWhenTextIsBlank() {
        val title = buildSessionTitle(
            currentTitle = "新会话",
            parts = listOf(MessagePart.LocalImagePart("content://image"))
        )

        assertEquals("图片会话", title)
    }

    @Test
    fun extractAssistantParts_returnsPlainTextWhenNoMarkdownImages() {
        val parts = extractAssistantParts("hello")

        assertEquals(listOf(MessagePart.TextPart("hello")), parts)
    }

    @Test
    fun extractAssistantParts_splitsMarkdownImages() {
        val parts = extractAssistantParts("before ![img](https://example.com/a.png) after")

        assertEquals(3, parts.size)
        assertEquals(MessagePart.TextPart("before"), parts[0])
        assertEquals(MessagePart.RemoteImagePart("https://example.com/a.png"), parts[1])
        assertEquals(MessagePart.TextPart("after"), parts[2])
    }

    @Test
    fun extractAssistantParts_splitsPlainImageUrls() {
        val parts = extractAssistantParts("done https://example.com/a.png?x=1")

        assertEquals(2, parts.size)
        assertEquals(MessagePart.TextPart("done"), parts[0])
        assertEquals(MessagePart.RemoteImagePart("https://example.com/a.png?x=1"), parts[1])
    }

    @Test
    fun extractAssistantParts_supportsMarkdownDataImage() {
        val parts = extractAssistantParts("![img](data:image/png;base64,aGVsbG8=)")

        assertEquals(listOf(MessagePart.RemoteImagePart("data:image/png;base64,aGVsbG8=")), parts)
    }

    @Test
    fun extractAssistantParts_ignoresNonHttpMarkdownImages() {
        val parts = extractAssistantParts("![img](content://local)")

        assertEquals(1, parts.size)
        assertTrue(parts.first() is MessagePart.TextPart)
    }

    @Test
    fun messagePartsToContent_serializesWithTypeDiscriminator() {
        val content = messagePartsToContent(
            parts = listOf(MessagePart.TextPart("hello")),
            encodeLocalImage = { "" }
        )

        val json = Json { classDiscriminator = "type" }

        assertEquals("[{\"type\":\"text\",\"text\":\"hello\"}]", json.encodeToString(content))
    }

    @Test
    fun buildEffectiveSystemPrompt_includesAssistantName() {
        val prompt = buildEffectiveSystemPrompt(
            basePrompt = "你要简洁回答。",
            config = ApiConfig(assistantName = "小明", replyStyle = "精准", reasoningStrength = "低", contextMemory = "关闭")
        )

        assertTrue(prompt.contains("你要简洁回答。"))
        assertTrue(prompt.contains("助手名称：小明"))
        assertTrue(prompt.contains("回复风格：精准"))
        assertTrue(prompt.contains("推理强度：低"))
        assertTrue(prompt.contains("上下文记忆：关闭"))
    }

    @Test
    fun decodeMessageParts_supportsCurrentDiscriminator() {
        val partsJson = """[{"type":"text","text":"hello"}]"""

        val parts = decodeMessageParts(partsJson)

        assertEquals(listOf(MessagePart.TextPart("hello")), parts)
    }

    @Test
    fun encodeMessageParts_writesDiscriminatorForTextPart() {
        val partsJson = encodeMessageParts(listOf(MessagePart.TextPart("正在生成图片，请稍候…")))

        assertEquals("""[{"type":"text","text":"正在生成图片，请稍候…"}]""", partsJson)
        assertEquals(listOf(MessagePart.TextPart("正在生成图片，请稍候…")), decodeMessageParts(partsJson))
    }

    @Test
    fun shouldShowImageGenerationPlaceholder_ignoresPlainImageMentions() {
        val parts = listOf(MessagePart.TextPart("请解释一下这张图片里的内容"))

        assertFalse(shouldShowImageGenerationPlaceholder(parts))
    }

    @Test
    fun shouldShowImageGenerationPlaceholder_detectsExplicitGeneration() {
        val parts = listOf(MessagePart.TextPart("生成图片：一只橘猫在书桌旁睡觉"))

        assertTrue(shouldShowImageGenerationPlaceholder(parts))
    }

    @Test
    fun shouldShowImageGenerationPlaceholder_doesNotTreatGenericGenerateAsImage() {
        val parts = listOf(MessagePart.TextPart("生成一个 Kotlin 登录页面示例"))

        assertFalse(shouldShowImageGenerationPlaceholder(parts))
    }

    @Test
    fun shouldShowImageGenerationPlaceholder_detectsImageEditWithAttachment() {
        val parts = listOf(
            MessagePart.TextPart("把背景改成蓝色"),
            MessagePart.LocalImagePart("content://image")
        )

        assertTrue(shouldShowImageGenerationPlaceholder(parts))
    }

    @Test
    fun shouldShowImageGenerationPlaceholder_ignoresAttachmentAnalysis() {
        val parts = listOf(
            MessagePart.TextPart("请分析这张图片"),
            MessagePart.LocalImagePart("content://image")
        )

        assertFalse(shouldShowImageGenerationPlaceholder(parts))
    }

    @Test
    fun cleanImageInspirationResponse_skipsIntroLine() {
        val response = """
            当然可以，以下是一条随机生图提示词：
            雨夜的霓虹街角，一位穿透明雨衣的女孩站在自动贩卖机前，蓝紫色灯光映在湿润路面上，电影感长焦镜头。
        """.trimIndent()

        val prompt = cleanImageInspirationResponse(response)

        assertEquals(
            "雨夜的霓虹街角，一位穿透明雨衣的女孩站在自动贩卖机前，蓝紫色灯光映在湿润路面上，电影感长焦镜头。",
            prompt
        )
    }

    @Test
    fun cleanImageInspirationResponse_removesLabelPrefix() {
        val prompt = cleanImageInspirationResponse("提示词：清晨薄雾中的山谷木屋，暖光从窗户透出，远处群山层叠，写实摄影风格。")

        assertEquals("清晨薄雾中的山谷木屋，暖光从窗户透出，远处群山层叠，写实摄影风格。", prompt)
    }

    @Test
    fun decodeMessageParts_supportsLegacyDiscriminator() {
        val partsJson = """[{"type":"com.wuyousheng.modeltap.domain.model.MessagePart.TextPart","text":"hello"}]"""

        val parts = decodeMessageParts(partsJson)

        assertEquals(listOf(MessagePart.TextPart("hello")), parts)
    }

    @Test
    fun decodeMessageParts_supportsPreviousPackageLegacyDiscriminator() {
        val previousPackage = listOf("com", "codexapp", "aichat").joinToString(".")
        val partsJson = """[{"type":"$previousPackage.domain.model.MessagePart.TextPart","text":"hello"}]"""

        val parts = decodeMessageParts(partsJson)

        assertEquals(listOf(MessagePart.TextPart("hello")), parts)
    }

    @Test
    fun decodeMessageParts_supportsSingleObject() {
        val partsJson = """{"type":"remote_image","url":"https://example.com/a.png"}"""

        val parts = decodeMessageParts(partsJson)

        assertEquals(listOf(MessagePart.RemoteImagePart("https://example.com/a.png")), parts)
    }

    @Test
    fun decodeMessageParts_supportsPlainString() {
        val parts = decodeMessageParts("\"hello\"")

        assertEquals(listOf(MessagePart.TextPart("hello")), parts)
    }

    @Test
    fun decodeMessageParts_fallsBackForMalformedJson() {
        val parts = decodeMessageParts("{bad json")

        assertEquals(listOf(MessagePart.TextPart("ERROR：消息加载失败，历史内容无法解析。")), parts)
    }
}
