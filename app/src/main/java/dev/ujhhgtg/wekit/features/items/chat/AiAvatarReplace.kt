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
    name = "AI头像替换",
    categories = ["聊天", "美化"],
    description = "输入描述，AI生成头像图片并发送到当前聊天"
)
object AiAvatarReplace : ClickableFeature() {
    private const val KEY_AVATAR_URL = "ai_avatar_base_url"
    private const val KEY_AVATAR_API_KEY = "ai_avatar_api_key"
    private const val KEY_AVATAR_MODEL = "ai_avatar_model"

    override fun onEnable() {}

    override fun onClick(context: Context) {
        val talker = WeCurrentConversationApi.value
        if (talker.isNullOrEmpty()) {
            showToast(context, "请先进入一个聊天界面")
            return
        }
        showComposeDialog(context) {
            var prompt by remember { mutableStateOf("一个可爱的卡通头像") }
            AlertDialogContent(
                title = { Text("AI头像生成") },
                text = {
                    Column {
                        Text("目标: $talker")
                        TextField(value = prompt, onValueChange = { prompt = it }, label = { Text("头像描述") })
                    }
                },
                dismissButton = { TextButton(onDismiss) { Text("取消") } },
                confirmButton = {
                    Button(onClick = {
                        showToast(context, "正在生成头像...")
                        onDismiss()
                        Thread {
                            val imgPath = generateAvatar(prompt)
                            if (imgPath != null) WeMessageApi.sendImage(talker, imgPath)
                        }.start()
                    }) { Text("生成并发送") }
                }
            )
        }
    }

    private fun generateAvatar(prompt: String): String? {
        return try {
            val baseUrl = WePrefs.getStringOrDef(KEY_AVATAR_URL, "https://api.3213218.xyz/v1/images/generations")
            val apiKey = WePrefs.getStringOrDef(KEY_AVATAR_API_KEY, "fkall")
            val model = WePrefs.getStringOrDef(KEY_AVATAR_MODEL, "fkall-图像")
            val conn = URL(baseUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.connectTimeout = 60000
            conn.readTimeout = 60000
            val body = JSONObject().apply {
                put("model", model)
                put("prompt", prompt)
                put("n", 1)
                put("response_format", "b64_json")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val b64 = json.getJSONArray("data").getJSONObject(0).getString("b64_json")
            val imgBytes = Base64.getDecoder().decode(b64)
            val cacheDir = File(android.app.ActivityThread.currentApplication()?.cacheDir, "ai_avatars").apply { mkdirs() }
            val imgFile = File(cacheDir, "avatar_${System.currentTimeMillis()}.png")
            FileOutputStream(imgFile).use { it.write(imgBytes) }
            imgFile.absolutePath
        } catch (e: Exception) {
            WeLogger.e("AiAvatarReplace", "generate failed", e)
            null
        }
    }
}
