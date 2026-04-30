package com.wuyousheng.modeltap.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import com.wuyousheng.modeltap.data.api.ApiClientFactory
import com.wuyousheng.modeltap.data.api.ChatCompletionsRequest
import com.wuyousheng.modeltap.data.api.ChatMessagePayload
import com.wuyousheng.modeltap.data.api.ContentPartDto
import com.wuyousheng.modeltap.data.api.ImageUrlContentPartDto
import com.wuyousheng.modeltap.data.api.ImageUrlDto
import com.wuyousheng.modeltap.data.api.StreamOptionsDto
import com.wuyousheng.modeltap.data.api.TextContentPartDto
import com.wuyousheng.modeltap.data.api.UsageDto
import com.wuyousheng.modeltap.data.local.AppDatabase
import com.wuyousheng.modeltap.data.local.entity.ChatMessageEntity
import com.wuyousheng.modeltap.data.local.entity.ChatSessionEntity
import com.wuyousheng.modeltap.data.search.TavilySearchClientFactory
import com.wuyousheng.modeltap.data.search.TavilySearchRequest
import com.wuyousheng.modeltap.data.search.TavilySearchResponse
import com.wuyousheng.modeltap.domain.model.ApiConfig
import com.wuyousheng.modeltap.domain.model.ApiKeyEntry
import com.wuyousheng.modeltap.domain.model.bestMatch
import com.wuyousheng.modeltap.domain.model.ChatMessage
import com.wuyousheng.modeltap.domain.model.ChatSession
import com.wuyousheng.modeltap.domain.model.DailyUsageStat
import com.wuyousheng.modeltap.domain.model.GeneratedImage
import com.wuyousheng.modeltap.domain.model.MessagePart
import com.wuyousheng.modeltap.domain.model.MessageRole
import com.wuyousheng.modeltap.domain.model.ModelOption
import com.wuyousheng.modeltap.domain.model.ModelUsageStat
import com.wuyousheng.modeltap.domain.model.TokenUsage
import com.wuyousheng.modeltap.domain.model.UsageStats
import com.wuyousheng.modeltap.domain.model.visibleApiKeyEntries
import com.wuyousheng.modeltap.storage.AppPreferences
import com.wuyousheng.modeltap.storage.isValidHttpsBaseUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

fun buildSessionTitle(currentTitle: String, parts: List<MessagePart>): String {
    if (currentTitle != "新会话") return currentTitle
    val text = parts.filterIsInstance<MessagePart.TextPart>().firstOrNull()?.text?.trim().orEmpty()
    if (text.isBlank()) return "图片会话"
    return text.replace(Regex("\\s+"), " ").take(24)
}

fun extractAssistantParts(content: String): List<MessagePart> {
    val matches = findAssistantImageMatches(content)
    if (matches.isEmpty()) {
        return listOf(MessagePart.TextPart(content))
    }

    val parts = mutableListOf<MessagePart>()
    var currentIndex = 0
    for (match in matches) {
        val range = match.range
        if (range.first > currentIndex) {
            val text = content.substring(currentIndex, range.first).trim()
            if (text.isNotEmpty()) {
                parts += MessagePart.TextPart(text)
            }
        }
        parts += MessagePart.RemoteImagePart(match.url)
        currentIndex = range.last + 1
    }
    if (currentIndex < content.length) {
        val trailing = content.substring(currentIndex).trim()
        if (trailing.isNotEmpty()) {
            parts += MessagePart.TextPart(trailing)
        }
    }
    return if (parts.isEmpty()) listOf(MessagePart.TextPart(content)) else parts
}

private data class AssistantImageMatch(
    val range: IntRange,
    val url: String
)

private fun findAssistantImageMatches(content: String): List<AssistantImageMatch> {
    val markdownMatches = MarkdownImageRegex.findAll(content)
        .mapNotNull { match ->
            cleanAssistantImageUrl(match.groupValues[1])?.let { url ->
                AssistantImageMatch(match.range, url)
            }
        }
        .toList()
    if (markdownMatches.isNotEmpty()) return markdownMatches

    return PlainImageRegex.findAll(content)
        .mapNotNull { match ->
            cleanAssistantImageUrl(match.value)?.let { url ->
                AssistantImageMatch(match.range, url)
            }
        }
        .toList()
}

private fun cleanAssistantImageUrl(rawUrl: String): String? {
    val url = rawUrl.trim().trim('<', '>', '"', '\'').trimEnd('.', ',', ';')
    if (url.startsWith("data:image/", ignoreCase = true)) return url
    if (!url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://", ignoreCase = true)) {
        return null
    }
    return url.takeIf { it.length <= MaxRemoteImageUrlLength }
}

fun messagePartsToContent(
    parts: List<MessagePart>,
    encodeLocalImage: (String) -> String,
    includeLocalImages: Boolean = true
): List<ContentPartDto> {
    val historyParts = if (includeLocalImages) {
        parts
    } else {
        parts.mapNotNull { part ->
            when (part) {
                is MessagePart.TextPart -> part
                is MessagePart.RemoteImagePart -> part
                is MessagePart.LocalImagePart -> null
                is MessagePart.LocalFilePart -> null
            }
        }
    }
    return historyParts.map { part ->
        when (part) {
            is MessagePart.TextPart -> TextContentPartDto(part.text)
            is MessagePart.RemoteImagePart -> ImageUrlContentPartDto(ImageUrlDto(part.url))
            is MessagePart.LocalImagePart -> ImageUrlContentPartDto(ImageUrlDto(encodeLocalImage(part.uri)))
            is MessagePart.LocalFilePart -> TextContentPartDto("附件：${part.name}（${part.sizeBytes} bytes）")
        }
    }
}

fun estimateTextTokens(text: String): Int {
    if (text.isBlank()) return 0
    var tokens = 0
    var asciiRun = 0
    text.forEach { char ->
        when {
            char.isWhitespace() -> {
                tokens += (asciiRun + 3) / 4
                asciiRun = 0
            }
            char.code <= 0x7F -> asciiRun += 1
            else -> {
                tokens += (asciiRun + 3) / 4 + 1
                asciiRun = 0
            }
        }
    }
    return tokens + (asciiRun + 3) / 4
}

fun estimateMessageTokens(parts: List<MessagePart>): Int {
    return parts.sumOf { part ->
        when (part) {
            is MessagePart.TextPart -> estimateTextTokens(part.text)
            is MessagePart.RemoteImagePart -> ImageTokenEstimate
            is MessagePart.LocalImagePart -> ImageTokenEstimate
            is MessagePart.LocalFilePart -> estimateTextTokens(part.name) + 32
        }
    }
}

private fun selectContextMessages(
    messages: List<ChatMessage>,
    config: ApiConfig,
    reservedTokens: Int = 0
): List<ChatMessage> {
    if (messages.isEmpty()) return messages
    val lastMessage = messages.last()
    val history = messages.dropLast(1)
    val memoryLimitedHistory = when (config.contextMemory) {
        "关闭" -> emptyList()
        "短期" -> history.takeLast(8)
        else -> history
    }
    val budget = (config.contextWindow - reservedTokens - estimateMessageTokens(lastMessage.parts))
        .coerceAtLeast(0)
    var usedTokens = 0
    val selectedHistory = mutableListOf<ChatMessage>()
    for (message in memoryLimitedHistory.asReversed()) {
        val tokens = estimateMessageTokens(message.parts)
        if (usedTokens + tokens > budget) break
        selectedHistory += message
        usedTokens += tokens
    }
    return selectedHistory.asReversed() + lastMessage
}

private fun ChatMessage.isTransientFailureMessage(): Boolean {
    if (role != MessageRole.ASSISTANT) return false
    val text = parts.filterIsInstance<MessagePart.TextPart>().joinToString("\n") { it.text }
    return text.contains("HTTP 400", ignoreCase = true) ||
        text.contains("bad_response_status_code", ignoreCase = true) ||
        text.contains("模型请求失败", ignoreCase = true) ||
        text.contains("请求失败", ignoreCase = true)
}

fun buildEffectiveSystemPrompt(
    basePrompt: String,
    config: ApiConfig,
    requestMode: ChatRequestMode = ChatRequestMode.CHAT
): String {
    val assistantInstruction = config.assistantName
        .trim()
        .takeIf { it.isNotBlank() }
        ?.let { "助手名称：$it。回答时以该助手身份保持一致。" }
    val styleInstruction = when (config.replyStyle) {
        "精准" -> "回复风格：精准。优先给出直接结论，减少发散和不必要解释。"
        "创意" -> "回复风格：创意。在满足用户目标的前提下，可以给出更多启发式方案和替代思路。"
        else -> "回复风格：平衡。兼顾准确性、完整性和可读性。"
    }
    val reasoningInstruction = when (config.reasoningStrength) {
        "低" -> "推理强度：低。优先快速回答，只展开必要步骤。"
        "高" -> "推理强度：高。处理复杂问题时先充分分析约束、边界和风险，再给出答案。"
        else -> "推理强度：中。保持适度分析，避免遗漏关键前提。"
    }
    val memoryInstruction = when (config.contextMemory) {
        "关闭" -> "上下文记忆：关闭。除当前用户消息外，不依赖历史对话。"
        "短期" -> "上下文记忆：短期。只参考最近几轮对话。"
        else -> "上下文记忆：长期。在上下文窗口允许范围内参考完整历史。"
    }
    val modeInstruction = when (requestMode) {
        ChatRequestMode.IMAGE_GENERATION ->
            "当前模式：生图。用户本次消息是明确的图片生成或图片编辑请求，请直接按图片生成请求处理。"
        ChatRequestMode.CHAT ->
            "当前模式：普通聊天。不要直接生成图片、返回图片数据或把消息自动转成生图请求；如果用户确实想生图，请提示先点击输入框上方的“生图”。"
    }
    return listOfNotNull(basePrompt, assistantInstruction, styleInstruction, reasoningInstruction, memoryInstruction, modeInstruction)
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
}

enum class ChatRequestMode {
    CHAT,
    IMAGE_GENERATION
}

private fun ApiConfig.requestMaxTokens(): Int? {
    return maxTokens.takeIf { it > 0 }
}

private fun ApiConfig.outputTokenReserve(): Int {
    return maxTokens.takeIf { it > 0 } ?: 0
}

private fun ApiConfig.reasoningEffortForModel(): String? {
    val model = selectedModel.lowercase()
    val supportsReasoningEffort = listOf("gpt-5", "o1", "o3", "o4").any { it in model }
    if (!supportsReasoningEffort) return null
    return when (reasoningStrength) {
        "Low" -> "low"
        "低" -> "low"
        "Medium" -> "medium"
        "中" -> "medium"
        "High" -> "high"
        "高" -> "high"
        else -> null
    }
}

private fun isUsableApiConfig(config: ApiConfig): Boolean {
    return isValidHttpsBaseUrl(config.baseUrl) &&
        config.apiKey.isNotBlank() &&
        config.selectedModel.isNotBlank()
}

fun safeUserError(error: Throwable): String {
    val message = error.message.orEmpty()
    val fallback = "请求失败，请稍后重试"
    if (message.isBlank()) return fallback
    val redacted = message
        .replace(Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+"), "Bearer ***")
        .replace(Regex("sk-[A-Za-z0-9_-]{4,}"), "sk-***")
        .replace(Regex("tvly-[A-Za-z0-9_-]{4,}"), "tvly-***")
        .replace(Regex("AIza[0-9A-Za-z_-]{8,}"), "AIza***")
    return redacted.take(180)
}

private fun Response<*>.userFacingHttpError(): String {
    return when (code()) {
        400 -> "模型不支持当前生图请求格式，请切换支持图片生成的模型或调整提示词"
        401, 403 -> "API Key 无效或权限不足，请检查配置"
        404 -> "模型或接口地址不可用，请检查 Base URL 和模型名称"
        429 -> "请求过于频繁或额度不足，请稍后再试"
        in 500..599 -> "模型服务暂时不可用，请稍后再试"
        else -> "模型请求失败：HTTP ${code()}"
    }
}

private const val ImageTokenEstimate = 256
private const val MaxImageReferenceCount = 4
private const val MaxRemoteImageUrlLength = 4096
private const val MaxGeneratedImageBase64Chars = 28_000_000
private val MarkdownImageRegex = Regex("!\\[[^]]*]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)")
private val PlainImageRegex = Regex(
    "(?i)(https?://\\S+?\\.(?:png|jpe?g|webp|gif)(?:\\?\\S*)?|data:image/(?:png|jpe?g|webp);base64,[A-Za-z0-9+/=\\r\\n]+)"
)
private val DataImageRegex = Regex(
    "(?i)data:image/(png|jpe?g|webp);base64,([A-Za-z0-9+/=\\r\\n]+)"
)
private val ExplicitImagePromptRegexes = listOf(
    Regex("生图|文生图|图生图|画图", RegexOption.IGNORE_CASE),
    Regex("生成\\s*(?:一?张|[0-9]+张)?\\s*(?:图片|图像|照片|插画|海报|壁纸)", RegexOption.IGNORE_CASE),
    Regex("生成\\s*(?:一?张|[0-9]+张)\\s*图(?!表|标|例|形)", RegexOption.IGNORE_CASE),
    Regex("(?:^|[\\n，。！？；,!?;])\\s*(?:帮我|给我|请)?\\s*(?:画|绘制)\\s*(?:一?张|一个|一只|一条|一位|一名|一幅|[0-9]+张)\\s*\\S+", RegexOption.IGNORE_CASE),
    Regex("(?:编辑|修改|改|修|重绘)\\s*(?:这张|这幅|这个)?\\s*(?:图片|图像|图|照片)", RegexOption.IGNORE_CASE),
    Regex("(?:create|generate|draw)\\s+(?:an?\\s+)?(?:image|picture|photo|illustration|poster|wallpaper)", RegexOption.IGNORE_CASE),
    Regex("image\\s+generation", RegexOption.IGNORE_CASE)
)
private val ImageEditPromptRegex = Regex(
    "改成|变成|替换|去掉|去除|删除|加上|添加|标注|重绘|修图|编辑|修改|抠图|换背景|换色|上色|修复",
    RegexOption.IGNORE_CASE
)

internal fun shouldShowImageGenerationPlaceholder(parts: List<MessagePart>): Boolean {
    val hasImageAttachment = parts.any { it is MessagePart.LocalImagePart || it is MessagePart.RemoteImagePart }
    val text = parts.filterIsInstance<MessagePart.TextPart>().joinToString("\n") { it.text }
    return ExplicitImagePromptRegexes.any { it.containsMatchIn(text) } ||
        (hasImageAttachment && ImageEditPromptRegex.containsMatchIn(text))
}

private val ImageInspirationThemes = listOf(
    "雨夜城市街角",
    "清晨山谷与薄雾",
    "未来感室内空间",
    "海边小镇日落",
    "复古科幻实验室",
    "东方园林月色",
    "沙漠公路与远山",
    "冬日森林小屋",
    "玻璃温室植物",
    "宇航员日常生活",
    "霓虹市场人群",
    "极简产品摄影",
    "童话书插画场景",
    "电影感人物肖像",
    "水下遗迹探索",
    "屋顶花园午后"
)

private val ImageInspirationIntroRegex = Regex(
    "^(?:当然(?:可以)?|好的|可以|没问题|以下|这里|为你|给你|随机).*",
    RegexOption.IGNORE_CASE
)
private val ImageInspirationLabelRegex = Regex(
    "^(?:生图提示词|提示词|画面描述|创意画面描述|画面)\u005cs*[:：]\u005cs*",
    RegexOption.IGNORE_CASE
)

private fun buildImageInspirationRequestText(): String {
    val seed = UUID.randomUUID()
    val themeIndex = ((seed.leastSignificantBits and Long.MAX_VALUE) % ImageInspirationThemes.size).toInt()
    val theme = ImageInspirationThemes[themeIndex]
    return "随机种子：${seed.toString().take(8)}。主题方向：$theme。请直接输出一条全新的中文生图画面描述，第一句必须就是画面本身，禁止出现“当然、以下、提示词、画面描述、随机灵感”等说明性开头。"
}

internal fun cleanImageInspirationResponse(content: String?): String? {
    val normalized = content
        ?.trim()
        ?.trim('"', '“', '”', '\'', '‘', '’')
        ?.takeIf { it.isNotBlank() }
        ?: return null

    return normalized
        .lineSequence()
        .map(::cleanImageInspirationLine)
        .filter { it.isNotBlank() }
        .firstOrNull { !ImageInspirationIntroRegex.matches(it) }
        ?.take(1000)
}

private fun cleanImageInspirationLine(line: String): String {
    var cleaned = line
        .trim()
        .trim('"', '“', '”', '\'', '‘', '’')
        .replace(Regex("^[-•*]\\s*"), "")
        .replace(Regex("^\\d+[.、)\\]]\\s*"), "")
        .trim()

    val colonIndex = cleaned.indexOfAny(charArrayOf('：', ':'))
    if (colonIndex in 1..48) {
        val prefix = cleaned.take(colonIndex)
        if (
            prefix.contains("提示词") ||
            prefix.contains("描述") ||
            prefix.startsWith("当然") ||
            prefix.startsWith("以下") ||
            prefix.startsWith("这里") ||
            prefix.startsWith("随机")
        ) {
            cleaned = cleaned.drop(colonIndex + 1).trim()
        }
    }

    return cleaned
        .replace(ImageInspirationLabelRegex, "")
        .trim()
        .trim('"', '“', '”', '\'', '‘', '’')
        .trim()
}

@Serializable
private data class StreamChatCompletionsResponse(
    val choices: List<StreamChoiceDto> = emptyList(),
    val usage: UsageDto? = null
)

@Serializable
private data class StreamChoiceDto(
    val delta: StreamDeltaDto? = null
)

@Serializable
private data class StreamDeltaDto(
    val content: String? = null
)

private data class ActiveSend(
    val job: Job,
    val result: CompletableDeferred<Result<Unit>>
)

data class ImageGenerationTask(
    val id: String,
    val sessionId: Long?,
    val prompt: String,
    val referenceImageUris: List<String>,
    val startedAt: Long
)

@OptIn(ExperimentalSerializationApi::class)
private val messagePartsJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    classDiscriminator = "type"
}
@OptIn(ExperimentalSerializationApi::class)
private val legacyMessagePartsJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    classDiscriminator = "type"
}

fun decodeMessageParts(partsJson: String): List<MessagePart> {
    val normalized = partsJson.trim()
    if (normalized.isBlank()) return listOf(MessagePart.TextPart("消息为空"))

    runCatching { messagePartsJson.decodeFromString<List<MessagePart>>(normalized) }
        .getOrNull()
        ?.let { return it }

    val legacyJson = normalizeLegacyMessagePartDiscriminators(normalized)
    runCatching { legacyMessagePartsJson.decodeFromString<List<MessagePart>>(legacyJson) }
        .getOrNull()
        ?.let { return it }

    runCatching { messagePartsJson.decodeFromString<MessagePart>(legacyJson) }
        .getOrNull()
        ?.let { return listOf(it) }

    runCatching { messagePartsJson.decodeFromString<String>(normalized) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?.let { return listOf(MessagePart.TextPart(it)) }

    return listOf(MessagePart.TextPart("ERROR：消息加载失败，历史内容无法解析。"))
}

private val legacyMessagePartDiscriminatorPattern =
    Regex("""(?:[A-Za-z_]\w*\.)+domain\.model\.MessagePart\.(TextPart|RemoteImagePart|LocalImagePart|LocalFilePart)""")

private fun normalizeLegacyMessagePartDiscriminators(partsJson: String): String =
    legacyMessagePartDiscriminatorPattern.replace(partsJson) { match ->
        when (match.groupValues[1]) {
            "TextPart" -> "text"
            "RemoteImagePart" -> "remote_image"
            "LocalImagePart" -> "local_image"
            "LocalFilePart" -> "local_file"
            else -> match.value
        }
    }

fun encodeMessageParts(parts: List<MessagePart>): String {
    return messagePartsJson.encodeToString<List<MessagePart>>(parts)
}

@OptIn(ExperimentalSerializationApi::class)
class ChatRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)
    private val preferences = AppPreferences(appContext)
    private val json = messagePartsJson
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeSendLock = Any()
    private val activeSends = mutableMapOf<Long, ActiveSend>()
    private val activeSendSessionIds = MutableStateFlow<Set<Long>>(emptySet())
    private val activeImageGenerationTasks = MutableStateFlow<List<ImageGenerationTask>>(emptyList())

    fun observeConfig(): Flow<ApiConfig> = preferences.configFlow

    fun observeActiveImageGenerationTasks(): Flow<List<ImageGenerationTask>> {
        return activeImageGenerationTasks
    }

    fun observeRunningSessionIds(): Flow<Set<Long>> {
        return combine(activeSendSessionIds, activeImageGenerationTasks) { sendingIds, imageTasks ->
            sendingIds + imageTasks.mapNotNull { it.sessionId }
        }.distinctUntilChanged()
    }

    fun observeSessionSending(sessionId: Long): Flow<Boolean> {
        return activeSendSessionIds
            .map { sessionId in it }
            .distinctUntilChanged()
    }

    fun cancelSend(sessionId: Long) {
        synchronized(activeSendLock) {
            activeSends[sessionId]?.job
        }?.cancel(CancellationException("已停止生成"))
    }

    suspend fun saveConfig(config: ApiConfig) {
        preferences.saveConfig(config)
    }

    fun observeApiKeyEntries(): Flow<List<ApiKeyEntry>> = preferences.apiKeyEntriesFlow.map { it.visibleApiKeyEntries() }

    suspend fun saveApiKeyEntry(entry: ApiKeyEntry, makeCurrent: Boolean = true) {
        preferences.saveApiKeyEntry(entry, makeCurrent)
    }

    suspend fun useApiKeyEntry(entryId: String) {
        val entry = preferences.apiKeyEntriesFlow.first()
            .visibleApiKeyEntries()
            .firstOrNull { it.id == entryId }
            ?: throw IllegalStateException("无可用 API，请先配置后再使用")
        preferences.saveApiKeyEntry(entry, makeCurrent = true)
    }

    suspend fun migrateApiKeyStorage() {
        preferences.migrateApiKeyEntriesToSecureStore()
    }

    suspend fun deleteApiKeyEntry(entryId: String) {
        preferences.deleteApiKeyEntry(entryId)
    }

    private suspend fun currentResolvedConfig(): ApiConfig {
        val current = preferences.configFlow.first()
        val entries = preferences.apiKeyEntriesFlow.first().visibleApiKeyEntries()
        val entry = entries.bestMatch(
            baseUrl = current.baseUrl,
            apiKey = current.apiKey,
            selectedModel = current.selectedModel
        )
        if (entry == null) {
            return current
        }
        val shouldUseEntryModel = current.selectedModel.isBlank() ||
            (
                current.baseUrl.isNotBlank() &&
                    current.apiKey.isNotBlank() &&
                    current.baseUrl.trim().trimEnd('/') == entry.baseUrl.trim().trimEnd('/') &&
                    current.apiKey.trim() == entry.apiKey.trim()
                )
        return current.copy(
            baseUrl = entry.baseUrl.ifBlank { current.baseUrl },
            apiKey = entry.apiKey.ifBlank { current.apiKey },
            selectedModel = if (shouldUseEntryModel && entry.selectedModel.isNotBlank()) entry.selectedModel else current.selectedModel
        )
    }

    private suspend fun resolveConfigForApiEntry(entryId: String?): ApiConfig {
        val current = currentResolvedConfig()
        val id = entryId?.takeIf { it.isNotBlank() } ?: return current
        val entry = preferences.apiKeyEntriesFlow.first()
            .visibleApiKeyEntries()
            .firstOrNull { it.id == id }
            ?: throw IllegalStateException("无可用 API，请先配置后再使用")
        return current.copy(
            baseUrl = entry.baseUrl.ifBlank { current.baseUrl },
            apiKey = entry.apiKey.ifBlank { current.apiKey },
            selectedModel = entry.selectedModel.ifBlank { current.selectedModel }
        )
    }

    fun observeSessions(): Flow<List<ChatSession>> {
        return database.chatSessionDao().observeSessions().map { sessions ->
            sessions.map { entity -> entity.toChatSession() }
        }
    }

    fun observeFavoriteSessions(): Flow<List<ChatSession>> {
        return database.chatSessionDao().observeFavoriteSessions().map { sessions ->
            sessions.map { entity -> entity.toChatSession() }
        }
    }

    fun observeFavoriteSessionCount(): Flow<Int> {
        return database.chatSessionDao().observeFavoriteSessionCount()
    }

    fun observeSession(sessionId: Long): Flow<ChatSession?> {
        return database.chatSessionDao().observeSession(sessionId).map { entity ->
            entity?.toChatSession()
        }
    }

    fun observeMessages(sessionId: Long): Flow<List<ChatMessage>> {
        return database.chatMessageDao().observeMessages(sessionId).map { messages ->
            messages.map { entity -> entity.toChatMessage() }
        }
    }

    fun observeRecentMessages(sessionId: Long, limit: Int): Flow<List<ChatMessage>> {
        return database.chatMessageDao().observeRecentMessages(sessionId, limit).map { messages ->
            messages.map { entity -> entity.toChatMessage() }
        }
    }

    fun observeRecentGeneratedImages(limit: Int = 24): Flow<List<GeneratedImage>> {
        return database.chatMessageDao()
            .observeRecentImageMessages(MessageRole.ASSISTANT.name, limit * 2)
            .map { messages ->
                messages.flatMap { entity -> entity.toGeneratedImages() }
                    .filter { it.uri.isRenderableGeneratedImage() }
                    .sortedByDescending { it.createdAt }
                    .take(limit)
            }
    }

    fun observeAllGeneratedImages(): Flow<List<GeneratedImage>> {
        return database.chatMessageDao()
            .observeAllImageMessages(MessageRole.ASSISTANT.name)
            .map { messages ->
                messages.flatMap { entity -> entity.toGeneratedImages() }
                    .filter { it.uri.isRenderableGeneratedImage() }
                    .sortedByDescending { it.createdAt }
            }
    }

    fun observeMessageCount(sessionId: Long): Flow<Int> {
        return database.chatMessageDao().observeMessageCount(sessionId)
    }

    suspend fun getSessionMessages(sessionId: Long): List<ChatMessage> {
        return observeMessages(sessionId).first()
    }

    suspend fun getLastMessage(sessionId: Long): ChatMessage? {
        return database.chatMessageDao().getLastMessage(sessionId)?.toChatMessage()
    }

    fun observeUsageStats(
        startTime: Long? = null,
        endTime: Long? = null
    ): Flow<UsageStats> {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val rangeStart = startTime ?: LocalDate.now()
            .minusDays(6)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        val rangeEnd = endTime ?: (now + 1)
        val localOffsetMillis = ZoneId.systemDefault()
            .rules
            .getOffset(java.time.Instant.now())
            .totalSeconds * 1000L
        val messageDao = database.chatMessageDao()
        return combine(
            messageDao.observeActiveSessionCountBetween(rangeStart, rangeEnd),
            messageDao.observeMessageCountBetween(rangeStart, rangeEnd),
            messageDao.observeMessageCountByRoleBetween(MessageRole.USER.name, rangeStart, rangeEnd),
            messageDao.observeMessageCountByRoleBetween(MessageRole.ASSISTANT.name, rangeStart, rangeEnd),
            messageDao.observeMessageCountBetween(rangeStart, rangeEnd),
            messageDao.observeTokenStatsBetween(rangeStart, rangeEnd),
            messageDao.observeImageMessageCountBetween(MessageRole.ASSISTANT.name, rangeStart, rangeEnd),
            messageDao.observeModelTokenStatsBetween(rangeStart, rangeEnd),
            messageDao.observeDailyTokenStats(
                startTime = rangeStart,
                endTime = rangeEnd,
                localOffsetMillis = localOffsetMillis,
                assistantRole = MessageRole.ASSISTANT.name
            ),
            messageDao.observeModelTokenStatsBetween(rangeStart, rangeEnd)
        ) { values ->
            val promptTokens = values[5].let { (it as com.wuyousheng.modeltap.data.local.dao.TokenStatsRow).promptTokens }
            val completionTokens = values[5].let { (it as com.wuyousheng.modeltap.data.local.dao.TokenStatsRow).completionTokens }
            val totalTokens = values[5].let { (it as com.wuyousheng.modeltap.data.local.dao.TokenStatsRow).totalTokens }
            @Suppress("UNCHECKED_CAST")
            val dailyRows = values[8] as List<com.wuyousheng.modeltap.data.local.dao.DailyTokenStatsRow>
            @Suppress("UNCHECKED_CAST")
            val modelTokenRows = values[9] as List<com.wuyousheng.modeltap.data.local.dao.ModelTokenStatsRow>
            val labelFormatter = DateTimeFormatter.ofPattern("MM-dd")
            val dailyByDate = dailyRows.associateBy {
                java.time.Instant.ofEpochMilli(it.dayStart).atZone(zone).toLocalDate()
            }
            val startDate = java.time.Instant.ofEpochMilli(rangeStart).atZone(zone).toLocalDate()
            val endDateExclusive = java.time.Instant.ofEpochMilli((rangeEnd - 1).coerceAtLeast(rangeStart)).atZone(zone).toLocalDate().plusDays(1)
            val days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDateExclusive).coerceAtLeast(1)
            val dailyUsage = (0L until days).map { offset ->
                val date = startDate.plusDays(offset)
                val row = dailyByDate[date]
                DailyUsageStat(
                    label = date.format(labelFormatter),
                    totalTokens = row?.totalTokens ?: ((row?.promptTokens ?: 0) + (row?.completionTokens ?: 0)),
                    imageCount = row?.imageMessages ?: 0
                )
            }
            UsageStats(
                totalSessions = values[0] as Int,
                totalMessages = values[1] as Int,
                userMessages = values[2] as Int,
                assistantMessages = values[3] as Int,
                todayMessages = values[4] as Int,
                totalPromptTokens = promptTokens,
                totalCompletionTokens = completionTokens,
                totalTokens = totalTokens ?: promptTokens + completionTokens,
                imageMessages = values[6] as Int,
                modelUsage = modelTokenRows.map { row ->
                    ModelUsageStat(
                        modelId = row.modelId,
                        sessionCount = row.sessionCount,
                        totalTokens = row.totalTokens ?: (row.promptTokens + row.completionTokens)
                    )
                },
                dailyUsage = dailyUsage
            )
        }
    }

    suspend fun createSession(title: String): Long {
        val config = currentResolvedConfig()
        val now = System.currentTimeMillis()
        return database.chatSessionDao().insert(
            ChatSessionEntity(
                title = title,
                modelId = config.selectedModel,
                systemPrompt = "",
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun createSessionAndLaunchMessage(
        title: String,
        parts: List<MessagePart>,
        webSearchOverride: Boolean? = null,
        includeHistory: Boolean = true,
        configOverride: ApiConfig? = null,
        requestMode: ChatRequestMode = ChatRequestMode.CHAT
    ): Long {
        val sessionId = createSession(title)
        launchSendMessage(
            sessionId = sessionId,
            parts = parts,
            webSearchOverride = webSearchOverride,
            includeHistory = includeHistory,
            configOverride = configOverride,
            requestMode = requestMode
        )
        return sessionId
    }

    suspend fun createImageSessionAndGenerate(
        prompt: String,
        apiEntryId: String? = null,
        referenceImageUris: List<String> = emptyList()
    ): Result<Long> {
        return runCatching {
            val trimmedPrompt = prompt.trim()
            require(trimmedPrompt.isNotBlank()) { "请输入生图描述" }
            val config = resolveConfigForApiEntry(apiEntryId)
            require(isUsableApiConfig(config)) { "无可用 API，请先配置后再使用" }
            val now = System.currentTimeMillis()
            val sessionId = database.chatSessionDao().insert(
                ChatSessionEntity(
                    title = "AI生图",
                    modelId = config.selectedModel,
                    systemPrompt = "",
                    createdAt = now,
                    updatedAt = now
                )
            )
            val referenceUris = referenceImageUris
                .filter { it.isNotBlank() }
                .distinct()
                .take(MaxImageReferenceCount)
            val task = ImageGenerationTask(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                prompt = trimmedPrompt,
                referenceImageUris = referenceUris,
                startedAt = System.currentTimeMillis()
            )
            activeImageGenerationTasks.update { current -> listOf(task) + current }
            val messageParts = buildList {
                add(MessagePart.TextPart(trimmedPrompt))
                referenceUris.forEach { add(MessagePart.LocalImagePart(it)) }
            }
            try {
                sendMessage(
                    sessionId = sessionId,
                    parts = messageParts,
                    webSearchOverride = false,
                    includeHistory = false,
                    configOverride = config,
                    requestMode = ChatRequestMode.IMAGE_GENERATION
                ).getOrThrow()
                sessionId
            } finally {
                activeImageGenerationTasks.removeTask(task.id)
            }
        }
    }

    suspend fun generateImageInspiration(apiEntryId: String? = null): Result<String> {
        return runCatching {
            apiEntryId?.takeIf { it.isNotBlank() }?.let { useApiKeyEntry(it) }
            val config = currentResolvedConfig()
            require(isUsableApiConfig(config)) { "无可用 API，请先配置后再使用" }
            val api = ApiClientFactory.create(config.baseUrl, config.apiKey)
            val response = api.createChatCompletion(
                ChatCompletionsRequest(
                    model = config.selectedModel,
                    messages = listOf(
                        ChatMessagePayload(
                            role = MessageRole.SYSTEM.name.lowercase(),
                            content = listOf(
                                TextContentPartDto(
                                    "你是生图提示词助手。只输出一条中文生图提示词，不要解释，不要编号，长度 40 到 90 字。输出第一句必须直接描写画面主体，不要寒暄，不要写“当然、以下、提示词、画面描述”。"
                                )
                            )
                        ),
                        ChatMessagePayload(
                            role = MessageRole.USER.name.lowercase(),
                            content = listOf(
                                TextContentPartDto(
                                    buildImageInspirationRequestText()
                                )
                            )
                        )
                    ),
                    temperature = 1.1f,
                    maxTokens = 160,
                    topP = 0.95f,
                    stream = false
                )
            )
            cleanImageInspirationResponse(response.choices.firstOrNull()?.message?.content)
                ?: throw IOException("模型未返回随机灵感")
        }
    }

    suspend fun getOrCreateLatestSessionId(): Long {
        return database.chatSessionDao().getLatest()?.id ?: createSession("新会话")
    }

    suspend fun deleteSession(sessionId: Long) {
        cancelSend(sessionId)
        database.chatSessionDao().delete(sessionId)
    }

    suspend fun deleteSessions(sessionIds: Collection<Long>) {
        val ids = sessionIds
            .filter { it > 0L }
            .distinct()
        if (ids.isEmpty()) return
        ids.forEach(::cancelSend)
        database.chatSessionDao().deleteSessions(ids)
    }

    suspend fun renameSession(sessionId: Long, title: String) {
        val session = database.chatSessionDao().get(sessionId) ?: return
        database.chatSessionDao().update(
            session.copy(
                title = title.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun setSessionFavorite(sessionId: Long, isFavorite: Boolean) {
        if (sessionId <= 0L) return
        database.chatSessionDao().setFavorite(
            sessionId = sessionId,
            isFavorite = isFavorite
        )
    }

    suspend fun toggleSessionFavorite(sessionId: Long) {
        val session = database.chatSessionDao().get(sessionId) ?: return
        setSessionFavorite(sessionId, !session.isFavorite)
    }

    suspend fun deleteMessage(messageId: Long) {
        database.chatMessageDao().delete(messageId)
    }

    suspend fun updateSessionPrompt(sessionId: Long, prompt: String) {
        val session = database.chatSessionDao().get(sessionId) ?: return
        database.chatSessionDao().update(
            session.copy(
                systemPrompt = prompt.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun estimateOutgoingTokens(parts: List<MessagePart>): Int {
        return estimateMessageTokens(parts)
    }

    suspend fun estimateContextTokens(sessionId: Long, outgoing: List<MessagePart>): Int {
        val config = currentResolvedConfig()
        val session = database.chatSessionDao().get(sessionId)
        val prompt = buildEffectiveSystemPrompt(
            basePrompt = session?.systemPrompt?.ifBlank { config.systemPrompt }.orEmpty(),
            config = config
        )
        val history = selectContextMessages(
            messages = observeMessages(sessionId).first(),
            config = config,
            reservedTokens = estimateTextTokens(prompt) + config.outputTokenReserve()
        )
        return estimateTextTokens(prompt) +
            history.sumOf { estimateMessageTokens(it.parts) } +
            estimateMessageTokens(outgoing)
    }

    suspend fun getLastUserMessage(sessionId: Long): ChatMessage? {
        val entity = database.chatMessageDao().getLastMessageByRole(sessionId, MessageRole.USER.name) ?: return null
        return entity.toChatMessage()
    }

    suspend fun fetchModels(): List<ModelOption> {
        val config = currentResolvedConfig()
        require(isUsableApiConfig(config)) { "无可用 API，请先配置后再使用" }
        val api = ApiClientFactory.create(config.baseUrl, config.apiKey)
        return api.getModels().data.mapNotNull { model ->
            model.id?.takeIf { it.isNotBlank() }?.let { id -> ModelOption(id = id, ownedBy = model.ownedBy) }
        }
    }

    suspend fun fetchModels(baseUrl: String, apiKey: String): List<ModelOption> {
        require(isValidHttpsBaseUrl(baseUrl) && apiKey.isNotBlank()) { "无可用 API，请先配置后再使用" }
        val api = ApiClientFactory.create(baseUrl, apiKey.trim())
        return api.getModels().data.mapNotNull { model ->
            model.id?.takeIf { it.isNotBlank() }?.let { id -> ModelOption(id = id, ownedBy = model.ownedBy) }
        }
    }

    suspend fun testTavilySearch(apiKey: String): Int {
        val key = apiKey.trim()
        require(key.isNotBlank()) { "请先填写 Tavily API Key" }
        val response = TavilySearchClientFactory.create(key)
            .search(TavilySearchRequest(query = "OpenAI", maxResults = 1))
        return response.results.size
    }

    suspend fun sendMessage(
        sessionId: Long,
        parts: List<MessagePart>,
        webSearchOverride: Boolean? = null,
        includeHistory: Boolean = true,
        configOverride: ApiConfig? = null,
        requestMode: ChatRequestMode = ChatRequestMode.CHAT
    ): Result<Unit> {
        return launchSendMessage(sessionId, parts, webSearchOverride, includeHistory, configOverride, requestMode).await()
    }

    private fun launchSendMessage(
        sessionId: Long,
        parts: List<MessagePart>,
        webSearchOverride: Boolean?,
        includeHistory: Boolean,
        configOverride: ApiConfig?,
        requestMode: ChatRequestMode
    ): CompletableDeferred<Result<Unit>> {
        synchronized(activeSendLock) {
            if (activeSends.containsKey(sessionId)) {
                return completedSendResult(
                    Result.failure(IllegalStateException("当前会话正在回复，请稍后再发"))
                )
            }

            val result = CompletableDeferred<Result<Unit>>()
            val job = repositoryScope.launch(start = CoroutineStart.LAZY) {
                val outcome = sendMessageInternal(sessionId, parts, webSearchOverride, includeHistory, configOverride, requestMode)
                if (!result.isCompleted) {
                    result.complete(outcome)
                }
            }
            val activeSend = ActiveSend(job = job, result = result)
            activeSends[sessionId] = activeSend
            activeSendSessionIds.update { it + sessionId }
            job.invokeOnCompletion { cause ->
                if (cause != null && !result.isCompleted) {
                    result.complete(Result.failure(cause))
                }
                synchronized(activeSendLock) {
                    if (activeSends[sessionId] === activeSend) {
                        activeSends.remove(sessionId)
                    }
                }
                activeSendSessionIds.update { it - sessionId }
            }
            job.start()
            return result
        }
    }

    private fun completedSendResult(result: Result<Unit>): CompletableDeferred<Result<Unit>> {
        return CompletableDeferred<Result<Unit>>().also { it.complete(result) }
    }

    private fun MutableStateFlow<List<ImageGenerationTask>>.removeTask(taskId: String) {
        update { current -> current.filterNot { it.id == taskId } }
    }

    private suspend fun sendMessageInternal(
        sessionId: Long,
        parts: List<MessagePart>,
        webSearchOverride: Boolean? = null,
        includeHistory: Boolean = true,
        configOverride: ApiConfig? = null,
        requestMode: ChatRequestMode = ChatRequestMode.CHAT
    ): Result<Unit> {
        return runCatching {
            val config = configOverride ?: currentResolvedConfig()
            require(isUsableApiConfig(config)) { "无可用 API，请先配置后再使用" }

            val session = database.chatSessionDao().get(sessionId)
                ?: throw IllegalStateException("会话不存在或已被删除")
            val api = ApiClientFactory.create(config.baseUrl, config.apiKey)

            database.chatMessageDao().insert(
                ChatMessageEntity(
                    sessionId = sessionId,
                    role = MessageRole.USER.name,
                    partsJson = encodeMessageParts(parts),
                    promptTokens = estimateMessageTokens(parts),
                    createdAt = System.currentTimeMillis()
                )
            )

            val allMessages = observeMessages(sessionId).first()
            val shouldIncludeHistory = includeHistory && !session.title.equals("AI生图", ignoreCase = true)
            val effectivePrompt = buildEffectiveSystemPrompt(
                basePrompt = session.systemPrompt.ifBlank { config.systemPrompt },
                config = config,
                requestMode = requestMode
            )
            val history = if (shouldIncludeHistory) {
                selectContextMessages(
                    messages = allMessages.filterNot { it.isTransientFailureMessage() },
                    config = config,
                    reservedTokens = estimateTextTokens(effectivePrompt) + config.outputTokenReserve()
                )
            } else {
                allMessages.lastOrNull()?.let { listOf(it) }.orEmpty()
            }
            val webSearchContext = buildWebSearchContext(config, parts, webSearchOverride)
            val payload = withContext(Dispatchers.Default) {
                buildList {
                    val systemContent = buildList {
                        if (effectivePrompt.isNotBlank()) add(effectivePrompt)
                        if (webSearchContext.isNotBlank()) add(webSearchContext)
                    }.joinToString("\n\n")
                    if (systemContent.isNotBlank()) {
                        add(
                            ChatMessagePayload(
                                role = MessageRole.SYSTEM.name.lowercase(),
                                content = listOf(TextContentPartDto(systemContent))
                            )
                        )
                    }
                    addAll(
                        history.dropLast(1).mapNotNull { message ->
                            val content = messagePartsToContent(
                                message.parts,
                                ::encodeImage,
                                includeLocalImages = false
                            )
                            if (content.isEmpty()) null else ChatMessagePayload(
                                role = message.role.name.lowercase(),
                                content = content
                            )
                        }
                    )
                    history.lastOrNull()?.let { message ->
                        add(
                            ChatMessagePayload(
                                role = message.role.name.lowercase(),
                                content = messagePartsToContent(message.parts, ::encodeImage)
                            )
                        )
                    }
                }
            }

            val assistantMessage = ChatMessageEntity(
                sessionId = sessionId,
                role = MessageRole.ASSISTANT.name,
                partsJson = encodeMessageParts(listOf(MessagePart.TextPart(buildGeneratingPlaceholder(requestMode)))),
                createdAt = System.currentTimeMillis()
            )
            val assistantMessageId = database.chatMessageDao().insert(assistantMessage)
            val contentBuilder = StringBuilder()
            var usage: TokenUsage? = null

            runCatching {
                val request = ChatCompletionsRequest(
                    model = config.selectedModel,
                    messages = payload,
                    temperature = config.temperature,
                    maxTokens = config.requestMaxTokens(),
                    topP = config.topP,
                    reasoningEffort = config.reasoningEffortForModel(),
                    stream = true,
                    streamOptions = StreamOptionsDto()
                )
                val call = api.streamChatCompletionCall(request)
                withContext(Dispatchers.IO) {
                    val coroutineContext = currentCoroutineContext()
                    val job = coroutineContext[Job]
                    val cancellationHandle = job?.invokeOnCompletion { cause ->
                        if (cause is CancellationException) {
                            call.cancel()
                        }
                    }
                    try {
                        val response = call.execute()
                        try {
                            coroutineContext.ensureActive()
                            if (!response.isSuccessful) {
                                response.errorBody()?.close()
                                throw IOException(response.userFacingHttpError())
                            }
                            val responseBody = response.body() ?: throw IOException("模型未返回响应内容")
                            responseBody.byteStream().bufferedReader().useLines { lines ->
                                lines.forEach { line ->
                                    coroutineContext.ensureActive()
                                    if (!line.startsWith("data:")) return@forEach
                                    val data = line.removePrefix("data:").trim()
                                    if (data.isBlank() || data == "[DONE]") return@forEach

                                    val chunk = runCatching { json.decodeFromString<StreamChatCompletionsResponse>(data) }
                                        .getOrNull() ?: return@forEach
                                    chunk.usage?.let { dto ->
                                        usage = TokenUsage(
                                            promptTokens = dto.promptTokens,
                                            completionTokens = dto.completionTokens,
                                            totalTokens = dto.totalTokens
                                        )
                                    }
                                    val delta = chunk.choices.firstOrNull()?.delta?.content.orEmpty()
                                    if (delta.isNotEmpty()) {
                                        contentBuilder.append(delta)
                                        database.chatMessageDao().update(
                                            assistantMessage.copy(
                                                id = assistantMessageId,
                                            partsJson = encodeMessageParts(
                                                extractAssistantPartsForStorage(
                                                    contentBuilder.toString(),
                                                    saveDataImages = false,
                                                    allowImageParts = requestMode == ChatRequestMode.IMAGE_GENERATION
                                                )
                                            ),
                                                promptTokens = usage?.promptTokens,
                                                completionTokens = usage?.completionTokens,
                                                totalTokens = usage?.totalTokens
                                            )
                                        )
                                    }
                                }
                            }
                        } finally {
                            response.body()?.close()
                            response.errorBody()?.close()
                        }
                    } catch (error: Throwable) {
                        if (job?.isCancelled == true || call.isCanceled()) {
                            throw CancellationException("已停止生成").initCause(error)
                        }
                        throw error
                    } finally {
                        cancellationHandle?.dispose()
                        if (job?.isCancelled == true) {
                            call.cancel()
                        }
                    }
                }
            }.onFailure { error ->
                if (error is CancellationException) {
                    withContext(NonCancellable) {
                        database.chatMessageDao().delete(assistantMessageId)
                    }
                    throw error
                }
                val errorText = if (error is CancellationException) "已停止生成" else safeUserError(error)
                val errorParts = listOf(MessagePart.TextPart(errorText))
                withContext(NonCancellable) {
                    database.chatMessageDao().update(
                        assistantMessage.copy(
                            id = assistantMessageId,
                            partsJson = encodeMessageParts(errorParts),
                            completionTokens = estimateMessageTokens(errorParts),
                            totalTokens = usage?.promptTokens?.let { it + estimateMessageTokens(errorParts) }
                                ?: estimateMessageTokens(errorParts)
                        )
                    )
                }
                throw error
            }

            val assistantText = contentBuilder.toString().ifBlank { "模型未返回内容" }
            val assistantParts = extractAssistantPartsForStorage(
                assistantText,
                saveDataImages = true,
                allowImageParts = requestMode == ChatRequestMode.IMAGE_GENERATION
            )
            val fallbackCompletionTokens = estimateMessageTokens(assistantParts)
            database.chatMessageDao().update(
                assistantMessage.copy(
                    id = assistantMessageId,
                    partsJson = encodeMessageParts(assistantParts),
                    promptTokens = usage?.promptTokens,
                    completionTokens = usage?.completionTokens ?: fallbackCompletionTokens,
                    totalTokens = usage?.totalTokens ?: usage?.promptTokens?.let { it + fallbackCompletionTokens } ?: fallbackCompletionTokens
                )
            )

            database.chatSessionDao().update(
                session.copy(
                    title = buildSessionTitle(session.title, parts),
                    updatedAt = System.currentTimeMillis(),
                    modelId = config.selectedModel
                )
            )
        }
    }

    private fun extractAssistantPartsForStorage(
        content: String,
        saveDataImages: Boolean,
        allowImageParts: Boolean
    ): List<MessagePart> {
        if (!allowImageParts && content.contains("data:image/", ignoreCase = true)) {
            return listOf(MessagePart.TextPart("模型返回了图片数据，但当前是普通聊天模式。请先点击输入框上方的“生图”，再发送生图请求。"))
        }
        if (!saveDataImages && content.contains("data:image/", ignoreCase = true)) {
            return listOf(MessagePart.TextPart("正在生成图片，请稍候…"))
        }
        return extractAssistantParts(content).flatMap { part ->
            when (part) {
                is MessagePart.RemoteImagePart -> {
                    if (allowImageParts) {
                        listOf(part.toStoredImagePart())
                    } else {
                        listOf(MessagePart.TextPart(part.url))
                    }
                }
                is MessagePart.TextPart -> listOf(part.copy(text = redactInlineDataImages(part.text)))
                is MessagePart.LocalImagePart -> listOf(part)
                is MessagePart.LocalFilePart -> listOf(part)
            }
        }
    }

    private fun MessagePart.RemoteImagePart.toStoredImagePart(): MessagePart {
        if (!url.startsWith("data:image/", ignoreCase = true)) return this

        return runCatching {
            val match = DataImageRegex.find(url)
                ?: return MessagePart.TextPart("模型返回了无法识别的图片数据。")
            val extension = when (match.groupValues[1].lowercase()) {
                "jpeg", "jpg" -> "jpg"
                "webp" -> "webp"
                else -> "png"
            }
            val base64Data = match.groupValues[2].filterNot { it.isWhitespace() }
            if (base64Data.length > MaxGeneratedImageBase64Chars) {
                return MessagePart.TextPart("模型返回的图片数据过大，已跳过保存以避免闪退。")
            }
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            val directory = File(appContext.filesDir, "generated_images").apply { mkdirs() }
            val file = File(directory, "assistant_${System.currentTimeMillis()}_${UUID.randomUUID()}.$extension")
            file.writeBytes(bytes)
            MessagePart.LocalImagePart(Uri.fromFile(file).toString())
        }.getOrElse {
            MessagePart.TextPart("图片保存失败，请重新生成。")
        }
    }

    private fun redactInlineDataImages(text: String): String {
        return DataImageRegex.replace(text, "[图片数据已隐藏]")
    }

    private fun ChatMessageEntity.toChatMessage(): ChatMessage {
        return ChatMessage(
            id = id,
            sessionId = sessionId,
            role = runCatching { MessageRole.valueOf(role) }.getOrDefault(MessageRole.ASSISTANT),
            parts = decodeMessageParts(partsJson),
            usage = if (promptTokens != null || completionTokens != null || totalTokens != null) {
                TokenUsage(
                    promptTokens = promptTokens,
                    completionTokens = completionTokens,
                    totalTokens = totalTokens
                )
            } else {
                null
            },
            createdAt = createdAt
        )
    }

    private fun ChatSessionEntity.toChatSession(): ChatSession {
        return ChatSession(
            id = id,
            title = title,
            modelId = modelId,
            systemPrompt = systemPrompt,
            isFavorite = isFavorite,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun ChatMessageEntity.toGeneratedImages(): List<GeneratedImage> {
        val decodedParts = decodeMessageParts(partsJson)
        val imageUris = decodedParts.mapNotNull { part ->
            when (part) {
                is MessagePart.LocalImagePart -> part.uri
                is MessagePart.RemoteImagePart -> part.url
                else -> null
            }
        }
        val prompt = decodedParts.filterIsInstance<MessagePart.TextPart>()
            .joinToString(" ") { it.text }
            .replace(Regex("\\s+"), " ")
            .take(80)
        return imageUris.mapIndexed { index, uri ->
            GeneratedImage(
                id = "$id-$index",
                sessionId = sessionId,
                messageId = id,
                uri = uri,
                prompt = prompt,
                createdAt = createdAt
            )
        }
    }

    private fun String.isRenderableGeneratedImage(): Boolean {
        return if (startsWith("file:", ignoreCase = true)) {
            runCatching {
                val file = Uri.parse(this).path?.let(::File)
                file != null && file.exists() && file.length() > 0L
            }.getOrDefault(false)
        } else {
            false
        }
    }

    private fun buildGeneratingPlaceholder(requestMode: ChatRequestMode): String {
        val isImageRequest = requestMode == ChatRequestMode.IMAGE_GENERATION
        return if (isImageRequest) "正在生成图片，请稍候…" else "正在回复，请稍候…"
    }

    private suspend fun buildWebSearchContext(
        config: ApiConfig,
        parts: List<MessagePart>,
        webSearchOverride: Boolean?
    ): String {
        val enabled = webSearchOverride ?: config.webSearchEnabled
        if (!enabled || config.tavilyApiKey.isBlank()) return ""
        val query = parts.filterIsInstance<MessagePart.TextPart>()
            .joinToString("\n") { it.text }
            .trim()
            .take(400)
        if (query.isBlank()) return ""

        return runCatching {
            val response = TavilySearchClientFactory.create(config.tavilyApiKey)
                .search(TavilySearchRequest(query = query, maxResults = 5))
            response.toPromptContext()
        }.getOrElse { error ->
            "联网搜索失败：${safeUserError(error)}。请在无法确认实时信息时明确说明。"
        }
    }

    private fun TavilySearchResponse.toPromptContext(): String {
        val builder = StringBuilder()
        builder.appendLine("联网搜索结果：")
        answer?.takeIf { it.isNotBlank() }?.let { builder.appendLine("摘要：$it") }
        results.take(5).forEachIndexed { index, result ->
            val title = result.title.orEmpty().ifBlank { "搜索结果 ${index + 1}" }
            val url = result.url.orEmpty()
            val content = result.content.orEmpty().take(500)
            builder.appendLine("${index + 1}. $title")
            if (url.isNotBlank()) builder.appendLine("链接：$url")
            if (content.isNotBlank()) builder.appendLine("内容：$content")
        }
        builder.appendLine("请基于以上搜索结果回答，并在答案中给出相关链接。")
        return builder.toString()
    }

    private fun encodeImage(uriString: String): String {
        val uri = Uri.parse(uriString)
        val inputStream = appContext.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Unable to open image stream")
        val bitmap = inputStream.use { BitmapFactory.decodeStream(it) }
            ?: throw IllegalStateException("Unable to decode image")
        val encodedBitmap = bitmap.scaleDown(maxSize = 1600)
        val output = ByteArrayOutputStream()
        encodedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)
        val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }

    private fun Bitmap.scaleDown(maxSize: Int): Bitmap {
        val largestSide = maxOf(width, height)
        if (largestSide <= maxSize) return this
        val scale = maxSize.toFloat() / largestSide
        val matrix = Matrix().apply { postScale(scale, scale) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }
}
