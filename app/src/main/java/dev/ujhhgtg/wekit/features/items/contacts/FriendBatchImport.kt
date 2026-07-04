package dev.ujhhgtg.wekit.features.items.contacts

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

@Feature(name = "密友批量导入", categories = ["联系人"], description = "批量导入密友wxid列表")
object FriendBatchImport : ClickableFeature() {
    private const val KEY_FRIEND_LIST = "friend_batch_list"
    override fun onEnable() {}
    override fun onClick(context: Context) {
        showComposeDialog(context) {
            var input by remember { mutableStateOf(WePrefs.getStringOrDef(KEY_FRIEND_LIST, "")) }
            AlertDialogContent(
                title = { Text("密友批量导入") },
                text = { Column { Text("输入密友wxid，每行一个或逗号分隔"); TextField(value = input, onValueChange = { input = it }, label = { Text("wxid列表") }) } },
                dismissButton = { TextButton(onDismiss) { Text("取消") } },
                confirmButton = { Button(onClick = {
                    val set = input.split("\n", ",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                    WePrefs.putStringSet(KEY_FRIEND_LIST, set)
                    showToast(context, "已导入${set.size}个密友")
                    onDismiss()
                }) { Text("导入") } }
            )
        }
    }
}
