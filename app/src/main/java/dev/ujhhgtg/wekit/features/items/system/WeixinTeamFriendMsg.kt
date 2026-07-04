package dev.ujhhgtg.wekit.features.items.system

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
    name = "好友系统消息",
    categories = ["系统"],
    description = "在好友聊天界面快捷发送伪装系统消息（好友专用）"
)
object WeixinTeamFriendMsg : ClickableFeature(), IResolveDex {
    private val methodInsertSysMsg by dexMethod {
        matcher {
            usingStrings("MicroMsg.HoneyPayUtil", "insert sys msg: %s, %s")
        }
    }

    override fun onEnable() {}

    override fun onClick(context: Context) {
        val talker = WeCurrentConversationApi.value
        if (talker.isNullOrEmpty()) {
            showToast(context, "请先进入一个好友聊天界面")
            return
        }
        if (talker.contains("@chatroom")) {
            showToast(context, "当前是群聊，请使用「群聊系统消息」")
            return
        }
        showComposeDialog(context) {
            var content by remember {
                mutableStateOf("你有一条新的系统通知，<a href=\"weixin://dl/feedback\">点击查看</a>。")
            }
            AlertDialogContent(
                title = { Text("好友系统消息") },
                text = {
                    Column {
                        Text("目标好友: $talker")
                        TextField(value = content, onValueChange = { content = it }, label = { Text("系统消息内容 (支持HTML)") })
                    }
                },
                dismissButton = { TextButton(onDismiss) { Text("取消") } },
                confirmButton = {
                    Button(onClick = {
                        if (sendSysMsg("weixin", content)) {
                            showToast(context, "已发送给好友")
                        } else {
                            showToast(context, "发送失败")
                        }
                        onDismiss()
                    }) { Text("发送") }
                }
            )
        }
    }

    private fun sendSysMsg(sender: String, content: String): Boolean {
        return try {
            val m = methodInsertSysMsg.method
            m.isAccessible = true
            m.invoke(null, sender, content, "")
            true
        } catch (e: Exception) {
            WeLogger.e("WeixinTeamFriendMsg", "send failed", e)
            false
        }
    }
}
