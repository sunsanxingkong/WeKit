package dev.ujhhgtg.wekit.features.items.chat

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.features.core.SwitchFeature
import dev.ujhhgtg.wekit.preferences.WePrefs
import dev.ujhhgtg.wekit.ui.content.AlertDialogContent
import dev.ujhhgtg.wekit.ui.content.Button
import dev.ujhhgtg.wekit.ui.content.TextButton
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog
import dev.ujhhgtg.wekit.utils.android.showToast

@Feature(
    name = "AI智能回复",
    categories = ["聊天"],
    description = "使用AI自动回复消息（需要配置AI接口）"
)
object AiQuickReply : SwitchFeature() {
    private const val KEY_TEXT_URL = "ai_text_base_url"
    private const val KEY_TEXT_API_KEY = "ai_text_api_key"
    private const val KEY_TEXT_MODEL = "ai_text_model"

    override fun onBeforeToggle(newState: Boolean, context: Context): Boolean {
        if (newState) {
            showConfigDialog(context)
        }
        return true
    }

    override fun onEnable() {}

    private fun showConfigDialog(context: Context) {
        showComposeDialog(context) {
            var baseUrl by remember { mutableStateOf(WePrefs.getStringOrDef(KEY_TEXT_URL, "https://api.3213218.xyz/v1/chat/completions")) }
            var apiKey by remember { mutableStateOf(WePrefs.getStringOrDef(KEY_TEXT_API_KEY, "fkall")) }
            var model by remember { mutableStateOf(WePrefs.getStringOrDef(KEY_TEXT_MODEL, "fkall-文本")) }
            AlertDialogContent(
                title = { Text("AI智能回复配置") },
                text = {
                    Column {
                        TextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("API地址") })
                        TextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("API密钥") })
                        TextField(value = model, onValueChange = { model = it }, label = { Text("模型名称") })
                    }
                },
                dismissButton = { TextButton(onDismiss) { Text("取消") } },
                confirmButton = {
                    Button(onClick = {
                        WePrefs.putString(KEY_TEXT_URL, baseUrl)
                        WePrefs.putString(KEY_TEXT_API_KEY, apiKey)
                        WePrefs.putString(KEY_TEXT_MODEL, model)
                        showToast(context, "已保存")
                        onDismiss()
                    }) { Text("保存") }
                }
            )
        }
    }
}