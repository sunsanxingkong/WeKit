package dev.ujhhgtg.wekit.features.items.system

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.ujhhgtg.wekit.features.core.ClickableFeature
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.preferences.WePrefs
import dev.ujhhgtg.wekit.ui.content.AlertDialogContent
import dev.ujhhgtg.wekit.ui.content.Button
import dev.ujhhgtg.wekit.ui.content.TextButton
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog
import dev.ujhhgtg.wekit.utils.android.showToast

@Feature(
    name = "全局AI配置",
    categories = ["系统"],
    description = "一键配置官方AI中转接口（文本/图像/视频/语音）"
)
object AiGlobalConfig : ClickableFeature() {
    override fun onEnable() {}

    override fun onClick(context: Context) {
        showComposeDialog(context) {
            Column {
                Text("官方中转 AI 地址：api.3213218.xyz")
                Text("默认密钥：fkall")
            }
            AlertDialogContent(
                title = { Text("全局AI配置") },
                text = {
                    Column {
                        Text("点击「一键填入」将配置所有AI接口：")
                        Text("• 文本：fkall-文本")
                        Text("• 图像：fkall-图像")
                        Text("• 视频：fkall-视频")
                        Text("• 语音：fkall-语音")
                    }
                },
                dismissButton = { TextButton(onDismiss) { Text("取消") } },
                confirmButton = {
                    Button(onClick = {
                        val prefs = context.getSharedPreferences("xoxo_ai_config", 0).edit()
                        prefs.putString("base_url", "https://api.3213218.xyz/v1/chat/completions")
                        prefs.putString("api_key", "fkall")
                        prefs.putString("model", "fkall-文本")
                        prefs.putString("image_base_url", "https://api.3213218.xyz/v1/images/generations")
                        prefs.putString("image_api_key", "fkall")
                        prefs.putString("image_model", "fkall-图像")
                        prefs.putString("video_base_url", "https://api.3213218.xyz/v1/videos")
                        prefs.putString("video_api_key", "fkall")
                        prefs.putString("video_model", "fkall-视频")
                        prefs.putString("voice_base_url", "https://api.3213218.xyz/v1/chat/completions")
                        prefs.putString("voice_api_key", "fkall")
                        prefs.putString("voice_model", "fkall-语音")
                        prefs.apply()
                        showToast(context, "官方中转 AI 全部配置已保存")
                        onDismiss()
                    }) { Text("一键填入") }
                }
            )
        }
    }
}