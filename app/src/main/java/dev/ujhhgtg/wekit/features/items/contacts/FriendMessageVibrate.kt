package dev.ujhhgtg.wekit.features.items.contacts

import android.content.Context
import dev.ujhhgtg.wekit.features.core.SwitchFeature
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.preferences.WePrefs
import dev.ujhhgtg.wekit.ui.content.AlertDialogContent
import dev.ujhhgtg.wekit.ui.content.Button
import dev.ujhhgtg.wekit.ui.content.TextButton
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog
import dev.ujhhgtg.wekit.utils.android.showToast
import androidx.compose.material3.Text

@Feature(
    name = "密友消息振动",
    categories = ["联系人"],
    description = "收到密友消息时触发特殊振动（需先在密友批量导入中选择密友）"
)
object FriendMessageVibrate : SwitchFeature() {
    override fun onBeforeToggle(newState: Boolean, context: Context): Boolean {
        if (newState) {
            val friends = WePrefs.getStringSetOrDef("friend_vibrate_contacts", emptySet())
            if (friends.isEmpty()) {
                showToast(context, "请先在密友批量导入中选择密友")
                return false
            }
            showComposeDialog(context) {
                AlertDialogContent(
                    title = { Text("密友消息振动") },
                    text = { Text("已导入${friends.size}个密友，开启后收到他们的消息将触发特殊振动") },
                    confirmButton = { Button(onClick = { onDismiss() }) { Text("确定") } },
                    dismissButton = { TextButton(onDismiss) { Text("取消") } }
                )
            }
        }
        return true
    }
    override fun onEnable() {}
}
