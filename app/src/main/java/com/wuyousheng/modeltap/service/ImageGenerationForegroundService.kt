package com.wuyousheng.modeltap.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.wuyousheng.modeltap.MainActivity
import com.wuyousheng.modeltap.R
import com.wuyousheng.modeltap.data.repository.ChatRepositoryProvider
import com.wuyousheng.modeltap.data.repository.safeUserError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ImageGenerationForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prompt = intent?.getStringExtra(EXTRA_PROMPT).orEmpty()
        val apiEntryId = intent?.getStringExtra(EXTRA_API_ENTRY_ID).orEmpty()
        val referenceUris = intent?.getStringArrayListExtra(EXTRA_REFERENCE_URIS).orEmpty()

        if (prompt.isBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification("图片生成中", "后台任务已启动，请保持网络连接。"),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )

        serviceScope.launch {
            val repository = ChatRepositoryProvider.get(applicationContext)
            val result = repository.createImageSessionAndGenerate(
                prompt = prompt,
                apiEntryId = apiEntryId,
                referenceImageUris = referenceUris
            )
            ServiceCompat.stopForeground(this@ImageGenerationForegroundService, ServiceCompat.STOP_FOREGROUND_DETACH)
            val manager = getSystemService(NotificationManager::class.java)
            val notification = result.fold(
                onSuccess = { buildNotification("图片生成完成", "已加入最近生成。", ongoing = false) },
                onFailure = { error -> buildNotification("图片生成失败", safeUserError(error), ongoing = false) }
            )
            manager.notify(NOTIFICATION_ID, notification)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "图片生成",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "图片生成后台任务"
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, text: String, ongoing: Boolean = true) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_image_24)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent())
            .build()

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val CHANNEL_ID = "image_generation"
        private const val NOTIFICATION_ID = 2101
        private const val EXTRA_PROMPT = "extra_prompt"
        private const val EXTRA_API_ENTRY_ID = "extra_api_entry_id"
        private const val EXTRA_REFERENCE_URIS = "extra_reference_uris"

        fun start(
            context: Context,
            prompt: String,
            apiEntryId: String,
            referenceImageUris: List<String>
        ) {
            val intent = Intent(context, ImageGenerationForegroundService::class.java).apply {
                putExtra(EXTRA_PROMPT, prompt)
                putExtra(EXTRA_API_ENTRY_ID, apiEntryId)
                putStringArrayListExtra(EXTRA_REFERENCE_URIS, ArrayList(referenceImageUris))
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }
}
