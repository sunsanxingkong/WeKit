package dev.ujhhgtg.wekit.features.items.moments

import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.features.core.SwitchFeature

@Feature(
    name = "密友朋友圈隐藏",
    categories = ["朋友圈"],
    description = "在朋友圈中隐藏指定密友的内容"
)
object FriendHideMoments : SwitchFeature() {
    override fun onEnable() {}
}