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
    name = "群聊系统消息",
    categories = ["系统"],
    description = "在当前群聊界面插入系统提示消息"
)
object WeixinTeamGroupMsg : ClickableFeature() {
    override fun onEnable() {}
    override fun onClick(context: Context) {
        val talker = WeCurrentConversationApi.value
        if (talker.isNullOrEmpty()) {
            showToast(context, "请先进入一个聊天界面")
            return
        }
        if (!talker.contains("@chatroom")) {
            showToast(context, "当前不是群聊")
            return
        }
        showComposeDialog(context) {
            var content by remember {
                mutableStateOf("你已移出群聊，可以通过以下方式联系群主")
            }
            AlertDialogContent(
                title = { Text("群聊系统消息") },
                text = {
                    Column {
                        Text("目标群聊: $talker")
                        Text("将以系统提示样式插入（灰色居中，不显示发送者）")
                        TextField(value = content, onValueChange = { content = it }, label = { Text("消息内容") })
                    }
                },
                dismissButton = { TextButton(onDismiss) { Text("取消") } },
                confirmButton = {
                    Button(onClick = {
                        try {
                            WeMessageApi.createSimpleMsgInfoAndInsert(10000, talker, content, System.currentTimeMillis())
                            showToast(context, "已插入群聊系统消息")
                        } catch (e: Exception) {
                            WeLogger.e("WeixinTeamGroupMsg", "insert failed", e)
                            showToast(context, "插入失败")
                        }
                        onDismiss()
                    }) { Text("发送") }
                }
            )
        }
    }
}
