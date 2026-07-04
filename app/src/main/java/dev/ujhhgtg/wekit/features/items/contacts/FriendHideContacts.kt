package dev.ujhhgtg.wekit.features.items.contacts

import android.content.Context
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
    name = "密友联系人隐藏",
    categories = ["联系人"],
    description = "在通讯录列表中隐藏指定密友"
)
object FriendHideContacts : SwitchFeature(), IResolveDex {
    private const val KEY_HIDE_CONTACTS = "friend_hide_contacts"

    private val methodGetContacts by dexMethod(allowFailure = true) {
        matcher {
            usingStrings("MicroMsg.ContactStorage", "getcontacts")
        }
    }

    override fun onBeforeToggle(newState: Boolean, context: Context): Boolean {
        if (newState) {
            showConfigDialog(context)
        }
        return true
    }

    override fun onEnable() {
        val hidden = WePrefs.getStringSetOrDef(KEY_HIDE_CONTACTS, emptySet())
        if (hidden.isEmpty()) {
            WeLogger.i("FriendHideContacts", "no contacts to hide")
            return
        }
        methodGetContacts.hookAfter {
            try {
                @Suppress("UNCHECKED_CAST")
                val list = result as? MutableList<Any> ?: return@hookAfter
                val iterator = list.iterator()
                while (iterator.hasNext()) {
                    val item = iterator.next()
                    val usernameField = item.javaClass.getDeclaredField("username")
                    usernameField.isAccessible = true
                    val username = usernameField.get(item) as? String
                    if (username != null && username in hidden) {
                        iterator.remove()
                    }
                }
            } catch (e: Exception) {
                WeLogger.e("FriendHideContacts", "hook failed", e)
            }
        }
    }

    private fun showConfigDialog(context: Context) {
        showComposeDialog(context) {
            var contactsText by remember {
                mutableStateOf(WePrefs.getStringSetOrDef(KEY_HIDE_CONTACTS, emptySet()).joinToString(","))
            }
            AlertDialogContent(
                title = { Text("密友联系人隐藏") },
                text = {
                    Column {
                        Text("输入要隐藏的密友wxid，逗号分隔")
                        TextField(
                            value = contactsText,
                            onValueChange = { contactsText = it },
                            label = { Text("密友wxid列表") }
                        )
                    }
                },
                dismissButton = { TextButton(onDismiss) { Text("取消") } },
                confirmButton = {
                    Button(onClick = {
                        val set = contactsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                        WePrefs.putStringSet(KEY_HIDE_CONTACTS, set)
                        showToast(context, "已保存，重启微信生效")
                        onDismiss()
                    }) { Text("保存") }
                }
            )
        }
    }
}
