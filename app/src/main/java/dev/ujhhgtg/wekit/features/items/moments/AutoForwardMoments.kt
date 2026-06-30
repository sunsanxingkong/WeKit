package dev.ujhhgtg.wekit.features.items.moments

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
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
 *
 * 实现方案：用 WeDatabaseApi 直接 SQL 查询 SnsInfo 表，
 * 通过关键词匹配好友内容后自动转发
 */
@Feature(
    name = "自动转发朋友圈",
    categories = ["朋友圈"],
    description = "监控指定好友朋友圈，当出现关键词时自动转发（支持好友多选、自定义时段）"
)
object AutoForwardMoments : ClickableFeature() {
    override val noSwitchWidget = true
    private val TAG = This.Class.simpleName

    // ── 配置项 ──
    private var targetWxIds by WePrefs.prefOption("auto_fwd_moments_targets", "")
    private var keywords by WePrefs.prefOption("auto_fwd_moments_keywords", "")
    private var detectIntervalSec by WePrefs.prefOption("auto_fwd_moments_interval", 30)
    private var timeMode by WePrefs.prefOption("auto_fwd_moments_time_mode", 0)
    private var startHour by WePrefs.prefOption("auto_fwd_moments_start_hr", 1)
    private var endHour by WePrefs.prefOption("auto_fwd_moments_end_hr", 23)

    // ── 运行时状态 ──
    private var monitoringJob: Job? = null
    private val forwardedSet = mutableSetOf<String>()
    private var isRunning by mutableStateOf(false)
    private var lastMaxRowId = 0L

    private fun getTargetList(): List<String> =
        targetWxIds.split(",").filter { it.isNotBlank() }

    override fun onEnable() {
        if (getTargetList().isNotEmpty() && keywords.isNotBlank()) {
            startMonitoring()
        }
    }

    override fun onDisable() {
        stopMonitoring()
    }

    private fun startMonitoring() {
        if (monitoringJob?.isActive == true) return
        isRunning = true
        forwardedSet.clear()
        lastMaxRowId = 0L
        monitoringJob = CoroutineScope(Dispatchers.IO).launch {
            WeLogger.i(TAG, "auto-forward monitoring started")
            while (isActive) {
                try {
                    if (isInTimeWindow()) scanAndForward()
                } catch (_: Exception) { WeLogger.w(TAG, "scan err: ${_.message}") }
                delay(detectIntervalSec * 1000L)
            }
        }
    }

    private fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        isRunning = false
    }

    private fun isInTimeWindow(): Boolean {
        if (timeMode == 0) return true
        val cal = java.util.Calendar.getInstance()
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        return if (startHour <= endHour) hour in startHour until endHour
        else hour >= startHour || hour < endHour
    }

    /**
     * 核心扫描 —— 直接用 SQL 查 SnsInfo 表
     */
    private fun scanAndForward() {
        val targets = getTargetList()
        val kwList = keywords.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }
        if (targets.isEmpty() || kwList.isEmpty()) return

        val sql = """
            SELECT rowid, snsId, userName, content, createTime
            FROM SnsInfo
            WHERE rowid > $lastMaxRowId
            ORDER BY rowid ASC
            LIMIT 100
        """.trimIndent()

        val rows = WeDatabaseApi.executeQuery(sql)
        if (rows.isEmpty()) return

        val maxRowId = rows.maxOfOrNull {
            (it["rowid"] as? Number)?.toLong() ?: 0L
        } ?: return
        lastMaxRowId = maxRowId

        for (row in rows) {
            try {
                val snsId = (row["snsId"] as? Number)?.toLong() ?: continue
                val userName = row["userName"] as? String ?: continue
                val contentBlob = row["content"] as? ByteArray ?: continue

                if (userName !in targets) continue

                val contentText = try {
                    kotlinx.serialization.protobuf.ProtoBuf
                        .decodeFromByteArray<dev.ujhhgtg.wekit.features.api.net.models.protobuf.TimelineObjectProto>(contentBlob)
                        .contentDesc
                } catch (_: Exception) { null }
                if (contentText.isNullOrBlank()) continue

                val dedupKey = "$userName:$contentText"
                if (dedupKey in forwardedSet) continue

                if (!kwList.any { contentText.contains(it, ignoreCase = true) }) continue

                WeLogger.i(TAG, "matched! $userName: ${contentText.take(60)}")
                if (WeMomentsApi.uploadText(contentText)) {
                    forwardedSet.add(dedupKey)
                    if (forwardedSet.size > 500) forwardedSet.removeAll(forwardedSet.take(250).toSet())
                }
            } catch (_: Exception) { }
        }
    }

    override fun onClick(context: Context) {
        showComposeDialog(context) {
            val friends = remember { WeDatabaseApi.getFriends() }
            val saved = remember { AutoForwardMoments.targetWxIds }
            val selected = remember {
                mutableStateListOf<String>().apply {
                    addAll(saved.split(",").filter { it.isNotBlank() })
                }
            }
            var keywordsStr by remember { mutableStateOf(AutoForwardMoments.keywords) }
            var interval by remember { mutableIntStateOf(AutoForwardMoments.detectIntervalSec) }
            var mode by remember { mutableIntStateOf(AutoForwardMoments.timeMode) }
            var sHour by remember { mutableIntStateOf(AutoForwardMoments.startHour) }
            var eHour by remember { mutableIntStateOf(AutoForwardMoments.endHour) }
            var running by remember { mutableStateOf(AutoForwardMoments.isRunning) }
            var showFriendPicker by remember { mutableStateOf(false) }

            if (showFriendPicker) {
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
                                        headlineContent = { Text(f.nickname, fontSize = 14.sp) },
                                        supportingContent = { Text(f.wxId, fontSize = 11.sp, color = Color.Gray) },
                                        leadingContent = {
                                            Checkbox(
                                                checked = f.wxId in selected,
                                                onCheckedChange = { c -> if (c) selected.add(f.wxId) else selected.remove(f.wxId) },
                                                colors = CheckboxDefaults.colors()
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = { Button(onClick = { showFriendPicker = false }) { Text("确定", fontSize = 14.sp) } },
                    dismissButton = { TextButton(onClick = { showFriendPicker = false }) { Text("取消", fontSize = 14.sp) } }
                )
            } else {
                AlertDialogContent(
                    title = { Text("自动转发朋友圈", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(Modifier.size(360.dp, 460.dp).verticalScroll(rememberScrollState())) {
                            Text("监控好友（${selected.size}人）", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Button(onClick = { showFriendPicker = true }) { Text("选择好友...", fontSize = 13.sp) }
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
                                    Spacer(Modifier.width(4.dp))
                                    Text("~", fontSize = 14.sp)
                                    Spacer(Modifier.width(4.dp))
                                    OutlinedTextField(value = eHour.toString(), onValueChange = { it.toIntOrNull()?.let { eHour = it.coerceIn(0, 23) } }, label = { Text("结束时") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(72.dp))
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("运行状态: ", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(if (running) "🟢 运行中" else "🔴 已停止", fontSize = 14.sp, color = if (running) Color(0xFF4CAF50) else Color(0xFFF44336))
                            }
                        }
                    },
                    confirmButton = { Button(onClick = {
                        AutoForwardMoments.targetWxIds = selected.joinToString(",")
                        AutoForwardMoments.keywords = keywordsStr
                        AutoForwardMoments.detectIntervalSec = interval
                        AutoForwardMoments.timeMode = mode
                        AutoForwardMoments.startHour = sHour
                        AutoForwardMoments.endHour = eHour
                        AutoForwardMoments.stopMonitoring()
                        if (selected.isNotEmpty() && keywordsStr.isNotBlank()) AutoForwardMoments.startMonitoring()
                        running = AutoForwardMoments.isRunning
                        Toast.makeText(context, if (running) "开始监控" else "请配置", Toast.LENGTH_SHORT).show()
                    }) { Text("保存并启动", fontSize = 14.sp) } },
                    dismissButton = { TextButton(onClick = onDismiss) { Text("关闭", fontSize = 14.sp) } }
                )
            }
        }
    }
}