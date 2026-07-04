package dev.ujhhgtg.wekit.features.items.contacts

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.ujhhgtg.wekit.features.api.core.WeDatabaseApi
import dev.ujhhgtg.wekit.features.core.ClickableFeature
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.preferences.WePrefs
import dev.ujhhgtg.wekit.ui.content.ContactsSelector
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog
import dev.ujhhgtg.wekit.utils.WeLogger
import dev.ujhhgtg.wekit.utils.android.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Feature(
    name = "密友消息振动",
    categories = ["联系人"],
    description = "选择密友，收到他们的消息时触发特殊振动"
)
object FriendMessageVibrate : ClickableFeature() {
    private const val KEY_VIBRATE_CONTACTS = "friend_vibrate_contacts"
    private const val KEY_DURATION = "friend_vibrate_duration"

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
            val current = WePrefs.getStringSetOrDef(KEY_VIBRATE_CONTACTS, emptySet())
            showComposeDialog(context) {
                ContactsSelector(
                    title = "选择振动提醒的密友",
                    contacts = friends,
                    initialSelectedWxIds = current,
                    onDismiss = { onDismiss() },
                    onConfirm = { selected ->
                        WePrefs.putStringSet(KEY_VIBRATE_CONTACTS, selected)
                        showToast(context, "已保存${selected.size}个密友")
                        onDismiss()
                    }
                )
            }
        }
    }
}
