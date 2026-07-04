package dev.ujhhgtg.wekit.features.items.chat

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.ujhhgtg.wekit.dexkit.abc.IResolveDex
import dev.ujhhgtg.wekit.dexkit.dsl.dexMethod
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.features.core.SwitchFeature
import dev.ujhhgtg.wekit.preferences.WePrefs
import dev.ujhhgtg.wekit.ui.content.AlertDialogContent
import dev.ujhhgtg.wekit.ui.content.Button
import dev.ujhhgtg.wekit.ui.content.TextButton
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog
import dev.ujhhgtg.wekit.utils.android.showToast

@Feature(
    name = "AI头像替换",
    categories = ["聊天", "美化"],
    description = "用AI生成的头像替换联系人默认头像"
)
object AiAvatarReplace : SwitchFeature() {
    private const val KEY_AVATAR_URL = "ai_avatar_base_url"
    private const val KEY_AVATAR_API_KEY = "ai_avatar_api_key"

    override fun onBeforeToggle(newState: Boolean, context: Context): Boolean {
        if (newState) {
            showConfigDialog(context)
        }
        return true
    }

    override fun onEnable() {}

    private fun showConfigDialog(context: Context) {
        showComposeDialog(context) {
            var baseUrl by remember { mutableStateOf(WePrefs.getStringOrDef(KEY_AVATAR_URL, "")) }
            var apiKey by remember { mutableStateOf(WePrefs.getStringOrDef(KEY_AVATAR_API_KEY, "")) }
            AlertDialogContent(
                title = { Text("AI头像配置") },
                text = {
                    Column {
                        TextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("API地址") })
                        TextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("API密钥") })
                    }
                },
                dismissButton = { TextButton(onDismiss) { Text("取消") } },
                confirmButton = {
                    Button(onClick = {
                        WePrefs.putString(KEY_AVATAR_URL, baseUrl)
                        WePrefs.putString(KEY_AVATAR_API_KEY, apiKey)
                        showToast(context, "已保存")
                        onDismiss()
                    }) { Text("保存") }
                }
            )
        }
    }
}