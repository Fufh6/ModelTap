package com.wuyousheng.modeltap.ui.screens.me

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuyousheng.modeltap.BuildConfig
import com.wuyousheng.modeltap.R
import com.wuyousheng.modeltap.data.repository.ChatRepository
import com.wuyousheng.modeltap.domain.model.UsageStats
import com.wuyousheng.modeltap.ui.components.AppIcon

@Composable
fun MeScreen(
    repository: ChatRepository,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenOfficialWebsite: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sessions by repository.observeSessions().collectAsState(initial = emptyList())
    val favoriteCount by repository.observeFavoriteSessionCount().collectAsState(initial = 0)
    val images by repository.observeRecentGeneratedImages(limit = 1).collectAsState(initial = emptyList())
    val stats by repository.observeUsageStats().collectAsState(initial = UsageStats())
    var showAbout by remember { mutableStateOf(false) }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF8FBFF), Color(0xFFF1F7FE), Color(0xFFFAFCFF))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MeTopBar(onBack = onBack)
            ProfileCard(
                totalSessions = sessions.size,
                favoriteCount = favoriteCount,
                totalMessages = stats.totalMessages
            )
            MenuCard {
                MeMenuRow(
                    title = "历史记录",
                    subtitle = "${sessions.size} 个会话",
                    iconRes = R.drawable.ic_history_24,
                    iconColor = MePrimary,
                    onClick = onOpenHistory
                )
                MeDivider()
                MeMenuRow(
                    title = "我的收藏",
                    subtitle = "$favoriteCount 个收藏会话",
                    iconRes = R.drawable.ic_diamond_24,
                    iconColor = Color(0xFFFFA629),
                    onClick = onOpenFavorites
                )
                MeDivider()
                MeMenuRow(
                    title = "用量统计",
                    subtitle = "${stats.totalTokens} tokens · ${stats.totalMessages} 条消息",
                    iconRes = R.drawable.ic_public_24,
                    iconColor = Color(0xFF28BFA7),
                    onClick = onOpenStats
                )
                MeDivider()
                MeMenuRow(
                    title = "图片展示",
                    subtitle = if (images.isEmpty()) "暂无生成图片" else "查看已生成图片",
                    iconRes = R.drawable.ic_image_24,
                    iconColor = Color(0xFF7A8CFF),
                    onClick = onOpenGallery
                )
            }
            MenuCard {
                MeMenuRow(
                    title = "模型设置",
                    subtitle = "默认模型、参数与偏好",
                    iconRes = R.drawable.ic_settings_24,
                    iconColor = MeMuted,
                    onClick = onOpenSettings
                )
            }
            MenuCard {
                MeMenuRow(
                    title = "问题反馈",
                    subtitle = "把问题、建议或截图说明发给我",
                    iconRes = R.drawable.ic_edit_24,
                    iconColor = Color(0xFFFF7A59),
                    onClick = { openFeedbackEmail(context) }
                )
                MeDivider()
                MeMenuRow(
                    title = "联系我",
                    subtitle = SupportEmail,
                    iconRes = R.drawable.ic_attach_file_24,
                    iconColor = Color(0xFF4967FF),
                    onClick = { openContactEmail(context) }
                )
                MeDivider()
                MeMenuRow(
                    title = "官方网站",
                    subtitle = OfficialWebsiteUrl,
                    iconRes = R.drawable.ic_public_24,
                    iconColor = Color(0xFF28BFA7),
                    onClick = onOpenOfficialWebsite
                )
                MeDivider()
                MeMenuRow(
                    title = "关于",
                    subtitle = "版本 ${BuildConfig.VERSION_NAME}",
                    iconRes = R.drawable.ic_diamond_24,
                    iconColor = MePrimary,
                    onClick = { showAbout = true }
                )
            }
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关于 ModelTap") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("版本：${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Text("官网：$OfficialWebsiteUrl")
                Text("联系：$SupportEmail")
                Text("会话、收藏、图片和统计数据默认保存在本机。")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了")
            }
        }
    )
}

@Composable
private fun MeTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(R.drawable.ic_arrow_back_24, MeText, Modifier.size(24.dp))
        }
        Text(
            text = "我的",
            color = MeText,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ProfileCard(
    totalSessions: Int,
    favoriteCount: Int,
    totalMessages: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .border(1.dp, MeBorder, RoundedCornerShape(24.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MeSoftBlue),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.modeltap_icon),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(42.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("ModelTap", color = MeText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("本地会话与创作资产", color = MeMuted, fontSize = 13.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatPill("会话", totalSessions.toString(), Modifier.weight(1f))
            StatPill("收藏", favoriteCount.toString(), Modifier.weight(1f))
            StatPill("消息", totalMessages.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MeField)
            .border(1.dp, MeBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(value, color = MeText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, color = MeMuted, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun MenuCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .border(1.dp, MeBorder, RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        content = content
    )
}

@Composable
private fun MeMenuRow(
    title: String,
    subtitle: String,
    iconRes: Int,
    iconColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(iconColor.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(iconRes, iconColor, Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MeText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                color = MeMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        AppIcon(R.drawable.ic_chevron_right_24, MeMuted, Modifier.size(21.dp))
    }
}

@Composable
private fun MeDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 62.dp)
            .height(1.dp)
            .background(MeBorder.copy(alpha = 0.72f))
    )
}

private val MeText = Color(0xFF162135)
private val MeMuted = Color(0xFF74829A)
private val MePrimary = Color(0xFF4B8BFF)
private val MeSoftBlue = Color(0xFFEFF6FF)
private val MeField = Color(0xFFF8FBFF)
private val MeBorder = Color(0xFFE0E9F5)

private const val SupportEmail = "support@modeltap.cn"
private const val OfficialWebsiteUrl = "https://www.modeltap.cn"

private fun openFeedbackEmail(context: Context) {
    val uri = Uri.parse(
        "mailto:$SupportEmail?subject=${Uri.encode("ModelTap 问题反馈")}&body=${Uri.encode("请描述问题、复现步骤和设备信息：\n\n")}"
    )
    openIntent(context, Intent(Intent.ACTION_SENDTO, uri), "未找到可用的邮件应用")
}

private fun openContactEmail(context: Context) {
    val uri = Uri.parse("mailto:$SupportEmail?subject=${Uri.encode("联系 ModelTap")}")
    openIntent(context, Intent(Intent.ACTION_SENDTO, uri), "未找到可用的邮件应用")
}

private fun openIntent(context: Context, intent: Intent, errorMessage: String) {
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show() }
}
