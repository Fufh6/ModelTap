package com.wuyousheng.modeltap.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuyousheng.modeltap.R
import com.wuyousheng.modeltap.data.repository.ChatRepository
import com.wuyousheng.modeltap.domain.model.ApiConfig
import com.wuyousheng.modeltap.ui.components.AppIcon
import kotlinx.coroutines.launch

@Composable
fun ModelSettingsScreen(repository: ChatRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(ApiConfig()) }
    var help by remember { mutableStateOf<Pair<String, String>?>(null) }
    var maxTokensText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        repository.observeConfig().collect {
            config = it
            maxTokensText = it.maxTokens.toOutputLengthText()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(SettingsBackgroundTop, SettingsBackgroundMid, SettingsBackgroundBottom)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsTopBar(onBack = onBack)
            SettingsCard {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SettingTitle("助手名称", "显示在本地配置中的助手名称，便于后续区分不同助手。") {
                        help = it
                    }
                    OutlinedTextField(
                        value = config.assistantName,
                        onValueChange = { config = config.copy(assistantName = it) },
                        placeholder = { Text("助手") },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    )

                    SettingTitle("提示词", "会作为系统提示词发送给模型，用来约束助手身份、语气和回答规则。") {
                        help = it
                    }
                    OutlinedTextField(
                        value = config.systemPrompt,
                        onValueChange = { config = config.copy(systemPrompt = it) },
                        minLines = 3,
                        maxLines = 8,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                    )
                }
            }

            SettingsCard {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SettingTitle("模型", "当前使用的模型标识。可在接口配置页获取模型列表后选择，也可以手动填写。") {
                        help = it
                    }
                    OutlinedTextField(
                        value = config.selectedModel,
                        onValueChange = { config = config.copy(selectedModel = it) },
                        placeholder = { Text("模型标识") },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    )

                    SliderSetting(
                        title = "创造性",
                        help = "对应创造性参数。数值越高，回答越发散；越低，回答越稳定。",
                        valueText = "%.2f".format(config.temperature),
                        value = config.temperature,
                        range = 0f..2f,
                        onValueChange = { config = config.copy(temperature = it) },
                        onHelp = {
                            help = it
                        }
                    )
                    SettingTitle("回复长度", "对应最大输出长度。填“无限”或 0 时不向接口传 max_tokens，由模型或服务端决定上限。") {
                        help = it
                    }
                    OutlinedTextField(
                        value = maxTokensText,
                        onValueChange = { value ->
                            maxTokensText = value
                            parseOutputLength(value)?.let { parsed ->
                                config = config.copy(maxTokens = parsed)
                            }
                        },
                        placeholder = { Text("无限 / 4096 / 8192") },
                        singleLine = true,
                        isError = parseOutputLength(maxTokensText) == null,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    )
                    SliderSetting(
                        title = "采样范围",
                        help = "对应采样范围。控制模型从多大概率范围内采样，通常保持 1.0 即可。",
                        valueText = "%.2f".format(config.topP),
                        value = config.topP,
                        range = 0f..1f,
                        onValueChange = { config = config.copy(topP = it) },
                        onHelp = {
                            help = it
                        }
                    )
                }
            }
            help?.let { (title, text) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SettingsSoftBlue),
                    border = BorderStroke(1.dp, SettingsBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, color = SettingsText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text,
                                color = SettingsMuted,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .border(1.dp, SettingsBorder, RoundedCornerShape(12.dp))
                                .clickable { help = null }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("关闭", color = SettingsMuted, fontSize = 13.sp)
                        }
                    }
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        repository.saveConfig(config.copy(maxTokens = parseOutputLength(maxTokensText) ?: config.maxTokens))
                        onBack()
                    }
                },
                enabled = config.selectedModel.isNotBlank() && parseOutputLength(maxTokensText) != null,
                colors = ButtonDefaults.buttonColors(containerColor = SettingsPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("保存设置")
            }
            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 34.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(R.drawable.ic_arrow_back_24, SettingsText, Modifier.size(24.dp))
        }
        Text(
            text = "模型设置",
            color = SettingsText,
            fontSize = 25.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Box(modifier = Modifier.size(46.dp))
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, SettingsBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            content = content
        )
    }
}

@Composable
private fun SettingTitle(
    title: String,
    help: String,
    onHelp: (Pair<String, String>) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = SettingsText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .size(26.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(SettingsSoftBlue)
                .clickable { onHelp(title to help) },
            contentAlignment = Alignment.Center
        ) {
            Text("?", color = SettingsPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SliderSetting(
    title: String,
    help: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onHelp: (Pair<String, String>) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = SettingsBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        SettingTitle("$title  $valueText", help, onHelp)
        Slider(
            value = value.coerceIn(range),
            onValueChange = onValueChange,
            valueRange = range
        )
    }
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

private val SettingsBackgroundTop = Color(0xFFF8FBFF)
private val SettingsBackgroundMid = Color(0xFFF2F7FE)
private val SettingsBackgroundBottom = Color(0xFFFAFCFF)
private val SettingsText = Color(0xFF1E293B)
private val SettingsMuted = Color(0xFF71809A)
private val SettingsBorder = Color(0xFFDCE7F4)
private val SettingsSoftBlue = Color(0xFFF1F6FD)
private val SettingsPrimary = Color(0xFF4B8BFF)
