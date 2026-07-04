package dev.ujhhgtg.wekit.features.items.system

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
import dev.ujhhgtg.wekit.utils.WeLogger
import dev.ujhhgtg.wekit.utils.android.showToast

@Feature(
    name = "微信团队消息",
    categories = ["系统"],
    description = "在当前聊天界面发送伪装系统消息（自动识别群聊/好友）"
)
object WeixinTeamSysMsg : ClickableFeature() {
    override fun onEnable() {}

    override fun onClick(context: Context) {
        val talker = WeCurrentConversationApi.value
        if (talker.isNullOrEmpty()) {
            showToast(context, "请先进入一个聊天界面")
            return
        }
        val isGroup = talker.contains("@chatroom")
        val title = if (isGroup) "发送系统消息到当前群聊" else "发送系统消息给当前好友"
        showComposeDialog(context) {
            var content by remember {
                mutableStateOf("[系统消息] 如果遇到问题，可轻触此处反馈给我们。")
            }
            AlertDialogContent(
                title = { Text(title) },
                text = {
                    Column {
                        Text("目标: $talker")
                        TextField(value = content, onValueChange = { content = it }, label = { Text("消息内容") })
                    }
                },
                dismissButton = { TextButton(onDismiss) { Text("取消") } },
                confirmButton = {
                    Button(onClick = {
                        try {
                            WeMessageApi.sendText(talker, content)
                            showToast(context, "已发送到当前聊天")
                        } catch (e: Exception) {
                            WeLogger.e("WeixinTeamSysMsg", "send failed", e)
                            showToast(context, "发送失败")
                        }
                        onDismiss()
                    }) { Text("发送") }
                }
            )
        }
    }
}
