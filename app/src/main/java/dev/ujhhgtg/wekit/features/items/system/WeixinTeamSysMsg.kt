package dev.ujhhgtg.wekit.features.items.system

import android.app.ActivityThread
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.robv.android.xposed.XposedHelpers
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
    name = "微信团队消息",
    categories = ["系统"],
    description = "以微信团队身份向当前会话发送伪装系统消息"
)
object WeixinTeamSysMsg : ClickableFeature(), IResolveDex {
    private val methodInsertSysMsg by dexMethod {
        matcher {
            usingEqStrings("MicroMsg.HoneyPayUtil", "insert sys msg: %s, %s")
        }
    }
    private var sysMsgClass: Class<*>? = null

    override fun onEnable() {}

    override fun onClick(context: Context) {
        val talker = WeCurrentConversationApi.value
        if (talker == null) {
            showToast(context, "无法获取当前会话")
            return
        }
        showComposeDialog(context) {
            var sender by remember { mutableStateOf("weixin") }
            var content by remember {
                mutableStateOf("如果遇到问题，可<a href=\"weixin://dl/feedback?from=一级\">轻触此处</a>反馈给我们。")
            }
            AlertDialogContent(
                title = { Text("发送微信团队消息") },
                text = {
                    Column {
                        TextField(value = sender, onValueChange = { sender = it }, label = { Text("发送者标识") })
                        TextField(value = content, onValueChange = { content = it }, label = { Text("消息内容 (支持HTML)") })
                    }
                },
                dismissButton = { TextButton(onDismiss) { Text("取消") } },
                confirmButton = {
                    Button(onClick = {
                        send(sender, content)
                        showToast(context, "已发送")
                        onDismiss()
                    }) { Text("发送") }
                }
            )
        }
    }

    private fun send(sender: String, content: String) {
        try {
            if (sysMsgClass == null) {
                val app = ActivityThread.currentApplication()
                if (app != null) {
                    sysMsgClass = XposedHelpers.findClassIfExists(methodInsertSysMsg.className, app.classLoader)
                }
            }
            sysMsgClass?.let { cls ->
                XposedHelpers.callStaticMethod(cls, methodInsertSysMsg.name, sender, content, "")
            }
        } catch (e: Exception) {
            WeLogger.e("WeixinTeamSysMsg", "send failed", e)
        }
    }
}
