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
import dev.ujhhgtg.wekit.ui.content.AlertDialogContent
import dev.ujhhgtg.wekit.ui.content.Button
import dev.ujhhgtg.wekit.ui.content.TextButton
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog
import dev.ujhhgtg.wekit.utils.android.showToast
import java.io.File

@Feature(name = "语音消息转发", categories = ["聊天"], description = "转发语音文件到指定聊天")
object VoiceForwardMenu : ClickableFeature() {
    override fun onEnable() {}
    override fun onClick(context: Context) {
        val talker = WeCurrentConversationApi.value
        if (talker.isNullOrEmpty()) { showToast(context, "请先进入聊天"); return }
        showComposeDialog(context) {
            var target by remember { mutableStateOf("") }
            var path by remember { mutableStateOf("") }
            AlertDialogContent(
                title = { Text("语音转发") },
                text = { Column { Text("当前: $talker"); TextField(value = path, onValueChange = { path = it }, label = { Text("语音文件路径") }); TextField(value = target, onValueChange = { target = it }, label = { Text("目标wxid") }) } },
                dismissButton = { TextButton(onDismiss) { Text("取消") } },
                confirmButton = { Button(onClick = { if (target.isBlank() || path.isBlank()) { showToast(context, "请填写完整"); return@Button }; if (!File(path).exists()) { showToast(context, "文件不存在"); return@Button }; WeMessageApi.sendVoice(target, path, 3000); showToast(context, "已转发"); onDismiss() }) { Text("转发") } }
            )
        }
    }
}
