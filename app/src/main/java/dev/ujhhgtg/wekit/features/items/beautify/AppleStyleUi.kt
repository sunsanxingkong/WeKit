package dev.ujhhgtg.wekit.features.items.beautify

import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.features.core.SwitchFeature

@Feature(
    name = "iOS风格界面",
    categories = ["美化"],
    description = "启用Apple风格底部导航栏和设置面板"
)
object AppleStyleUi : SwitchFeature() {
    override fun onEnable() {}
}