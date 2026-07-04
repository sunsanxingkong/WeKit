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
import dev.ujhhgtg.wekit.features.api.core.WeMessageApi
import dev.ujhhgtg.wekit.features.api.ui.WeCurrentConversationApi
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.features.core.SwitchFeature
import dev.ujhhgtg.wekit.preferences.WePrefs
import dev.ujhhgtg.wekit.ui.content.AlertDialogContent
import dev.ujhhgtg.wekit.ui.content.Button
import dev.ujhhgtg.wekit.ui.content.TextButton
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog
import dev.ujhhgtg.wekit.utils.WeLogger
import dev.ujhhgtg.wekit.utils.android.showToast



import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@Feature(
    name = "AI智能回复",
    categories = ["聊天"],
    description = "收到消息后用AI自动生成回复并发送"
)
object AiQuickReply : SwitchFeature(), IResolveDex {
    private const val KEY_TEXT_URL = "ai_text_base_url"
    private const val KEY_TEXT_API_KEY = "ai_text_api_key"
    private const val KEY_TEXT_MODEL = "ai_text_model"

    private val methodReceiveMsg by dexMethod(allowFailure = true) {
        matcher {
            usingStrings("MicroMsg.MessageStorage", "insert msg")
        }
    }

    override fun onBeforeToggle(newState: Boolean, context: Context): Boolean {
        if (newState) {
            showConfigDialog(context)
        }
        return true
    }

    override fun onEnable() {
        methodReceiveMsg.hookAfter {
            try {
                val talker = WeCurrentConversationApi.value
                if (talker.isNullOrEmpty()) return@hookAfter
                val content = args.getOrNull(0) as? String ?: return@hookAfter
                if (content.startsWith("AI:")) return@hookAfter

                Thread {
                    val reply = callAi(content)
                    if (reply != null) {
                        WeMessageApi.sendText(talker, reply)
                    }
                }.start()
            } catch (e: Exception) {
                WeLogger.e("AiQuickReply", "hook failed", e)
            }
        }
    }

    private fun callAi(userMessage: String): String? {
        return try {
            val baseUrl = WePrefs.getStringOrDef(KEY_TEXT_URL, "")
            val apiKey = WePrefs.getStringOrDef(KEY_TEXT_API_KEY, "")
            val model = WePrefs.getStringOrDef(KEY_TEXT_MODEL, "fkall-文本")
            if (baseUrl.isEmpty()) return null

            val conn = URL(baseUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.connectTimeout = 30000
            conn.readTimeout = 30000

            val body = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", "You are a helpful assistant. Reply concisely in Chinese."))
                    put(JSONObject().put("role", "user").put("content", userMessage))
                })
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        } catch (e: Exception) {
            WeLogger.e("AiQuickReply", "AI call failed", e)
            null
        }
    }

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
