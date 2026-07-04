package dev.ujhhgtg.wekit.features.items.contacts

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import dev.ujhhgtg.wekit.dexkit.abc.IResolveDex
import dev.ujhhgtg.wekit.dexkit.dsl.dexMethod
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.features.core.SwitchFeature
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog
import dev.ujhhgtg.wekit.ui.content.AlertDialogContent
import dev.ujhhgtg.wekit.ui.content.Button
import dev.ujhhgtg.wekit.ui.content.TextButton
import dev.ujhhgtg.wekit.utils.android.showToast
import androidx.compose.material3.Text

@Feature(
    name = "密友消息振动",
    categories = ["联系人"],
    description = "收到密友消息时触发特殊振动提醒"
)
object FriendMessageVibrate : SwitchFeature() {
    override fun onEnable() {}
}