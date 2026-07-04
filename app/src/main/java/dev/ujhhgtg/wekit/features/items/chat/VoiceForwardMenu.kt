package dev.ujhhgtg.wekit.features.items.chat

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.compose.material3.Text
import dev.ujhhgtg.reflekt.reflekt
import dev.ujhhgtg.comptime.This
import dev.ujhhgtg.wekit.dexkit.abc.IResolveDex
import dev.ujhhgtg.wekit.dexkit.dsl.dexMethod
import dev.ujhhgtg.wekit.features.api.core.WeMessageApi
import dev.ujhhgtg.wekit.features.api.ui.WeCurrentConversationApi
import dev.ujhhgtg.wekit.features.api.ui.WeChatMessageContextMenuApi
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.features.core.SwitchFeature
import dev.ujhhgtg.wekit.ui.content.AlertDialogContent
import dev.ujhhgtg.wekit.ui.content.Button
import dev.ujhhgtg.wekit.ui.content.TextButton
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog
import dev.ujhhgtg.wekit.utils.AudioUtils
import dev.ujhhgtg.wekit.utils.WeLogger
import dev.ujhhgtg.wekit.utils.android.showToast
import dev.ujhhgtg.wekit.utils.coerceToInt

@Feature(
    name = "语音消息转发",
    categories = ["聊天"],
    description = "在语音消息长按菜单中添加转发选项"
)
object VoiceForwardMenu : SwitchFeature() {
    override fun onEnable() {
        WeLogger.i("VoiceForwardMenu", "enabled")
    }
}