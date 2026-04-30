package com.wuyousheng.modeltap.ui.screens.imagecreate

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.annotation.DrawableRes
import coil.compose.SubcomposeAsyncImage
import com.wuyousheng.modeltap.R
import com.wuyousheng.modeltap.data.repository.ChatRepository
import com.wuyousheng.modeltap.data.repository.ImageGenerationTask
import com.wuyousheng.modeltap.data.repository.safeUserError
import com.wuyousheng.modeltap.domain.model.GeneratedImage
import com.wuyousheng.modeltap.domain.model.toModelDisplay
import com.wuyousheng.modeltap.service.ImageGenerationForegroundService
import com.wuyousheng.modeltap.storage.saveGeneratedImageToDownloads
import com.wuyousheng.modeltap.ui.components.AppIcon
import kotlinx.coroutines.launch

@Composable
fun ImageCreateScreen(
    repository: ChatRepository,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenGallery: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val config by repository.observeConfig().collectAsState(initial = com.wuyousheng.modeltap.domain.model.ApiConfig())
    val apiKeyEntries by repository.observeApiKeyEntries().collectAsState(initial = emptyList())
    val usableApiEntries = remember(apiKeyEntries) {
        apiKeyEntries.filter { it.apiKey.isNotBlank() && it.baseUrl.isNotBlank() && it.selectedModel.isNotBlank() }
    }
    val modelDisplay = remember(config, apiKeyEntries) { config.toModelDisplay(apiKeyEntries) }
    val modelOptions = remember(usableApiEntries, modelDisplay.label) {
        (usableApiEntries.map { it.toModelDisplay().label } + modelDisplay.label)
            .filter { it.isNotBlank() }
            .filterNot { it == "未选择模型" }
            .distinct()
    }
    val recentImages by repository.observeRecentGeneratedImages(limit = 18).collectAsState(initial = emptyList())
    val activeImageTasks by repository.observeActiveImageGenerationTasks().collectAsState(initial = emptyList())
    var prompt by remember { mutableStateOf("") }
    var selectedStyle by remember { mutableStateOf("写实") }
    var selectedRatio by remember { mutableStateOf("1:1") }
    var selectedModel by remember(modelDisplay.label) { mutableStateOf(modelDisplay.label) }
    var selectedApiEntryId by remember(modelDisplay.label, usableApiEntries) {
        mutableStateOf(usableApiEntries.firstOrNull { it.toModelDisplay().label == modelDisplay.label }?.id.orEmpty())
    }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var ratioMenuExpanded by remember { mutableStateOf(false) }
    var isLoadingInspiration by remember { mutableStateOf(false) }
    val referenceImages = remember { mutableStateListOf<String>() }
    val isGenerating = activeImageTasks.isNotEmpty()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            scope.launch { snackbarHostState.showSnackbar("未开启通知权限，后台生图仍会执行，但可能看不到通知进度") }
        }
    }
    val referenceImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val newUris = uris.map(Uri::toString).filterNot { it in referenceImages }
        val remainingSlots = MaxReferenceImages - referenceImages.size
        referenceImages += newUris.take(remainingSlots)
        scope.launch {
            when {
                uris.isNotEmpty() && newUris.isEmpty() -> snackbarHostState.showSnackbar("已忽略重复参考图")
                newUris.size > remainingSlots -> snackbarHostState.showSnackbar("最多上传 $MaxReferenceImages 张参考图")
            }
        }
    }

    LaunchedEffect(modelDisplay.label) {
        selectedModel = modelDisplay.label
    }

    LaunchedEffect(Unit) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(activeImageTasks.firstOrNull()?.id) {
        val task = activeImageTasks.firstOrNull() ?: return@LaunchedEffect
        val draft = parseImagePromptDraft(task.prompt)
        prompt = draft.prompt
        selectedStyle = draft.style
        selectedRatio = draft.ratio
        referenceImages.clear()
        referenceImages += task.referenceImageUris.take(MaxReferenceImages)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF8FBFF), Color(0xFFF2F7FE), Color(0xFFFAFCFF))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 34.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ImageCreateTopBar(onBack = onBack, onOpenHistory = onOpenHistory)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                item {
                    SelectorCard(
                        label = "模型",
                        value = selectedModel,
                        leadingIconRes = R.drawable.ic_diamond_24,
                        expanded = modelMenuExpanded,
                        onClick = { modelMenuExpanded = true },
                        onDismiss = { modelMenuExpanded = false },
                        options = modelOptions.ifEmpty { listOf("无可用 API，请先配置") },
                        onSelect = { label ->
                            val entry = usableApiEntries.firstOrNull { it.toModelDisplay().label == label }
                            selectedModel = label
                            selectedApiEntryId = entry?.id.orEmpty()
                            modelMenuExpanded = false
                            entry?.let {
                                scope.launch { repository.saveApiKeyEntry(entry, makeCurrent = true) }
                            }
                        },
                        modifier = Modifier.width(190.dp)
                    )
                }
                item {
                    SelectorCard(
                        label = "比例",
                        value = selectedRatio,
                        leadingIconRes = R.drawable.ic_image_24,
                        expanded = ratioMenuExpanded,
                        onClick = { ratioMenuExpanded = true },
                        onDismiss = { ratioMenuExpanded = false },
                        options = listOf("1:1", "3:4", "4:3", "16:9", "9:16"),
                        onSelect = { selectedRatio = it; ratioMenuExpanded = false },
                        modifier = Modifier.width(142.dp)
                    )
                }
            }
            PromptCard(
                prompt = prompt,
                isLoadingInspiration = isLoadingInspiration,
                onPromptChange = { prompt = it },
                onRandomInspiration = {
                    if (usableApiEntries.isEmpty() || selectedApiEntryId.isBlank()) {
                        scope.launch { snackbarHostState.showSnackbar("无可用 API，请先配置后再使用") }
                    } else {
                        scope.launch {
                            isLoadingInspiration = true
                            repository.generateImageInspiration(selectedApiEntryId)
                                .onSuccess { prompt = it }
                                .onFailure { error -> snackbarHostState.showSnackbar(safeUserError(error)) }
                            isLoadingInspiration = false
                        }
                    }
                }
            )
            StyleSelector(selected = selectedStyle, onSelect = { selectedStyle = it })
            ReferenceImageCard(
                images = referenceImages,
                onAddImages = { referenceImageLauncher.launch(arrayOf("image/jpeg", "image/png", "image/webp")) },
                onRemoveImage = { referenceImages.remove(it) },
                onClear = { referenceImages.clear() }
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GenerateButton(
                    isGenerating = isGenerating,
                    enabled = !isGenerating && prompt.isNotBlank() && usableApiEntries.isNotEmpty() && selectedApiEntryId.isNotBlank(),
                    onClick = {
                        if (usableApiEntries.isEmpty() || selectedApiEntryId.isBlank()) {
                            scope.launch { snackbarHostState.showSnackbar("无可用 API，请先配置后再使用") }
                            return@GenerateButton
                        }
                        val fullPrompt = buildImagePrompt(prompt, selectedStyle, selectedRatio)
                        runCatching {
                            ImageGenerationForegroundService.start(
                                context = context,
                                prompt = fullPrompt,
                                apiEntryId = selectedApiEntryId,
                                referenceImageUris = referenceImages.toList()
                            )
                        }.onFailure { error ->
                            scope.launch {
                                snackbarHostState.showSnackbar("启动后台生图失败：${safeUserError(error)}")
                            }
                        }
                    }
                )
                if (isGenerating) {
                    Text(
                        text = "生图已转入后台任务，生成期间请保持网络连接",
                        color = ImageMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    text = "图片由 AI 生成，仅供参考，请自行判断。",
                    color = ImageMuted.copy(alpha = 0.62f),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            ActiveImageTaskSection(tasks = activeImageTasks)
            RecentGeneratedSection(
                images = recentImages,
                onOpenGallery = onOpenGallery,
                onSave = { image ->
                    scope.launch {
                        runCatching { context.saveGeneratedImageToDownloads(image) }
                            .onSuccess { snackbarHostState.showSnackbar("已保存到下载目录 ModelTap 文件夹") }
                            .onFailure { error -> snackbarHostState.showSnackbar(safeUserError(error)) }
                    }
                }
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun ImageCreateTopBar(onBack: () -> Unit, onOpenHistory: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(R.drawable.ic_arrow_back_24, ImageText, Modifier.size(24.dp))
        }
        Text(
            text = "AI生图",
            color = ImageText,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Row(
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onOpenHistory)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(R.drawable.ic_history_24, ImageMuted, Modifier.size(17.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("历史记录", color = ImageMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SelectorCard(
    label: String,
    value: String,
    @DrawableRes
    leadingIconRes: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color.White)
                .border(1.dp, ImageBorder, RoundedCornerShape(13.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = ImageMuted, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(18.dp))
            AppIcon(leadingIconRes, ImageBlue, Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(value, color = ImageText, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            AppIcon(R.drawable.ic_chevron_down_24, ImageMuted, Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option) })
            }
        }
    }
}

@Composable
private fun PromptCard(
    prompt: String,
    isLoadingInspiration: Boolean,
    onPromptChange: (String) -> Unit,
    onRandomInspiration: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(134.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, ImageBorder, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        BasicTextField(
            value = prompt,
            onValueChange = { onPromptChange(it.take(1000)) },
            cursorBrush = SolidColor(ImageBlue),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = Color.Black,
                fontSize = 15.sp,
                lineHeight = 22.sp
            ),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (prompt.isBlank()) {
                        Text("描述你想生成的画面", color = ImagePlaceholder, fontSize = 15.sp)
                    }
                    innerTextField()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${prompt.length}/1000", color = ImagePlaceholder, fontSize = 13.sp)
            Spacer(modifier = Modifier.weight(1f))
            InspirationButton(
                isLoading = isLoadingInspiration,
                onClick = onRandomInspiration
            )
        }
    }
}

@Composable
private fun InspirationButton(isLoading: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, ImageBorder, RoundedCornerShape(12.dp))
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = ImageBlue)
        } else {
            AppIcon(R.drawable.ic_magic_24, ImageBlue, Modifier.size(14.dp))
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(if (isLoading) "生成中" else "随机灵感", color = ImageMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StyleSelector(selected: String, onSelect: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            Text("风格", color = ImageMuted, fontSize = 14.sp, modifier = Modifier.width(42.dp))
        }
        listOf(
            StyleOption(R.drawable.ic_camera_alt_24, "写实"),
            StyleOption(R.drawable.ic_image_24, "插画"),
            StyleOption(R.drawable.ic_diamond_24, "极简"),
            StyleOption(R.drawable.ic_image_24, "电影感")
        ).forEach { option ->
            item {
                StyleChip(
                    option = option,
                    selected = selected == option.label,
                    onClick = { onSelect(option.label) },
                    modifier = Modifier.width(88.dp)
                )
            }
        }
    }
}

@Composable
private fun StyleChip(option: StyleOption, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) ImageSelected else Color.White)
            .border(1.dp, if (selected) ImageBlue.copy(alpha = 0.52f) else ImageBorder, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        AppIcon(option.iconRes, if (selected) ImageBlue else ImageMuted, Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(option.label, color = if (selected) ImageBlue else ImageMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ReferenceImageCard(
    images: List<String>,
    onAddImages: () -> Unit,
    onRemoveImage: (String) -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, ImageBorder, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("参考图", color = ImageText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.width(4.dp))
            Text("(可选)", color = ImageMuted, fontSize = 12.sp)
            Spacer(modifier = Modifier.weight(1f))
            if (images.isNotEmpty()) {
                Text(
                    "清空",
                    color = ImageMuted,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onClear)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        if (images.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, ImageDashedBorder, RoundedCornerShape(14.dp))
                    .background(Color(0xFFFBFDFF))
                    .clickable(onClick = onAddImages),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("▧", color = ImageMuted, fontSize = 24.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("点击上传参考图", color = ImageMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("支持 JPG / PNG / WEBP，最多 $MaxReferenceImages 张", color = ImagePlaceholder, fontSize = 11.sp)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                images.forEach { imageUri ->
                    ReferenceImageThumbnail(
                        uri = imageUri,
                        onRemove = { onRemoveImage(imageUri) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (images.size < MaxReferenceImages) {
                    AddReferenceImageButton(
                        onClick = onAddImages,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(MaxReferenceImages - images.size - if (images.size < MaxReferenceImages) 1 else 0) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ReferenceImageThumbnail(uri: String, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFEAF2FB))
    ) {
        SubcomposeAsyncImage(
            model = uri,
            contentDescription = "参考图",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = { ImageThumbnailPlaceholder() },
            error = { ImageThumbnailPlaceholder() }
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(R.drawable.ic_close_24, Color.White, Modifier.size(14.dp))
        }
    }
}

@Composable
private fun AddReferenceImageButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, ImageDashedBorder, RoundedCornerShape(14.dp))
            .background(Color(0xFFFBFDFF))
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIcon(R.drawable.ic_image_24, ImageMuted, Modifier.size(22.dp))
        Text("添加", color = ImagePlaceholder, fontSize = 11.sp)
    }
}

@Composable
private fun GenerateButton(isGenerating: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    if (enabled || isGenerating) {
                        listOf(Color(0xFFB9D2FF), Color(0xFF7EE8E1))
                    } else {
                        listOf(Color(0xFFE1E9F5), Color(0xFFE8F1F8))
                    }
                )
            )
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (isGenerating) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
        } else {
            AppIcon(R.drawable.ic_magic_24, Color.White, Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(if (isGenerating) "正在生成" else "立即生成", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ActiveImageTaskSection(tasks: List<ImageGenerationTask>) {
    if (tasks.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("进行中的任务", color = ImageText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            Text("${tasks.size} 个", color = ImageMuted, fontSize = 13.sp)
        }
        tasks.take(5).forEachIndexed { index, task ->
            ActiveImageTaskRow(index = index, task = task)
        }
    }
}

@Composable
private fun ActiveImageTaskRow(index: Int, task: ImageGenerationTask) {
    val draft = remember(task.prompt) { parseImagePromptDraft(task.prompt) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White)
            .border(1.dp, ImageBorder, RoundedCornerShape(15.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(ImageSelected),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = ImageBlue)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "任务 ${index + 1} 生成中",
                color = ImageText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = draft.prompt.ifBlank { "生图任务" },
                color = ImageMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "${draft.style} · ${draft.ratio}",
            color = ImageBlue,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun RecentGeneratedSection(
    images: List<GeneratedImage>,
    onOpenGallery: () -> Unit,
    onSave: (GeneratedImage) -> Unit
) {
    var previewIndex by remember(images) { mutableStateOf<Int?>(null) }
    val previewImages = remember(images) { images.take(5) }
    val previewSnackbarHostState = remember { SnackbarHostState() }
    val previewScope = rememberCoroutineScope()

    previewIndex?.let { index ->
        ImagePreviewDialog(
            images = previewImages,
            currentIndex = index.coerceIn(0, (previewImages.size - 1).coerceAtLeast(0)),
            onDismiss = { previewIndex = null },
            onPrevious = {
                previewIndex = if (previewImages.isEmpty()) null else (index - 1 + previewImages.size) % previewImages.size
            },
            onNext = {
                previewIndex = if (previewImages.isEmpty()) null else (index + 1) % previewImages.size
            },
            snackbarHostState = previewSnackbarHostState,
            showSaveAction = true,
            onSave = { image ->
                onSave(image)
                previewScope.launch { previewSnackbarHostState.showSnackbar("已保存到下载目录 ModelTap 文件夹") }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("最近生成", color = ImageText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        if (images.isEmpty()) {
            EmptyRecentImages()
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.heightIn(max = 460.dp),
                userScrollEnabled = false,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(previewImages, key = { it.id }) { image ->
                    RecentImageCard(
                        image = image,
                        onOpen = { previewIndex = previewImages.indexOfFirst { it.id == image.id }.takeIf { it >= 0 } }
                    )
                }
                item(key = "open-gallery") {
                    MoreGeneratedImagesCard(onClick = onOpenGallery)
                }
            }
        }
    }
}

@Composable
private fun MoreGeneratedImagesCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, ImageBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("▦", color = ImageBlue, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        Text("查看更多", color = ImageText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RecentImageCard(image: GeneratedImage, onOpen: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFEAF2FB))
            .clickable(onClick = onOpen)
    ) {
        SubcomposeAsyncImage(
            model = image.uri,
            contentDescription = image.prompt.ifBlank { "最近生成图片" },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = { ImageThumbnailPlaceholder() },
            error = { ImageThumbnailPlaceholder() }
        )
    }
}

@Composable
fun ImagePreviewDialog(
    images: List<GeneratedImage>,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    snackbarHostState: SnackbarHostState? = null,
    showSaveAction: Boolean = true,
    onSave: (GeneratedImage) -> Unit
) {
    if (images.isEmpty()) return
    val image = images[currentIndex]
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .padding(horizontal = 16.dp, vertical = 28.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(48.dp)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(R.drawable.ic_close_24, Color.White, Modifier.size(28.dp))
            }
            SubcomposeAsyncImage(
                model = image.uri,
                contentDescription = image.prompt.ifBlank { "预览图片" },
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .heightIn(max = 620.dp),
                loading = { ImageThumbnailPlaceholder() },
                error = { ImageThumbnailPlaceholder() }
            )
            if (images.size > 1) {
                PreviewArrow(
                    iconRes = R.drawable.ic_arrow_back_24,
                    onClick = onPrevious,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                PreviewArrow(
                    iconRes = R.drawable.ic_arrow_forward_24,
                    onClick = onNext,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "${currentIndex + 1} / ${images.size}",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = image.prompt.ifBlank { "最近生成图片" },
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (showSaveAction) {
                    Text(
                        text = "保存",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.16f))
                            .clickable { onSave(image) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
            snackbarHostState?.let { hostState ->
                SnackbarHost(
                    hostState = hostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 76.dp)
                )
            }
        }
    }
}

@Composable
private fun PreviewArrow(@DrawableRes iconRes: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(50.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AppIcon(iconRes, Color.White, Modifier.size(28.dp))
    }
}

@Composable
fun ImageThumbnailPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFEAF2FB), Color(0xFFF7FAFE))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        AppIcon(R.drawable.ic_image_24, ImagePlaceholder, Modifier.size(24.dp))
    }
}

@Composable
fun EmptyRecentImages() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, ImageBorder, RoundedCornerShape(18.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.modeltap_icon),
            contentDescription = null,
            modifier = Modifier.size(42.dp)
        )
        Text("暂无生成图片", color = ImageText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("聊天会话里生成的图片也会出现在这里", color = ImageMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

private fun buildImagePrompt(prompt: String, style: String, ratio: String): String {
    return """
        生成图片：$prompt

        风格 $style，比例 $ratio。
    """.trimIndent()
}

private fun parseImagePromptDraft(fullPrompt: String): ImagePromptDraft {
    val prompt = fullPrompt
        .lineSequence()
        .firstOrNull { it.startsWith("生成图片：") }
        ?.removePrefix("生成图片：")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: fullPrompt.trim()
    val settingsLine = fullPrompt
        .lineSequence()
        .firstOrNull { it.startsWith("风格 ") && "，比例 " in it }
        .orEmpty()
    val style = settingsLine
        .substringAfter("风格 ", "")
        .substringBefore("，比例", "")
        .takeIf { it.isNotBlank() }
        ?: "写实"
    val ratio = settingsLine
        .substringAfter("，比例 ", "")
        .substringBefore("。")
        .takeIf { it.isNotBlank() }
        ?: "1:1"
    return ImagePromptDraft(prompt = prompt, style = style, ratio = ratio)
}

private data class StyleOption(@DrawableRes val iconRes: Int, val label: String)

private data class ImagePromptDraft(
    val prompt: String,
    val style: String,
    val ratio: String
)

private const val MaxReferenceImages = 4
val ImageText = Color(0xFF273244)
val ImageMuted = Color(0xFF8190A6)
val ImagePlaceholder = Color(0xFFAEBBD0)
val ImageBorder = Color(0xFFE3EAF4)
val ImageDashedBorder = Color(0xFFEAF0F8)
val ImageSelected = Color(0xFFF2F7FF)
val ImageBlue = Color(0xFF5D9BFF)
