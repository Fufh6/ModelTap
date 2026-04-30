package com.wuyousheng.modeltap.ui.screens.config

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuyousheng.modeltap.R
import com.wuyousheng.modeltap.data.repository.ChatRepository
import com.wuyousheng.modeltap.data.repository.safeUserError
import com.wuyousheng.modeltap.domain.model.ApiConfig
import com.wuyousheng.modeltap.domain.model.ApiKeyEntry
import com.wuyousheng.modeltap.domain.model.ModelOption
import com.wuyousheng.modeltap.storage.isValidHttpsBaseUrl
import com.wuyousheng.modeltap.storage.normalized
import com.wuyousheng.modeltap.ui.components.AppIcon
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(repository: ChatRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val entries by repository.observeApiKeyEntries().collectAsState(initial = emptyList())
    val providers = remember { providerConfigs() }
    val configuredGroupCount = providers.count { provider ->
        entries.any { it.groupId == provider.groupId && it.apiKey.isNotBlank() }
    }
    var currentConfig by remember { mutableStateOf(ApiConfig()) }
    var editorProvider by remember { mutableStateOf<ProviderConfig?>(null) }
    var draft by remember { mutableStateOf<ApiKeyEntry?>(null) }
    var showApiKey by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var isStatusError by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf<List<ModelOption>>(emptyList()) }
    var isTesting by remember { mutableStateOf(false) }
    var isLoadingModels by remember { mutableStateOf(false) }
    var tavilyApiKeyDraft by remember { mutableStateOf("") }
    var tavilyEnabledDraft by remember { mutableStateOf(false) }
    var showTavilyKey by remember { mutableStateOf(false) }
    var tavilyStatus by remember { mutableStateOf("") }
    var isTavilyStatusError by remember { mutableStateOf(false) }
    var isTavilyTesting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repository.observeConfig().collect {
            currentConfig = it
            tavilyApiKeyDraft = it.tavilyApiKey
            tavilyEnabledDraft = it.webSearchEnabled
        }
    }

    fun openEditor(provider: ProviderConfig, entry: ApiKeyEntry? = null) {
        editorProvider = provider
        draft = entry ?: ApiKeyEntry(
            id = if (provider.multi) UUID.randomUUID().toString() else provider.groupId,
            groupId = provider.groupId,
            name = if (provider.multi) "" else provider.name,
            baseUrl = provider.baseUrl,
            apiKey = "",
            selectedModel = ""
        )
        models = emptyList()
        status = ""
        isStatusError = false
        showApiKey = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ConfigBackground)
            .padding(horizontal = 24.dp)
    ) {
        ApiHeader(onBack = onBack)
        Text("管理和使用第三方平台的接口密钥", color = ConfigMuted, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("已配置 $configuredGroupCount / ${providers.size}", color = ConfigMuted, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            providers.forEach { provider ->
                val providerEntries = entries.filter { it.groupId == provider.groupId }
                if (provider.multi) {
                    CompatibleGroupCard(
                        provider = provider,
                        entries = providerEntries,
                        onAdd = { openEditor(provider) },
                        onEdit = { openEditor(provider, it) },
                        onDelete = { entry -> scope.launch { repository.deleteApiKeyEntry(entry.id) } }
                    )
                } else {
                    val entry = providerEntries.firstOrNull()
                    ProviderCard(
                        provider = provider,
                        entry = entry,
                        onEdit = { openEditor(provider, entry) },
                        onDelete = { entry?.let { scope.launch { repository.deleteApiKeyEntry(it.id) } } }
                    )
                }
            }

            TavilySearchConfigCard(
                enabled = tavilyEnabledDraft,
                apiKey = tavilyApiKeyDraft,
                showApiKey = showTavilyKey,
                status = tavilyStatus,
                isStatusError = isTavilyStatusError,
                isTesting = isTavilyTesting,
                onEnabledChange = { tavilyEnabledDraft = it },
                onApiKeyChange = { tavilyApiKeyDraft = it },
                onToggleKey = { showTavilyKey = !showTavilyKey },
                onTest = {
                    scope.launch {
                        isTavilyTesting = true
                        runCatching { repository.testTavilySearch(tavilyApiKeyDraft) }
                            .onSuccess {
                                tavilyStatus = "Tavily 连接成功"
                                isTavilyStatusError = false
                            }
                            .onFailure {
                                tavilyStatus = safeUserError(it)
                                isTavilyStatusError = true
                            }
                        isTavilyTesting = false
                    }
                },
                onSave = {
                    scope.launch {
                        repository.saveConfig(
                            currentConfig.copy(
                                webSearchEnabled = tavilyEnabledDraft,
                                tavilyApiKey = tavilyApiKeyDraft
                            )
                        )
                        tavilyStatus = "联网搜索配置已保存"
                        isTavilyStatusError = false
                    }
                }
            )

            AddKeyButton(onClick = { openEditor(providers.first { it.multi }) })
            Text(
                "您的接口密钥仅存储在本地，不会上传到服务器，安全可靠。",
                color = ConfigMuted,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            )
        }
    }

    val provider = editorProvider
    val editing = draft
    if (provider != null && editing != null) {
        val normalized = ApiConfig(
            baseUrl = editing.baseUrl,
            apiKey = editing.apiKey,
            selectedModel = editing.selectedModel
        ).normalized()
        val baseUrlError = editing.baseUrl.isNotBlank() && !isValidHttpsBaseUrl(editing.baseUrl)
        val hasStoredApiKey = editing.apiKey.isNotBlank()
        val apiKeyError = !hasStoredApiKey
        val canConnect = !baseUrlError && hasStoredApiKey && editing.baseUrl.isNotBlank()

        ModalBottomSheet(
            onDismissRequest = { editorProvider = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color.White
        ) {
            EditProviderSheet(
                provider = provider,
                entry = editing,
                showApiKey = showApiKey,
                models = models,
                status = status,
                isStatusError = isStatusError,
                isTesting = isTesting,
                isLoadingModels = isLoadingModels,
                baseUrlError = baseUrlError,
                apiKeyError = apiKeyError,
                canConnect = canConnect,
                onEntryChange = { draft = it },
                onToggleKey = { showApiKey = !showApiKey },
                onSave = {
                    scope.launch {
                        val saved = editing.copy(
                            name = editing.name.ifBlank { provider.name },
                            baseUrl = normalized.baseUrl,
                            apiKey = normalized.apiKey,
                            selectedModel = normalized.selectedModel
                        )
                        repository.saveApiKeyEntry(saved, makeCurrent = true)
                        status = "${saved.name} 配置已保存"
                        isStatusError = false
                        editorProvider = null
                    }
                },
                onTest = {
                    scope.launch {
                        isTesting = true
                        runCatching { repository.fetchModels(normalized.baseUrl, normalized.apiKey) }
                            .onSuccess {
                                models = it
                                status = "连接成功，获取到 ${it.size} 个模型"
                                isStatusError = false
                            }
                            .onFailure {
                                models = emptyList()
                                status = it.message ?: "连接失败"
                                isStatusError = true
                            }
                        isTesting = false
                    }
                },
                onLoadModels = {
                    scope.launch {
                        isLoadingModels = true
                        runCatching { repository.fetchModels(normalized.baseUrl, normalized.apiKey) }
                            .onSuccess {
                                models = it
                                status = if (it.isEmpty()) "没有获取到模型" else "请选择一个模型"
                                isStatusError = it.isEmpty()
                            }
                            .onFailure {
                                models = emptyList()
                                status = it.message ?: "加载失败"
                                isStatusError = true
                            }
                        isLoadingModels = false
                    }
                },
                onSelectModel = { model ->
                    draft = editing.copy(selectedModel = model.id)
                    status = "已选择 ${model.id}"
                    isStatusError = false
                }
            )
        }
    }
}

@Composable
private fun ApiHeader(onBack: () -> Unit) {
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
            BackIcon(Modifier.size(24.dp), ConfigText)
        }
        Text(
            "接口密钥管理",
            color = ConfigText,
            fontSize = 25.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            HelpIcon(Modifier.size(24.dp), ConfigMuted)
        }
    }
}

@Composable
private fun ProviderCard(
    provider: ProviderConfig,
    entry: ApiKeyEntry?,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ApiCardShell {
        ProviderLogo(provider, modifier = Modifier.size(54.dp))
        Spacer(modifier = Modifier.width(10.dp))
        EntryInfo(
            title = provider.name,
            configured = entry?.apiKey?.isNotBlank() == true,
            apiKey = entry?.apiKey.orEmpty(),
            keyPrefix = provider.keyPrefix,
            baseUrlText = entry?.baseUrl?.ifBlank { provider.hostLabel } ?: provider.hostLabel,
            modelText = entry?.selectedModel.orEmpty()
        )
        Spacer(modifier = Modifier.width(6.dp))
        ActionPill("编辑", ConfigPrimary, onEdit) { PenIcon(Modifier.size(16.dp), ConfigPrimary) }
        Spacer(modifier = Modifier.width(6.dp))
        ActionPill("删除", ConfigDanger, onDelete) { TrashIcon(Modifier.size(16.dp), ConfigDanger) }
    }
}

@Composable
private fun CompatibleGroupCard(
    provider: ProviderConfig,
    entries: List<ApiKeyEntry>,
    onAdd: () -> Unit,
    onEdit: (ApiKeyEntry) -> Unit,
    onDelete: (ApiKeyEntry) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, ConfigBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProviderLogo(provider, modifier = Modifier.size(54.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(provider.name, color = ConfigText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "${entries.size} 个",
                        color = ConfigPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(ConfigSoft)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(7.dp))
                Text("可添加多个第三方兼容接口", color = ConfigMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ActionPill("添加", ConfigPrimary, onAdd) { PlusCircleIcon(Modifier.size(16.dp), ConfigPrimary) }
        }
        entries.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(ConfigSoft)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EntryInfo(
                    title = entry.name,
                    configured = entry.apiKey.isNotBlank(),
                    apiKey = entry.apiKey,
                    keyPrefix = provider.keyPrefix,
                    baseUrlText = entry.baseUrl,
                    modelText = entry.selectedModel,
                    compact = true
                )
                Text("编辑", color = ConfigPrimary, fontSize = 14.sp, modifier = Modifier.clickable { onEdit(entry) })
                Spacer(modifier = Modifier.width(16.dp))
                Text("删除", color = ConfigDanger, fontSize = 14.sp, modifier = Modifier.clickable { onDelete(entry) })
            }
        }
    }
}

@Composable
private fun ApiCardShell(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, ConfigBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

@Composable
private fun RowScope.EntryInfo(
    title: String,
    configured: Boolean,
    apiKey: String,
    keyPrefix: String,
    baseUrlText: String,
    modelText: String,
    compact: Boolean = false
) {
    Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title.ifBlank { "未命名配置" }, color = ConfigText, fontSize = if (compact) 16.sp else 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (configured) {
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "已配置",
                    color = ConfigGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(ConfigGreenSoft)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(maskKey(apiKey, keyPrefix), color = ConfigMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.width(8.dp))
            EyeIcon(Modifier.size(18.dp), ConfigMuted)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("接口地址：${shortUrl(baseUrlText)}", color = ConfigMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(12.dp))
            Text("模型：${modelText.ifBlank { "未选" }}", color = ConfigMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.52f))
        }
    }
}

@Composable
private fun AddKeyButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF72A2FF), Color(0xFF90D9F4))))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        PlusCircleIcon(Modifier.size(26.dp), Color.White)
        Spacer(modifier = Modifier.width(12.dp))
        Text("添加兼容接口密钥", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TavilySearchConfigCard(
    enabled: Boolean,
    apiKey: String,
    showApiKey: Boolean,
    status: String,
    isStatusError: Boolean,
    isTesting: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onToggleKey: () -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, ConfigBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF43D3C0), Color(0xFF2E86FF)))),
                contentAlignment = Alignment.Center
            ) {
                SearchIcon(Modifier.size(28.dp), Color.White)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Tavily 联网搜索", color = ConfigText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("仅用于会话联网搜索，不作为聊天模型", color = ConfigMuted, fontSize = 12.sp)
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("Tavily API Key") },
            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = onToggleKey) {
                    Text(if (showApiKey) "隐藏" else "显示")
                }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onTest,
                enabled = apiKey.isNotBlank() && !isTesting,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isTesting) "测试中" else "测试连接")
            }
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = ConfigPrimary)
            ) {
                Text("保存搜索配置")
            }
        }
        if (status.isNotBlank()) {
            Text(status, color = if (isStatusError) ConfigDanger else ConfigText, fontSize = 13.sp)
        }
    }
}

@Composable
private fun EditProviderSheet(
    provider: ProviderConfig,
    entry: ApiKeyEntry,
    showApiKey: Boolean,
    models: List<ModelOption>,
    status: String,
    isStatusError: Boolean,
    isTesting: Boolean,
    isLoadingModels: Boolean,
    baseUrlError: Boolean,
    apiKeyError: Boolean,
    canConnect: Boolean,
    onEntryChange: (ApiKeyEntry) -> Unit,
    onToggleKey: () -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onLoadModels: () -> Unit,
    onSelectModel: (ModelOption) -> Unit
) {
    val sheetScrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(sheetScrollState)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProviderLogo(provider, modifier = Modifier.size(50.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("${provider.name} 配置", color = ConfigText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(if (provider.multi) "可保存多个第三方接口" else provider.hostLabel, color = ConfigMuted, fontSize = 13.sp)
            }
        }
        if (provider.multi) {
            OutlinedTextField(
                value = entry.name,
                onValueChange = { onEntryChange(entry.copy(name = it)) },
                label = { Text("名称") },
                placeholder = { Text("例如：OpenRouter / Groq / 本地网关") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
                modifier = Modifier.fillMaxWidth()
            )
        }
        OutlinedTextField(
            value = entry.baseUrl,
            onValueChange = { onEntryChange(entry.copy(baseUrl = it)) },
            label = { Text("接口地址") },
            placeholder = { Text(provider.baseUrl.ifBlank { "https://example.com/v1/" }) },
            supportingText = { if (baseUrlError) Text("请输入 HTTPS 接口地址") },
            isError = baseUrlError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = entry.apiKey,
            onValueChange = { onEntryChange(entry.copy(apiKey = it)) },
            label = { Text("接口密钥") },
            supportingText = { if (apiKeyError) Text("请输入接口密钥") else Text("已填写，保存后将加密存储") },
            trailingIcon = {
                TextButton(onClick = onToggleKey) {
                    Text(if (showApiKey) "隐藏" else "显示")
                }
            },
            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
            isError = apiKeyError,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = entry.selectedModel,
            onValueChange = { onEntryChange(entry.copy(selectedModel = it)) },
            label = { Text("默认模型") },
            placeholder = { Text(provider.defaultModel) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onTest, enabled = canConnect && !isTesting && !isLoadingModels, modifier = Modifier.weight(1f)) {
                Text(if (isTesting) "测试中" else "测试连接")
            }
            Button(onClick = onLoadModels, enabled = canConnect && !isTesting && !isLoadingModels, modifier = Modifier.weight(1f)) {
                Text(if (isLoadingModels) "加载中" else "获取模型")
            }
        }
        Button(
            onClick = onSave,
            enabled = entry.baseUrl.isNotBlank() && !baseUrlError && entry.apiKey.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ConfigPrimary)
        ) {
            Text("保存配置")
        }
        if (status.isNotBlank()) {
            Text(status, color = if (isStatusError) ConfigDanger else ConfigText, fontSize = 14.sp)
        }
        if (models.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(models, key = { it.id }) { model ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ConfigSoft)
                            .clickable { onSelectModel(model) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(model.id, color = ConfigText, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (model.id == entry.selectedModel) "当前" else model.ownedBy ?: "选择", color = ConfigMuted, fontSize = 13.sp)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Composable
private fun ActionPill(text: String, color: Color, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .border(1.dp, ConfigBorder, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, color = color, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ProviderLogo(provider: ProviderConfig, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(62.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(provider.brush),
        contentAlignment = Alignment.Center
    ) {
        when (provider.kind) {
            ProviderKind.OpenAi -> OpenAiMark(Modifier.size(36.dp), Color.White)
            ProviderKind.Compatible -> AppIcon(R.drawable.ic_diamond_24, Color.White, Modifier.size(34.dp))
            ProviderKind.Anthropic -> AppIcon(R.drawable.ic_diamond_24, Color(0xFF252A33), Modifier.size(36.dp))
            ProviderKind.Gemini -> SparkMark(Modifier.size(36.dp), Color.White)
            ProviderKind.OpenRouter -> RouterMark(Modifier.size(36.dp), Color(0xFF303746))
            ProviderKind.SiliconFlow -> Text("sf", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            ProviderKind.DeepSeek -> Text("D", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private data class ProviderConfig(
    val groupId: String,
    val name: String,
    val baseUrl: String,
    val hostLabel: String,
    val keyPrefix: String,
    val defaultModel: String,
    val kind: ProviderKind,
    val brush: Brush,
    val multi: Boolean = false
)

private enum class ProviderKind {
    OpenAi,
    Compatible,
    Anthropic,
    Gemini,
    OpenRouter,
    SiliconFlow,
    DeepSeek
}

private fun providerConfigs(): List<ProviderConfig> = listOf(
    ProviderConfig("openai", "OpenAI", "https://api.openai.com/v1/", "api.openai.com", "sk-", "输入模型名称", ProviderKind.OpenAi, Brush.linearGradient(listOf(Color(0xFF65D1B2), Color(0xFF2BA980)))),
    ProviderConfig("openai-compatible", "OpenAI 兼容", "", "第三方兼容接口", "sk-", "输入模型名称", ProviderKind.Compatible, Brush.linearGradient(listOf(Color(0xFF72A2FF), Color(0xFF90D9F4))), multi = true),
    ProviderConfig("anthropic", "Anthropic", "https://api.anthropic.com/v1/", "api.anthropic.com", "sk-ant-", "claude-3-7-sonnet-latest", ProviderKind.Anthropic, Brush.linearGradient(listOf(Color(0xFFF1C692), Color(0xFFDCA468)))),
    ProviderConfig("gemini", "Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/", "generativelanguage.googleapis.com", "AIza", "gemini-2.5-pro", ProviderKind.Gemini, Brush.linearGradient(listOf(Color(0xFF59B8FF), Color(0xFF3978FF)))),
    ProviderConfig("openrouter", "OpenRouter", "https://openrouter.ai/api/v1/", "openrouter.ai", "sk-or-", "openai/gpt-5", ProviderKind.OpenRouter, Brush.linearGradient(listOf(Color(0xFFF6F8FC), Color(0xFFE8EDF6)))),
    ProviderConfig("siliconflow", "SiliconFlow", "https://api.siliconflow.cn/v1/", "api.siliconflow.cn", "sk-sf-", "Qwen/Qwen3-235B-A22B", ProviderKind.SiliconFlow, Brush.linearGradient(listOf(Color(0xFF8A63FF), Color(0xFF5F52DD)))),
    ProviderConfig("deepseek", "DeepSeek", "https://api.deepseek.com/v1/", "api.deepseek.com", "sk-", "deepseek-chat", ProviderKind.DeepSeek, Brush.linearGradient(listOf(Color(0xFF4A7DFF), Color(0xFF223CD9))))
)

private fun maskKey(key: String, prefix: String): String {
    return if (key.isBlank()) "${prefix}未配置" else "已安全保存"
}

private fun shortUrl(url: String): String {
    val clean = url.removePrefix("https://").removePrefix("http://").trimEnd('/')
    return clean.ifBlank { "未填写" }
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
private fun ChevronRight(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.12f
        drawLine(color, Offset(size.width * 0.36f, size.height * 0.2f), Offset(size.width * 0.66f, size.height * 0.5f), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.66f, size.height * 0.5f), Offset(size.width * 0.36f, size.height * 0.8f), strokeWidth, StrokeCap.Round)
    }
}

@Composable
private fun SearchIcon(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.1f
        drawCircle(color, radius = size.minDimension * 0.28f, center = Offset(size.width * 0.43f, size.height * 0.42f), style = Stroke(strokeWidth))
        drawLine(color, Offset(size.width * 0.63f, size.height * 0.63f), Offset(size.width * 0.86f, size.height * 0.86f), strokeWidth, StrokeCap.Round)
    }
}

@Composable
private fun HelpIcon(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.09f
        drawCircle(color, radius = size.minDimension * 0.42f, center = Offset(size.width / 2f, size.height / 2f), style = Stroke(strokeWidth))
        drawCircle(color, radius = size.minDimension * 0.035f, center = Offset(size.width * 0.5f, size.height * 0.72f))
        drawLine(color, Offset(size.width * 0.5f, size.height * 0.56f), Offset(size.width * 0.5f, size.height * 0.6f), strokeWidth, StrokeCap.Round)
        drawArc(color, startAngle = 205f, sweepAngle = 230f, useCenter = false, topLeft = Offset(size.width * 0.34f, size.height * 0.22f), size = Size(size.width * 0.32f, size.height * 0.32f), style = Stroke(strokeWidth, cap = StrokeCap.Round))
    }
}

@Composable
private fun EyeIcon(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.09f
        val eye = Path().apply {
            moveTo(size.width * 0.08f, size.height * 0.5f)
            quadraticBezierTo(size.width * 0.5f, size.height * 0.14f, size.width * 0.92f, size.height * 0.5f)
            quadraticBezierTo(size.width * 0.5f, size.height * 0.86f, size.width * 0.08f, size.height * 0.5f)
        }
        drawPath(eye, color, style = Stroke(strokeWidth, cap = StrokeCap.Round))
        drawCircle(color, radius = size.minDimension * 0.13f, center = Offset(size.width * 0.5f, size.height * 0.5f))
    }
}

@Composable
private fun PenIcon(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.11f
        drawLine(color, Offset(size.width * 0.24f, size.height * 0.72f), Offset(size.width * 0.72f, size.height * 0.24f), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.62f, size.height * 0.16f), Offset(size.width * 0.82f, size.height * 0.36f), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.2f, size.height * 0.78f), Offset(size.width * 0.44f, size.height * 0.72f), strokeWidth, StrokeCap.Round)
    }
}

@Composable
private fun TrashIcon(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.1f
        drawLine(color, Offset(size.width * 0.22f, size.height * 0.3f), Offset(size.width * 0.78f, size.height * 0.3f), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.42f, size.height * 0.16f), Offset(size.width * 0.58f, size.height * 0.16f), strokeWidth, StrokeCap.Round)
        drawRoundRect(color, Offset(size.width * 0.3f, size.height * 0.36f), Size(size.width * 0.4f, size.height * 0.48f), style = Stroke(strokeWidth))
    }
}

@Composable
private fun PlusCircleIcon(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.1f
        drawCircle(color, radius = size.minDimension * 0.4f, center = Offset(size.width / 2f, size.height / 2f), style = Stroke(strokeWidth))
        drawLine(color, Offset(size.width * 0.32f, size.height * 0.5f), Offset(size.width * 0.68f, size.height * 0.5f), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.5f, size.height * 0.32f), Offset(size.width * 0.5f, size.height * 0.68f), strokeWidth, StrokeCap.Round)
    }
}

@Composable
private fun OpenAiMark(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.09f
        repeat(6) { index ->
            val angle = Math.toRadians((index * 60).toDouble())
            val cx = size.width * 0.5f + kotlin.math.cos(angle).toFloat() * size.width * 0.16f
            val cy = size.height * 0.5f + kotlin.math.sin(angle).toFloat() * size.height * 0.16f
            drawCircle(color, radius = size.minDimension * 0.22f, center = Offset(cx, cy), style = Stroke(strokeWidth))
        }
    }
}

@Composable
private fun SparkMark(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.5f, size.height * 0.05f)
            cubicTo(size.width * 0.58f, size.height * 0.35f, size.width * 0.65f, size.height * 0.42f, size.width * 0.95f, size.height * 0.5f)
            cubicTo(size.width * 0.65f, size.height * 0.58f, size.width * 0.58f, size.height * 0.65f, size.width * 0.5f, size.height * 0.95f)
            cubicTo(size.width * 0.42f, size.height * 0.65f, size.width * 0.35f, size.height * 0.58f, size.width * 0.05f, size.height * 0.5f)
            cubicTo(size.width * 0.35f, size.height * 0.42f, size.width * 0.42f, size.height * 0.35f, size.width * 0.5f, size.height * 0.05f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
private fun RouterMark(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.16f
        drawLine(color, Offset(size.width * 0.2f, size.height * 0.5f), Offset(size.width * 0.78f, size.height * 0.2f), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.2f, size.height * 0.5f), Offset(size.width * 0.78f, size.height * 0.8f), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.2f, size.height * 0.5f), Offset(size.width * 0.72f, size.height * 0.5f), strokeWidth, StrokeCap.Round)
    }
}

private val ConfigBackground = Color(0xFFF7FAFE)
private val ConfigText = Color(0xFF162135)
private val ConfigMuted = Color(0xFF74829A)
private val ConfigBorder = Color(0xFFE0E8F4)
private val ConfigSoft = Color(0xFFF4F8FF)
private val ConfigPrimary = Color(0xFF2E86FF)
private val ConfigGreen = Color(0xFF28A979)
private val ConfigGreenSoft = Color(0xFFE6F7F1)
private val ConfigDanger = Color(0xFFFF4D4F)
