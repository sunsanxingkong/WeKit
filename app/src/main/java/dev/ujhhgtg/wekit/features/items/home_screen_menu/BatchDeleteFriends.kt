package dev.ujhhgtg.wekit.features.items.home_screen_menu

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
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
import dev.ujhhgtg.wekit.ui.content.GlobalImageLoader
import dev.ujhhgtg.wekit.ui.content.TextButton
import dev.ujhhgtg.wekit.ui.utils.PersonRemoveIcon
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog
import dev.ujhhgtg.wekit.utils.WeLogger
import dev.ujhhgtg.wekit.utils.android.getTopMostActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Feature(
    name = "批量删除好友",
    categories = ["首页右上角菜单"],
    description = "批量删除好友，支持全选/反选/搜索/头像显示/速率控制/三次确认"
)
object BatchDeleteFriends : SwitchFeature(), WeHomeScreenPopupMenuApi.IMenuItemsProvider {
    private val TAG = nameOf(BatchDeleteFriends::class)
    private var deleteMode by WePrefs.prefOption("batch_delete_friends_mode", 0)
    private var deleteInterval by WePrefs.prefOption("batch_delete_friends_interval", 1000)
    private val executor = Executors.newSingleThreadExecutor()

    override fun onEnable() { WeHomeScreenPopupMenuApi.addProvider(this) }
    override fun onDisable() { WeHomeScreenPopupMenuApi.removeProvider(this) }

    override fun getMenuItems(param: XC_MethodHook.MethodHookParam): List<WeHomeScreenPopupMenuApi.MenuItem> {
        return listOf(
            WeHomeScreenPopupMenuApi.MenuItem(777020, "批量删除好友", PersonRemoveIcon) {
                val ctx = getTopMostActivity() ?: return@MenuItem
                showComposeDialog(ctx) {
                    val friends = remember { WeDatabaseApi.getFriends() }
                    val sel = remember { mutableStateListOf<String>() }
                    var mode by remember { mutableIntStateOf(deleteMode) }
                    var interval by remember { mutableIntStateOf(deleteInterval) }
                    var phase by remember { mutableIntStateOf(0) }
                    var progress by remember { mutableIntStateOf(0) }
                    var total by remember { mutableIntStateOf(0) }
                    var failList by remember { mutableStateOf("") }
                    var searchQuery by remember { mutableStateOf("") }

                    val filteredFriends = remember(searchQuery, friends) {
                        if (searchQuery.isBlank()) friends
                        else friends.filter {
                            it.displayName.lowercase().contains(searchQuery.lowercase()) ||
                            it.wxId.lowercase().contains(searchQuery.lowercase()) ||
                            it.nickname.lowercase().contains(searchQuery.lowercase())
                        }
                    }

                    AlertDialogContent(
                        title = { Text("批量删除好友", fontWeight = FontWeight.Bold) },
                        text = {
                            when (phase) {
                                0 -> {
                                    Column(Modifier.size(360.dp, 480.dp)) {
                                        OutlinedTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            label = { Text("搜索好友") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(selected = mode == 0, onClick = { mode = 0 })
                                            Text("仅删除", fontSize = 13.sp)
                                            Spacer(Modifier.width(12.dp))
                                            RadioButton(selected = mode == 1, onClick = { mode = 1 })
                                            Text("拉黑+删除", fontSize = 13.sp)
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
                                        Text("已选 ${sel.size}/${filteredFriends.size}", fontSize = 12.sp, color = Color.Gray)
                                        LazyColumn(Modifier.weight(1f)) {
                                            items(filteredFriends, { it.wxId }) { f ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Checkbox(
                                                        checked = f.wxId in sel,
                                                        onCheckedChange = { c -> if (c) sel.add(f.wxId) else sel.remove(f.wxId) }
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    AsyncImage(
                                                        model = f.avatarUrl,
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.size(36.dp).clip(MaterialTheme.shapes.small),
                                                        imageLoader = GlobalImageLoader
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Column(Modifier.weight(1f)) {
                                                        Text(f.displayName.ifEmpty { f.nickname }, fontSize = 14.sp)
                                                        Text(f.wxId, fontSize = 11.sp, color = Color.Gray)
                                                    }
                                                }
                                            }
                                        }
                                        if (sel.isNotEmpty()) {
                                            Button(onClick = {
                                                deleteMode = mode; deleteInterval = interval
                                                phase = 1
                                            }, Modifier.fillMaxWidth()) { Text("下一步 (${sel.size})", fontSize = 13.sp) }
                                        }
                                    }
                                }
                                1 -> {
                                    val msgs = listOf(
                                        "⚠️ 第一次确认：即将删除 ${sel.size} 位好友",
                                        "⚠️ 第二次确认：操作不可逆！",
                                        "⚠️ 最终确认：即将执行"
                                    )
                                    var confirmStep by remember { mutableIntStateOf(1) }
                                    Column {
                                        Text(msgs.getOrElse(confirmStep - 1) { "" })
                                        Spacer(Modifier.height(12.dp))
                                        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                                            Button(onClick = {
                                                if (confirmStep >= 3) {
                                                    phase = 2
                                                    progress = 0; total = sel.size; failList = ""
                                                    startDeletion(ctx, friends.filter { it.wxId in sel }, deleteMode, deleteInterval) { done, totalCnt, fails ->
                                                        progress = done; total = totalCnt; failList = fails.take(3).joinToString(", ")
                                                        if (done >= totalCnt) phase = 3
                                                    }
                                                } else confirmStep++
                                            }) { Text("确认 (${confirmStep}/3)", fontSize = 13.sp) }
                                            TextButton(onClick = { phase = 0 }) { Text("取消", fontSize = 13.sp) }
                                        }
                                    }
                                }
                                2 -> {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                        Text("正在删除... $progress/$total", fontSize = 14.sp)
                                        Spacer(Modifier.height(8.dp))
                                        LinearProgressIndicator(progress = { if (total > 0) progress.toFloat() / total else 0f }, modifier = Modifier.fillMaxWidth())
                                        if (failList.isNotEmpty()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text("失败: $failList", fontSize = 11.sp, color = Color(0xFFF44336))
                                        }
                                    }
                                }
                                3 -> {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                        Text("✅ 删除完成！", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                        val failCount = failList.split(",").filter { it.isNotBlank() }.size
                                        Text("成功: ${total - failCount}/${total}", fontSize = 14.sp)
                                        if (failList.isNotEmpty()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text("失败: $failList", fontSize = 12.sp, color = Color(0xFFF44336))
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Button(onClick = onDismiss) { Text("关闭", fontSize = 13.sp) }
                                    }
                                }
                            }
                        }
                    )
                }
            }
        )
    }

    // 用独立线程执行，避免阻塞 Dispatchers.IO（sendCgi 内部也用 IO 线程池）
    private fun startDeletion(ctx: Context, targets: List<WeContact>, mode: Int, intervalMs: Int, onProgress: (Int, Int, List<String>) -> Unit) {
        executor.execute {
            var done = 0
            val fails = mutableListOf<String>()
            for (t in targets) {
                val latch = CountDownLatch(1)
                var ok = false
                try {
                    val body = if (mode == 0) """{"2":"${t.wxId.replace("'","''")}","4":1}"""
                        else """{"2":"${t.wxId.replace("'","''")}","4":3}"""
                    WeLogger.i(TAG, "deleting ${t.wxId} mode=$mode")
                    WePacketHelper.sendCgi("/cgi-bin/micromsg-bin/deletecontact", 376, 0, 0, body) {
                        onSuccess { json, _ ->
                            WeLogger.i(TAG, "delete ${t.wxId} success")
                            ok = true; latch.countDown()
                        }
                        onFailure { errType, errCode, errMsg ->
                            WeLogger.w(TAG, "delete ${t.wxId} failed: $errType,$errCode,$errMsg")
                            ok = false; latch.countDown()
                        }
                    }
                    latch.await(15, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    WeLogger.e(TAG, "delete ${t.wxId} error", e)
                    latch.countDown()
                }
                if (!ok) fails.add(t.displayName.ifEmpty { t.wxId })
                done++
                onProgress(done, targets.size, fails.toList())
                try { Thread.sleep(intervalMs.toLong()) } catch (_: InterruptedException) { break }
            }
        }
    }
}