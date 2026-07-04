package dev.ujhhgtg.wekit.features.items.contacts

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.ujhhgtg.wekit.dexkit.abc.IResolveDex
import dev.ujhhgtg.wekit.dexkit.dsl.dexMethod
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.features.core.SwitchFeature
import dev.ujhhgtg.wekit.preferences.WePrefs
import dev.ujhhgtg.wekit.ui.content.AlertDialogContent
import dev.ujhhgtg.wekit.ui.content.Button
import dev.ujhhgtg.wekit.ui.content.TextButton
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog
import dev.ujhhgtg.wekit.utils.WeLogger
import dev.ujhhgtg.wekit.utils.android.showToast

@Feature(
    name = "密友消息振动",
    categories = ["联系人"],
    description = "收到密友消息时触发特殊振动提醒"
)
object FriendMessageVibrate : SwitchFeature(), IResolveDex {
    private const val KEY_VIBRATE_CONTACTS = "friend_vibrate_contacts"
    private const val KEY_DURATION = "friend_vibrate_duration"

    private val methodInsertMsg by dexMethod(allowFailure = true) {
        matcher {
            usingStrings("MicroMsg.InsertMsg", "insert msg")
        }
    }

    override fun onBeforeToggle(newState: Boolean, context: Context): Boolean {
        if (newState) {
            showConfigDialog(context)
        }
        return true
    }

    override fun onEnable() {
        val contacts = WePrefs.getStringSetOrDef(KEY_VIBRATE_CONTACTS, emptySet())
        if (contacts.isEmpty()) {
            WeLogger.i("FriendMessageVibrate", "no contacts configured")
            return
        }
        methodInsertMsg.hookAfter {
            try {
                val isSend = args.getOrNull(0)
                if (isSend != null) return@hookAfter
                val contentValues = thisObject
                val talkerField = contentValues.javaClass.getDeclaredField("talker")
                talkerField.isAccessible = true
                val talker = talkerField.get(contentValues) as? String
                if (talker != null && talker in contacts && !talker.contains("@chatroom")) {
                    vibrate()
                }
            } catch (e: Exception) {
                WeLogger.e("FriendMessageVibrate", "hook failed", e)
            }
        }
    }

    private fun vibrate() {
        try {
            val context = android.app.ActivityThread.currentApplication() ?: return
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            val duration = WePrefs.getStringOrDef(KEY_DURATION, "700").toLongOrNull() ?: 700L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(duration, -1),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        } catch (e: Exception) {
            WeLogger.e("FriendMessageVibrate", "vibrate failed", e)
        }
    }

    private fun showConfigDialog(context: Context) {
        showComposeDialog(context) {
            var contactsText by remember {
                mutableStateOf(WePrefs.getStringSetOrDef(KEY_VIBRATE_CONTACTS, emptySet()).joinToString(","))
            }
            var duration by remember { mutableStateOf(WePrefs.getStringOrDef(KEY_DURATION, "700")) }
            AlertDialogContent(
                title = { Text("密友消息振动配置") },
                text = {
                    Column {
                        TextField(
                            value = contactsText,
                            onValueChange = { contactsText = it },
                            label = { Text("密友wxid，逗号分隔") }
                        )
                        TextField(
                            value = duration,
                            onValueChange = { duration = it.filter { c -> c.isDigit() } },
                            label = { Text("振动时长(毫秒)") }
                        )
                    }
                },
                dismissButton = { TextButton(onDismiss) { Text("取消") } },
                confirmButton = {
                    Button(onClick = {
                        val set = contactsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                        WePrefs.putStringSet(KEY_VIBRATE_CONTACTS, set)
                        WePrefs.putString(KEY_DURATION, duration)
                        showToast(context, "配置已保存")
                        onDismiss()
                    }) { Text("保存") }
                }
            )
        }
    }
}
