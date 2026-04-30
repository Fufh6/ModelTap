package com.wuyousheng.modeltap.ui.screens.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuyousheng.modeltap.data.repository.ChatRepository
import com.wuyousheng.modeltap.domain.model.ChatMessage
import com.wuyousheng.modeltap.domain.model.ChatSession
import com.wuyousheng.modeltap.domain.model.MessagePart
import com.wuyousheng.modeltap.domain.model.modelDisplayFor
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    repository: ChatRepository,
    initialSelectedTab: String = "全部",
    onBack: () -> Unit,
    onOpenSession: (Long) -> Unit
) {
    val sessions by repository.observeSessions().collectAsState(initial = emptyList())
    val runningSessionIds by repository.observeRunningSessionIds().collectAsState(initial = emptySet())
    val config by repository.observeConfig().collectAsState(initial = com.wuyousheng.modeltap.domain.model.ApiConfig())
    val apiKeyEntries by repository.observeApiKeyEntries().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val lastMessages = remember { mutableStateMapOf<Long, ChatMessage?>() }
    var selectedTab by remember(initialSelectedTab) { mutableStateOf(initialSelectedTab) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedSessionIds by remember { mutableStateOf(setOf<Long>()) }
    var pendingDeleteIds by remember { mutableStateOf<List<Long>>(emptyList()) }

    LaunchedEffect(sessions) {
        sessions.forEach { session ->
            if (!lastMessages.containsKey(session.id)) {
                scope.launch { lastMessages[session.id] = repository.getLastMessage(session.id) }
            }
        }
        val sessionIds = sessions.map { it.id }.toSet()
        lastMessages.keys.toList()
            .filterNot(sessionIds::contains)
            .forEach(lastMessages::remove)
        selectedSessionIds = selectedSessionIds.filter(sessionIds::contains).toSet()
        if (selectedSessionIds.isEmpty()) {
            selectionMode = false
        }
    }

    val filteredSessions = sessions.filter { session ->
        val lastMessage = lastMessages[session.id]
        when (selectedTab) {
            "聊天" -> !isImageSession(session, lastMessage)
            "图片" -> isImageSession(session, lastMessage)
            "收藏" -> session.isFavorite
            else -> true
        }
    }
    val filteredIds = filteredSessions.map { it.id }.toSet()
    val allFilteredSelected = filteredSessions.isNotEmpty() && filteredIds.all(selectedSessionIds::contains)

    pendingDeleteIds.takeIf { it.isNotEmpty() }?.let { ids ->
        val firstTitle = sessions.firstOrNull { it.id == ids.firstOrNull() }?.title.orEmpty()
        AlertDialog(
            onDismissRequest = { pendingDeleteIds = emptyList() },
            title = { Text(if (ids.size > 1) "删除会话" else "删除会话") },
            text = {
                Text(
                    if (ids.size > 1) {
                        "将永久删除 ${ids.size} 个会话及其中的全部消息。"
                    } else {
                        "确定删除「${firstTitle.ifBlank { "未命名会话" }}」及其中的全部消息吗？"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.deleteSessions(ids)
                        }
                        ids.forEach(lastMessages::remove)
                        selectedSessionIds = selectedSessionIds - ids.toSet()
                        selectionMode = selectedSessionIds.isNotEmpty()
                        pendingDeleteIds = emptyList()
                    }
                ) {
                    Text("删除", color = HistoryDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteIds = emptyList() }) {
                    Text("取消")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HistoryBackground)
            .padding(horizontal = 22.dp)
    ) {
        HistoryTopBar(
            onBack = onBack,
            selectionMode = selectionMode,
            selectedCount = selectedSessionIds.size,
            canSelectAll = filteredSessions.isNotEmpty(),
            allSelected = allFilteredSelected,
            onToggleSelectionMode = {
                selectionMode = !selectionMode
                if (!selectionMode) {
                    selectedSessionIds = emptySet()
                }
            },
            onToggleSelectAll = {
                selectedSessionIds = if (allFilteredSelected) {
                    selectedSessionIds - filteredIds
                } else {
                    selectedSessionIds + filteredIds
                }
            },
            onDeleteSelected = {
                pendingDeleteIds = selectedSessionIds.toList()
            }
        )
        HistoryTabs(selected = selectedTab, onSelect = { selectedTab = it })
        Spacer(modifier = Modifier.height(18.dp))

        val groups = groupedSessions(filteredSessions)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (groups.isEmpty()) {
                item {
                    EmptyHistoryState(selectedTab)
                }
            }
            groups.forEach { group ->
                item(key = "date-${group.title}") {
                    DateHeader(group.title, group.dateText)
                }
                items(group.sessions, key = { it.id }) { session ->
                    HistoryRow(
                        session = session,
                        lastMessage = lastMessages[session.id],
                        isRunning = session.id in runningSessionIds,
                        modelLabel = modelDisplayFor(session.modelId, apiKeyEntries, config).label,
                        channelLabel = modelDisplayFor(session.modelId, apiKeyEntries, config).channelLabel,
                        selectionMode = selectionMode,
                        selected = session.id in selectedSessionIds,
                        onClick = {
                            if (selectionMode) {
                                selectedSessionIds = selectedSessionIds.toggle(session.id)
                            } else {
                                onOpenSession(session.id)
                            }
                        },
                        onDelete = { pendingDeleteIds = listOf(session.id) }
                    )
                }
            }
            if (groups.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.width(52.dp).height(1.dp).background(HistoryBorder))
                        Text(
                            "已到底",
                            color = HistoryMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        Box(modifier = Modifier.width(52.dp).height(1.dp).background(HistoryBorder))
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryTopBar(
    onBack: () -> Unit,
    selectionMode: Boolean,
    selectedCount: Int,
    canSelectAll: Boolean,
    allSelected: Boolean,
    onToggleSelectionMode: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            BackIcon(Modifier.size(24.dp), HistoryText)
        }
        Text(
            if (selectionMode) "已选 $selectedCount 个" else "历史记录",
            color = HistoryText,
            fontSize = 25.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selectionMode) {
            HeaderActionButton(
                text = if (allSelected) "取消全选" else "全选",
                enabled = canSelectAll,
                color = HistoryMuted,
                onClick = onToggleSelectAll
            )
            Spacer(modifier = Modifier.width(6.dp))
            HeaderActionButton(
                text = if (selectedCount > 0) "删除($selectedCount)" else "删除",
                enabled = selectedCount > 0,
                color = HistoryDanger,
                onClick = onDeleteSelected
            )
            Spacer(modifier = Modifier.width(6.dp))
            HeaderActionButton(
                text = "完成",
                color = HistoryMuted,
                onClick = onToggleSelectionMode
            )
        } else {
            HeaderActionButton(
                text = "选择",
                color = HistoryMuted,
                onClick = onToggleSelectionMode
            )
        }
    }
}

@Composable
private fun HeaderActionButton(
    text: String,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, HistoryBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 6.dp)
    ) {
        Text(
            text,
            color = color,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 82.dp)
        )
    }
}

@Composable
private fun HistoryTabs(selected: String, onSelect: (String) -> Unit) {
    val tabs = listOf("全部", "聊天", "图片", "收藏")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, HistoryBorder, RoundedCornerShape(14.dp))
    ) {
        tabs.forEachIndexed { index, tab ->
            val active = tab == selected
            TextButton(
                onClick = { onSelect(tab) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) HistorySelected else Color.Transparent)
            ) {
                Text(tab, color = if (active) HistoryPrimary else HistoryMuted, fontSize = 15.sp)
            }
            if (index != tabs.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .align(Alignment.CenterVertically)
                        .background(HistoryBorder)
                )
            }
        }
    }
}

@Composable
private fun DateHeader(title: String, dateText: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(HistoryPrimary))
        Spacer(modifier = Modifier.width(20.dp))
        Text(title, color = HistoryText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.width(12.dp))
        Text(dateText, color = HistoryMuted, fontSize = 14.sp)
    }
}

@Composable
private fun HistoryRow(
    session: ChatSession,
    lastMessage: ChatMessage?,
    isRunning: Boolean,
    modelLabel: String,
    channelLabel: String,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val imageSession = isImageSession(session, lastMessage)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, HistoryBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SessionIcon(imageSession = imageSession, isRunning = isRunning)
        Spacer(modifier = Modifier.width(15.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                session.title.ifBlank { "未命名会话" },
                color = HistoryText,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (session.isFavorite) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "已收藏",
                    color = HistoryPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                historyPreview(session, lastMessage),
                color = HistoryMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(modelLabel)
                Chip(channelLabel)
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatTime(session.updatedAt), color = HistoryMuted, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            if (selectionMode) {
                SelectionIndicator(selected = selected)
            } else {
                TextButton(onClick = onDelete) {
                    Text("删除", color = HistoryDanger, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SessionIcon(imageSession: Boolean, isRunning: Boolean) {
    val accent = if (imageSession) HistoryTeal else HistoryPrimary
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HistoryIconBg),
        contentAlignment = Alignment.Center
    ) {
        if (isRunning) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.5.dp,
                color = accent,
                trackColor = HistoryBorder
            )
        } else if (imageSession) {
            ImageGlyph(Modifier.size(28.dp), accent)
        } else {
            ChatGlyph(Modifier.size(30.dp), accent)
        }
    }
}

@Composable
private fun SelectionIndicator(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (selected) HistoryPrimary else Color.Transparent)
            .border(1.dp, if (selected) HistoryPrimary else HistoryBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Canvas(modifier = Modifier.size(12.dp)) {
                val strokeWidth = size.minDimension * 0.18f
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * 0.15f, size.height * 0.55f),
                    end = Offset(size.width * 0.42f, size.height * 0.82f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * 0.42f, size.height * 0.82f),
                    end = Offset(size.width * 0.88f, size.height * 0.2f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Text(
        text = text,
        color = HistoryMuted,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(HistorySelected)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    )
}

@Composable
private fun EmptyHistoryState(tab: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(74.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White)
                .border(1.dp, HistoryBorder, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            ChatGlyph(Modifier.size(34.dp), HistoryPrimary)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("${tab}为空", color = HistoryText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        Text("新的会话会显示在这里。", color = HistoryMuted, fontSize = 14.sp)
    }
}

@Composable
private fun ChatGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = size.minDimension * 0.1f, cap = StrokeCap.Round)
        val bubble = Path().apply {
            moveTo(size.width * 0.18f, size.height * 0.22f)
            quadraticBezierTo(size.width * 0.18f, size.height * 0.08f, size.width * 0.34f, size.height * 0.08f)
            lineTo(size.width * 0.78f, size.height * 0.08f)
            quadraticBezierTo(size.width * 0.94f, size.height * 0.08f, size.width * 0.94f, size.height * 0.24f)
            lineTo(size.width * 0.94f, size.height * 0.58f)
            quadraticBezierTo(size.width * 0.94f, size.height * 0.74f, size.width * 0.78f, size.height * 0.74f)
            lineTo(size.width * 0.48f, size.height * 0.74f)
            lineTo(size.width * 0.25f, size.height * 0.92f)
            lineTo(size.width * 0.31f, size.height * 0.72f)
            quadraticBezierTo(size.width * 0.18f, size.height * 0.68f, size.width * 0.18f, size.height * 0.54f)
            close()
        }
        drawPath(bubble, color, style = stroke)
        listOf(0.4f, 0.56f, 0.72f).forEach { x ->
            drawCircle(color, radius = size.minDimension * 0.055f, center = Offset(size.width * x, size.height * 0.42f))
        }
    }
}

@Composable
private fun ImageGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = size.minDimension * 0.1f, cap = StrokeCap.Round)
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.12f, size.height * 0.14f),
            size = Size(size.width * 0.76f, size.height * 0.72f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.12f, size.height * 0.12f),
            style = stroke
        )
        drawCircle(color, radius = size.minDimension * 0.08f, center = Offset(size.width * 0.34f, size.height * 0.36f))
        val mountain = Path().apply {
            moveTo(size.width * 0.22f, size.height * 0.74f)
            lineTo(size.width * 0.43f, size.height * 0.54f)
            lineTo(size.width * 0.57f, size.height * 0.66f)
            lineTo(size.width * 0.7f, size.height * 0.5f)
            lineTo(size.width * 0.83f, size.height * 0.74f)
        }
        drawPath(mountain, color, style = stroke)
    }
}

@Composable
private fun BackIcon(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = size.minDimension * 0.12f, cap = StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.68f, size.height * 0.16f), Offset(size.width * 0.32f, size.height * 0.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.32f, size.height * 0.5f), Offset(size.width * 0.68f, size.height * 0.84f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

private data class HistoryGroup(
    val title: String,
    val dateText: String,
    val sessions: List<ChatSession>
)

private fun groupedSessions(sessions: List<ChatSession>): List<HistoryGroup> {
    return sessions.groupBy { groupTitle(it.updatedAt) }
        .map { (title, items) ->
            HistoryGroup(title, groupDateText(items.first().updatedAt), items)
        }
}

private fun groupTitle(timestamp: Long): String {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameDay = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return "今天"
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = yesterday.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
        yesterday.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    if (isYesterday) return "昨天"
    return SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(timestamp))
}

private fun groupDateText(timestamp: Long): String {
    return SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(timestamp))
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun historyPreview(session: ChatSession, lastMessage: ChatMessage?): String {
    val part = lastMessage?.parts?.firstOrNull()
    return when (part) {
        is MessagePart.TextPart -> part.text.trim().ifBlank { "暂无消息" }
        is MessagePart.LocalImagePart, is MessagePart.RemoteImagePart -> "图片会话"
        is MessagePart.LocalFilePart -> "文件：${part.name}"
        null -> session.systemPrompt.ifBlank { "暂无消息" }
    }
}

private fun isImageSession(session: ChatSession, lastMessage: ChatMessage?): Boolean {
    val title = session.title.lowercase()
    if (listOf("image", "draw", "flux").any { it in title }) return true
    return lastMessage?.parts?.any { it is MessagePart.LocalImagePart || it is MessagePart.RemoteImagePart } == true
}

private fun Set<Long>.toggle(sessionId: Long): Set<Long> {
    return if (sessionId in this) this - sessionId else this + sessionId
}

private val HistoryBackground = Color(0xFFF7FAFE)
private val HistoryText = Color(0xFF162135)
private val HistoryMuted = Color(0xFF738197)
private val HistoryBorder = Color(0xFFE0E9F5)
private val HistoryPrimary = Color(0xFF3988FF)
private val HistoryTeal = Color(0xFF32C7BD)
private val HistorySelected = Color(0xFFF4F8FF)
private val HistoryIconBg = Color(0xFFF1F6FC)
private val HistoryDanger = Color(0xFFD14343)
