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
    name = "AI图像生成",
    categories = ["聊天", "娱乐"],
    description = "输入文字描述，AI生成图片并发送到当前聊天"
)
object AiImageGen : ClickableFeature() {
    private const val KEY_IMAGE_URL = "ai_image_base_url"
    private const val KEY_IMAGE_API_KEY = "ai_image_api_key"
    private const val KEY_IMAGE_MODEL = "ai_image_model"

    override fun onEnable() {}

    override fun onClick(context: Context) {
        val talker = WeCurrentConversationApi.value
        if (talker.isNullOrEmpty()) {
            showToast(context, "请先进入一个聊天界面")
            return
        }
        showComposeDialog(context) {
            var prompt by remember { mutableStateOf("") }
            AlertDialogContent(
                title = { Text("AI图像生成") },
                text = {
                    Column {
                        Text("目标: $talker")
                        TextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            label = { Text("图片描述") }
                        )
                    }
                },
                dismissButton = { TextButton(onDismiss) { Text("取消") } },
                confirmButton = {
                    Button(onClick = {
                        if (prompt.isBlank()) {
                            showToast(context, "请输入描述")
                            return@Button
                        }
                        showToast(context, "正在生成...")
                        onDismiss()
                        Thread {
                            val imgPath = generateImage(prompt)
                            if (imgPath != null) {
                                WeMessageApi.sendImage(talker, imgPath)
                            } else {
                                WeLogger.e("AiImageGen", "generate failed")
                            }
                        }.start()
                    }) { Text("生成并发送") }
                }
            )
        }
    }

    private fun generateImage(prompt: String): String? {
        return try {
            val baseUrl = WePrefs.getStringOrDef(KEY_IMAGE_URL, "https://api.3213218.xyz/v1/images/generations")
            val apiKey = WePrefs.getStringOrDef(KEY_IMAGE_API_KEY, "fkall")
            val model = WePrefs.getStringOrDef(KEY_IMAGE_MODEL, "fkall-图像")

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

            val cacheDir = File(android.app.ActivityThread.currentApplication()?.cacheDir, "ai_images").apply { mkdirs() }
            val imgFile = File(cacheDir, "ai_${System.currentTimeMillis()}.png")
            FileOutputStream(imgFile).use { it.write(imgBytes) }
            imgFile.absolutePath
        } catch (e: Exception) {
            WeLogger.e("AiImageGen", "generate failed", e)
            null
        }
    }
}
