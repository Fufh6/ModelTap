package com.wuyousheng.modeltap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wuyousheng.modeltap.navigation.AppNavGraph
import com.wuyousheng.modeltap.storage.AppPreferences
import com.wuyousheng.modeltap.ui.components.AdaptiveDensity
import com.wuyousheng.modeltap.ui.theme.ModelTapTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AdaptiveDensity {
                ModelTapTheme {
                    AppNavGraph()
                    PrivacyNoticeDialog()
                }
            }
        }
    }
}

@Composable
private fun PrivacyNoticeDialog() {
    val context = LocalContext.current
    val preferences = remember(context) { AppPreferences(context.applicationContext) }
    val privacyNoticeAccepted by preferences.privacyNoticeAcceptedFlow.collectAsStateWithLifecycle(
        initialValue = true
    )
    val coroutineScope = rememberCoroutineScope()

    if (!privacyNoticeAccepted) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("隐私声明") },
            text = {
                Column {
                    Text("本应用重视你的隐私。你的会话记录、应用设置、模型配置和使用统计默认保存在本机设备中，用于提供历史记录、继续对话和本地偏好功能。")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("你填写的 API Key 会通过系统安全存储保存；应用不会主动上传这些本地数据，也不会用于广告追踪。只有当你发起对话、联网搜索或图片生成等操作时，相关输入内容才会发送到你配置的模型或搜索服务。")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("继续使用即表示你已了解并同意上述本地存储和必要网络请求说明。")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            preferences.acceptPrivacyNotice()
                        }
                    }
                ) {
                    Text("我已了解")
                }
            }
        )
    }
}
