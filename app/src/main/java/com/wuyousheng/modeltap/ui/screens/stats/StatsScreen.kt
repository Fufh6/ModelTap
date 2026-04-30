package com.wuyousheng.modeltap.ui.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuyousheng.modeltap.data.repository.ChatRepository
import com.wuyousheng.modeltap.domain.model.DailyUsageStat
import com.wuyousheng.modeltap.domain.model.ModelUsageStat
import com.wuyousheng.modeltap.domain.model.UsageStats
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun StatsScreen(
    repository: ChatRepository,
    onBack: () -> Unit
) {
    var selectedRange by remember { mutableStateOf(defaultStatsRange()) }
    var showCustomRangePicker by remember { mutableStateOf(false) }
    val statsFlow = remember(selectedRange) {
        repository.observeUsageStats(
            startTime = selectedRange.startTime,
            endTime = selectedRange.endTime
        )
    }
    val stats by statsFlow.collectAsState(initial = UsageStats())
    val totalCost = estimateCost(stats.totalTokens)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StatsBackground)
            .padding(horizontal = 24.dp)
    ) {
        StatsTopBar(
            rangeLabel = selectedRange.label,
            onBack = onBack,
            onRangeSelected = { selectedRange = it },
            onCustomRange = {
                showCustomRangePicker = true
            }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryCard(
                    title = "总消耗",
                    value = formatToken(stats.totalTokens),
                    sub1 = "${stats.totalMessages} 条消息",
                    sub2 = "${stats.totalSessions} 个会话",
                    icon = SummaryIcon.Stack,
                    accent = StatsBlue,
                    soft = Color(0xFFEAF2FF)
                )
                SummaryCard(
                    title = "图片生成数量",
                    value = stats.imageMessages.toString(),
                    sub1 = "本地记录",
                    sub2 = if (stats.imageMessages > 0) "含历史图片消息" else "暂无图片消耗",
                    icon = SummaryIcon.Image,
                    accent = StatsPurple,
                    soft = Color(0xFFF1EAFE)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryCard(
                    title = "令牌使用量",
                    value = formatToken(stats.totalTokens),
                    sub1 = "输入 ${formatToken(stats.totalPromptTokens)}",
                    sub2 = "输出 ${formatToken(stats.totalCompletionTokens)}",
                    icon = SummaryIcon.Token,
                    accent = StatsTeal,
                    soft = Color(0xFFE6FAF5)
                )
                SummaryCard(
                    title = "费用估算",
                    value = "¥ ${formatMoney(totalCost)}",
                    sub1 = "按真实令牌估算",
                    sub2 = "非平台账单",
                    icon = SummaryIcon.Cost,
                    accent = StatsAmber,
                    soft = Color(0xFFFFF3DB)
                )
            }

            ChartCard(stats.dailyUsage, selectedRange.label)

            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                ChannelCard(stats = stats, modifier = Modifier.fillMaxWidth())
                ModelRankingCard(stats = stats, modifier = Modifier.fillMaxWidth())
            }

            NoticeBar()
            Spacer(modifier = Modifier.height(18.dp))
        }
    }

    if (showCustomRangePicker) {
        StatsDateRangeDialog(
            current = selectedRange,
            onDismiss = { showCustomRangePicker = false },
            onSelected = {
                selectedRange = it
                showCustomRangePicker = false
            }
        )
    }
}

@Composable
private fun StatsTopBar(
    rangeLabel: String,
    onBack: () -> Unit,
    onRangeSelected: (StatsDateRange) -> Unit,
    onCustomRange: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            BackIcon(Modifier.size(24.dp), StatsText)
        }
        Text(
            "消耗统计",
            color = StatsText,
            fontSize = 25.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Box {
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.dp, StatsBorder, RoundedCornerShape(12.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(rangeLabel, color = StatsMuted, fontSize = 15.sp, maxLines = 1)
                Spacer(modifier = Modifier.width(14.dp))
                CalendarIcon(Modifier.size(18.dp), StatsMuted)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                quickStatsRanges().forEach { range ->
                    DropdownMenuItem(
                        text = { Text(range.label, color = StatsText, fontSize = 14.sp) },
                        onClick = {
                            expanded = false
                            onRangeSelected(range)
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("自定义日期", color = StatsText, fontSize = 14.sp) },
                    onClick = {
                        expanded = false
                        onCustomRange()
                    }
                )
            }
        }
    }
}

@Composable
private fun RowScope.SummaryCard(
    title: String,
    value: String,
    sub1: String,
    sub2: String,
    icon: SummaryIcon,
    accent: Color,
    soft: Color
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 176.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, StatsBorder, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(soft),
                contentAlignment = Alignment.Center
            ) {
                SummaryGlyph(icon = icon, color = accent, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(title, color = StatsMuted, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.height(11.dp))
        Text(value, color = StatsText, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(modifier = Modifier.height(7.dp))
        Text(sub1, color = StatsMuted, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(2.dp))
        Text(sub2, color = StatsMuted, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ChartCard(dailyUsage: List<DailyUsageStat>, rangeLabel: String) {
    var selectedIndex by remember(dailyUsage) { mutableStateOf<Int?>(null) }
    val fallback = listOf(
        DailyUsageStat("前6天", 0, 0),
        DailyUsageStat("前5天", 0, 0),
        DailyUsageStat("前4天", 0, 0),
        DailyUsageStat("前3天", 0, 0),
        DailyUsageStat("前2天", 0, 0),
        DailyUsageStat("昨天", 0, 0),
        DailyUsageStat("今天", 0, 0)
    )
    val rows = dailyUsage.ifEmpty { fallback }
    val tokenValues = rows.map { it.totalTokens.toFloat() }
    val costValues = rows.map { estimateCost(it.totalTokens).coerceAtLeast(0f) }
    val labels = rows.map { it.label }
    val selectedRow = selectedIndex?.let { rows.getOrNull(it) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(326.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, StatsBorder, RoundedCornerShape(18.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("消耗趋势", color = StatsText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.width(8.dp))
            InfoIcon(Modifier.size(16.dp), StatsMuted)
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .border(1.dp, StatsBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(rangeLabel, color = StatsMuted, fontSize = 13.sp, maxLines = 1)
                Spacer(modifier = Modifier.width(10.dp))
                DownIcon(Modifier.size(12.dp), StatsMuted)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            LegendPill(color = StatsBlue, text = "令牌")
            Spacer(modifier = Modifier.width(10.dp))
            LegendPill(color = StatsTeal, text = "费用估算")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = selectedRow?.let { "${it.label}  令牌 ${formatToken(it.totalTokens)}  费用 ¥ ${formatMoney(estimateCost(it.totalTokens))}" }
                ?: "点按或滑动图表查看单日明细",
            color = if (selectedRow == null) StatsMuted else StatsText,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        LineChart(
            labels = labels,
            seriesA = costValues,
            seriesB = tokenValues,
            onSelected = { selectedIndex = it },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

@Composable
private fun ChannelCard(stats: UsageStats, modifier: Modifier = Modifier) {
    val values = channelSlices(stats).ifEmpty {
        listOf(ChannelSlice("暂无数据", 1f, StatsBorder))
    }
    val sum = values.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(1f)

    Column(
        modifier = modifier
            .height(258.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, StatsBorder, RoundedCornerShape(18.dp))
            .padding(18.dp)
    ) {
        SectionTitle("渠道消耗占比")
        Spacer(modifier = Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            DonutChart(values = values, modifier = Modifier.size(108.dp))
            Spacer(modifier = Modifier.width(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(11.dp), modifier = Modifier.weight(1f)) {
                values.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(99.dp)).background(item.color))
                        Spacer(modifier = Modifier.width(9.dp))
                        Text(item.label, color = StatsMuted, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${((item.value / sum) * 100).roundToInt()}%", color = StatsMuted, fontSize = 12.sp, modifier = Modifier.width(34.dp), textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelRankingCard(stats: UsageStats, modifier: Modifier = Modifier) {
    var menuExpanded by remember { mutableStateOf(false) }
    val allRows = stats.modelUsage
        .filter { it.totalTokens > 0 }
        .sortedWith(compareByDescending<ModelUsageStat> { it.totalTokens }.thenByDescending { it.sessionCount })
    val rows = allRows
        .take(5)
    val maxTokens = rows.maxOfOrNull { it.totalTokens }?.coerceAtLeast(1) ?: 1
    val totalTokens = rows.sumOf { it.totalTokens }.coerceAtLeast(1)

    Column(
        modifier = modifier
            .height(258.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, StatsBorder, RoundedCornerShape(18.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("模型消耗排行")
            Spacer(modifier = Modifier.weight(1f))
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { menuExpanded = true }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("查看更多", color = StatsMuted, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    ChevronRight(Modifier.size(13.dp), StatsMuted)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (allRows.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("暂无模型消耗", color = StatsMuted, fontSize = 13.sp) },
                            onClick = { menuExpanded = false }
                        )
                    } else {
                        allRows.forEach { row ->
                            DropdownMenuItem(
                                text = {
                                    Column(modifier = Modifier.widthIn(min = 190.dp)) {
                                        Text(row.modelId.ifBlank { "未选择模型" }, color = StatsText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("令牌 ${formatToken(row.totalTokens)} · ${row.sessionCount} 个会话", color = StatsMuted, fontSize = 11.sp)
                                    }
                                },
                                onClick = { menuExpanded = false }
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (rows.isEmpty()) {
            EmptyMiniState("暂无模型消耗")
        } else {
            rows.forEachIndexed { index, row ->
                RankingRow(
                    index = index + 1,
                    name = row.modelId.ifBlank { "未选择模型" },
                    percent = row.totalTokens / totalTokens.toFloat(),
                    progress = row.totalTokens / maxTokens.toFloat()
                )
                if (index != rows.lastIndex) Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun EmptyMiniState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = StatsMuted, fontSize = 13.sp)
    }
}

@Composable
private fun NoticeBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, StatsBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShieldIcon(Modifier.size(22.dp), StatsBlue)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            "提示：数据为本地估算值，可能与实际账单存在差异，仅供参考。",
            color = StatsMuted,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Row(
            modifier = Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(StatsBackground)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("更新 5 分钟前", color = StatsMuted, fontSize = 11.sp, maxLines = 1)
            Spacer(modifier = Modifier.width(6.dp))
            RefreshIcon(Modifier.size(13.dp), StatsMuted)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = StatsText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.width(8.dp))
        InfoIcon(Modifier.size(15.dp), StatsMuted)
    }
}

@Composable
private fun LegendPill(color: Color, text: String) {
    Row(
        modifier = Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(StatsBackground)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(7.dp).clip(RoundedCornerShape(99.dp)).background(color))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, color = StatsMuted, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun LineChart(
    labels: List<String>,
    seriesA: List<Float>,
    seriesB: List<Float>,
    onSelected: (Int) -> Unit,
    modifier: Modifier
) {
    Canvas(
        modifier = modifier.pointerInput(labels.size) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val position = event.changes.firstOrNull { it.pressed }?.position
                    if (position != null && labels.isNotEmpty() && size.width > 0) {
                        val index = ((position.x / size.width) * labels.size)
                            .toInt()
                            .coerceIn(0, labels.lastIndex)
                        onSelected(index)
                    }
                }
            }
            Unit
        }
    ) {
        val left = 54.dp.toPx()
        val right = 12.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = 32.dp.toPx()
        val chartWidth = size.width - left - right
        val chartHeight = size.height - top - bottom
        val tokenMax = (seriesB.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val costMax = (seriesA.maxOrNull() ?: 1f).coerceAtLeast(1f)

        repeat(6) { index ->
            val y = top + chartHeight * index / 5f
            drawLine(StatsGrid, Offset(left, y), Offset(size.width - right, y), strokeWidth = 1.dp.toPx())
        }

        val count = labels.size.coerceAtLeast(1)
        val slot = chartWidth / count
        val barWidth = min(slot * 0.54f, 16.dp.toPx()).coerceAtLeast(3.dp.toPx())
        seriesB.forEachIndexed { index, value ->
            val barHeight = chartHeight * (value / tokenMax).coerceIn(0f, 1f)
            val minVisibleHeight = if (value > 0f) 2.dp.toPx() else 0f
            val x = left + slot * index + (slot - barWidth) / 2f
            drawRoundRect(
                color = StatsBlue.copy(alpha = 0.86f),
                topLeft = Offset(x, top + chartHeight - barHeight.coerceAtLeast(minVisibleHeight)),
                size = Size(barWidth, barHeight.coerceAtLeast(minVisibleHeight)),
                cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
            )
        }

        fun costPoint(index: Int): Offset {
            val x = left + slot * index + slot / 2f
            val y = top + chartHeight * (1f - seriesA[index] / costMax)
            return Offset(x, y)
        }

        fun drawCostSeries(color: Color) {
            val path = Path()
            seriesA.forEachIndexed { index, _ ->
                val p = costPoint(index)
                if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(path, color, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
            seriesA.indices.forEach { index ->
                val p = costPoint(index)
                drawCircle(Color.White, radius = 4.dp.toPx(), center = p)
                drawCircle(color, radius = 4.dp.toPx(), center = p, style = Stroke(1.8.dp.toPx()))
            }
        }

        drawCostSeries(StatsTeal)

        val labelPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(116, 130, 154)
            textSize = 12.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        val labelStride = when {
            labels.size > 24 -> 5
            labels.size > 14 -> 3
            labels.size > 8 -> 2
            else -> 1
        }
        labels.forEachIndexed { index, label ->
            if (index % labelStride == 0 || index == labels.lastIndex) {
                val x = left + slot * index + slot / 2f
                drawContext.canvas.nativeCanvas.drawText(label, x, size.height - 7.dp.toPx(), labelPaint)
            }
        }
        val axisPaint = android.graphics.Paint(labelPaint).apply { textAlign = android.graphics.Paint.Align.RIGHT }
        val axisValues = (5 downTo 0).map { step ->
            formatAxisToken((tokenMax * step / 5f).roundToInt())
        }
        axisValues.forEachIndexed { index, text ->
            val y = top + chartHeight * index / 5f + 4.dp.toPx()
            drawContext.canvas.nativeCanvas.drawText(text, left - 8.dp.toPx(), y, axisPaint)
        }
    }
}

@Composable
private fun DonutChart(values: List<ChannelSlice>, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val total = values.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(1f)
        var start = -90f
        val stroke = Stroke(width = 30.dp.toPx(), cap = StrokeCap.Butt)
        values.forEach { item ->
            val sweep = item.value / total * 360f
            drawArc(item.color, startAngle = start, sweepAngle = sweep, useCenter = false, style = stroke, size = Size(size.width, size.height))
            start += sweep
        }
        drawCircle(Color.White, radius = size.minDimension * 0.28f, center = Offset(size.width / 2f, size.height / 2f))
    }
}

@Composable
private fun RankingRow(index: Int, name: String, percent: Float, progress: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(rankColor(index)),
            contentAlignment = Alignment.Center
        ) {
            Text(index.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(10.dp))
        ModelMiniIcon(name)
        Spacer(modifier = Modifier.width(8.dp))
        Text(name, color = StatsText, fontSize = 13.sp, modifier = Modifier.width(90.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(StatsBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0.05f, 1f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(StatsBlue)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text("${(percent * 100).roundToInt()}%", color = StatsMuted, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.width(42.dp))
    }
}

@Composable
private fun ModelMiniIcon(name: String) {
    val color = when {
        name.contains("Claude", true) -> Color(0xFFE9B77A)
        name.contains("Gemini", true) -> Color(0xFF4A91FF)
        name.contains("Flux", true) -> Color(0xFF6F70FF)
        name.contains("GPT", true) || name.contains("OpenAI", true) -> StatsTeal
        else -> Color(0xFFDDE7F4)
    }
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            when {
                name.contains("Claude", true) -> "AI"
                name.contains("Gemini", true) -> "✦"
                name.contains("Flux", true) -> "△"
                name.contains("GPT", true) -> "◎"
                else -> "□"
            },
            color = color,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SummaryGlyph(icon: SummaryIcon, color: Color, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(size.minDimension * 0.1f, cap = StrokeCap.Round)
        when (icon) {
            SummaryIcon.Stack -> {
                repeat(3) { index ->
                    val y = size.height * (0.28f + index * 0.18f)
                    drawLine(color, Offset(size.width * 0.2f, y), Offset(size.width * 0.5f, y + size.height * 0.12f), stroke.width, StrokeCap.Round)
                    drawLine(color, Offset(size.width * 0.5f, y + size.height * 0.12f), Offset(size.width * 0.8f, y), stroke.width, StrokeCap.Round)
                    drawLine(color, Offset(size.width * 0.2f, y), Offset(size.width * 0.5f, y - size.height * 0.12f), stroke.width, StrokeCap.Round)
                    drawLine(color, Offset(size.width * 0.5f, y - size.height * 0.12f), Offset(size.width * 0.8f, y), stroke.width, StrokeCap.Round)
                }
            }
            SummaryIcon.Token -> {
                drawOval(color, topLeft = Offset(size.width * 0.18f, size.height * 0.18f), size = Size(size.width * 0.64f, size.height * 0.28f), style = stroke)
                drawOval(color, topLeft = Offset(size.width * 0.18f, size.height * 0.54f), size = Size(size.width * 0.64f, size.height * 0.28f), style = stroke)
                drawLine(color, Offset(size.width * 0.2f, size.height * 0.32f), Offset(size.width * 0.2f, size.height * 0.68f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.8f, size.height * 0.32f), Offset(size.width * 0.8f, size.height * 0.68f), stroke.width, StrokeCap.Round)
            }
            SummaryIcon.Image -> {
                drawRoundRect(color, Offset(size.width * 0.14f, size.height * 0.16f), Size(size.width * 0.72f, size.height * 0.68f), style = stroke)
                drawCircle(color, radius = size.minDimension * 0.07f, center = Offset(size.width * 0.36f, size.height * 0.36f))
                val path = Path().apply {
                    moveTo(size.width * 0.22f, size.height * 0.72f)
                    lineTo(size.width * 0.44f, size.height * 0.52f)
                    lineTo(size.width * 0.58f, size.height * 0.66f)
                    lineTo(size.width * 0.72f, size.height * 0.48f)
                }
                drawPath(path, color, style = stroke)
            }
            SummaryIcon.Cost -> {
                drawCircle(color, radius = size.minDimension * 0.36f, center = Offset(size.width / 2f, size.height / 2f), style = stroke)
                drawLine(color, Offset(size.width * 0.36f, size.height * 0.38f), Offset(size.width * 0.5f, size.height * 0.52f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.64f, size.height * 0.38f), Offset(size.width * 0.5f, size.height * 0.52f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.5f, size.height * 0.52f), Offset(size.width * 0.5f, size.height * 0.72f), stroke.width, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun BackIcon(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.12f
        drawLine(color, Offset(size.width * 0.68f, size.height * 0.16f), Offset(size.width * 0.32f, size.height * 0.5f), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.32f, size.height * 0.5f), Offset(size.width * 0.68f, size.height * 0.84f), strokeWidth, StrokeCap.Round)
    }
}

@Composable
private fun CalendarIcon(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(size.minDimension * 0.1f, cap = StrokeCap.Round)
        drawRoundRect(color, Offset(size.width * 0.15f, size.height * 0.2f), Size(size.width * 0.7f, size.height * 0.65f), style = stroke)
        drawLine(color, Offset(size.width * 0.15f, size.height * 0.4f), Offset(size.width * 0.85f, size.height * 0.4f), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.32f, size.height * 0.1f), Offset(size.width * 0.32f, size.height * 0.28f), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.68f, size.height * 0.1f), Offset(size.width * 0.68f, size.height * 0.28f), stroke.width, StrokeCap.Round)
    }
}

@Composable
private fun InfoIcon(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(size.minDimension * 0.1f, cap = StrokeCap.Round)
        drawCircle(color, size.minDimension * 0.4f, Offset(size.width / 2f, size.height / 2f), style = stroke)
        drawLine(color, Offset(size.width * 0.5f, size.height * 0.45f), Offset(size.width * 0.5f, size.height * 0.68f), stroke.width, StrokeCap.Round)
        drawCircle(color, size.minDimension * 0.035f, Offset(size.width * 0.5f, size.height * 0.3f))
    }
}

@Composable
private fun DownIcon(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(size.minDimension * 0.14f, cap = StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.25f, size.height * 0.38f), Offset(size.width * 0.5f, size.height * 0.62f), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.5f, size.height * 0.62f), Offset(size.width * 0.75f, size.height * 0.38f), stroke.width, StrokeCap.Round)
    }
}

@Composable
private fun ChevronRight(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(size.minDimension * 0.12f, cap = StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.35f, size.height * 0.2f), Offset(size.width * 0.65f, size.height * 0.5f), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.65f, size.height * 0.5f), Offset(size.width * 0.35f, size.height * 0.8f), stroke.width, StrokeCap.Round)
    }
}

@Composable
private fun ShieldIcon(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.5f, size.height * 0.08f)
            lineTo(size.width * 0.82f, size.height * 0.2f)
            lineTo(size.width * 0.76f, size.height * 0.62f)
            quadraticBezierTo(size.width * 0.68f, size.height * 0.82f, size.width * 0.5f, size.height * 0.92f)
            quadraticBezierTo(size.width * 0.32f, size.height * 0.82f, size.width * 0.24f, size.height * 0.62f)
            lineTo(size.width * 0.18f, size.height * 0.2f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
private fun RefreshIcon(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(size.minDimension * 0.1f, cap = StrokeCap.Round)
        drawArc(color, startAngle = 40f, sweepAngle = 280f, useCenter = false, topLeft = Offset(size.width * 0.14f, size.height * 0.14f), size = Size(size.width * 0.72f, size.height * 0.72f), style = stroke)
        drawLine(color, Offset(size.width * 0.76f, size.height * 0.18f), Offset(size.width * 0.86f, size.height * 0.38f), stroke.width, StrokeCap.Round)
    }
}

private fun formatToken(value: Int): String {
    return when {
        value >= 1_000_000 -> "%.2fM".format(value / 1_000_000f)
        value >= 10_000 -> "%.2f万".format(value / 10_000f)
        value >= 1_000 -> "%.1fK".format(value / 1_000f)
        else -> value.toString()
    }
}

private fun formatAxisToken(value: Int): String {
    return when {
        value >= 1_000_000 -> "%.1fM".format(value / 1_000_000f)
        value >= 10_000 -> "%.1f万".format(value / 10_000f)
        value >= 1_000 -> "%.0fK".format(value / 1_000f)
        else -> value.toString()
    }
}

private fun estimateCost(tokens: Int): Float = tokens / 1_000_000f * 11.5f

private fun formatMoney(value: Float): String = "%.2f".format(value)

private fun defaultStatsRange(): StatsDateRange {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    return statsDateRange("近 7 天", today.minusDays(6), today.plusDays(1), zone)
}

private fun quickStatsRanges(): List<StatsDateRange> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    return listOf(
        statsDateRange("近 7 天", today.minusDays(6), today.plusDays(1), zone),
        statsDateRange("近 30 天", today.minusDays(29), today.plusDays(1), zone),
        statsDateRange("本月", today.withDayOfMonth(1), today.plusDays(1), zone),
        StatsDateRange("全部", null, null)
    )
}

private fun statsDateRange(
    label: String,
    startDate: LocalDate,
    endDateExclusive: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): StatsDateRange {
    return StatsDateRange(
        label = label,
        startTime = startDate.atStartOfDay(zone).toInstant().toEpochMilli(),
        endTime = endDateExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsDateRangeDialog(
    current: StatsDateRange,
    onDismiss: () -> Unit,
    onSelected: (StatsDateRange) -> Unit
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val defaultStartMillis = current.startTime
    val defaultEndMillis = current.endTime
        ?.let { it - 1 }
        ?: today.atStartOfDay(zone).toInstant().toEpochMilli()
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = defaultStartMillis,
        initialSelectedEndDateMillis = defaultEndMillis
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val startMillis = pickerState.selectedStartDateMillis ?: return@TextButton
                    val endMillis = pickerState.selectedEndDateMillis ?: startMillis
                    val startDate = java.time.Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
                    val endDate = java.time.Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()
                    val normalizedStart = if (startDate.isAfter(endDate)) endDate else startDate
                    val normalizedEnd = if (endDate.isBefore(startDate)) startDate else endDate
                    val formatter = DateTimeFormatter.ofPattern("MM/dd")
                    val label = "${normalizedStart.format(formatter)}-${normalizedEnd.format(formatter)}"
                    onSelected(statsDateRange(label, normalizedStart, normalizedEnd.plusDays(1), zone))
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    ) {
        DateRangePicker(
            state = pickerState,
            title = {
                Text(
                    text = "选择统计区间",
                    color = StatsText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp)
                )
            },
            headline = {
                Text(
                    text = "开始日期 - 结束日期",
                    color = StatsMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            },
            modifier = Modifier.height(520.dp)
        )
    }
}

private fun legacyCustomDateRangeDefaults(current: StatsDateRange): Pair<LocalDate, LocalDate> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val defaultStart = current.startTime
        ?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        ?: today.minusDays(6)
    val defaultEnd = current.endTime
        ?.let { java.time.Instant.ofEpochMilli((it - 1).coerceAtLeast(0)).atZone(zone).toLocalDate() }
        ?: today
    return defaultStart to defaultEnd
}

private fun rankColor(index: Int): Color = when (index) {
    1 -> Color(0xFFFFD56A)
    2 -> Color(0xFFE8A66E)
    3 -> Color(0xFFB9CDE8)
    else -> Color(0xFFD6E1EF)
}

private fun channelSlices(stats: UsageStats): List<ChannelSlice> {
    return stats.modelUsage
        .filter { it.totalTokens > 0 }
        .groupBy { channelName(it.modelId) }
        .map { (label, rows) ->
            ChannelSlice(
                label = label,
                value = rows.sumOf { it.totalTokens }.toFloat(),
                color = channelColor(label)
            )
        }
        .sortedByDescending { it.value }
        .take(4)
}

private fun channelName(modelId: String): String {
    val model = modelId.lowercase()
    return when {
        "claude" in model || "anthropic" in model -> "Anthropic"
        "gemini" in model || "google" in model -> "Google"
        "qwen" in model || "deepseek" in model || "silicon" in model -> "第三方 API"
        "flux" in model || "dall" in model || "image" in model -> "图片模型"
        "gpt" in model || "openai" in model || "o1" in model || "o3" in model || "o4" in model -> "OpenAI"
        else -> "自定义模型"
    }
}

private fun channelColor(label: String): Color {
    return when (label) {
        "OpenAI" -> StatsTeal
        "Anthropic" -> Color(0xFFE8A66E)
        "Google" -> StatsBlue
        "第三方 API" -> StatsPurple
        "图片模型" -> StatsAmber
        else -> Color(0xFFB9CDE8)
    }
}

private data class StatsDateRange(
    val label: String,
    val startTime: Long?,
    val endTime: Long?
)

private data class ChannelSlice(
    val label: String,
    val value: Float,
    val color: Color
)

private enum class SummaryIcon {
    Stack,
    Token,
    Image,
    Cost
}

private val StatsBackground = Color(0xFFF7FAFE)
private val StatsText = Color(0xFF162135)
private val StatsMuted = Color(0xFF74829A)
private val StatsBorder = Color(0xFFE0E8F4)
private val StatsGrid = Color(0xFFE7EDF6)
private val StatsBlue = Color(0xFF2F83FF)
private val StatsTeal = Color(0xFF2ECDB4)
private val StatsPurple = Color(0xFF9D70E8)
private val StatsAmber = Color(0xFFFFC866)
private val StatsRed = Color(0xFFFF5A66)
