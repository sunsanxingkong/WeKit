package dev.ujhhgtg.wekit.ui.utils

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.Window
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as CColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import dev.ujhhgtg.wekit.utils.WeLogger
import dev.ujhhgtg.wekit.utils.android.getTopMostActivity

fun showComposeDialog(
    context: Context,
    directlyDismissable: Boolean = true,
    content: @Composable ShowComposeDialogScope.() -> Unit
) {
    val ctx = CommonContextWrapper.create(context)

    val dialog = Dialog(
        ctx,
        android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar_MinWidth
    )
    val lifecycleOwner = XposedLifecycleOwner.create()

    // 截取当前 Activity 画面作为液态玻璃背景
    val screenshot = runCatching {
        val activity = getTopMostActivity() ?: return@runCatching null
        val decorView = activity.window.decorView
        val bitmap = Bitmap.createBitmap(decorView.width, decorView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        decorView.draw(canvas)
        bitmap
    }.getOrNull().also {
        if (it == null) WeLogger.w("ComposeUtils", "screenshot failed, liquid glass disabled")
    }

    dialog.apply {
        window!!.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            requestFeature(Window.FEATURE_NO_TITLE)
        }

        setCancelable(directlyDismissable)
        val scope = ShowComposeDialogScope(ctx, this, window!!, ::dismiss)

        setContentView(
            ComposeView(ctx).apply {
                setLifecycleOwner(lifecycleOwner)
                setContent {
                    CompositionLocalProvider(LocalContext provides ctx) {
                        AppTheme {
                            if (screenshot != null) {
                                LiquidGlassBox(screenshot, scope, content)
                            } else {
                                Box(Modifier.fillMaxSize().wrapContentSize(Alignment.Center)) {
                                    scope.content()
                                }
                            }
                        }
                    }
                }
            }
        )

        window!!.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        setOnDismissListener { lifecycleOwner.onDestroy() }
        show()
    }
}

@Composable
private fun LiquidGlassBox(
    screenshot: Bitmap,
    scope: ShowComposeDialogScope,
    content: @Composable ShowComposeDialogScope.() -> Unit
) {
    val imageBitmap = remember(screenshot) { screenshot.asImageBitmap() }
    val backdrop = rememberCanvasBackdrop {
        drawImage(imageBitmap)
    }

    Box(Modifier.fillMaxSize()) {
        // 1. 截屏作为背景源（供 backdrop 截取）
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        // 2. 暗色遮罩
        Box(Modifier.fillMaxSize().background(CColor.Black.copy(alpha = 0.25f)))

        // 3. 玻璃效果的内容区域
        Box(
            modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(28.dp) },
                    effects = {
                        colorControls(brightness = 0.15f, saturation = 1.3f)
                        blur(24f)
                        lens(20f, 40f, depthEffect = true)
                    },
                    highlight = { Highlight.Plain },
                    onDrawSurface = {
                        drawRect(CColor.White.copy(alpha = 0.45f))
                    }
                )
        ) {
            // 内部内容保留原来外观
            Box(Modifier.fillMaxSize()) {
                scope.content()
            }
        }
    }
}

class ShowComposeDialogScope(
    val context: Context,
    val dialog: Dialog,
    val window: Window,
    val onDismiss: () -> Unit
)

fun View.setLifecycleOwner(lifecycleOwner: XposedLifecycleOwner) {
    apply {
        setViewTreeLifecycleOwner(lifecycleOwner)
        setViewTreeViewModelStoreOwner(lifecycleOwner)
        setViewTreeSavedStateRegistryOwner(lifecycleOwner)
    }
}