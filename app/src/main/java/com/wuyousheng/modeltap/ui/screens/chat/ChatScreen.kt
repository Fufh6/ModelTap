package com.wuyousheng.modeltap.ui.screens.chat

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.wuyousheng.modeltap.R
import com.wuyousheng.modeltap.data.repository.ChatRequestMode
import com.wuyousheng.modeltap.data.repository.ChatRepository
import com.wuyousheng.modeltap.domain.model.ApiConfig
import com.wuyousheng.modeltap.domain.model.ChatMessage
import com.wuyousheng.modeltap.domain.model.ChatSession
import com.wuyousheng.modeltap.domain.model.MessagePart
import com.wuyousheng.modeltap.domain.model.MessageRole
import com.wuyousheng.modeltap.domain.model.ApiKeyEntry
import com.wuyousheng.modeltap.domain.model.toModelDisplay
import com.wuyousheng.modeltap.ui.components.AppIcon
import com.wuyousheng.modeltap.ui.components.MessageBubble
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    repository: ChatRepository,
    sessionId: Long,
    onBack: () -> Unit,
    onOpenHome: () -> Unit = onBack,
    onOpenSessionDetail: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenImageCreate: () -> Unit = {},
    onOpenSession: (Long) -> Unit = {},
    onCreateSession: () -> Unit = {},
    onDraftSessionCreated: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val sessions by repository.observeSessions().collectAsState(initial = emptyList())
    var drawerSessionLimit by remember { mutableIntStateOf(DrawerInitialSessionCount) }
    val drawerSessions = sessions.take(drawerSessionLimit)
    val drawerListState = rememberLazyListState()
    val drawerLastVisibleIndex by remember {
        derivedStateOf { drawerListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
    }
    val isDraftSession = sessionId <= 0L
    var messageLoadLimit by remember(sessionId) { mutableIntStateOf(InitialMessageLoadCount) }
    val messagesFlow = remember(sessionId, messageLoadLimit, isDraftSession) {
        if (isDraftSession) flowOf(emptyList()) else repository.observeRecentMessages(sessionId, messageLoadLimit)
    }
    val messageCountFlow = remember(sessionId, isDraftSession) {
        if (isDraftSession) flowOf(0) else repository.observeMessageCount(sessionId)
    }
    val sessionFlow = remember(sessionId, isDraftSession) {
        if (isDraftSession) flowOf(null) else repository.observeSession(sessionId)
    }
    val sendingFlow = remember(sessionId, isDraftSession) {
        if (isDraftSession) flowOf(false) else repository.observeSessionSending(sessionId)
    }
    val messages by messagesFlow.collectAsState(initial = emptyList())
    val totalMessageCount by messageCountFlow.collectAsState(initial = 0)
    val config by repository.observeConfig().collectAsState(initial = com.wuyousheng.modeltap.domain.model.ApiConfig())
    val apiKeyEntries by repository.observeApiKeyEntries().collectAsState(initial = emptyList())
    val modelDisplay = remember(config, apiKeyEntries) { config.toModelDisplay(apiKeyEntries) }
    val sessionState by sessionFlow.collectAsState(initial = null)
    var hasCheckedSession by remember(sessionId) { mutableStateOf(false) }
    val sessionMissing = !isDraftSession && hasCheckedSession && sessionState == null
    val sessionLoading = !isDraftSession && !hasCheckedSession && sessionState == null
    val listState = rememberLazyListState()
    val selectedImages = remember { mutableStateListOf<String>() }
    val selectedFiles = remember { mutableStateListOf<SelectedFile>() }
    val queuedMessages = remember { mutableStateListOf<QueuedMessage>() }
    var text by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isImageGenerationArmed by remember(sessionId) { mutableStateOf(false) }
    val isSending by sendingFlow.collectAsState(initial = false)
    var isCreatingDraftSession by remember(sessionId) { mutableStateOf(false) }
    var failedMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var isRenaming by remember { mutableStateOf(false) }
    var editingTitle by remember { mutableStateOf("") }
    var isPromptEditing by remember { mutableStateOf(false) }
    var editingPrompt by remember { mutableStateOf("") }
    var isAttachmentMenuExpanded by remember { mutableStateOf(false) }
    var isModelMenuExpanded by remember { mutableStateOf(false) }
    var showChatParams by remember { mutableStateOf(false) }
    var draftConfig by remember { mutableStateOf(config) }
    val availableApiEntries = remember(apiKeyEntries) { apiKeyEntries.filter { it.apiKey.isNotBlank() && it.baseUrl.isNotBlank() && it.selectedModel.isNotBlank() } }
    var webSearchForDraft by remember(sessionId) { mutableStateOf<Boolean?>(null) }
    var outgoingTokenEstimate by remember { mutableIntStateOf(0) }
    var contextTokenEstimate by remember { mutableIntStateOf(0) }
    var hasUserScrolledHistory by remember(sessionId) { mutableStateOf(false) }
    var lastLoadedMessageCount by remember(sessionId) { mutableIntStateOf(0) }
    var lastAutoLoadMessageCount by remember(sessionId) { mutableIntStateOf(-1) }
    var historyBoundaryMessage by remember(sessionId) { mutableStateOf("") }
    val trimmedEditingTitle = editingTitle.trim()
    val canRename = !sessionMissing && !isDraftSession && trimmedEditingTitle.isNotBlank()
    val canAddImage = selectedImages.size < MaxSelectedImages
    val isWebSearchEnabledForDraft = webSearchForDraft ?: config.webSearchEnabled
    val canLoadMoreMessages = messages.size < totalMessageCount
    val hasHistoryHeader = !sessionLoading && !sessionMissing && messages.isNotEmpty()
    val bottomListIndex = messages.size + if (hasHistoryHeader) 1 else 0
    val firstVisibleItemIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val isListScrollInProgress by remember { derivedStateOf { listState.isScrollInProgress } }
    val isScrolledAwayFromBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            val bottomItemIndex = layoutInfo.totalItemsCount - 1
            bottomItemIndex > 0 && lastVisibleItemIndex < bottomItemIndex
        }
    }
    val showScrollToBottomButton = !sessionLoading && !sessionMissing && messages.isNotEmpty() && isScrolledAwayFromBottom
    val selectedImageSnapshot = selectedImages.toList()
    val lastMessageSignature = messages.lastOrNull()?.let { message ->
        val contentLength = message.parts.sumOf { part ->
            when (part) {
                is MessagePart.TextPart -> part.text.length
                is MessagePart.RemoteImagePart -> part.url.length
                is MessagePart.LocalImagePart -> part.uri.length
                is MessagePart.LocalFilePart -> part.name.length
            }
        }
        "${message.id}:$contentLength:${message.parts.size}:$isSending"
    }
    val draftParts = buildList {
        if (text.isNotBlank()) add(MessagePart.TextPart(text.trim()))
        selectedImageSnapshot.forEach { add(MessagePart.LocalImagePart(it)) }
        selectedFiles.forEach { add(it.toMessagePart()) }
    }

    LaunchedEffect(drawerLastVisibleIndex, drawerSessionLimit, sessions.size) {
        val totalItems = drawerListState.layoutInfo.totalItemsCount
        if (
            totalItems > 0 &&
            drawerLastVisibleIndex >= totalItems - 2 &&
            drawerSessionLimit < sessions.size
        ) {
            drawerSessionLimit = (drawerSessionLimit + DrawerSessionPageSize).coerceAtMost(sessions.size)
        }
    }

    fun startSend(
        fallbackMessage: ChatMessage,
        onFinished: () -> Unit = {},
        block: suspend () -> Unit
    ) {
        scope.launch {
            try {
                runCatching { block() }
                    .onSuccess {
                        failedMessage = null
                        errorMessage = ""
                    }
                    .onFailure {
                        errorMessage = if (it is CancellationException) {
                            failedMessage = null
                            "已停止，可重新发送。"
                        } else {
                            failedMessage = repository.getLastUserMessage(sessionId) ?: fallbackMessage
                            it.message ?: "发送失败，请重试。"
                        }
                    }
            } finally {
                onFinished()
            }
        }
    }

    fun drainQueue() {
        if (isSending || queuedMessages.isEmpty() || sessionMissing || isDraftSession) return
        val next = queuedMessages.removeAt(0)
        val pendingMessage = ChatMessage(
            id = 0,
            sessionId = sessionId,
            role = MessageRole.USER,
            parts = next.parts,
            usage = null,
            createdAt = System.currentTimeMillis()
        )
        startSend(pendingMessage, onFinished = { drainQueue() }) {
            repository.sendMessage(
                sessionId = sessionId,
                parts = next.parts,
                webSearchOverride = next.webSearchEnabled,
                includeHistory = next.requestMode != ChatRequestMode.IMAGE_GENERATION,
                requestMode = next.requestMode
            ).getOrThrow()
        }
    }

    fun submitMessage() {
        if (sessionMissing) return
        val outgoing = buildList {
            if (text.isNotBlank()) add(MessagePart.TextPart(text.trim()))
            selectedImages.forEach { add(MessagePart.LocalImagePart(it)) }
            selectedFiles.forEach { add(it.toMessagePart()) }
        }
        if (outgoing.isEmpty()) {
            errorMessage = "请输入文字或选择图片"
            return
        }
        val pendingMessage = ChatMessage(
            id = 0,
            sessionId = sessionId,
            role = MessageRole.USER,
            parts = outgoing,
            usage = null,
            createdAt = System.currentTimeMillis()
        )
        val requestMode = if (isImageGenerationArmed) ChatRequestMode.IMAGE_GENERATION else ChatRequestMode.CHAT
        val includeHistory = requestMode != ChatRequestMode.IMAGE_GENERATION
        text = ""
        isImageGenerationArmed = false
        selectedImages.clear()
        selectedFiles.clear()
        if (isSending) {
            queuedMessages += QueuedMessage(outgoing, isWebSearchEnabledForDraft, requestMode)
            return
        }
        if (isDraftSession) {
            isCreatingDraftSession = true
            startSend(pendingMessage, onFinished = { isCreatingDraftSession = false }) {
                val newSessionId = repository.createSessionAndLaunchMessage(
                    title = "新会话",
                    parts = outgoing,
                    webSearchOverride = isWebSearchEnabledForDraft,
                    includeHistory = includeHistory,
                    requestMode = requestMode
                )
                onDraftSessionCreated(newSessionId)
            }
            return
        }
        startSend(pendingMessage, onFinished = { drainQueue() }) {
            repository.sendMessage(
                sessionId = sessionId,
                parts = outgoing,
                webSearchOverride = isWebSearchEnabledForDraft,
                includeHistory = includeHistory,
                requestMode = requestMode
            ).getOrThrow()
        }
    }

    fun loadMoreMessages() {
        hasUserScrolledHistory = true
        if (!canLoadMoreMessages) {
            historyBoundaryMessage = "已经是第一条消息"
            return
        }
        historyBoundaryMessage = ""
        messageLoadLimit = (messageLoadLimit + MessageLoadPageSize)
            .coerceAtMost(totalMessageCount.coerceAtLeast(messageLoadLimit + MessageLoadPageSize))
    }

    fun openModelMenu() {
        isModelMenuExpanded = true
    }

    LaunchedEffect(sessionId) {
        if (!isDraftSession) {
            withFrameNanos { }
        }
        hasCheckedSession = true
    }

    LaunchedEffect(config) {
        if (!showChatParams) draftConfig = config
    }

    LaunchedEffect(isSending, queuedMessages.size, sessionMissing) {
        if (!isSending) {
            drainQueue()
        }
    }

    LaunchedEffect(sessionId, text, selectedImageSnapshot, messages.size, sessionState?.systemPrompt) {
        outgoingTokenEstimate = repository.estimateOutgoingTokens(draftParts)
        contextTokenEstimate = repository.estimateContextTokens(sessionId, draftParts)
    }

    if (isRenaming) {
        AlertDialog(
            onDismissRequest = { isRenaming = false },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = editingTitle,
                    onValueChange = { editingTitle = it },
                    label = { Text("会话标题") },
                    singleLine = true,
                    isError = trimmedEditingTitle.isBlank(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { repository.renameSession(sessionId, trimmedEditingTitle) }
                        isRenaming = false
                    },
                    enabled = canRename
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { isRenaming = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (isPromptEditing) {
        AlertDialog(
            onDismissRequest = { isPromptEditing = false },
            title = { Text("会话提示词") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "留空时使用全局系统提示词。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = editingPrompt,
                        onValueChange = { editingPrompt = it },
                        label = { Text("模型提示词") },
                        minLines = 3,
                        maxLines = 8,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { repository.updateSessionPrompt(sessionId, editingPrompt) }
                        isPromptEditing = false
                    },
                    enabled = !sessionMissing
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { isPromptEditing = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showChatParams) {
        ChatParamsSheet(
            config = draftConfig,
            onConfigChange = { draftConfig = it },
            onDismiss = { showChatParams = false },
            onSavePreset = {
                scope.launch {
                    repository.saveConfig(draftConfig)
                    showChatParams = false
                }
            },
            onApply = {
                scope.launch {
                    repository.saveConfig(draftConfig)
                    webSearchForDraft = draftConfig.webSearchEnabled
                    showChatParams = false
                }
            }
        )
    }

    LaunchedEffect(lastMessageSignature) {
        val loadedMoreHistory = messages.size > lastLoadedMessageCount && hasUserScrolledHistory
        if (messages.isNotEmpty() && !loadedMoreHistory) {
            withFrameNanos { }
            listState.animateScrollToItem(bottomListIndex)
        }
        lastLoadedMessageCount = messages.size
    }

    LaunchedEffect(lastMessageSignature, isSending, bottomListIndex) {
        if (isSending && messages.isNotEmpty()) {
            withFrameNanos { }
            listState.scrollToItem(bottomListIndex)
        }
    }

    LaunchedEffect(firstVisibleItemIndex, messages.size) {
        if (messages.size > InitialMessageLoadCount && firstVisibleItemIndex < messages.lastIndex) {
            hasUserScrolledHistory = true
        }
    }

    LaunchedEffect(
        firstVisibleItemIndex,
        isListScrollInProgress,
        canLoadMoreMessages,
        messages.size,
        sessionLoading,
        sessionMissing
    ) {
        if (
            isListScrollInProgress &&
            firstVisibleItemIndex == 0 &&
            !sessionLoading &&
            !sessionMissing &&
            messages.isNotEmpty() &&
            lastAutoLoadMessageCount != messages.size
        ) {
            lastAutoLoadMessageCount = messages.size
            loadMoreMessages()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val newUris = uris.map(Uri::toString).filterNot { it in selectedImages }
        val remainingSlots = MaxSelectedImages - selectedImages.size
        selectedImages += newUris.take(remainingSlots)
        val ignoredDuplicate = uris.size != newUris.size
        val exceededLimit = newUris.size > remainingSlots
        errorMessage = when {
            ignoredDuplicate && exceededLimit -> "已忽略重复图片，最多可选择 $MaxSelectedImages 张图片。"
            ignoredDuplicate -> "已忽略重复图片。"
            exceededLimit -> "最多可选择 $MaxSelectedImages 张图片。"
            else -> ""
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val file = context.readSelectedFile(uri)
        if (file == null) {
            errorMessage = "无法读取文件信息"
            return@rememberLauncherForActivityResult
        }
        if (file.sizeBytes > MaxSelectedFileBytes) {
            errorMessage = "文件不能超过 5MB"
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (selectedFiles.none { it.uri == file.uri }) {
            selectedFiles += file
        }
        errorMessage = ""
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatSessionDrawer(
                sessions = drawerSessions,
                totalSessionCount = sessions.size,
                currentSessionId = sessionId,
                selectedModel = modelDisplay.label,
                listState = drawerListState,
                hasMoreSessions = drawerSessionLimit < sessions.size,
                onOpenHistory = {
                    scope.launch { drawerState.close() }
                    onOpenHistory()
                },
                onOpenImageCreate = {
                    scope.launch { drawerState.close() }
                    onOpenImageCreate()
                },
                onOpenSession = { targetSessionId ->
                    scope.launch { drawerState.close() }
                    if (targetSessionId != sessionId) onOpenSession(targetSessionId)
                },
                onCreateSession = {
                    scope.launch {
                        drawerState.close()
                        onCreateSession()
                    }
                }
            )
        }
    ) {
    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp),
        topBar = {
            ChatHeader(
                subtitle = "${messages.size} / $totalMessageCount",
                selectedModelLabel = modelDisplay.label,
                channelLabel = modelDisplay.channelLabel,
                onOpenMenu = { scope.launch { drawerState.open() } },
                onOpenSessionDetail = onOpenSessionDetail,
                onOpenHome = onOpenHome,
                onOpenModelMenu = { openModelMenu() },
                isModelMenuExpanded = isModelMenuExpanded,
                onDismissModelMenu = { isModelMenuExpanded = false },
                availableApiEntries = availableApiEntries,
                onSelectApiEntry = { entry ->
                    isModelMenuExpanded = false
                    scope.launch {
                        repository.useApiKeyEntry(entry.id)
                    }
                }
            )
        },
        containerColor = ChatPageBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (sessionLoading) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "正在加载会话...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                    if (sessionMissing) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "会话不存在或已被删除",
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                    if (hasHistoryHeader) {
                        item(key = "history-loader") {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (canLoadMoreMessages) {
                                        TextButton(onClick = { loadMoreMessages() }) {
                                            Text("加载更多")
                                        }
                                    } else {
                                        Text(
                                            text = historyBoundaryMessage.ifBlank { "已经是第一条消息" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    items(messages, key = { it.id }) { message ->
                        val isLastAssistantMessage = message.id == messages.lastOrNull()?.id &&
                            message.role == MessageRole.ASSISTANT
                    MessageBubble(
                        message = message,
                        isStreaming = isSending &&
                            isLastAssistantMessage &&
                            message.parts.none { it is MessagePart.LocalImagePart || it is MessagePart.RemoteImagePart }
                    )
                }
                    item(key = "bottom-anchor") {
                        Spacer(modifier = Modifier.height(1.dp))
                    }
                }
                if (showScrollToBottomButton) {
                    FloatingActionButton(
                        onClick = {
                            hasUserScrolledHistory = false
                            scope.launch { listState.animateScrollToItem(bottomListIndex) }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 8.dp, bottom = 8.dp),
                        containerColor = Color(0xFFEAF4FF),
                        contentColor = Color(0xFF3B82F6)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_keyboard_arrow_down_24),
                            contentDescription = "回到底部"
                        )
                    }
                }
                if (!sessionLoading && !sessionMissing && messages.isEmpty()) {
                    EmptyConversationPlaceholder()
                }
            }
            Text(
                text = "内容由 AI 生成，仅供参考，请自行判断。",
                color = ChatMuted.copy(alpha = 0.62f),
                fontSize = 10.sp,
                lineHeight = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            QuickActionRow(
                isImageGenerationArmed = isImageGenerationArmed,
                onImageGeneration = {
                    isImageGenerationArmed = !isImageGenerationArmed
                    selectedFiles.clear()
                    if (isImageGenerationArmed && text.isBlank()) {
                        text = "生成图片："
                    }
                    errorMessage = if (isImageGenerationArmed) {
                        "已开启生图模式，下一条消息将按图片生成处理。"
                    } else {
                        ""
                    }
                },
                onSummary = {
                    isImageGenerationArmed = false
                    text = "请总结当前对话。"
                    selectedImages.clear()
                    selectedFiles.clear()
                },
                onTranslate = {
                    isImageGenerationArmed = false
                    text = "请翻译以下内容："
                    selectedImages.clear()
                    selectedFiles.clear()
                },
                onCode = {
                    isImageGenerationArmed = false
                    text = "请帮我编写代码。"
                    selectedImages.clear()
                    selectedFiles.clear()
                },
                onOptimizePrompt = {
                    isImageGenerationArmed = false
                    text = "请优化这段提示词。"
                    selectedImages.clear()
                    selectedFiles.clear()
                }
            )
            if (selectedImages.isNotEmpty() || selectedFiles.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("附件 ${selectedImages.size + selectedFiles.size}")
                    TextButton(
                        onClick = {
                            selectedImages.clear()
                            selectedFiles.clear()
                            if (errorMessage.startsWith("最多可选择") || errorMessage == "已忽略重复图片。") {
                                errorMessage = ""
                            }
                        },
                        enabled = !isSending
                    ) {
                        Text("清除")
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(selectedImages, key = { it }) { imageUri ->
                        Box {
                            Card {
                                AsyncImage(
                                    model = imageUri,
                                    contentDescription = "已选择图片",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(88.dp)
                                )
                            }
                            TextButton(
                                onClick = {
                                    selectedImages.remove(imageUri)
                                    if (errorMessage.startsWith("最多可选择") || errorMessage == "已忽略重复图片。") {
                                        errorMessage = ""
                                    }
                                },
                                enabled = !isSending
                            ) {
                                Text("移除")
                            }
                        }
                    }
                    items(selectedFiles, key = { it.uri }) { file ->
                        Card {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("文件：${file.name}", maxLines = 1)
                                TextButton(
                                    onClick = { selectedFiles.remove(file) },
                                    enabled = !isSending
                                ) {
                                    Text("移除")
                                }
                            }
                        }
                    }
                }
            }
            if (errorMessage.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    failedMessage?.let { message ->
                        TextButton(
                            onClick = {
                                startSend(message) {
                                    repository.deleteMessage(message.id)
                                    repository.sendMessage(
                                        sessionId = sessionId,
                                        parts = message.parts,
                                        webSearchOverride = isWebSearchEnabledForDraft,
                                        includeHistory = true,
                                        requestMode = ChatRequestMode.CHAT
                                    ).getOrThrow()
                                }
                            },
                            enabled = !isSending
                        ) {
                            Text("重试")
                        }
                    }
                }
            }
            if (queuedMessages.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(queuedMessages, key = { it.id }) { queued ->
                        Card {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = queued.preview,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                TextButton(onClick = { queuedMessages.remove(queued) }) {
                                    Text("移除")
                                }
                            }
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                ChatInputPanel(
                text = text,
                onTextChange = {
                    text = it
                    if (isImageGenerationArmed && it.isBlank()) {
                        isImageGenerationArmed = false
                    }
                    if (errorMessage.isNotBlank()) errorMessage = ""
                },
                enabled = !sessionMissing,
                isSending = isSending || isCreatingDraftSession,
                webSearchEnabled = isWebSearchEnabledForDraft,
                outgoingTokenEstimate = outgoingTokenEstimate,
                contextTokenEstimate = contextTokenEstimate,
                onSubmit = { submitMessage() },
                onStop = {
                    if (!isDraftSession) {
                        repository.cancelSend(sessionId)
                    }
                },
                onToggleWebSearch = { webSearchForDraft = !isWebSearchEnabledForDraft },
                onOpenSettings = {
                    draftConfig = config.copy(webSearchEnabled = isWebSearchEnabledForDraft)
                    showChatParams = true
                },
                onVoice = { errorMessage = "语音输入暂未接入" },
                isAttachmentMenuExpanded = isAttachmentMenuExpanded,
                onOpenAttachmentMenu = { isAttachmentMenuExpanded = true },
                onDismissAttachmentMenu = { isAttachmentMenuExpanded = false },
                canAddImage = canAddImage,
                onOpenGallery = {
                    isAttachmentMenuExpanded = false
                    galleryLauncher.launch(arrayOf("image/*"))
                },
                onOpenFile = {
                    isAttachmentMenuExpanded = false
                    fileLauncher.launch(arrayOf("*/*"))
                }
                )
            }
        }
    }
    }
}


@Composable
private fun ChatParamsSheet(
    config: ApiConfig,
    onConfigChange: (ApiConfig) -> Unit,
    onDismiss: () -> Unit,
    onSavePreset: () -> Unit,
    onApply: () -> Unit
) {
    var helpTip by remember { mutableStateOf<Pair<String, String>?>(null) }
    var maxTokensText by remember(config.maxTokens) { mutableStateOf(config.maxTokens.toOutputLengthText()) }
    val maxTokensIsValid = parseOutputLength(maxTokensText) != null
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.38f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.78f)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Color.White)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(ChatHandle)
                        .align(Alignment.CenterHorizontally)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(ChatSoftBlue)
                            .border(1.dp, ChatBorder, RoundedCornerShape(99.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AppIcon(R.drawable.ic_settings_24, ChatPrimary, Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "聊天参数",
                        color = ChatText,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {},
                        modifier = Modifier
                            .height(42.dp)
                            .border(1.dp, ChatBorder, RoundedCornerShape(14.dp))
                            .padding(horizontal = 8.dp)
                    ) {
                        Text("默认预设", color = ChatMuted, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        AppIcon(R.drawable.ic_chevron_down_24, ChatMuted, Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .size(42.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(ChatIconBackground)
                            .border(1.dp, ChatBorder, RoundedCornerShape(99.dp))
                    ) {
                        AppIcon(R.drawable.ic_close_24, ChatMuted, Modifier.size(23.dp))
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ParameterCard(modifier = Modifier.weight(1f)) {
                            SliderParameter(
                                title = "温度",
                                help = "控制回答随机性。数值越低越稳定，越高越发散。",
                                onHelp = { helpTip = it },
                                value = config.temperature,
                                valueLabel = "%.1f".format(config.temperature),
                                range = 0f..2f,
                                tickLabels = listOf("0", "0.7", "1.5", "2"),
                                onValueChange = { onConfigChange(config.copy(temperature = it)) }
                            )
                        }
                        ParameterCard(modifier = Modifier.weight(1f)) {
                            TextParameter(
                                title = "最大长度",
                                help = "限制单次回复最多生成的令牌数量。填“无限”或 0 时不向接口传 max_tokens。",
                                onHelp = { helpTip = it },
                                value = maxTokensText,
                                placeholder = "无限 / 4096",
                                isError = parseOutputLength(maxTokensText) == null,
                                onValueChange = { value ->
                                    maxTokensText = value
                                    parseOutputLength(value)?.let { parsed ->
                                        onConfigChange(config.copy(maxTokens = parsed))
                                    }
                                }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ParameterCard(modifier = Modifier.weight(1f)) {
                            ParameterTitle(
                                "联网搜索",
                                "开启后会把 Tavily 搜索结果加入模型上下文。",
                                onHelp = { helpTip = it }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Switch(
                                    checked = config.webSearchEnabled,
                                    onCheckedChange = { onConfigChange(config.copy(webSearchEnabled = it)) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = ChatPrimary,
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = ChatBorder
                                    )
                                )
                            }
                        }
                        ParameterCard(modifier = Modifier.weight(1f)) {
                            ParameterTitle(
                                "上下文记忆",
                                "控制带入模型上下文的历史消息范围。",
                                onHelp = { helpTip = it }
                            )
                            SegmentedOptions(
                                options = listOf("关闭", "短期", "长期"),
                                selected = config.contextMemory,
                                onSelect = { onConfigChange(config.copy(contextMemory = it)) }
                            )
                        }
                    }
                    ParameterCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ParameterTitle(
                                "系统提示词",
                                "作为系统提示词发送，用于约束助手行为和回答风格。",
                                modifier = Modifier.weight(1f),
                                onHelp = { helpTip = it }
                            )
                            TextButton(onClick = {}) {
                                Text("编辑", color = ChatMuted, fontSize = 13.sp)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(ChatFieldBackground)
                                .border(1.dp, ChatBorder, RoundedCornerShape(13.dp))
                                .padding(horizontal = 14.dp, vertical = 13.dp)
                        ) {
                            BasicTextField(
                                value = config.systemPrompt.ifBlank { "你是 ModelTap AI，一位专业且乐于助人的助手。" },
                                onValueChange = { onConfigChange(config.copy(systemPrompt = it)) },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.Black,
                                    fontSize = 14.sp
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ParameterCard(modifier = Modifier.weight(1f)) {
                            ParameterTitle(
                                "推理强度",
                                "对支持的模型设置推理强度，并通过系统提示词进一步强化。",
                                onHelp = { helpTip = it }
                            )
                            SegmentedOptions(
                                options = listOf("低", "中", "高"),
                                selected = config.reasoningStrength,
                                onSelect = { onConfigChange(config.copy(reasoningStrength = it)) }
                            )
                        }
                        ParameterCard(modifier = Modifier.weight(1f)) {
                            ParameterTitle(
                                "回复风格",
                                "调整回答更偏精准、平衡或创意。",
                                onHelp = { helpTip = it }
                            )
                            SegmentedOptions(
                                options = listOf("精准", "平衡", "创意"),
                                selected = config.replyStyle,
                                onSelect = { onConfigChange(config.copy(replyStyle = it)) }
                            )
                        }
                    }
                    ParameterCard {
                        DiscreteSliderParameter(
                            title = "上下文窗口",
                            help = "控制可用于会话历史的令牌预算。",
                            onHelp = { helpTip = it },
                            selectedValue = config.contextWindow,
                            valueLabel = formatContextWindow(config.contextWindow),
                            values = listOf(4096, 8192, 16384, 32768, 65536, 131072),
                            tickLabels = listOf("4K", "8K", "16K", "32K", "64K", "128K"),
                            onValueChange = { onConfigChange(config.copy(contextWindow = it)) }
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = onSavePreset,
                        enabled = maxTokensIsValid,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = ChatPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .border(1.dp, ChatPrimary.copy(alpha = 0.65f), RoundedCornerShape(15.dp))
                    ) {
                        AppIcon(R.drawable.ic_diamond_24, ChatPrimary, Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(7.dp))
                        Text("保存预设", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onApply,
                        enabled = maxTokensIsValid,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ChatPrimary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                    ) {
                        AppIcon(R.drawable.ic_magic_24, Color.White, Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(7.dp))
                        Text("应用", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                helpTip?.let { (title, message) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(ChatSelectedSegment)
                            .border(1.dp, ChatBorder, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, color = ChatText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                message,
                                color = ChatMuted,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(onClick = { helpTip = null }) {
                            Text("关闭", color = ChatPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }
            Text(
                text = "内容由 AI 生成，仅供参考，请自行判断。",
                color = ChatMuted.copy(alpha = 0.58f),
                fontSize = 10.sp,
                lineHeight = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ParameterCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White)
            .border(1.dp, ChatBorder, RoundedCornerShape(15.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun ParameterTitle(title: String, modifier: Modifier = Modifier) {
    ParameterTitle(title = title, help = "", modifier = modifier, onHelp = {})
}

@Composable
private fun ParameterTitle(
    title: String,
    help: String,
    modifier: Modifier = Modifier,
    onHelp: (Pair<String, String>) -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, color = ChatText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "?",
            color = ChatMuted,
            fontSize = 13.sp,
            modifier = Modifier.clickable(enabled = help.isNotBlank()) { onHelp(title to help) }
        )
    }
}

@Composable
private fun SliderParameter(
    title: String,
    help: String,
    onHelp: (Pair<String, String>) -> Unit,
    value: Float,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    tickLabels: List<String>,
    onValueChange: (Float) -> Unit
) {
    ParameterTitle(title, help, onHelp = onHelp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Slider(
            value = value.coerceIn(range),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = ChatPrimary,
                activeTrackColor = ChatPrimary,
                inactiveTrackColor = ChatTrack
            ),
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .height(42.dp)
                .width(58.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ChatFieldBackground)
                .border(1.dp, ChatBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(valueLabel, color = ChatText, fontSize = 14.sp)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        tickLabels.forEach { label ->
            Text(label, color = ChatMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun TextParameter(
    title: String,
    help: String,
    onHelp: (Pair<String, String>) -> Unit,
    value: String,
    placeholder: String,
    isError: Boolean,
    onValueChange: (String) -> Unit
) {
    ParameterTitle(title, help, onHelp = onHelp)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        singleLine = true,
        isError = isError,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun DiscreteSliderParameter(
    title: String,
    help: String,
    onHelp: (Pair<String, String>) -> Unit,
    selectedValue: Int,
    valueLabel: String,
    values: List<Int>,
    tickLabels: List<String>,
    onValueChange: (Int) -> Unit
) {
    val selectedIndex = values.indexOf(selectedValue).takeIf { it >= 0 } ?: values.indexOf(32768).coerceAtLeast(0)
    ParameterTitle(title, help, onHelp = onHelp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Slider(
            value = selectedIndex.toFloat(),
            onValueChange = { index ->
                onValueChange(values[index.roundToInt().coerceIn(values.indices)])
            },
            valueRange = 0f..(values.lastIndex).toFloat(),
            steps = (values.size - 2).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = ChatPrimary,
                activeTrackColor = ChatPrimary,
                inactiveTrackColor = ChatTrack
            ),
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .height(42.dp)
                .width(58.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ChatFieldBackground)
                .border(1.dp, ChatBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(valueLabel, color = ChatText, fontSize = 14.sp)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        tickLabels.forEach { label ->
            Text(label, color = ChatMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SegmentedOptions(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ChatFieldBackground)
            .border(1.dp, ChatBorder, RoundedCornerShape(10.dp))
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            TextButton(
                onClick = { onSelect(option) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) ChatSelectedSegment else Color.Transparent)
                    .border(
                        width = if (isSelected) 1.dp else 0.dp,
                        color = if (isSelected) ChatPrimary else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    )
            ) {
                Text(
                    option,
                    color = if (isSelected) ChatPrimary else ChatMuted,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

private fun formatContextWindow(value: Int): String {
    return "${value / 1024}K"
}

private fun Int.toOutputLengthText(): String {
    return if (this <= 0) "无限" else toString()
}

private fun parseOutputLength(value: String): Int? {
    val normalized = value.trim()
    if (normalized.isBlank()) return null
    if (
        normalized == "0" ||
        normalized.equals("无限", ignoreCase = true) ||
        normalized.equals("unlimited", ignoreCase = true)
    ) {
        return 0
    }
    return normalized.toIntOrNull()?.takeIf { it > 0 }
}

@Composable
private fun ChatHeader(
    subtitle: String,
    selectedModelLabel: String,
    channelLabel: String,
    onOpenMenu: () -> Unit,
    onOpenSessionDetail: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenModelMenu: () -> Unit,
    isModelMenuExpanded: Boolean,
    onDismissModelMenu: () -> Unit,
    availableApiEntries: List<ApiKeyEntry>,
    onSelectApiEntry: (ApiKeyEntry) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ChatPageBackground)
            .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        IconButton(onClick = onOpenMenu, modifier = Modifier.size(42.dp)) {
            AppIcon(R.drawable.ic_menu_24, ChatMuted, Modifier.size(25.dp))
        }
        Box(
            modifier = Modifier
                .widthIn(min = 42.dp, max = 58.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onOpenSessionDetail)
                .padding(horizontal = 2.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = subtitle,
                color = ChatMuted,
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            TextButton(
                onClick = onOpenModelMenu,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .border(1.dp, ChatBorder, RoundedCornerShape(15.dp))
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = selectedModelLabel.ifBlank { "模型" },
                    color = ChatText,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                AppIcon(R.drawable.ic_chevron_down_24, ChatMuted, Modifier.size(18.dp))
            }
            DropdownMenu(
                expanded = isModelMenuExpanded,
                onDismissRequest = onDismissModelMenu
            ) {
                if (availableApiEntries.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("无可用 API，请先完成配置。") },
                        onClick = onDismissModelMenu
                    )
                } else {
                    availableApiEntries.forEach { entry ->
                        DropdownMenuItem(
                            text = { Text(entry.toModelDisplay().label) },
                            onClick = { onSelectApiEntry(entry) }
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .height(42.dp)
                .border(1.dp, ChatBorder, RoundedCornerShape(15.dp))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0xFF38C9BA))
            )
            Text(channelLabel, color = ChatText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 64.dp))
        }
        Box(
            modifier = Modifier
                .height(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenHome)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("首页", color = ChatMuted, fontSize = 15.sp, maxLines = 1)
        }
    }
}

@Composable
private fun QuickActionRow(
    isImageGenerationArmed: Boolean,
    onImageGeneration: () -> Unit,
    onSummary: () -> Unit,
    onTranslate: () -> Unit,
    onCode: () -> Unit,
    onOptimizePrompt: () -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { QuickActionButton("图", "生图", onImageGeneration, active = isImageGenerationArmed) }
        item { QuickActionButton("摘", "总结", onSummary) }
        item { QuickActionButton("译", "翻译", onTranslate) }
        item { QuickActionButton("</>", "代码", onCode) }
        item { QuickActionButton("优", "优化提示", onOptimizePrompt) }
    }
}

@Composable
private fun QuickActionButton(
    icon: String,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false
) {
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(13.dp))
            .border(1.dp, if (active) ChatPrimary else ChatBorder, RoundedCornerShape(13.dp))
            .background(if (active) ChatSelectedSegment else Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, color = if (active) ChatPrimary else ChatMuted, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(5.dp))
        Text(label, color = if (active) ChatPrimary else ChatText, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun ChatInputPanel(
    text: String,
    onTextChange: (String) -> Unit,
    enabled: Boolean,
    isSending: Boolean,
    webSearchEnabled: Boolean,
    outgoingTokenEstimate: Int,
    contextTokenEstimate: Int,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
    onToggleWebSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onVoice: () -> Unit,
    isAttachmentMenuExpanded: Boolean,
    onOpenAttachmentMenu: () -> Unit,
    onDismissAttachmentMenu: () -> Unit,
    canAddImage: Boolean,
    onOpenGallery: () -> Unit,
    onOpenFile: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        border = BorderStroke(1.dp, ChatComposerBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(ChatInputFieldBackground)
                    .border(1.dp, ChatInputFieldBorder, RoundedCornerShape(18.dp))
                    .padding(horizontal = 13.dp, vertical = 8.dp)
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    enabled = enabled,
                    minLines = 1,
                    maxLines = 5,
                    cursorBrush = SolidColor(ChatPrimary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = Color.Black,
                        fontSize = 16.sp,
                        lineHeight = 22.sp
                    ),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (text.isBlank()) {
                                Text(
                                    text = if (enabled) "请输入消息，换行请使用 Shift + Enter" else "会话不可用",
                                    color = ChatMuted.copy(alpha = 0.72f),
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 34.dp)
                        .onPreviewKeyEvent { event ->
                            if (event.key == Key.Enter && event.type == KeyEventType.KeyUp && !event.isShiftPressed) {
                                onSubmit()
                                true
                            } else {
                                false
                            }
                        }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box {
                        RoundInputIconButton(
                            iconRes = R.drawable.ic_attach_file_24,
                            contentDescription = "附件",
                            onClick = onOpenAttachmentMenu,
                            enabled = enabled
                        )
                        DropdownMenu(
                            expanded = isAttachmentMenuExpanded,
                            onDismissRequest = onDismissAttachmentMenu
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (canAddImage) "添加图片" else "图片已满") },
                                onClick = onOpenGallery,
                                enabled = canAddImage
                            )
                            DropdownMenuItem(
                                text = { Text("添加文件") },
                                onClick = onOpenFile,
                                enabled = enabled
                            )
                        }
                    }
                    RoundInputIconButton(
                        iconRes = R.drawable.ic_mic_24,
                        contentDescription = "语音",
                        onClick = onVoice,
                        enabled = enabled
                    )
                    RoundInputIconButton(
                        iconRes = R.drawable.ic_public_24,
                        contentDescription = "联网搜索",
                        onClick = onToggleWebSearch,
                        enabled = enabled,
                        active = webSearchEnabled
                    )
                    RoundInputIconButton(
                        iconRes = R.drawable.ic_settings_24,
                        contentDescription = "设置",
                        onClick = onOpenSettings
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "本次 $outgoingTokenEstimate",
                            color = ChatMuted,
                            fontSize = 10.sp,
                            lineHeight = 13.sp
                        )
                        Text(
                            text = "上下文$contextTokenEstimate",
                            color = ChatMuted.copy(alpha = 0.72f),
                            fontSize = 10.sp,
                            lineHeight = 13.sp
                        )
                    }
                    IconButton(
                        onClick = if (isSending) onStop else onSubmit,
                        enabled = enabled,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isSending) ChatStopBackground else ChatPrimary,
                            disabledContainerColor = ChatSoftBlue
                        ),
                        modifier = Modifier.size(46.dp)
                    ) {
                        if (isSending) {
                            Text("停止", color = ChatText, fontSize = 14.sp)
                        } else {
                            Image(
                                painter = painterResource(R.drawable.ic_send_24),
                                contentDescription = "发送",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundInputIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    active: Boolean = false
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (active) ChatActiveIconBackground else ChatIconBackground,
            disabledContainerColor = ChatIconBackground.copy(alpha = 0.58f)
        ),
        modifier = Modifier
            .size(36.dp)
            .border(
                1.dp,
                if (active) ChatPrimary.copy(alpha = 0.28f) else ChatInputFieldBorder,
                RoundedCornerShape(13.dp)
            )
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp)
        )
    }
}

private val ChatPageBackground = Color(0xFFF7FAFE)
private val ChatDrawerBackground = Color(0xFFF4F8FD)
private val ChatBorder = Color(0xFFDDE7F4)
private val ChatText = Color(0xFF1E293B)
private val ChatMuted = Color(0xFF6B778D)
private val ChatIconBackground = Color(0xFFFBFDFF)
private val ChatActiveIconBackground = Color(0xFFE8F2FF)
private val ChatSoftBlue = Color(0xFFEFF6FF)
private val ChatInputFieldBackground = Color(0xFFF8FBFF)
private val ChatInputFieldBorder = Color(0xFFE7EEF8)
private val ChatComposerBorder = Color(0xFFD8E4F2)
private val ChatStopBackground = Color(0xFFFFEEF0)
private val ChatPrimary = Color(0xFF4B8BFF)
private val ChatHandle = Color(0xFFC6D2E3)
private val ChatFieldBackground = Color(0xFFFBFCFF)
private val ChatSelectedSegment = Color(0xFFEAF3FF)
private val ChatTrack = Color(0xFFE8EDF5)

private const val MaxSelectedImages = 5
private const val MaxSelectedFileBytes = 5L * 1024L * 1024L
private const val InitialMessageLoadCount = 5
private const val MessageLoadPageSize = 10
private const val DrawerInitialSessionCount = 7
private const val DrawerSessionPageSize = 7

@Composable
private fun ChatSessionDrawer(
    sessions: List<ChatSession>,
    totalSessionCount: Int,
    currentSessionId: Long,
    selectedModel: String,
    listState: LazyListState,
    hasMoreSessions: Boolean,
    onOpenHistory: () -> Unit,
    onOpenImageCreate: () -> Unit,
    onOpenSession: (Long) -> Unit,
    onCreateSession: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = ChatDrawerBackground,
        drawerContentColor = ChatText,
        modifier = Modifier.widthIn(max = 340.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DrawerProfileHeader(
                selectedModel = selectedModel,
                totalSessionCount = totalSessionCount
            )
            DrawerHistoryButton(onClick = onOpenHistory)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, start = 2.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "最近会话",
                    color = ChatText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "共 $totalSessionCount 个",
                    color = ChatMuted,
                    fontSize = 12.sp
                )
            }
            if (sessions.isEmpty()) {
                DrawerEmptyState(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    groupedSessionsByDate(sessions).forEach { group ->
                        item(key = "date-${group.title}") {
                            DrawerDateHeader(title = group.title)
                        }
                        items(group.sessions, key = { it.id }) { session ->
                            DrawerSessionRow(
                                session = session,
                                selected = session.id == currentSessionId,
                                onClick = { onOpenSession(session.id) }
                            )
                        }
                    }
                    if (hasMoreSessions) {
                        item(key = "drawer-loading-more") {
                            DrawerMoreHint()
                        }
                    }
                }
            }
            DrawerActionButtons(
                onCreateSession = onCreateSession,
                onOpenImageCreate = onOpenImageCreate
            )
        }
    }
}

@Composable
private fun DrawerActionButtons(
    onCreateSession: () -> Unit,
    onOpenImageCreate: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DrawerActionButton(
            text = "新会话",
            iconRes = R.drawable.ic_add_24,
            background = ChatPrimary,
            foreground = Color.White,
            border = ChatPrimary,
            onClick = onCreateSession
        )
        DrawerActionButton(
            text = "生图",
            iconRes = R.drawable.ic_magic_24,
            background = ChatSelectedSegment,
            foreground = ChatPrimary,
            border = Color(0xFFD2E4FF),
            onClick = onOpenImageCreate
        )
    }
}

@Composable
private fun RowScope.DrawerActionButton(
    text: String,
    iconRes: Int,
    background: Color,
    foreground: Color,
    border: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .weight(1f)
            .height(52.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(17.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        AppIcon(iconRes, foreground, Modifier.size(21.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, color = foreground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun DrawerProfileHeader(
    selectedModel: String,
    totalSessionCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .border(1.dp, ChatBorder, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ChatSoftBlue)
            .border(1.dp, ChatBorder, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.modeltap_icon),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(34.dp)
        )
    }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "本地工作区",
                    color = ChatText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "会话保存在此设备",
                    color = ChatMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0xFF38C9BA))
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ChatFieldBackground)
                .border(1.dp, ChatBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("当前模型", color = ChatMuted, fontSize = 11.sp)
                Text(
                    text = selectedModel.ifBlank { "未选择模型" },
                    color = ChatText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "$totalSessionCount",
                color = ChatPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DrawerHistoryButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ChatSelectedSegment)
            .border(1.dp, Color(0xFFD2E4FF), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("全部会话", color = ChatPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.weight(1f))
        Text("查看完整历史", color = ChatMuted, fontSize = 12.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(">", color = ChatPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DrawerDateHeader(title: String) {
    Text(
        text = title,
        color = ChatMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun DrawerSessionRow(
    session: ChatSession,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background = if (selected) ChatSelectedSegment else Color.White
    val borderColor = if (selected) ChatPrimary.copy(alpha = 0.45f) else ChatBorder
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) Color.White else ChatFieldBackground)
                .border(1.dp, ChatBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("AI", color = if (selected) ChatPrimary else ChatMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.title.ifBlank { "新会话" },
                color = ChatText,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatDrawerSessionTime(session.updatedAt),
                color = ChatMuted,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(ChatPrimary)
            )
        }
    }
}

@Composable
private fun DrawerMoreHint() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("继续下滑加载更多", color = ChatMuted, fontSize = 12.sp)
    }
}

@Composable
private fun DrawerEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, ChatBorder, RoundedCornerShape(18.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("暂无会话", color = ChatText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        Text("开始聊天后，会话会显示在这里。", color = ChatMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EmptyConversationPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "💬",
            style = MaterialTheme.typography.displaySmall
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "今天想做点什么？",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "你可以聊天、整理想法、查看图片和文件，也可以描述画面来生成图片。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private data class SessionDateGroup(
    val title: String,
    val sessions: List<ChatSession>
)

private data class QueuedMessage(
    val parts: List<MessagePart>,
    val webSearchEnabled: Boolean,
    val requestMode: ChatRequestMode,
    val id: Long = System.nanoTime()
) {
    val preview: String = parts.joinToString(" + ") { part ->
        when (part) {
            is MessagePart.TextPart -> part.text.take(24).ifBlank { "文字" }
            is MessagePart.LocalImagePart -> "图片"
            is MessagePart.RemoteImagePart -> "图片"
            is MessagePart.LocalFilePart -> "文件:${part.name}"
        }
    }
}

private data class SelectedFile(
    val uri: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String?
) {
    fun toMessagePart(): MessagePart.LocalFilePart {
        return MessagePart.LocalFilePart(
            uri = uri,
            name = name,
            sizeBytes = sizeBytes,
            mimeType = mimeType
        )
    }
}

private fun groupedSessionsByDate(sessions: List<ChatSession>): List<SessionDateGroup> {
    return sessions.groupBy { formatSessionGroupDate(it.updatedAt) }
        .map { (title, groupedSessions) -> SessionDateGroup(title, groupedSessions) }
}

private fun formatSessionGroupDate(timestamp: Long): String {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameYear = now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
    val sameDay = sameYear &&
        now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return "今天"

    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = yesterday.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
        yesterday.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    if (isYesterday) return "昨天"

    val pattern = if (sameYear) "MM/dd" else "yyyy/MM/dd"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
}

private fun formatDrawerSessionTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = (now - timestamp).coerceAtLeast(0L)
    val minute = 60L * 1000L
    val hour = 60L * minute
    val day = 24L * hour
    return when {
        diff < minute -> "刚刚"
        diff < hour -> "${diff / minute} 分钟前"
        diff < day -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        diff < day * 2 -> "昨天 ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))}"
        else -> SimpleDateFormat("MM月dd日HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun Context.readSelectedFile(uri: Uri): SelectedFile? {
    val resolver = contentResolver
    var name = uri.lastPathSegment ?: "文件"
    var size = -1L
    resolver.query(uri, null, null, null, null)?.use { cursor ->
        name = cursor.getStringOrNull(OpenableColumns.DISPLAY_NAME) ?: name
        size = cursor.getLongOrNull(OpenableColumns.SIZE) ?: size
    }
    if (size < 0L) {
        size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    }
    if (size < 0L) return null
    return SelectedFile(
        uri = uri.toString(),
        name = name,
        sizeBytes = size,
        mimeType = resolver.getType(uri)
    )
}

private fun Cursor.getStringOrNull(columnName: String): String? {
    val index = getColumnIndex(columnName)
    if (index < 0 || isNull(index)) return null
    return getString(index)
}

private fun Cursor.getLongOrNull(columnName: String): Long? {
    val index = getColumnIndex(columnName)
    if (index < 0 || isNull(index)) return null
    return getLong(index)
}
