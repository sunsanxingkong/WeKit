package dev.ujhhgtg.wekit.features.items.contacts

import android.content.Context
import dev.ujhhgtg.wekit.features.api.core.WeDatabaseApi
import dev.ujhhgtg.wekit.features.core.ClickableFeature
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.preferences.WePrefs
import dev.ujhhgtg.wekit.ui.content.ContactsSelector
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog
import dev.ujhhgtg.wekit.utils.android.showToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Feature(
    name = "密友批量导入",
    categories = ["联系人"],
    description = "选择好友导入为密友，其他密友功能共享此列表（带头像、搜索、全选）"
)
object FriendBatchImport : ClickableFeature() {
    private const val KEY_FRIEND_LIST = "friend_vibrate_contacts"

    override fun onEnable() {}

    override fun onClick(context: Context) {
        CoroutineScope(Dispatchers.Main).launch {
            val friends = withContext(Dispatchers.IO) {
                try { WeDatabaseApi.getFriends() } catch (e: Exception) { emptyList() }
            }
            if (friends.isEmpty()) {
                showToast(context, "无法获取好友列表")
                return@launch
            }
            val current = WePrefs.getStringSetOrDef(KEY_FRIEND_LIST, emptySet())
            showComposeDialog(context) {
                ContactsSelector(
                    title = "导入密友（其他密友功能共享此列表）",
                    contacts = friends,
                    initialSelectedWxIds = current,
                    onDismiss = { onDismiss() },
                    onConfirm = { selected ->
                        WePrefs.putStringSet(KEY_FRIEND_LIST, selected)
                        showToast(context, "已导入${selected.size}个密友，其他密友功能可直接使用")
                        onDismiss()
                    }
                )
            }
        }
    }
}
