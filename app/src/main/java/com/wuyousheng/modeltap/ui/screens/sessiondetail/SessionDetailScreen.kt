package com.wuyousheng.modeltap.ui.screens.sessiondetail

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuyousheng.modeltap.R
import com.wuyousheng.modeltap.data.repository.ChatRepository
import com.wuyousheng.modeltap.domain.model.ChatMessage
import com.wuyousheng.modeltap.domain.model.ChatSession
import com.wuyousheng.modeltap.domain.model.MessagePart
import com.wuyousheng.modeltap.domain.model.MessageRole
import com.wuyousheng.modeltap.domain.model.modelDisplayFor
import com.wuyousheng.modeltap.ui.components.AppIcon
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionDetailScreen(
    repository: ChatRepository,
    sessionId: Long,
    onBack: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val session by repository.observeSession(sessionId).collectAsState(initial = null)
    val messages by repository.observeMessages(sessionId).collectAsState(initial = emptyList())
    val config by repository.observeConfig().collectAsState(initial = com.wuyousheng.modeltap.domain.model.ApiConfig())
    val apiKeyEntries by repository.observeApiKeyEntries().collectAsState(initial = emptyList())
    val modelDisplay = remember(session?.modelId, apiKeyEntries, config) {
        modelDisplayFor(session?.modelId.orEmpty(), apiKeyEntries, config)
    }
    var isRenaming by remember { mutableStateOf(false) }
    var editingTitle by remember(session?.title) { mutableStateOf(session?.title.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (isRenaming) {
        AlertDialog(
            onDismissRequest = { isRenaming = false },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(
                    value = editingTitle,
                    onValueChange = { editingTitle = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { repository.renameSession(sessionId, editingTitle) }
                        isRenaming = false
                    },
                    enabled = editingTitle.isNotBlank()
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { isRenaming = false }) { Text("取消") }
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除会话") },
            text = { Text("会话和其中的消息将被永久删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.deleteSession(sessionId)
                            confirmDelete = false
                            onDeleted()
                        }
                    }
                ) { Text("删除", color = DetailDanger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DetailBackground)
            .padding(horizontal = 18.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DetailTopBar(onBack = onBack)
        Spacer(modifier = Modifier.height(14.dp))
        SessionInfoCard(
            session = session,
            messages = messages,
            modelLabel = modelDisplay.label,
            channelLabel = modelDisplay.channelLabel,
            onRename = {
                editingTitle = session?.title.orEmpty()
                isRenaming = true
            }
        )
        Spacer(modifier = Modifier.height(18.dp))
        DetailActionCard(
            isFavorite = session?.isFavorite == true,
            onToggleFavorite = {
                scope.launch { repository.toggleSessionFavorite(sessionId) }
            },
            onRename = {
                editingTitle = session?.title.orEmpty()
                isRenaming = true
            },
            onExport = {
                scope.launch {
                    val export = buildMarkdownExport(session, repository.getSessionMessages(sessionId), modelDisplay.label)
                    shareText(context, "${session?.title.orEmpty().ifBlank { "会话记录" }}.md", export)
                }
            },
            onCopyLink = {
                val link = "codexapp://session/$sessionId"
                clipboard.setText(AnnotatedString(link))
                Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
            },
            onDelete = { confirmDelete = true }
        )
    }
}

@Composable
private fun DetailTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(R.drawable.ic_arrow_back_24, DetailText, Modifier.size(23.dp))
        }
        Text(
            text = "会话详情",
            color = DetailText,
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            AppIcon(R.drawable.ic_more_horizontal_24, DetailText, Modifier.size(23.dp))
        }
    }
}

@Composable
private fun SessionInfoCard(
    session: ChatSession?,
    messages: List<ChatMessage>,
    modelLabel: String,
    channelLabel: String,
    onRename: () -> Unit
) {
    DetailCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .border(1.dp, DetailBorder, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.modeltap_icon),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(45.dp)
                )
            }
            Spacer(modifier = Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = session?.title ?: "会话",
                        color = DetailText,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    AppIcon(
                        R.drawable.ic_edit_24,
                        DetailMuted,
                        Modifier
                            .size(20.dp)
                            .clickable(onClick = onRename)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "与 AI 的深度对话 · 探索前沿技术",
                    color = DetailMuted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        DetailDivider()
        InfoRow("◈", "使用模型", modelLabel)
        InfoRow("●", "接入渠道", channelLabel, iconColor = Color(0xFF35CDB6))
        InfoRow("☵", "消息数量", "${messages.size} 条")
        InfoRow("▥", "消耗估算", "≈ ¥${formatCost(messages)}")
        InfoRow("□", "创建时间", session?.createdAt?.let(::formatDateTime).orEmpty())
    }
}

@Composable
private fun DetailActionCard(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onCopyLink: () -> Unit,
    onDelete: () -> Unit
) {
    DetailCard {
        ActionRow("★", if (isFavorite) "取消收藏" else "收藏会话", DetailPrimary, onToggleFavorite)
        ActionRow("╱", "重命名", DetailPrimary, onRename)
        ActionRow("□", "导出记录（.md）", Color(0xFF20C7A8), onExport)
        ActionRow("∞", "复制链接", Color(0xFF4967FF), onCopyLink)
        ActionRow("▢", "删除会话", DetailDanger, onDelete)
    }
}

@Composable
private fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .border(1.dp, DetailBorder, RoundedCornerShape(22.dp))
            .padding(horizontal = 20.dp, vertical = 20.dp),
        content = content
    )
}

@Composable
private fun InfoRow(icon: String, label: String, value: String, iconColor: Color = DetailPrimary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DetailRowIcon(icon, iconColor)
        Text(label, color = DetailText, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(value, color = DetailText, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        AppIcon(
            R.drawable.ic_chevron_right_24,
            DetailMuted,
            Modifier
                .padding(start = 10.dp)
                .size(20.dp)
        )
    }
    DetailDivider()
}

@Composable
private fun ActionRow(icon: String, label: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, DetailBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DetailRowIcon(icon, color)
        Text(label, color = color, fontSize = 15.sp, modifier = Modifier.weight(1f))
        AppIcon(R.drawable.ic_chevron_right_24, DetailMuted, Modifier.size(20.dp))
    }
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun DetailRowIcon(icon: String, color: Color) {
    val resId = when (icon) {
        "◈" -> R.drawable.ic_diamond_24
        "●" -> R.drawable.ic_public_24
        "☵" -> R.drawable.ic_menu_24
        "▥" -> R.drawable.ic_diamond_24
        "□" -> R.drawable.ic_history_24
        "★" -> R.drawable.ic_diamond_24
        "╱" -> R.drawable.ic_edit_24
        "∞" -> R.drawable.ic_attach_file_24
        "▢" -> R.drawable.ic_close_24
        else -> R.drawable.ic_diamond_24
    }
    AppIcon(resId, color, Modifier.width(38.dp).size(21.dp))
}

@Composable
private fun DetailDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(DetailBorder)
    )
}

private fun buildMarkdownExport(session: ChatSession?, messages: List<ChatMessage>, modelLabel: String): String {
    val title = session?.title.orEmpty().ifBlank { "会话记录" }
    return buildString {
        appendLine("# $title")
        appendLine()
        appendLine("- 创建时间：${session?.createdAt?.let(::formatDateTime).orEmpty()}")
        appendLine("- 使用模型：$modelLabel")
        appendLine("- 消息数量：${messages.size}")
        appendLine()
        messages.forEach { message ->
            appendLine("## ${message.role.exportName()} · ${formatDateTime(message.createdAt)}")
            appendLine()
            appendLine(message.parts.joinToString("\n") { it.exportText() })
            appendLine()
        }
    }
}

private fun shareText(context: Context, filename: String, content: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/markdown"
        putExtra(Intent.EXTRA_TITLE, filename)
        putExtra(Intent.EXTRA_SUBJECT, filename)
        putExtra(Intent.EXTRA_TEXT, content)
    }
    context.startActivity(Intent.createChooser(sendIntent, "导出记录"))
}

private fun formatCost(messages: List<ChatMessage>): String {
    val totalTokens = messages.sumOf { message ->
        message.usage?.totalTokens ?: message.usage?.promptTokens ?: 0
    }
    val cost = totalTokens / 1000.0 * 0.01
    return String.format(Locale.getDefault(), "%.2f", cost)
}

private fun formatDateTime(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun MessageRole.exportName(): String {
    return when (this) {
        MessageRole.USER -> "用户"
        MessageRole.ASSISTANT -> "助手"
        MessageRole.SYSTEM -> "系统"
    }
}

private fun MessagePart.exportText(): String {
    return when (this) {
        is MessagePart.TextPart -> text
        is MessagePart.RemoteImagePart -> "![图片]($url)"
        is MessagePart.LocalImagePart -> "![本地图片]($uri)"
        is MessagePart.LocalFilePart -> "[文件] $name ($sizeBytes bytes)"
    }
}

private val DetailBackground = Color(0xFFF7FAFE)
private val DetailText = Color(0xFF152033)
private val DetailMuted = Color(0xFF76849A)
private val DetailBorder = Color(0xFFE1E9F5)
private val DetailPrimary = Color(0xFF5795FF)
private val DetailDanger = Color(0xFFFF4E4E)
