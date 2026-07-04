package dev.ujhhgtg.wekit.features.items.entertain

import android.content.Context
import dev.ujhhgtg.wekit.features.core.ClickableFeature
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.utils.android.showToast
import androidx.compose.material3.Text
import dev.ujhhgtg.wekit.ui.content.AlertDialogContent
import dev.ujhhgtg.wekit.ui.content.Button
import dev.ujhhgtg.wekit.ui.content.TextButton
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog

@Feature(
    name = "语音克隆",
    categories = ["娱乐", "聊天"],
    description = "录制声音样本用于AI语音克隆"
)
object VoiceClone : ClickableFeature() {
    override fun onEnable() {}

    override fun onClick(context: Context) {
        showComposeDialog(context) {
            AlertDialogContent(
                title = { Text("语音克隆") },
                text = { Text("此功能需要配合AI语音变声使用。\n请在AI语音变声配置中设置API后进行声音克隆。") },
                dismissButton = { TextButton(onDismiss) { Text("关闭") } },
                confirmButton = {
                    Button(onClick = {
                        showToast(context, "录制功能待实现")
                        onDismiss()
                    }) { Text("开始录制") }
                }
            )
        }
    }
}