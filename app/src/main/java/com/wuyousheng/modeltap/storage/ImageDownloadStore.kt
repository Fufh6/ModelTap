package com.wuyousheng.modeltap.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.annotation.TargetApi
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.wuyousheng.modeltap.domain.model.GeneratedImage
import java.io.File
import java.io.IOException
import java.net.URL

private const val DownloadSubdirectory = "ModelTap"

fun Context.saveGeneratedImageToDownloads(image: GeneratedImage) {
    saveImageToDownloads(image.uri)
}

fun Context.saveImageToDownloads(imageUri: String) {
    val extension = imageUri.resolveImageExtension()
    val displayName = "modeltap_${System.currentTimeMillis()}.$extension"
    val mimeType = extension.toImageMimeType()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        saveImageToMediaStore(imageUri, displayName, mimeType)
    } else {
        saveImageToLegacyDownloads(imageUri, displayName)
    }
}

@TargetApi(Build.VERSION_CODES.Q)
private fun Context.saveImageToMediaStore(
    imageUri: String,
    displayName: String,
    mimeType: String
) {
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, displayName)
        put(MediaStore.Downloads.MIME_TYPE, mimeType)
        put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$DownloadSubdirectory")
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val resolver = contentResolver
    val targetUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: throw IOException("无法创建保存文件")

    try {
        openImageInputStream(imageUri).use { input ->
            resolver.openOutputStream(targetUri)?.use { output -> input.copyTo(output) }
                ?: throw IOException("无法写入保存文件")
        }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(targetUri, values, null, null)
    } catch (error: Throwable) {
        resolver.delete(targetUri, null, null)
        throw error
    }
}

private fun Context.saveImageToLegacyDownloads(imageUri: String, displayName: String) {
    val directory = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        DownloadSubdirectory
    ).apply {
        if (!exists() && !mkdirs()) throw IOException("无法创建保存目录：$path")
    }
    val target = File(directory, displayName)
    openImageInputStream(imageUri).use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    }
}

private fun Context.openImageInputStream(imageUri: String) = when {
    imageUri.startsWith("http://", ignoreCase = true) ||
        imageUri.startsWith("https://", ignoreCase = true) -> {
        URL(imageUri).openStream()
    }
    else -> {
        contentResolver.openInputStream(Uri.parse(imageUri))
            ?: throw IOException("无法读取图片文件")
    }
}

private fun String.resolveImageExtension(): String {
    val extension = substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('.', "")
        .lowercase()
    return when (extension) {
        "jpg", "jpeg" -> "jpg"
        "webp" -> "webp"
        "png" -> "png"
        else -> "png"
    }
}

private fun String.toImageMimeType(): String {
    return when (this) {
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> "image/png"
    }
}
