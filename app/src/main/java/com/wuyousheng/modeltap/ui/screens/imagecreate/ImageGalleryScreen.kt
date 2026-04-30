package com.wuyousheng.modeltap.ui.screens.imagecreate

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.wuyousheng.modeltap.R
import com.wuyousheng.modeltap.data.repository.ChatRepository
import com.wuyousheng.modeltap.domain.model.GeneratedImage
import com.wuyousheng.modeltap.storage.saveGeneratedImageToDownloads
import com.wuyousheng.modeltap.ui.components.AppIcon
import kotlinx.coroutines.launch

private const val InitialGalleryImageCount = 16
private const val GalleryImagePageSize = 9

@Composable
fun ImageGalleryScreen(
    repository: ChatRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val images by repository.observeAllGeneratedImages().collectAsState(initial = emptyList())
    val gridState = rememberLazyGridState()
    var visibleCount by remember(images.size) {
        mutableIntStateOf(InitialGalleryImageCount.coerceAtMost(images.size))
    }
    var previewIndex by remember(images) { mutableStateOf<Int?>(null) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var isBatchSaving by remember { mutableStateOf(false) }
    val selectedImageIds = remember { mutableStateListOf<String>() }
    val visibleImages = remember(images, visibleCount) { images.take(visibleCount) }
    val lastVisibleIndex by remember {
        derivedStateOf { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
    }
    val hasMore = visibleCount < images.size

    LaunchedEffect(lastVisibleIndex, visibleCount, images.size) {
        if (hasMore && lastVisibleIndex >= visibleImages.size - 4) {
            visibleCount = (visibleCount + GalleryImagePageSize).coerceAtMost(images.size)
        }
    }

    LaunchedEffect(images) {
        val imageIds = images.map { it.id }.toSet()
        selectedImageIds.removeAll { it !in imageIds }
        if (images.isEmpty()) {
            isSelectionMode = false
        }
    }

    fun toggleSelection(image: GeneratedImage) {
        if (image.id in selectedImageIds) {
            selectedImageIds.remove(image.id)
        } else {
            selectedImageIds += image.id
        }
    }

    fun finishSelection() {
        isSelectionMode = false
        selectedImageIds.clear()
    }

    previewIndex?.let { index ->
        ImagePreviewDialog(
            images = images,
            currentIndex = index.coerceIn(0, (images.size - 1).coerceAtLeast(0)),
            onDismiss = { previewIndex = null },
            onPrevious = {
                previewIndex = if (images.isEmpty()) null else (index - 1 + images.size) % images.size
            },
            onNext = {
                previewIndex = if (images.isEmpty()) null else (index + 1) % images.size
            },
            snackbarHostState = snackbarHostState,
            showSaveAction = true,
            onSave = { image ->
                scope.launch {
                    runCatching { context.saveGeneratedImageToDownloads(image) }
                        .onSuccess { snackbarHostState.showSnackbar("已保存到下载目录 ModelTap 文件夹") }
                        .onFailure { snackbarHostState.showSnackbar("保存失败") }
                }
            }
        )
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
                .padding(start = 16.dp, end = 16.dp, top = 34.dp)
        ) {
            GalleryTopBar(
                onBack = onBack,
                total = images.size,
                isSelectionMode = isSelectionMode,
                selectedCount = selectedImageIds.size,
                isSaving = isBatchSaving,
                onStartSelection = {
                    if (images.isNotEmpty()) {
                        isSelectionMode = true
                    }
                },
                onCancelSelection = { finishSelection() },
                onSaveSelected = {
                    val selectedImages = images.filter { it.id in selectedImageIds }
                    if (selectedImages.isEmpty()) {
                        scope.launch { snackbarHostState.showSnackbar("请先选择图片") }
                        return@GalleryTopBar
                    }
                    scope.launch {
                        isBatchSaving = true
                        val failedCount = selectedImages.count { image ->
                            runCatching { context.saveGeneratedImageToDownloads(image) }.isFailure
                        }
                        isBatchSaving = false
                        if (failedCount == 0) {
                            snackbarHostState.showSnackbar("已保存 ${selectedImages.size} 张图片到下载目录 ModelTap 文件夹")
                        } else {
                            snackbarHostState.showSnackbar("保存完成，$failedCount 张失败")
                        }
                        finishSelection()
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (images.isEmpty()) {
                EmptyRecentImages()
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibleImages.size, key = { index -> visibleImages[index].id }) { index ->
                        val image = visibleImages[index]
                        GalleryImageCard(
                            image = image,
                            selectionMode = isSelectionMode,
                            selected = image.id in selectedImageIds,
                            onOpen = {
                                if (isSelectionMode) {
                                    toggleSelection(image)
                                } else {
                                    previewIndex = images.indexOfFirst { it.id == image.id }.takeIf { it >= 0 }
                                }
                            }
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }, key = "gallery-footer") {
                        GalleryFooter(hasMore = hasMore, visibleCount = visibleCount, total = images.size)
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun GalleryTopBar(
    onBack: () -> Unit,
    total: Int,
    isSelectionMode: Boolean,
    selectedCount: Int,
    isSaving: Boolean,
    onStartSelection: () -> Unit,
    onCancelSelection: () -> Unit,
    onSaveSelected: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp),
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
            text = if (isSelectionMode) "已选 $selectedCount 张" else "图片展示",
            color = ImageText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (isSelectionMode) {
            Row(
                modifier = Modifier.widthIn(max = 126.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "取消",
                    color = ImageMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = !isSaving, onClick = onCancelSelection)
                        .padding(horizontal = 8.dp, vertical = 7.dp)
                )
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (selectedCount > 0) ImageBlue else ImageBorder)
                        .clickable(enabled = selectedCount > 0 && !isSaving, onClick = onSaveSelected)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Text("保存", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            Text(
                text = if (total > 0) "批量保存" else "${total}张",
                color = if (total > 0) ImageBlue else ImageMuted,
                fontSize = 13.sp,
                fontWeight = if (total > 0) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 82.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = total > 0, onClick = onStartSelection)
                    .padding(vertical = 7.dp),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun GalleryImageCard(
    image: GeneratedImage,
    selectionMode: Boolean,
    selected: Boolean,
    onOpen: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFEAF2FB))
            .border(
                2.dp,
                if (selected) ImageBlue else Color.Transparent,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onOpen)
    ) {
        SubcomposeAsyncImage(
            model = image.uri,
            contentDescription = image.prompt.ifBlank { "生成图片" },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = { ImageThumbnailPlaceholder() },
            error = { ImageThumbnailPlaceholder() }
        )
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(24.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (selected) ImageBlue else Color.White.copy(alpha = 0.88f))
                    .border(1.dp, if (selected) ImageBlue else ImageBorder, RoundedCornerShape(99.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Text("✓", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun GalleryFooter(hasMore: Boolean, visibleCount: Int, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(1.dp)
                .background(ImageBorder)
        )
        Text(
            text = if (hasMore) "已显示 $visibleCount / $total" else "没有更多了",
            color = ImageMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(1.dp)
                .background(ImageBorder)
        )
    }
}
