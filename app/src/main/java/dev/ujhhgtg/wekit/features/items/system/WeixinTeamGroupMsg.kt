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
    name = "群聊系统消息",
    categories = ["系统"],
    description = "在群聊界面快捷发送伪装系统消息（群聊专用）"
)
object WeixinTeamGroupMsg : ClickableFeature(), IResolveDex {
    private val methodInsertSysMsg by dexMethod {
        matcher {
            usingStrings("MicroMsg.HoneyPayUtil", "insert sys msg: %s, %s")
        }
    }

    override fun onEnable() {}

    override fun onClick(context: Context) {
        val talker = WeCurrentConversationApi.value
        if (talker.isNullOrEmpty()) {
            showToast(context, "请先进入一个群聊界面")
            return
        }
        if (!talker.contains("@chatroom")) {
            showToast(context, "当前不是群聊，请使用「微信团队消息」")
            return
        }
        showComposeDialog(context) {
            var content by remember {
                mutableStateOf("群公告：请遵守群规，文明交流。<a href=\"weixin://dl/feedback\">查看详情</a>")
            }
            AlertDialogContent(
                title = { Text("群聊系统消息") },
                text = {
                    Column {
                        Text("目标群聊: $talker")
                        TextField(value = content, onValueChange = { content = it }, label = { Text("系统消息内容 (支持HTML)") })
                    }
                },
                dismissButton = { TextButton(onDismiss) { Text("取消") } },
                confirmButton = {
                    Button(onClick = {
                        if (sendSysMsg("weixin", content)) {
                            showToast(context, "已发送到群聊")
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
            WeLogger.e("WeixinTeamGroupMsg", "send failed", e)
            false
        }
    }
}
