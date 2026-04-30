package com.wuyousheng.modeltap.domain.model

data class ModelDisplay(
    val configName: String,
    val modelName: String
) {
    val label: String
        get() = when {
            configName.isNotBlank() && modelName.isNotBlank() -> "$configName / $modelName"
            configName.isNotBlank() -> configName
            modelName.isNotBlank() -> modelName
            else -> "未选择模型"
        }

    val channelLabel: String
        get() = configName.ifBlank { if (modelName.isNotBlank()) "自定义配置" else "未配置" }
}

fun List<ApiKeyEntry>.visibleApiKeyEntries(): List<ApiKeyEntry> {
    return filterNot { it.groupId.equals("tavily", ignoreCase = true) }
}

fun ApiConfig.toModelDisplay(entries: List<ApiKeyEntry>): ModelDisplay {
    val visibleEntries = entries.visibleApiKeyEntries()
    val selectedModel = selectedModel.trim()
    val entry = visibleEntries.bestMatch(baseUrl = baseUrl, apiKey = apiKey, selectedModel = selectedModel)
    val configName = entry?.name.orEmpty()
        .ifBlank { if (baseUrl.isNotBlank() || apiKey.isNotBlank()) "自定义配置" else "" }
    val modelName = when {
        selectedModel.isNotBlank() -> selectedModel
        else -> entry?.selectedModel.orEmpty().trim()
    }
    return ModelDisplay(configName = configName.trim(), modelName = modelName)
}

fun ApiKeyEntry.toModelDisplay(): ModelDisplay {
    return ModelDisplay(
        configName = name.trim().ifBlank { "未命名配置" },
        modelName = selectedModel.trim()
    )
}

fun modelDisplayFor(
    modelId: String,
    entries: List<ApiKeyEntry>,
    currentConfig: ApiConfig? = null
): ModelDisplay {
    val visibleEntries = entries.visibleApiKeyEntries()
    val modelName = modelId.trim()
    if (modelName.isBlank()) return ModelDisplay(configName = "", modelName = "")
    val currentDisplay = currentConfig?.takeIf { it.selectedModel.trim() == modelName }?.toModelDisplay(visibleEntries)
    val entry = visibleEntries.firstOrNull { it.selectedModel.trim() == modelName }
    val configName = currentDisplay?.configName.orEmpty()
        .ifBlank { entry?.name.orEmpty().trim() }
    return ModelDisplay(configName = configName, modelName = modelName)
}

fun List<ApiKeyEntry>.bestMatch(
    baseUrl: String,
    apiKey: String,
    selectedModel: String
): ApiKeyEntry? {
    val normalizedBaseUrl = baseUrl.normalizedForCompare()
    val normalizedApiKey = apiKey.trim()
    val exact = firstOrNull { entry ->
        normalizedBaseUrl.isNotBlank() &&
            normalizedApiKey.isNotBlank() &&
            entry.baseUrl.normalizedForCompare() == normalizedBaseUrl &&
            entry.apiKey.trim() == normalizedApiKey &&
            (selectedModel.isBlank() || entry.selectedModel.trim() == selectedModel)
    }
    if (exact != null) return exact

    val sameEndpoint = firstOrNull { entry ->
        normalizedBaseUrl.isNotBlank() &&
            normalizedApiKey.isNotBlank() &&
            entry.baseUrl.normalizedForCompare() == normalizedBaseUrl &&
            entry.apiKey.trim() == normalizedApiKey
    }
    if (sameEndpoint != null) return sameEndpoint

    val sameModel = firstOrNull { entry ->
        selectedModel.isNotBlank() && entry.selectedModel.trim() == selectedModel
    }
    if (sameModel != null) return sameModel

    return if (selectedModel.isBlank()) singleOrNull { entry ->
        entry.apiKey.isNotBlank() &&
            entry.baseUrl.isNotBlank() &&
            entry.selectedModel.isNotBlank()
    } else {
        null
    }
}

fun String.normalizedForCompare(): String {
    return trim().trimEnd('/')
}
