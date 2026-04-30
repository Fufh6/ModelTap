package com.wuyousheng.modeltap.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wuyousheng.modeltap.domain.model.ChatMessage
import com.wuyousheng.modeltap.domain.model.MessagePart
import com.wuyousheng.modeltap.domain.model.MessageRole
import com.wuyousheng.modeltap.storage.saveImageToDownloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    isStreaming: Boolean = false
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var previewImage by remember { mutableStateOf<String?>(null) }
    val copyableText = message.parts.filterIsInstance<MessagePart.TextPart>().joinToString("\n") { it.text }
    val isUser = message.role == MessageRole.USER
    val bubbleColor = if (isUser) MessageUserBubble else MessageAssistantBubble
    val borderColor = if (isUser) Color.Transparent else MessageBubbleBorder
    val label = if (isUser) "我" else "AI"
    val avatarColor = if (isUser) MessageUserAccent else MessageAssistantAccent
    val contentColor = if (isUser) Color.White else MessageText
    val metaColor = if (isUser) Color.White.copy(alpha = 0.76f) else MessageMuted
    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 22.dp, topEnd = 8.dp, bottomStart = 22.dp, bottomEnd = 22.dp)
    } else {
        RoundedCornerShape(topStart = 8.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            MessageAvatar(label = label, color = avatarColor)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Card(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.82f else 0.84f),
            shape = bubbleShape,
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isUser) 2.dp else 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .border(1.dp, borderColor, bubbleShape)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${message.role.displayName()} · ${formatMessageTime(message.createdAt)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = metaColor
                    )
                    if (copyableText.isNotBlank()) {
                        TextButton(onClick = { clipboard.setText(AnnotatedString(copyableText)) }) {
                            Text(
                                text = "复制",
                                color = metaColor,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                message.parts.forEachIndexed { index, part ->
                    when (part) {
                        is MessagePart.TextPart -> {
                            if (message.role == MessageRole.ASSISTANT) {
                                MarkdownContent(part.text)
                            } else {
                                SelectionContainer {
                                    Text(
                                        text = part.text,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = contentColor
                                    )
                                }
                            }
                        }
                        is MessagePart.RemoteImagePart -> MessageImage(
                            model = part.url,
                            onPreview = { previewImage = it }
                        )
                        is MessagePart.LocalImagePart -> MessageImage(
                            model = part.uri,
                            onPreview = { previewImage = it }
                        )
                        is MessagePart.LocalFilePart -> FilePartView(part)
                    }
                    if (index != message.parts.lastIndex) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                if (isStreaming) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(99.dp))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (message.isImageGenerationPlaceholder()) {
                            "正在生成图片…"
                        } else {
                            "正在输出回复…"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MessageAssistantAccent
                    )
                }
                messageTokenText(message)?.let { tokenText ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = tokenText,
                        style = MaterialTheme.typography.labelSmall,
                        color = metaColor.copy(alpha = 0.7f),
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            MessageAvatar(label = label, color = avatarColor)
        }
    }

    previewImage?.let { image ->
        AlertDialog(
            onDismissRequest = { previewImage = null },
            text = {
                AsyncImage(
                    model = image,
                    contentDescription = "图片预览",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .heightIn(max = 520.dp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    context.saveImageToDownloads(image)
                                }
                            }.onSuccess {
                                Toast.makeText(context, "已保存到 Download/ModelTap", Toast.LENGTH_SHORT).show()
                            }.onFailure { error ->
                                Toast.makeText(
                                    context,
                                    error.message ?: "保存失败",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { previewImage = null }) {
                    Text("关闭")
                }
            }
        )
    }
}

@Composable
private fun MessageAvatar(label: String, color: Color) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun FilePartView(part: MessagePart.LocalFilePart) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.66f))
            .border(1.dp, MessageBubbleBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "文件：${part.name}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = formatFileSize(part.sizeBytes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun ChatMessage.isImageGenerationPlaceholder(): Boolean {
    return parts.filterIsInstance<MessagePart.TextPart>()
        .any { it.text.contains("生成图片") || it.text.contains("生图") }
}

@Composable
private fun MessageImage(
    model: String,
    onPreview: (String) -> Unit
) {
    val safeModel = remember(model) { model.trim().takeIf(::canRenderImageModel) }
    if (safeModel == null) {
        Text(
            text = "图片数据无法直接显示，请重新生成。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    AsyncImage(
        model = safeModel,
        contentDescription = "消息图片",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 280.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onPreview(safeModel) }
    )
}

private fun canRenderImageModel(model: String): Boolean {
    if (model.isBlank()) return false
    if (model.startsWith("data:image/", ignoreCase = true)) return false
    return model.length <= MaxRenderableImageModelLength
}

@Composable
private fun MarkdownContent(text: String) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Code -> CodeBlock(block)
                    is MarkdownBlock.Table -> CodeLikeBlock(block.lines.joinToString("\n"))
                    is MarkdownBlock.HorizontalRule -> Text(
                        text = "─".repeat(18),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    is MarkdownBlock.Paragraph -> ParagraphBlock(block)
                }
            }
        }
    }
}

@Composable
private fun ParagraphBlock(block: MarkdownBlock.Paragraph) {
    val style = when (block.headingLevel) {
        1 -> MaterialTheme.typography.titleLarge
        2 -> MaterialTheme.typography.titleMedium
        3 -> MaterialTheme.typography.titleSmall
        else -> MaterialTheme.typography.bodyLarge
    }
    Text(
        text = buildInlineMarkdown(block.text),
        style = style,
        fontWeight = if (block.headingLevel != null) FontWeight.SemiBold else null
    )
}

@Composable
private fun CodeBlock(block: MarkdownBlock.Code) {
    val language = block.language.takeIf { it.isNotBlank() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        language?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        CodeLikeBlock(block.code)
    }
}

@Composable
private fun CodeLikeBlock(content: String) {
    Text(
        text = content,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(10.dp)
    )
}

private fun messageTokenText(message: ChatMessage): String? {
    val usage = message.usage ?: return null
    val tokenCount = when (message.role) {
        MessageRole.USER -> usage.promptTokens
        MessageRole.ASSISTANT -> usage.totalTokens ?: usage.completionTokens
        MessageRole.SYSTEM -> usage.totalTokens ?: usage.promptTokens ?: usage.completionTokens
    } ?: return null
    return "token $tokenCount"
}

private const val MaxRenderableImageModelLength = 8192

private sealed interface MarkdownBlock {
    data class Paragraph(
        val text: String,
        val headingLevel: Int? = null
    ) : MarkdownBlock

    data class Code(
        val language: String,
        val code: String
    ) : MarkdownBlock

    data class Table(val lines: List<String>) : MarkdownBlock
    data object HorizontalRule : MarkdownBlock
}

private fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val lines = text.replace("\r\n", "\n").lines()
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = mutableListOf<String>()
    var inCode = false
    var codeLanguage = ""
    val codeLines = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraph.isEmpty()) return
        val paragraphText = paragraph.joinToString("\n").trim()
        if (paragraphText.isNotBlank()) {
            val heading = HeadingRegex.matchEntire(paragraphText)
            blocks += if (heading != null && !paragraphText.contains('\n')) {
                MarkdownBlock.Paragraph(
                    text = heading.groupValues[2].trim(),
                    headingLevel = heading.groupValues[1].length
                )
            } else if (paragraph.all { it.trim().startsWith("|") } && paragraph.any { it.contains("---") }) {
                MarkdownBlock.Table(paragraph.toList())
            } else {
                MarkdownBlock.Paragraph(paragraphText)
            }
        }
        paragraph.clear()
    }

    lines.forEach { line ->
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            if (inCode) {
                blocks += MarkdownBlock.Code(codeLanguage, codeLines.joinToString("\n"))
                codeLines.clear()
                codeLanguage = ""
                inCode = false
            } else {
                flushParagraph()
                codeLanguage = trimmed.removePrefix("```").trim()
                inCode = true
            }
            return@forEach
        }

        if (inCode) {
            codeLines += line
            return@forEach
        }

        if (trimmed.isBlank()) {
            flushParagraph()
            return@forEach
        }

        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            flushParagraph()
            blocks += MarkdownBlock.HorizontalRule
            return@forEach
        }

        paragraph += line
    }

    if (inCode) {
        blocks += MarkdownBlock.Code(codeLanguage, codeLines.joinToString("\n"))
    }
    flushParagraph()
    return blocks
}

private fun buildInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var index = 0
        InlineMarkdownRegex.findAll(text).forEach { match ->
            append(text.substring(index, match.range.first))
            val marker = match.groupValues[1]
                .ifBlank { match.groupValues[3] }
                .ifBlank { match.groupValues[5] }
            val content = match.groupValues[2]
                .ifBlank { match.groupValues[4] }
                .ifBlank { match.groupValues[6] }
            val style = when {
                marker == "`" -> SpanStyle(fontFamily = FontFamily.Monospace)
                marker.startsWith("**") || marker.startsWith("__") -> SpanStyle(fontWeight = FontWeight.SemiBold)
                marker.startsWith("*") || marker.startsWith("_") -> SpanStyle(fontWeight = FontWeight.Medium)
                else -> SpanStyle()
            }
            pushStyle(style)
            append(content)
            pop()
            index = match.range.last + 1
        }
        append(text.substring(index))
    }
}

private val HeadingRegex = Regex("^(#{1,6})\\s+(.+)$")
private val InlineMarkdownRegex = Regex("(`)([^`]+)`|(\\*\\*|__)(.+?)(?:\\*\\*|__)|(\\*|_)(.+?)(?:\\*|_)")

private val MessageText = Color(0xFF1F2937)
private val MessageMuted = Color(0xFF64748B)
private val MessageBubbleBorder = Color(0xFFE2E8F0)
private val MessageAssistantBubble = Color(0xFFFFFFFF)
private val MessageUserBubble = Color(0xFF3B82F6)
private val MessageAssistantAccent = Color(0xFF14B8A6)
private val MessageUserAccent = Color(0xFF2563EB)

private fun formatMessageTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes < 1024) return "$sizeBytes B"
    val kb = sizeBytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
    return String.format(Locale.getDefault(), "%.1f MB", kb / 1024.0)
}

private fun MessageRole.displayName(): String {
    return when (this) {
        MessageRole.USER -> "我"
        MessageRole.ASSISTANT -> "助手"
        MessageRole.SYSTEM -> "系统"
    }
}
