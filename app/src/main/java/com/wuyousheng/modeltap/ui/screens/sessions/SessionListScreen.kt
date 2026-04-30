package com.wuyousheng.modeltap.ui.screens.sessions

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuyousheng.modeltap.R
import com.wuyousheng.modeltap.data.repository.ChatRepository
import com.wuyousheng.modeltap.domain.model.ApiKeyEntry
import com.wuyousheng.modeltap.domain.model.ChatSession
import com.wuyousheng.modeltap.domain.model.UsageStats
import com.wuyousheng.modeltap.domain.model.toModelDisplay
import com.wuyousheng.modeltap.ui.components.AppIcon
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionListScreen(
    repository: ChatRepository,
    onOpenSession: (Long) -> Unit,
    onOpenDraft: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenConfig: () -> Unit,
    onOpenCreate: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMe: () -> Unit
) {
    val sessions by repository.observeSessions().collectAsState(initial = emptyList())
    val runningSessionIds by repository.observeRunningSessionIds().collectAsState(initial = emptySet())
    val config by repository.observeConfig().collectAsState(initial = com.wuyousheng.modeltap.domain.model.ApiConfig())
    val apiKeyEntries by repository.observeApiKeyEntries().collectAsState(initial = emptyList())
    val usageStats by repository.observeUsageStats().collectAsState(initial = UsageStats())
    val modelDisplay = remember(config, apiKeyEntries) { config.toModelDisplay(apiKeyEntries) }
    val scope = rememberCoroutineScope()
    var pendingDeleteSession by remember { mutableStateOf<ChatSession?>(null) }
    var editingSession by remember { mutableStateOf<ChatSession?>(null) }
    var editingTitle by remember { mutableStateOf("") }
    val trimmedEditingTitle = editingTitle.trim()

    fun createAndOpen(@Suppress("UNUSED_PARAMETER") title: String) {
        onOpenDraft()
    }

    editingSession?.let { session ->
        AlertDialog(
            onDismissRequest = { editingSession = null },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = editingTitle,
                    onValueChange = { editingTitle = it },
                    label = { Text("会话标题") },
                    singleLine = true,
                    isError = editingTitle.isBlank(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { repository.renameSession(session.id, trimmedEditingTitle) }
                        editingSession = null
                    },
                    enabled = trimmedEditingTitle.isNotBlank()
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSession = null }) { Text("取消") }
            }
        )
    }

    pendingDeleteSession?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingDeleteSession = null },
            title = { Text("删除会话？") },
            text = { Text("会话「${session.title}」及其中的消息将被永久删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { repository.deleteSession(session.id) }
                        pendingDeleteSession = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSession = null }) { Text("取消") }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF4FAFF), Color(0xFFEEF6FD), Color(0xFFF7FBFF))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HomeHeader()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FeatureCard(
                    title = "AI聊天",
                    subtitle = "智能对话，答你所问",
                    variant = FeatureVariant.Chat,
                    onClick = { createAndOpen("新会话") },
                    modifier = Modifier.weight(1f)
                )
                FeatureCard(
                    title = "AI生图",
                    subtitle = "描述想象，生成精美图像",
                    variant = FeatureVariant.Image,
                    onClick = onOpenCreate,
                    modifier = Modifier.weight(1f)
                )
            }
            DefaultModelCard(
                model = modelDisplay.label,
                onClick = onOpenSettings
            )
            ModelStrip(
                selectedModel = modelDisplay.label,
                configuredModels = apiKeyEntries,
                onManage = onOpenConfig,
                onAdd = onOpenConfig,
                onSelectModel = { entry ->
                    scope.launch { repository.useApiKeyEntry(entry.id) }
                }
            )
            RecentSessionsCard(
                sessions = sessions,
                runningSessionIds = runningSessionIds,
                onMore = onOpenHistory,
                onOpenSession = onOpenSession,
                modifier = Modifier.weight(1f)
            )
            UsageEntryCard(
                stats = usageStats,
                onClick = onOpenStats
            )
        }
        BottomNavBar(
            onHome = {},
            onCreate = onOpenCreate,
            onMe = onOpenMe,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun HomeHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppLogo()
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun AppLogo() {
    Image(
        painter = painterResource(R.drawable.modeltap_logo_horizontal),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .width(116.dp)
            .height(32.dp)
    )
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        color = Color.White.copy(alpha = 0.76f),
        shadowElevation = 3.dp,
        modifier = Modifier
            .width(108.dp)
            .height(44.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppIcon(R.drawable.ic_search_24, HomeMuted, Modifier.size(21.dp))
            Text("搜索", color = HomeMuted, fontSize = 16.sp)
        }
    }
}

@Composable
private fun Avatar() {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFFE7F1FF), Color(0xFF6D91B8)))),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.modeltap_icon),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(36.dp)
        )
    }
}

private enum class FeatureVariant { Chat, Image }

@Composable
private fun FeatureCard(
    title: String,
    subtitle: String,
    variant: FeatureVariant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(0.9f)
            .heightIn(min = 170.dp)
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color(0xFF9EB7D6).copy(alpha = 0.18f),
                spotColor = Color(0xFF9EB7D6).copy(alpha = 0.20f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.90f), Color(0xFFF5FAFF).copy(alpha = 0.78f))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.95f), RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            FeatureArtwork(variant = variant, modifier = Modifier.matchParentSize())
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color.White.copy(alpha = 0.96f), Color(0xFFF0F7FF).copy(alpha = 0.86f))
                            )
                        )
                        .border(1.dp, Color(0xFFEAF1FA), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    FeatureIcon(variant = variant)
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(title, color = HomeText, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    subtitle,
                    color = HomeMuted,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            RoundArrow(
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
private fun FeatureIcon(variant: FeatureVariant) {
    Canvas(modifier = Modifier.size(30.dp)) {
        val strokeWidth = 2.4.dp.toPx()
        val color = if (variant == FeatureVariant.Chat) Color(0xFF68A8FF) else Color(0xFF54B7C4)
        if (variant == FeatureVariant.Chat) {
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width * 0.12f, size.height * 0.16f),
                size = Size(size.width * 0.76f, size.height * 0.58f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                style = Stroke(width = strokeWidth)
            )
            val tail = Path().apply {
                moveTo(size.width * 0.34f, size.height * 0.74f)
                lineTo(size.width * 0.25f, size.height * 0.92f)
                lineTo(size.width * 0.48f, size.height * 0.75f)
            }
            drawPath(tail, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            listOf(0.38f, 0.50f, 0.62f).forEach { x ->
                drawCircle(color.copy(alpha = 0.8f), 1.8.dp.toPx(), Offset(size.width * x, size.height * 0.45f))
            }
        } else {
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width * 0.12f, size.height * 0.14f),
                size = Size(size.width * 0.76f, size.height * 0.72f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                style = Stroke(width = strokeWidth)
            )
            drawCircle(color.copy(alpha = 0.75f), 3.dp.toPx(), Offset(size.width * 0.34f, size.height * 0.34f))
            val mountain = Path().apply {
                moveTo(size.width * 0.2f, size.height * 0.76f)
                lineTo(size.width * 0.44f, size.height * 0.52f)
                lineTo(size.width * 0.58f, size.height * 0.66f)
                lineTo(size.width * 0.72f, size.height * 0.48f)
                lineTo(size.width * 0.86f, size.height * 0.76f)
            }
            drawPath(mountain, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun FeatureArtwork(variant: FeatureVariant, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (variant == FeatureVariant.Chat) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.88f), Color(0xFFBBD3FF).copy(alpha = 0.34f)),
                    center = Offset(size.width * 0.78f, size.height * 0.48f),
                    radius = size.width * 0.30f
                ),
                radius = size.width * 0.24f,
                center = Offset(size.width * 0.79f, size.height * 0.48f)
            )
            drawCircle(Color(0xFF8DAEF8).copy(alpha = 0.50f), 5.dp.toPx(), Offset(size.width * 0.69f, size.height * 0.49f))
            drawCircle(Color(0xFF8DAEF8).copy(alpha = 0.50f), 5.dp.toPx(), Offset(size.width * 0.79f, size.height * 0.49f))
            drawCircle(Color(0xFF8DAEF8).copy(alpha = 0.50f), 5.dp.toPx(), Offset(size.width * 0.89f, size.height * 0.49f))
        } else {
            drawCircle(
                Color(0xFFBDEEEA).copy(alpha = 0.44f),
                radius = size.width * 0.14f,
                center = Offset(size.width * 0.82f, size.height * 0.32f)
            )
            val mountain = Path().apply {
                moveTo(size.width * 0.48f, size.height * 0.92f)
                cubicTo(size.width * 0.58f, size.height * 0.80f, size.width * 0.62f, size.height * 0.62f, size.width * 0.70f, size.height * 0.75f)
                cubicTo(size.width * 0.74f, size.height * 0.58f, size.width * 0.79f, size.height * 0.42f, size.width * 0.84f, size.height * 0.55f)
                cubicTo(size.width * 0.90f, size.height * 0.68f, size.width * 0.94f, size.height * 0.80f, size.width * 0.95f, size.height * 0.92f)
                close()
            }
            drawPath(
                mountain,
                brush = Brush.linearGradient(listOf(Color(0xFFE9FBFC), Color(0xFF92DCEB).copy(alpha = 0.45f)))
            )
        }
    }
}

@Composable
private fun RoundArrow(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.86f))
            .border(1.dp, Color(0xFFDCE8F6), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        AppIcon(R.drawable.ic_arrow_forward_24, HomeText, Modifier.size(22.dp))
    }
}

@Composable
private fun DefaultModelCard(model: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 5.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, HomeBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(R.drawable.ic_diamond_24, Color(0xFFB3C0D2), Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text("默认模型", color = HomeMuted, fontSize = 15.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                model,
                color = HomeText,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            StatusDot(color = HomeMuted.copy(alpha = 0.58f))
            Spacer(modifier = Modifier.width(8.dp))
            Text("可用", color = HomeMuted, fontSize = 15.sp)
            Spacer(modifier = Modifier.width(12.dp))
            AppIcon(R.drawable.ic_chevron_right_24, HomeMuted, Modifier.size(23.dp))
        }
    }
}

@Composable
private fun ModelStrip(
    selectedModel: String,
    configuredModels: List<ApiKeyEntry>,
    onManage: () -> Unit,
    onAdd: () -> Unit,
    onSelectModel: (ApiKeyEntry) -> Unit
) {
    val models = configuredModels
        .filter { it.apiKey.isNotBlank() && it.baseUrl.isNotBlank() && it.selectedModel.isNotBlank() }
        .distinctBy { it.toModelDisplay().label }
        .take(4)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 5.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, HomeBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("已配置模型", color = HomeText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.clickable(onClick = onManage),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("管理", color = HomeMuted, fontSize = 15.sp)
                    Spacer(modifier = Modifier.width(2.dp))
                    AppIcon(R.drawable.ic_chevron_right_24, HomeMuted, Modifier.size(16.dp))
                }
            }
            if (models.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, HomeBorder, RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .clickable(onClick = onAdd)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("未配置模型", color = HomeMuted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("添加", color = HomeText, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(2.dp))
                        AppIcon(R.drawable.ic_chevron_right_24, HomeText, Modifier.size(16.dp))
                    }
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(models, key = { it.id }) { entry ->
                        val label = entry.toModelDisplay().label
                        ModelChip(
                            label = compactModelLabel(label),
                            selected = selectedModel == label,
                            onClick = { onSelectModel(entry) },
                            modifier = Modifier.width(104.dp)
                        )
                    }
                    item {
                        AddChip(onClick = onAdd, modifier = Modifier.width(82.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                color = if (selected) HomePrimary.copy(alpha = 0.52f) else HomeBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .background(if (selected) HomePrimary.copy(alpha = 0.08f) else Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        ModelMiniIcon(label = label)
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = HomeText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.width(5.dp))
        StatusDot(color = if (selected) HomeText else HomeMuted.copy(alpha = 0.55f), size = 5)
    }
}

@Composable
private fun ModelMiniIcon(label: String) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val color = modelColor(label)
        val strokeWidth = 1.8.dp.toPx()
        when {
            label.contains("Claude", ignoreCase = true) -> {
                drawLine(color, Offset(size.width * 0.18f, size.height * 0.82f), Offset(size.width * 0.44f, size.height * 0.18f), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.44f, size.height * 0.18f), Offset(size.width * 0.70f, size.height * 0.82f), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.30f, size.height * 0.58f), Offset(size.width * 0.58f, size.height * 0.58f), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.78f, size.height * 0.22f), Offset(size.width * 0.78f, size.height * 0.82f), strokeWidth, StrokeCap.Round)
            }
            label.contains("Gemini", ignoreCase = true) -> {
                val path = Path().apply {
                    moveTo(size.width * 0.5f, size.height * 0.06f)
                    cubicTo(size.width * 0.58f, size.height * 0.36f, size.width * 0.68f, size.height * 0.44f, size.width * 0.94f, size.height * 0.5f)
                    cubicTo(size.width * 0.68f, size.height * 0.56f, size.width * 0.58f, size.height * 0.64f, size.width * 0.5f, size.height * 0.94f)
                    cubicTo(size.width * 0.42f, size.height * 0.64f, size.width * 0.32f, size.height * 0.56f, size.width * 0.06f, size.height * 0.5f)
                    cubicTo(size.width * 0.32f, size.height * 0.44f, size.width * 0.42f, size.height * 0.36f, size.width * 0.5f, size.height * 0.06f)
                    close()
                }
                drawPath(path, color)
            }
            label.contains("Flux", ignoreCase = true) -> {
                val path = Path().apply {
                    moveTo(size.width * 0.5f, size.height * 0.12f)
                    lineTo(size.width * 0.88f, size.height * 0.82f)
                    lineTo(size.width * 0.12f, size.height * 0.82f)
                    close()
                }
                drawPath(path, color, style = Stroke(strokeWidth, cap = StrokeCap.Round))
            }
            else -> {
                drawCircle(color, radius = size.minDimension * 0.38f, style = Stroke(strokeWidth))
                drawCircle(color.copy(alpha = 0.18f), radius = size.minDimension * 0.24f)
            }
        }
    }
}

@Composable
private fun AddChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, HomeBorder, RoundedCornerShape(14.dp))
            .background(Color.White)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        AppIcon(R.drawable.ic_add_24, HomeMuted, Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(5.dp))
        Text("添加", color = HomeMuted, fontSize = 14.sp)
    }
}

@Composable
private fun RecentSessionsCard(
    sessions: List<ChatSession>,
    runningSessionIds: Set<Long>,
    onMore: () -> Unit,
    onOpenSession: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 5.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, HomeBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("最近会话", color = HomeText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.clickable(onClick = onMore),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("更多", color = HomeMuted, fontSize = 15.sp)
                    Spacer(modifier = Modifier.width(2.dp))
                    AppIcon(R.drawable.ic_chevron_right_24, HomeMuted, Modifier.size(16.dp))
                }
            }
            if (sessions.isEmpty()) {
                Text(
                    text = "暂无会话，点击 AI聊天 开始。",
                    color = HomeMuted,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    val scrollIndicatorState by remember {
                        derivedStateOf {
                            ScrollIndicatorState(
                                firstVisibleIndex = listState.firstVisibleItemIndex,
                                visibleItems = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                            )
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(sessions, key = { _, session -> session.id }) { index, session ->
                            RecentSessionRow(
                                session = session,
                                isRunning = session.id in runningSessionIds,
                                showDivider = index < sessions.lastIndex,
                                onOpen = { onOpenSession(session.id) }
                            )
                        }
                    }
                    if (sessions.size > 3) {
                        ScrollIndicator(
                            firstVisibleIndex = scrollIndicatorState.firstVisibleIndex,
                            visibleItems = scrollIndicatorState.visibleItems,
                            totalItems = sessions.size,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

private data class ScrollIndicatorState(
    val firstVisibleIndex: Int,
    val visibleItems: Int
)

@Composable
private fun UsageEntryCard(
    stats: UsageStats,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, HomeBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(HomePrimary.copy(alpha = 0.09f)),
                contentAlignment = Alignment.Center
            ) {
                UsageMiniIcon()
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("用量统计", color = HomeText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    "令牌 ${formatCompactCount(stats.totalTokens)} · ${stats.totalMessages} 条消息",
                    color = HomeMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text("查看", color = HomePrimary, fontSize = 13.sp)
            Spacer(modifier = Modifier.width(4.dp))
            AppIcon(R.drawable.ic_chevron_right_24, HomeMuted, Modifier.size(18.dp))
        }
    }
}

@Composable
private fun UsageMiniIcon() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val color = HomePrimary
        val strokeWidth = 1.7.dp.toPx()
        val baseY = size.height * 0.82f
        listOf(0.24f to 0.52f, 0.50f to 0.30f, 0.76f to 0.62f).forEach { (x, top) ->
            drawLine(
                color = color,
                start = Offset(size.width * x, baseY),
                end = Offset(size.width * x, size.height * top),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
        drawLine(
            color = color.copy(alpha = 0.55f),
            start = Offset(size.width * 0.14f, baseY),
            end = Offset(size.width * 0.88f, baseY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun ScrollIndicator(
    firstVisibleIndex: Int,
    visibleItems: Int,
    totalItems: Int,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .width(4.dp)
            .height(96.dp)
    ) {
        val trackColor = Color(0xFFE7EEF6)
        val thumbColor = Color(0xFFB9C6D6)
        val visibleRatio = (visibleItems.toFloat() / totalItems.toFloat()).coerceIn(0.18f, 1f)
        val thumbHeight = size.height * visibleRatio
        val maxOffset = (size.height - thumbHeight).coerceAtLeast(0f)
        val scrollableItems = (totalItems - visibleItems).coerceAtLeast(1)
        val offset = maxOffset * (firstVisibleIndex.toFloat() / scrollableItems.toFloat()).coerceIn(0f, 1f)
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(size.width * 0.25f, 0f),
            size = Size(size.width * 0.5f, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width, size.width)
        )
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(0f, offset),
            size = Size(size.width, thumbHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width, size.width)
        )
    }
}

@Composable
private fun RecentSessionRow(
    session: ChatSession,
    isRunning: Boolean,
    showDivider: Boolean,
    onOpen: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RecentSessionIcon(isRunning = isRunning)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = session.title,
                color = HomeText,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(formatRelativeSessionTime(session.updatedAt), color = HomeMuted, fontSize = 13.sp)
            Spacer(modifier = Modifier.width(4.dp))
            AppIcon(R.drawable.ic_chevron_right_24, HomeMuted, Modifier.size(20.dp))
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 46.dp, end = 14.dp)
                    .height(1.dp)
                    .background(Color(0xFFE6EEF7).copy(alpha = 0.65f))
            )
        }
    }
}

@Composable
private fun RecentSessionIcon(isRunning: Boolean) {
    Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
        if (isRunning) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = HomePrimary,
                trackColor = HomeBorder
            )
        } else {
            MessageMiniIcon()
        }
    }
}

@Composable
private fun MessageMiniIcon() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val color = Color(0xFF5D8FF1)
        val strokeWidth = 1.7.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.12f, size.height * 0.16f),
            size = Size(size.width * 0.76f, size.height * 0.58f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()),
            style = Stroke(width = strokeWidth)
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.35f, size.height * 0.75f),
            end = Offset(size.width * 0.25f, size.height * 0.9f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        listOf(0.40f, 0.50f, 0.60f).forEach { x ->
            drawCircle(color, 1.2.dp.toPx(), Offset(size.width * x, size.height * 0.46f))
        }
    }
}

@Composable
private fun BottomNavBar(
    onHome: () -> Unit,
    onCreate: () -> Unit,
    onMe: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.90f),
        shadowElevation = 5.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(BottomNavIcon.Home, "首页", selected = true, onClick = onHome)
            BottomNavItem(BottomNavIcon.Add, "创作", selected = false, onClick = onCreate)
            BottomNavItem(BottomNavIcon.Me, "我的", selected = false, onClick = onMe)
        }
    }
}

@Composable
private fun BottomNavItem(icon: BottomNavIcon, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(58.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BottomIcon(icon = icon, selected = selected)
        Text(label, fontSize = 11.sp, color = if (selected) Color(0xFF2487FF) else HomeMuted)
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .width(if (selected) 30.dp else 0.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF2487FF))
        )
    }
}

private enum class BottomNavIcon { Home, Add, Me }

@Composable
private fun BottomIcon(icon: BottomNavIcon, selected: Boolean) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val color = if (selected) Color(0xFF2487FF) else HomeMuted
        val strokeWidth = 2.dp.toPx()
        when (icon) {
            BottomNavIcon.Home -> {
                val path = Path().apply {
                    moveTo(size.width * 0.18f, size.height * 0.48f)
                    lineTo(size.width * 0.5f, size.height * 0.18f)
                    lineTo(size.width * 0.82f, size.height * 0.48f)
                    lineTo(size.width * 0.82f, size.height * 0.84f)
                    lineTo(size.width * 0.26f, size.height * 0.84f)
                    lineTo(size.width * 0.26f, size.height * 0.48f)
                }
                drawPath(path, color = color, style = Stroke(strokeWidth, cap = StrokeCap.Round))
            }
            BottomNavIcon.Add -> {
                drawCircle(color, radius = size.width * 0.35f, style = Stroke(strokeWidth))
                drawLine(color, Offset(size.width * 0.5f, size.height * 0.32f), Offset(size.width * 0.5f, size.height * 0.68f), strokeWidth, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.32f, size.height * 0.5f), Offset(size.width * 0.68f, size.height * 0.5f), strokeWidth, StrokeCap.Round)
            }
            BottomNavIcon.Me -> {
                drawCircle(color, radius = size.width * 0.18f, center = Offset(size.width * 0.5f, size.height * 0.34f), style = Stroke(strokeWidth))
                drawArc(
                    color = color,
                    startAngle = 205f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.22f, size.height * 0.50f),
                    size = Size(size.width * 0.56f, size.height * 0.42f),
                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color = AccentGreen, size: Int = 7) {
    Canvas(modifier = Modifier.size(size.dp)) {
        drawCircle(color)
    }
}

private fun compactModelLabel(label: String): String {
    return when {
        label.contains("Claude", ignoreCase = true) -> "Claude"
        label.contains("Gemini", ignoreCase = true) -> "Gemini"
        else -> label
    }
}

private fun modelColor(@Suppress("UNUSED_PARAMETER") label: String): Color {
    return HomeMuted
}

private fun formatRelativeSessionTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = (now - timestamp).coerceAtLeast(0L)
    val oneDay = 24L * 60L * 60L * 1000L
    return when {
        diff < oneDay -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        diff < oneDay * 2 -> "昨天"
        else -> "${diff / oneDay}天前"
    }
}

private fun formatCompactCount(value: Int): String {
    return when {
        value >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", value / 1_000_000f)
        value >= 10_000 -> "${value / 1000}K"
        value >= 1_000 -> String.format(Locale.getDefault(), "%.1fK", value / 1000f)
        else -> value.toString()
    }
}

private val HomeBorder = Color(0xFFE4EAF2)
private val HomeText = Color(0xFF142033)
private val HomeMuted = Color(0xFF76849B)
private val HomePrimary = Color(0xFF2487FF)
private val AccentGreen = Color(0xFF18C9A0)
