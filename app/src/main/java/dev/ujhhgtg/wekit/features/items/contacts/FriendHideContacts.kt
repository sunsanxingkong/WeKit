package dev.ujhhgtg.wekit.features.items.contacts

import android.content.Context
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.features.core.SwitchFeature
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog
import dev.ujhhgtg.wekit.ui.content.AlertDialogContent
import dev.ujhhgtg.wekit.ui.content.Button
import dev.ujhhgtg.wekit.ui.content.TextButton
import androidx.compose.material3.Text
import dev.ujhhgtg.wekit.utils.android.showToast

@Feature(
    name = "密友联系人隐藏",
    categories = ["联系人"],
    description = "在通讯录列表中隐藏指定密友"
)
object FriendHideContacts : SwitchFeature() {
    override fun onEnable() {}
}