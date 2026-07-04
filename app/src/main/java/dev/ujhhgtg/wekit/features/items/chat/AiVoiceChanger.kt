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
import dev.ujhhgtg.wekit.utils.WeLogger
import dev.ujhhgtg.wekit.utils.android.showToast
import dev.ujhhgtg.wekit.dexkit.abc.IResolveDex
import dev.ujhhgtg.wekit.dexkit.dsl.dexMethod

@Feature(
    name = "AI语音变声",
    categories = ["聊天"],
    description = "通过AI接口将语音消息变声后发送"
)
object AiVoiceChanger : SwitchFeature(), IResolveDex {
    private const val KEY_BASE_URL = "ai_voice_base_url"
    private const val KEY_API_KEY = "ai_voice_api_key"
    private const val KEY_MODEL = "ai_voice_model"
    private const val KEY_VOICE_STYLE = "ai_voice_style"

    private val methodVoiceRecorderFinish by dexMethod {
        matcher {
            declaredClass {
                usingEqStrings("MicroMsg.SceneVoice.Recorder", "Stop file success: ")
            }
        }
    }

    override fun onBeforeToggle(newState: Boolean, context: Context): Boolean {
        if (newState) {
            showConfigDialog(context)
        }
        return true
    }

    override fun onEnable() {
        WeLogger.i("AiVoiceChanger", "enabled, base_url=${WePrefs.getStringOrDef(KEY_BASE_URL, "")}")
    }

    private fun showConfigDialog(context: Context) {
        showComposeDialog(context) {
            var baseUrl by remember { mutableStateOf(WePrefs.getStringOrDef(KEY_BASE_URL, "https://api.3213218.xyz/v1/chat/completions")) }
            var apiKey by remember { mutableStateOf(WePrefs.getStringOrDef(KEY_API_KEY, "fkall")) }
            var model by remember { mutableStateOf(WePrefs.getStringOrDef(KEY_MODEL, "fkall-语音")) }
            var style by remember { mutableStateOf(WePrefs.getStringOrDef(KEY_VOICE_STYLE, "girl")) }
            AlertDialogContent(
                title = { Text("AI语音变声配置") },
                text = {
                    Column {
                        TextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("API地址") })
                        TextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("API密钥") })
                        TextField(value = model, onValueChange = { model = it }, label = { Text("模型名称") })
                        TextField(value = style, onValueChange = { style = it }, label = { Text("音色 (girl/hanser/jiazi/lubenwei/mature_female/robot)") })
                    }
                },
                dismissButton = { TextButton(onDismiss) { Text("取消") } },
                confirmButton = {
                    Button(onClick = {
                        WePrefs.putString(KEY_BASE_URL, baseUrl)
                        WePrefs.putString(KEY_API_KEY, apiKey)
                        WePrefs.putString(KEY_MODEL, model)
                        WePrefs.putString(KEY_VOICE_STYLE, style)
                        showToast(context, "配置已保存")
                        onDismiss()
                    }) { Text("保存") }
                }
            )
        }
    }
}
