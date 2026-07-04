package dev.ujhhgtg.wekit.features.items.chat

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.ujhhgtg.wekit.features.api.core.WeMessageApi
import dev.ujhhgtg.wekit.features.api.ui.WeCurrentConversationApi
import dev.ujhhgtg.wekit.features.core.ClickableFeature
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.preferences.WePrefs
import dev.ujhhgtg.wekit.ui.content.AlertDialogContent
import dev.ujhhgtg.wekit.ui.content.Button
import dev.ujhhgtg.wekit.ui.content.TextButton
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog
import dev.ujhhgtg.wekit.utils.WeLogger
import dev.ujhhgtg.wekit.utils.android.showToast



import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

@Feature(
    name = "AI语音变声",
    categories = ["聊天"],
    description = "选择音色，输入文字，AI生成变声语音并发送"
)
object AiVoiceChanger : ClickableFeature() {
    private const val KEY_BASE_URL = "ai_voice_base_url"
    private const val KEY_API_KEY = "ai_voice_api_key"
    private const val KEY_MODEL = "ai_voice_model"
    private const val KEY_VOICE_STYLE = "ai_voice_style"

    override fun onEnable() {}

    override fun onClick(context: Context) {
        val talker = WeCurrentConversationApi.value
        if (talker.isNullOrEmpty()) {
            showToast(context, "请先进入一个聊天界面")
            return
        }
        showComposeDialog(context) {
            var text by remember { mutableStateOf("") }
            var style by remember { mutableStateOf(WePrefs.getStringOrDef(KEY_VOICE_STYLE, "girl")) }
            AlertDialogContent(
                title = { Text("AI语音变声") },
                text = {
                    Column {
                        Text("目标: $talker")
                        TextField(value = text, onValueChange = { text = it }, label = { Text("输入文字内容") })
                        TextField(value = style, onValueChange = { style = it }, label = { Text("音色(girl/hanser/jiazi/lubenwei/mature_female/robot)") })
                    }
                },
                dismissButton = { TextButton(onDismiss) { Text("取消") } },
                confirmButton = {
                    Button(onClick = {
                        if (text.isBlank()) { showToast(context, "请输入文字"); return@Button }
                        WePrefs.putString(KEY_VOICE_STYLE, style)
                        showToast(context, "正在生成语音...")
                        onDismiss()
                        Thread {
                            val vp = generateVoice(text, style)
                            if (vp != null) WeMessageApi.sendVoice(talker, vp, text.length * 200)
                        }.start()
                    }) { Text("生成并发送") }
                }
            )
        }
    }

    private fun generateVoice(text: String, style: String): String? {
        return try {
            val baseUrl = WePrefs.getStringOrDef(KEY_BASE_URL, "https://api.3213218.xyz/v1/chat/completions")
            val apiKey = WePrefs.getStringOrDef(KEY_API_KEY, "fkall")
            val model = WePrefs.getStringOrDef(KEY_MODEL, "fkall-语音")

            val conn = URL(baseUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.connectTimeout = 60000
            conn.readTimeout = 60000

            val body = JSONObject().apply {
                put("model", model)
                put("voice", style)
                put("input", text)
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val b64 = json.optString("audio", json.optString("b64_json", ""))
            if (b64.isEmpty()) return null
            val audioBytes = Base64.getDecoder().decode(b64)

            val cacheDir = File(android.app.ActivityThread.currentApplication()?.cacheDir, "ai_voices").apply { mkdirs() }
            val audioFile = File(cacheDir, "voice_${System.currentTimeMillis()}.mp3")
            FileOutputStream(audioFile).use { it.write(audioBytes) }
            audioFile.absolutePath
        } catch (e: Exception) {
            WeLogger.e("AiVoiceChanger", "generate failed", e)
            null
        }
    }
}
