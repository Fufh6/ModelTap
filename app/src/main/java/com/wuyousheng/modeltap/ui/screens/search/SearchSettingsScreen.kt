package com.wuyousheng.modeltap.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuyousheng.modeltap.R
import com.wuyousheng.modeltap.data.repository.ChatRepository
import com.wuyousheng.modeltap.data.repository.safeUserError
import com.wuyousheng.modeltap.domain.model.ApiConfig
import com.wuyousheng.modeltap.ui.components.AppIcon
import kotlinx.coroutines.launch

@Composable
fun SearchSettingsScreen(repository: ChatRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(ApiConfig()) }
    var status by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repository.observeConfig().collect { config = it }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(SearchBackgroundTop, SearchBackgroundMid, SearchBackgroundBottom)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SearchTopBar(onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White)
                    .border(1.dp, SearchBorder, RoundedCornerShape(22.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Tavily 搜索",
                            color = SearchText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (config.webSearchEnabled) "聊天时可调用联网检索" else "当前关闭",
                            color = SearchMuted,
                            fontSize = 13.sp
                        )
                    }
                    Switch(
                        checked = config.webSearchEnabled,
                        onCheckedChange = { config = config.copy(webSearchEnabled = it) }
                    )
                }
                OutlinedTextField(
                    value = config.tavilyApiKey,
                    onValueChange = { config = config.copy(tavilyApiKey = it) },
                    label = { Text("Tavily 接口密钥") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                isTesting = true
                                runCatching { repository.testTavilySearch(config.tavilyApiKey) }
                                    .onSuccess {
                                        status = "Tavily 连接成功"
                                        isError = false
                                    }
                                    .onFailure {
                                        status = safeUserError(it)
                                        isError = true
                                    }
                                isTesting = false
                            }
                        },
                        enabled = config.tavilyApiKey.isNotBlank() && !isTesting,
                        colors = ButtonDefaults.buttonColors(containerColor = SearchPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Text(if (isTesting) "测试中…" else "测试连接")
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                repository.saveConfig(config)
                                status = "联网搜索配置已保存"
                                isError = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SearchText),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Text("保存配置")
                    }
                }
                if (status.isNotBlank()) {
                    Text(
                        text = status,
                        color = if (isError) MaterialTheme.colorScheme.error else SearchText,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 34.dp, bottom = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(R.drawable.ic_arrow_back_24, SearchText, Modifier.size(24.dp))
        }
        Text(
            text = "联网搜索",
            color = SearchText,
            fontSize = 25.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Box(modifier = Modifier.size(46.dp))
    }
}

private val SearchBackgroundTop = Color(0xFFF8FBFF)
private val SearchBackgroundMid = Color(0xFFF2F7FE)
private val SearchBackgroundBottom = Color(0xFFFAFCFF)
private val SearchText = Color(0xFF1E293B)
private val SearchMuted = Color(0xFF71809A)
private val SearchBorder = Color(0xFFDCE7F4)
private val SearchPrimary = Color(0xFF4B8BFF)
