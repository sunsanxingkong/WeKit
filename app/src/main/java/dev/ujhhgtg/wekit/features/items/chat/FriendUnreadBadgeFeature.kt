package dev.ujhhgtg.wekit.features.items.chat

import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.features.core.SwitchFeature

@Feature(
    name = "密友未读角标",
    categories = ["聊天", "联系人"],
    description = "为密友消息显示特殊未读角标"
)
object FriendUnreadBadgeFeature : SwitchFeature() {
    override fun onEnable() {}
}