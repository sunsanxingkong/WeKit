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
import dev.ujhhgtg.wekit.features.api.core.models.WeGroup
import dev.ujhhgtg.wekit.features.api.net.WePacketHelper
import dev.ujhhgtg.wekit.features.api.ui.WeHomeScreenPopupMenuApi
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.features.core.SwitchFeature
import dev.ujhhgtg.wekit.preferences.WePrefs
import dev.ujhhgtg.wekit.ui.content.AlertDialogContent
import dev.ujhhgtg.wekit.ui.content.Button
import dev.ujhhgtg.wekit.ui.content.TextButton
import dev.ujhhgtg.wekit.ui.utils.GroupRemoveIcon
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog
import dev.ujhhgtg.wekit.utils.WeLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 批量退出群聊
 * 在首页右上角 ⊕ 菜单提供入口
 */
@Feature(
    name = "批量退出群聊",
    categories = ["首页右上角菜单"],
    description = "批量退出群聊，支持全选/反选/速率控制/三次确认"
)
object BatchQuitGroups : SwitchFeature(), WeHomeScreenPopupMenuApi.IMenuItemsProvider {
    private val TAG = nameOf(BatchQuitGroups::class)
    private var quitInterval by WePrefs.prefOption("batch_quit_groups_interval", 1500)

    private sealed class Phase {
        data object Idle : Phase()
        data class Picking(val interval: Int) : Phase()
        data class Confirming(val targets: List<WeGroup>, val step: Int) : Phase()
        data class Running(val total: Int, val done: Int, val failed: MutableList<String>) : Phase()
        data class Finished(val total: Int, val success: Int, val failed: List<String>) : Phase()
    }
    private val phase = mutableStateOf<Phase>(Phase.Idle)

    override fun onEnable() { WeHomeScreenPopupMenuApi.addProvider(this) }
    override fun onDisable() { WeHomeScreenPopupMenuApi.removeProvider(this) }

    override fun getMenuItems(param: XC_MethodHook.MethodHookParam): List<WeHomeScreenPopupMenuApi.MenuItem> {
        return listOf(
            WeHomeScreenPopupMenuApi.MenuItem(777021, "批量退出群聊", GroupRemoveIcon) {
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
        val groups = remember { WeDatabaseApi.getGroups() }
        val sel = mutableStateListOf<String>()
        var interval by remember { mutableIntStateOf(quitInterval) }

        AlertDialogContent(
            title = { Text("批量退出群聊", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.size(340.dp, 440.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("操作间隔: ${interval}ms", fontSize = 13.sp)
                        Slider(value = interval.toFloat(), onValueChange = { interval = it.toInt() }, valueRange = 500f..5000f, steps = 17, modifier = Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                        TextButton(onClick = { sel.clear(); sel.addAll(groups.map { it.wxId }) }) { Text("全选", fontSize = 13.sp) }
                        TextButton(onClick = { val cur = sel.toSet(); sel.clear(); sel.addAll(groups.map { it.wxId }.filter { it !in cur }) }) { Text("反选", fontSize = 13.sp) }
                        TextButton(onClick = { sel.clear() }) { Text("清空", fontSize = 13.sp) }
                    }
                    HorizontalDivider()
                    Text("已选 ${sel.size}/${groups.size}", fontSize = 12.sp, color = Color.Gray)
                    LazyColumn(Modifier.weight(1f)) {
                        items(groups, { it.wxId }) { g ->
                            ListItem(
                                headlineContent = { Text(g.nickname.ifEmpty { g.wxId }, fontSize = 14.sp) },
                                supportingContent = { Text(g.wxId, fontSize = 11.sp, color = Color.Gray) },
                                leadingContent = {
                                    Checkbox(checked = g.wxId in sel,
                                        onCheckedChange = { c -> if (c) sel.add(g.wxId) else sel.remove(g.wxId) },
                                        colors = CheckboxDefaults.colors()
                                    )
                                }
                            )
                        }
                    }
                    Button(onClick = {
                        if (sel.isEmpty()) { Toast.makeText(ctx, "请至少选一个群聊", Toast.LENGTH_SHORT).show(); return@Button }
                        quitInterval = interval
                        val targets = groups.filter { it.wxId in sel }
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
            "⚠️ 第一确认：即将退出 ${cp.targets.size} 个群聊",
            "⚠️ 第二确认：操作不可逆！退出后需重新邀请",
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
                        startQuit(ctx, cp.targets, quitInterval, fails)
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
            title = { Text("正在退出群聊...", fontWeight = FontWeight.Bold) },
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
            title = { Text("退出完成", fontWeight = FontWeight.Bold) },
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

    private fun startQuit(ctx: Context, targets: List<WeGroup>, intervalMs: Int, fails: MutableList<String>) {
        CoroutineScope(Dispatchers.IO).launch {
            var done = 0
            for (t in targets) {
                try {
                    val body = """{"2":"${t.wxId}"}"""
                    var ok = false
                    WePacketHelper.sendCgi("/cgi-bin/micromsg-bin/quitchatroom", 343, 0, 0, body) {
                        onSuccess { ok = true }
                        onFailure { ok = false }
                    }
                    if (!ok) fails.add(t.nickname.ifEmpty { t.wxId })
                } catch (e: Exception) {
                    WeLogger.e(TAG, "quit ${t.wxId} failed", e)
                    fails.add(t.nickname.ifEmpty { t.wxId })
                }
                done++
                phase.value = Phase.Running(targets.size, done, fails)
                delay(intervalMs.toLong())
            }
            phase.value = Phase.Finished(targets.size, targets.size - fails.size, fails.toList())
        }
    }
}