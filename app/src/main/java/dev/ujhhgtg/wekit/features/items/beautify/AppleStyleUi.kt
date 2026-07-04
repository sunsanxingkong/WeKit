package dev.ujhhgtg.wekit.features.items.beautify

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import dev.ujhhgtg.wekit.dexkit.abc.IResolveDex
import dev.ujhhgtg.wekit.dexkit.dsl.dexMethod
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.features.core.SwitchFeature
import dev.ujhhgtg.wekit.utils.WeLogger

@Feature(
    name = "iOS风格界面",
    categories = ["美化"],
    description = "启用Apple风格底部导航栏圆角和磨砂效果"
)
object AppleStyleUi : SwitchFeature(), IResolveDex {
    private val methodBottomNavInit by dexMethod(allowFailure = true) {
        matcher {
            usingStrings("MicroMsg.LauncherUI.BottomNavigationView")
        }
    }

    override fun onEnable() {
        methodBottomNavInit.hookAfter {
            try {
                val view = thisObject as? View ?: return@hookAfter
                val density = view.resources.displayMetrics.density
                val bg = GradientDrawable().apply {
                    cornerRadius = 24f * density
                    setColor(Color.argb(240, 246, 246, 246))
                }
                view.background = bg
                view.elevation = 8f * density
                if (view is ViewGroup) {
                    for (i in 0 until view.childCount) {
                        val child = view.getChildAt(i)
                        if (child is TextView) {
                            child.gravity = Gravity.CENTER
                            child.setTextColor(Color.argb(180, 0, 0, 0))
                        }
                    }
                }
            } catch (e: Exception) {
                WeLogger.e("AppleStyleUi", "hook failed", e)
            }
        }
    }
}
