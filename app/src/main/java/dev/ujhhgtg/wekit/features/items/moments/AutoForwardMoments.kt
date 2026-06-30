package dev.ujhhgtg.wekit.features.items.moments

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ujhhgtg.comptime.This
import dev.ujhhgtg.wekit.features.api.core.WeDatabaseApi
import dev.ujhhgtg.wekit.features.api.ui.WeMomentsApi
import dev.ujhhgtg.wekit.features.core.ClickableFeature
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.preferences.WePrefs
import dev.ujhhgtg.wekit.ui.content.AlertDialogContent
import dev.ujhhgtg.wekit.ui.content.Button
import dev.ujhhgtg.wekit.ui.content.TextButton
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog
import dev.ujhhgtg.wekit.utils.WeLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 自动转发朋友圈
 * 配置好友 + 关键词，后台定时扫描 SnsInfo 表
 */
@Feature(
    name = "自动转发朋友圈",
    categories = ["朋友圈"],
    description = "监控指定好友朋友圈，当出现关键词时自动转发（支持好友多选、自定义时段）"
)
object AutoForwardMoments : ClickableFeature() {
    override val noSwitchWidget = true
    private val TAG = This.Class.simpleName

    private var targetWxIds by WePrefs.prefOption("auto_fwd_moments_targets", "")
    private var keywords by WePrefs.prefOption("auto_fwd_moments_keywords", "")
    private var detectIntervalSec by WePrefs.prefOption("auto_fwd_moments_interval", 30)
    private var timeMode by WePrefs.prefOption("auto_fwd_moments_time_mode", 0)
    private var startHour by WePrefs.prefOption("auto_fwd_moments_start_hr", 1)
    private var endHour by WePrefs.prefOption("auto_fwd_moments_end_hr", 23)

    private var monitoringJob: Job? = null
    private var isRunning by mutableStateOf(false)
    private var lastMaxRowId = 0L
    private val forwardedSet = mutableSetOf<String>()

    private fun getTargetList(): List<String> = targetWxIds.split(",").filter { it.isNotBlank() }

    override fun onEnable() {
        if (getTargetList().isNotEmpty() && keywords.isNotBlank()) { startMonitor() }
    }
    override fun onDisable() { stopMonitor() }

    private fun startMonitor() {
        if (monitoringJob?.isActive == true) return
        isRunning = true
        lastMaxRowId = 0L
        monitoringJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    if (isInTimeWindow()) checkAndForward()
                } catch (_: Exception) { }
                delay(detectIntervalSec * 1000L)
            }
        }
    }

    private fun stopMonitor() {
        monitoringJob?.cancel()
        monitoringJob = null
        isRunning = false
    }

    private fun isInTimeWindow(): Boolean {
        if (timeMode == 0) return true
        val cal = java.util.Calendar.getInstance()
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        return if (startHour <= endHour) hour in startHour until endHour else hour >= startHour || hour < endHour
    }

    private fun checkAndForward() {
        val targets = getTargetList()
        val kwList = keywords.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }
        if (targets.isEmpty() || kwList.isEmpty()) return

        // 直接查 SnsInfo 表（微信朋友圈本地 SQLite 表）
        val sql = "SELECT rowid, snsId, userName, content FROM SnsInfo WHERE rowid > $lastMaxRowId ORDER BY rowid ASC LIMIT 50"
        val rows = WeDatabaseApi.executeQuery(sql)
        if (rows.isEmpty()) return
        val newMax = rows.maxOfOrNull { (it["rowid"] as? Number)?.toLong() ?: 0L } ?: return
        lastMaxRowId = newMax

        for (row in rows) {
            try {
                val userName = row["userName"] as? String ?: continue
                val content = row["content"] as? ByteArray ?: continue
                if (userName !in targets) continue
                val contentText = try {
                    String(content, Charsets.UTF_8).substringBefore("\u0000")
                } catch (_: Exception) { null }
                if (contentText.isNullOrBlank()) continue
                val dedup = "$userName:$contentText"
                if (dedup in forwardedSet) continue
                if (!kwList.any { contentText.contains(it, ignoreCase = true) }) continue

                WeLogger.i(TAG, "matched! $userName: ${contentText.take(60)}")
                forwardedSet.add(dedup)
                if (forwardedSet.size > 500) forwardedSet.removeAll(forwardedSet.take(250).toSet())
                // 转发（用 WeMomentsApi.uploadText）
                WeMomentsApi.uploadText(contentText)
            } catch (_: Exception) { }
        }
    }

    override fun onClick(context: Context) {
        showComposeDialog(context) {
            val friends = remember { WeDatabaseApi.getFriends() }
            val selected = remember { mutableStateListOf<String>().apply { addAll(targetWxIds.split(",").filter { it.isNotBlank() }) } }
            var keywordsStr by remember { mutableStateOf(keywords) }
            var interval by remember { mutableIntStateOf(detectIntervalSec) }
            var mode by remember { mutableIntStateOf(timeMode) }
            var sHour by remember { mutableIntStateOf(startHour) }
            var eHour by remember { mutableIntStateOf(endHour) }
            var showPicker by remember { mutableStateOf(false) }

            if (showPicker) {
                AlertDialogContent(
                    title = { Text("选择监控好友", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(Modifier.size(340.dp, 440.dp)) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                                TextButton(onClick = { selected.clear(); selected.addAll(friends.map { it.wxId }) }) { Text("全选", fontSize = 13.sp) }
                                TextButton(onClick = { selected.clear() }) { Text("清空", fontSize = 13.sp) }
                                Text("已选 ${selected.size}/${friends.size}", fontSize = 13.sp, color = Color.Gray)
                            }
                            HorizontalDivider()
                            LazyColumn(Modifier.weight(1f)) {
                                items(friends, { it.wxId }) { f ->
                                    ListItem(
                                        headlineContent = { Text(f.displayName.ifEmpty { f.nickname }, fontSize = 14.sp) },
                                        supportingContent = { Text(f.wxId, fontSize = 11.sp, color = Color.Gray) },
                                        leadingContent = {
                                            Checkbox(checked = f.wxId in selected,
                                                onCheckedChange = { c -> if (c) selected.add(f.wxId) else selected.remove(f.wxId) },
                                                colors = CheckboxDefaults.colors()
                                            )
                                        }
                                    )
                                }
                            }
                            Button(onClick = { showPicker = false }) { Text("确定", fontSize = 13.sp) }
                        }
                    }
                )
            } else {
                AlertDialogContent(
                    title = { Text("自动转发朋友圈", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(Modifier.size(360.dp, 460.dp).verticalScroll(rememberScrollState())) {
                            Text("监控好友（${selected.size}人）", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Button(onClick = { showPicker = true }) { Text("选择好友...", fontSize = 13.sp) }
                            if (selected.isNotEmpty()) Text(selected.take(3).joinToString(", "), fontSize = 11.sp, color = Color.Gray, maxLines = 1)
                            Spacer(Modifier.height(12.dp))
                            Text("关键词", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            OutlinedTextField(value = keywordsStr, onValueChange = { keywordsStr = it }, placeholder = { Text("如: 优惠,活动") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(12.dp))
                            Text("检测间隔: ${interval}s", fontSize = 14.sp)
                            Slider(value = interval.toFloat(), onValueChange = { interval = it.toInt() }, valueRange = 10f..600f, steps = 59)
                            Spacer(Modifier.height(12.dp))
                            Text("检测时间", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = mode == 0, onClick = { mode = 0 }) { Text("全天", fontSize = 14.sp) }
                                Spacer(Modifier.width(16.dp))
                                RadioButton(selected = mode == 1, onClick = { mode = 1 }) { Text("仅时段", fontSize = 14.sp) }
                            }
                            if (mode == 1) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(value = sHour.toString(), onValueChange = { it.toIntOrNull()?.let { sHour = it.coerceIn(0, 23) } }, label = { Text("起始时") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(72.dp))
                                    Text("~", fontSize = 14.sp)
                                    OutlinedTextField(value = eHour.toString(), onValueChange = { it.toIntOrNull()?.let { eHour = it.coerceIn(0, 23) } }, label = { Text("结束时") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(72.dp))
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("运行状态: ", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(if (isRunning) "🟢 运行中" else "🔴 已停止", fontSize = 14.sp, color = if (isRunning) Color(0xFF4CAF50) else Color(0xFFF44336))
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            targetWxIds = selected.joinToString(",")
                            keywords = keywordsStr
                            detectIntervalSec = interval
                            timeMode = mode
                            startHour = sHour
                            endHour = eHour
                            stopMonitor()
                            if (selected.isNotEmpty() && keywordsStr.isNotBlank()) startMonitor()
                            isRunning = monitoringJob?.isActive == true
                            Toast.makeText(context, if (isRunning) "✅ 已启动" else "配置保存", Toast.LENGTH_SHORT).show()
                        }) { Text("保存并启动", fontSize = 14.sp) }
                    },
                    dismissButton = { TextButton(onClick = onDismiss) { Text("关闭", fontSize = 14.sp) } }
                )
            }
        }
    }
}