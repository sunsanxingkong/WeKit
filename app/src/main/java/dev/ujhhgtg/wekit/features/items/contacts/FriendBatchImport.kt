package dev.ujhhgtg.wekit.features.items.contacts

import android.content.Context
import androidx.compose.material3.Text
import dev.ujhhgtg.wekit.features.core.ClickableFeature
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.ui.content.AlertDialogContent
import dev.ujhhgtg.wekit.ui.content.TextButton
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog

@Feature(
    name = "密友批量导入",
    categories = ["联系人"],
    description = "从微信原生联系人选择器批量导入密友"
)
object FriendBatchImport : ClickableFeature() {
    override fun onEnable() {}

    override fun onClick(context: Context) {
        showComposeDialog(context) {
            AlertDialogContent(
                title = { Text("密友批量导入") },
                text = { Text("请在微信联系人列表中使用「选择联系人」功能批量导入密友。") },
                dismissButton = { TextButton(onDismiss) { Text("关闭") } }
            )
        }
    }
}