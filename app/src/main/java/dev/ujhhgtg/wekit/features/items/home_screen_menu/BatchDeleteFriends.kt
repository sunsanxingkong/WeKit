package dev.ujhhgtg.wekit.features.items.home_screen_menu

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robv.android.xposed.XC_MethodHook
import dev.ujhhgtg.comptime.nameOf
import dev.ujhhgtg.wekit.features.api.core.WeDatabaseApi
import dev.ujhhgtg.wekit.features.api.core.models.WeContact
import dev.ujhhgtg.wekit.features.api.net.WePacketHelper
import dev.ujhhgtg.wekit.features.api.ui.WeHomeScreenPopupMenuApi
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.features.core.SwitchFeature
import dev.ujhhgtg.wekit.preferences.WePrefs
import dev.ujhhgtg.wekit.ui.content.AlertDialogContent
import dev.ujhhgtg.wekit.ui.content.Button
import dev.ujhhgtg.wekit.ui.content.TextButton
import dev.ujhhgtg.wekit.ui.utils.PersonRemoveIcon
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog
import dev.ujhhgtg.wekit.utils.WeLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 批量删除好友
 * 在首页右上角 ⊕ 菜单提供入口
 */
@Feature(
    name = "批量删除好友",
    categories = ["首页右上角菜单"],
    description = "批量删除好友，支持全选/反选/速率控制/三次确认"
)
object BatchDeleteFriends : SwitchFeature(), WeHomeScreenPopupMenuApi.IMenuItemsProvider {
    private val TAG = nameOf(BatchDeleteFriends::class)
    private var deleteMode by WePrefs.prefOption("batch_delete_friends_mode", 0)
    private var deleteInterval by WePrefs.prefOption("batch_delete_friends_interval", 1000)

    // ── 阶段 ──
    private sealed class Phase {
        data object Idle : Phase()
        data class Picking(val mode: Int, val interval: Int) : Phase()
        data class Confirming(val targets: List<WeContact>, val step: Int) : Phase()
        data class Running(val total: Int, val done: Int, val failed: MutableList<String>) : Phase()
        data class Finished(val total: Int, val success: Int, val failed: List<String>) : Phase()
    }
    private val phase = mutableStateOf<Phase>(Phase.Idle)

    override fun onEnable() { WeHomeScreenPopupMenuApi.addProvider(this) }
    override fun onDisable() { WeHomeScreenPopupMenuApi.removeProvider(this) }

    override fun getMenuItems(param: XC_MethodHook.MethodHookParam): List<WeHomeScreenPopupMenuApi.MenuItem> {
        return listOf(
            WeHomeScreenPopupMenuApi.MenuItem(777020, "批量删除好友", PersonRemoveIcon) {
                val ctx = try { (param.thisObject as? android.app.Activity) ?: return@MenuItem } catch (_: Exception) { return@MenuItem }
                phase.value = Phase.Idle
                showComposeDialog(ctx) {
                    when (val p = phase.value) {
                        is Phase.Idle -> PickerUI(ctx)
                        is Phase.Picking -> PickerUI(ctx)
                        is Phase.Confirming -> ConfirmUI(ctx, p as Phase.Confirming)
                        is Phase.Running -> ProgressUI(ctx, p as Phase.Running)
                        is Phase.Finished -> ResultUI(ctx, p as Phase.Finished)
                    }
                }
            }
        )
    }

    @Composable
    private fun PickerUI(ctx: Context) {
        val friends = remember { WeDatabaseApi.getFriends() }
        val sel = mutableStateListOf<String>()
        var mode by remember { mutableIntStateOf(deleteMode) }
        var interval by remember { mutableIntStateOf(deleteInterval) }

        AlertDialogContent(
            title = { Text("批量删除好友", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.size(340.dp, 440.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == 0, onClick = { mode = 0 }) { Text("仅删除", fontSize = 13.sp) }
                        Spacer(Modifier.width(12.dp))
                        RadioButton(selected = mode == 1, onClick = { mode = 1 }) { Text("拉黑+删除", fontSize = 13.sp) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("间隔: ${interval}ms", fontSize = 13.sp)
                        Slider(value = interval.toFloat(), onValueChange = { interval = it.toInt() }, valueRange = 200f..5000f, steps = 23, modifier = Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                        TextButton(onClick = { sel.clear(); sel.addAll(friends.map { it.wxId }) }) { Text("全选", fontSize = 13.sp) }
                        TextButton(onClick = { val s = sel.toSet(); sel.clear(); sel.addAll(friends.filter { it.wxId !in s }.map { it.wxId }) }) { Text("反选", fontSize = 13.sp) }
                        TextButton(onClick = { sel.clear() }) { Text("清空", fontSize = 13.sp) }
                    }
                    HorizontalDivider()
                    Text("已选 ${sel.size}/${friends.size}", fontSize = 12.sp, color = Color.Gray)
                    LazyColumn(Modifier.weight(1f)) {
                        items(friends, { it.wxId }) { f ->
                            ListItem(
                                headlineContent = { Text(f.displayName.ifEmpty { f.nickname }, fontSize = 14.sp) },
                                supportingContent = { Text(f.wxId, fontSize = 11.sp, color = Color.Gray) },
                                leadingContent = {
                                    Checkbox(checked = f.wxId in sel,
                                        onCheckedChange = { c -> if (c) sel.add(f.wxId) else sel.remove(f.wxId) },
                                        colors = CheckboxDefaults.colors()
                                    )
                                }
                            )
                        }
                    }
                    Button(onClick = {
                        if (sel.isEmpty()) { Toast.makeText(ctx, "请至少选一个", Toast.LENGTH_SHORT).show(); return@Button }
                        deleteMode = mode; deleteInterval = interval
                        val targets = friends.filter { it.wxId in sel }
                        phase.value = Phase.Confirming(targets, 1)
                    }) { Text("下一步", fontSize = 13.sp) }
                }
            }
        )
    }

    @Composable
    private fun ConfirmUI(ctx: Context, cp: Phase.Confirming) {
        val step = cp.step
        val msgs = listOf(
            "⚠️ 第一确认：即将删除 ${cp.targets.size} 位好友",
            "⚠️ 第二确认：操作不可逆！",
            "⚠️ 最终确认：即将执行，确认继续？"
        )
        AlertDialogContent(
            title = { Text(listOf("第一", "第二", "最终")[step - 1] + "次确认", fontWeight = FontWeight.Bold) },
            text = { Text(msgs.getOrElse(step - 1) { "确认?" }) },
            confirmButton = {
                Button(onClick = {
                    if (step >= 3) {
                        val fails = mutableListOf<String>()
                        phase.value = Phase.Running(cp.targets.size, 0, fails)
                        startDeletion(ctx, cp.targets, deleteMode, deleteInterval, fails)
                    } else {
                        phase.value = cp.copy(step = step + 1)
                    }
                }) { Text("确认", fontSize = 13.sp) }
            },
            dismissButton = { TextButton(onClick = { phase.value = Phase.Idle }) { Text("取消", fontSize = 13.sp) } }
        )
    }

    @Composable
    private fun ProgressUI(ctx: Context, rp: Phase.Running) {
        val progress = if (rp.total > 0) rp.done.toFloat() / rp.total else 0f
        AlertDialogContent(
            title = { Text("正在删除...", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("进度: ${rp.done}/${rp.total}")
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    if (rp.failed.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("失败: ${rp.failed.size}", color = Color.Red, fontSize = 12.sp)
                    }
                }
            }
        )
    }

    @Composable
    private fun ResultUI(ctx: Context, fp: Phase.Finished) {
        AlertDialogContent(
            title = { Text("删除完成", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("总计: ${fp.total} | 成功: ${fp.success} | 失败: ${fp.failed.size}")
                    if (fp.failed.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("失败:")
                        fp.failed.forEach { Text("  • $it", fontSize = 12.sp) }
                    }
                }
            },
            confirmButton = { Button(onClick = { phase.value = Phase.Idle }) { Text("关闭", fontSize = 13.sp) } }
        )
    }

    private fun startDeletion(ctx: Context, targets: List<WeContact>, mode: Int, intervalMs: Int, fails: MutableList<String>) {
        CoroutineScope(Dispatchers.IO).launch {
            var done = 0
            for (t in targets) {
                try {
                    val body = if (mode == 0) """{"2":"${t.wxId}","4":1}""" else """{"2":"${t.wxId}","4":3}"""
                    var ok = false
                    WePacketHelper.sendCgi("/cgi-bin/micromsg-bin/deletecontact", 376, 0, 0, body) {
                        onSuccess { ok = true }
                        onFailure { ok = false }
                    }
                    if (!ok) fails.add(t.displayName.ifEmpty { t.wxId })
                } catch (e: Exception) {
                    WeLogger.e(TAG, "delete ${t.wxId} failed", e)
                    fails.add(t.displayName.ifEmpty { t.wxId })
                }
                done++
                phase.value = Phase.Running(targets.size, done, fails)
                delay(intervalMs.toLong())
            }
            phase.value = Phase.Finished(targets.size, targets.size - fails.size, fails.toList())
        }
    }
}