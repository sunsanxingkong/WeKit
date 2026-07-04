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
    name = "密友联系人隐藏",
    categories = ["联系人"],
    description = "选择密友，在通讯录中隐藏他们"
)
object FriendHideContacts : ClickableFeature() {
    private const val KEY_HIDE_CONTACTS = "friend_hide_contacts"

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
            val current = WePrefs.getStringSetOrDef(KEY_HIDE_CONTACTS, emptySet())
            showComposeDialog(context) {
                ContactsSelector(
                    title = "选择要隐藏的密友",
                    contacts = friends,
                    initialSelectedWxIds = current,
                    onDismiss = { onDismiss() },
                    onConfirm = { selected ->
                        WePrefs.putStringSet(KEY_HIDE_CONTACTS, selected)
                        showToast(context, "已保存，重启微信生效")
                        onDismiss()
                    }
                )
            }
        }
    }
}
