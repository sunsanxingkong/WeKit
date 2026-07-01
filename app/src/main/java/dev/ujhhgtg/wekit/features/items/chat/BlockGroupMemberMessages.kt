package dev.ujhhgtg.wekit.features.items.chat

import android.content.Context
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import de.robv.android.xposed.XC_MethodHook
import dev.ujhhgtg.comptime.nameOf
import dev.ujhhgtg.wekit.features.api.core.WeDatabaseApi
import dev.ujhhgtg.wekit.features.api.core.models.WeContact
import dev.ujhhgtg.wekit.features.api.ui.WeChatMessageViewApi
import dev.ujhhgtg.wekit.features.api.ui.WeConversationContextMenuApi
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.features.core.SwitchFeature
import dev.ujhhgtg.wekit.preferences.WePrefs
import dev.ujhhgtg.wekit.ui.content.AlertDialogContent
import dev.ujhhgtg.wekit.ui.content.Button
import dev.ujhhgtg.wekit.ui.content.GlobalImageLoader
import dev.ujhhgtg.wekit.ui.content.TextButton
import dev.ujhhgtg.wekit.ui.utils.CancelIcon
import dev.ujhhgtg.wekit.ui.utils.showComposeDialog
import dev.ujhhgtg.wekit.utils.WeLogger
import dev.ujhhgtg.wekit.utils.android.showToast

@Feature(
    name = "群聊屏蔽功能",
    categories = ["聊天"],
    description = "屏蔽群成员消息和屏蔽关键词自动屏蔽；在对话列表长按群聊进入管理"
)
object BlockGroupMemberMessages : SwitchFeature(), WeConversationContextMenuApi.IMenuItemsProvider {
    private val TAG = nameOf(BlockGroupMemberMessages::class)

    override fun getMenuItems(): List<WeConversationContextMenuApi.MenuItem> {
        return listOf(
            WeConversationContextMenuApi.MenuItem(
                id = 777030,
                text = "群聊屏蔽功能",
                drawable = CancelIcon,
                shouldShow = { ctx, _ -> ctx.talker.contains("@chatroom") },
                onClick = { ctx -> showBlockManager(ctx.activity, ctx.talker) }
            )
        )
    }

    // ── 缓存被隐藏的消息 View ──
    // groupId -> (sender -> list of hidden views)
    // 解除屏蔽时保持缓存不清除，以便重新屏蔽时能恢复隐藏
    private val hiddenSendersCache = mutableMapOf<String, MutableMap<String, MutableList<View>>>()

    override fun onEnable() {
        WeConversationContextMenuApi.addProvider(this)
        WeChatMessageViewApi.addListener(object : WeChatMessageViewApi.ICreateViewListener {
            override fun onCreateView(param: XC_MethodHook.MethodHookParam, view: View) {
                try {
                    val msgInfo = WeChatMessageViewApi.getMsgInfoFromParam(param) ?: return
                    val groupId = msgInfo.talker
                    if (!groupId.contains("@chatroom")) return
                    val sender = msgInfo.sender
                    if (sender.isEmpty() || sender == "system") return

                    // 1. 检查手动屏蔽列表
                    if (sender in getBlockedSet(groupId)) {
                        hideView(view, groupId, sender)
                        return
                    }

                    // 2. 关键词自动屏蔽
                    if (getKeywordBlockEnabled(groupId)) {
                        val keywords = getKeywords(groupId)
                        val targets = getKeywordTargets(groupId)
                        if (keywords.isNotEmpty() && (targets.isEmpty() || sender in targets)) {
                            val content = msgInfo.actualContent
                            for (kw in keywords) {
                                if (content.contains(kw, ignoreCase = true)) {
                                    getBlockedSet(groupId).toMutableSet().also {
                                        it.add(sender)
                                        saveBlockedSet(groupId, it)
                                    }
                                    WeLogger.i(TAG, "auto-blocked $sender in $groupId due to keyword: $kw")
                                    hideView(view, groupId, sender)
                                    return
                                }
                            }
                        }
                    }
                } catch (_: Exception) { }
            }
        })
    }

    override fun onDisable() {
        WeConversationContextMenuApi.removeProvider(this)
        hiddenSendersCache.clear()
    }

    // 隐藏 View 并缓存（缓存不清除，以便重新屏蔽时能恢复隐藏）
    private fun hideView(view: View, groupId: String, sender: String) {
        synchronized(hiddenSendersCache) {
            hiddenSendersCache.getOrPut(groupId) { mutableMapOf() }
                .getOrPut(sender) { mutableListOf() }.add(view)
        }
        view.visibility = View.GONE
    }

    // ── Pref 读写 ──

    private fun blockedPrefKey(groupId: String) = "blocked_members_$groupId"
    private fun keywordsPrefKey(groupId: String) = "block_keywords_$groupId"
    private fun targetsPrefKey(groupId: String) = "block_keyword_targets_$groupId"

    private fun getBlockedSet(groupId: String): Set<String>
        = WePrefs.getStringSetOrDef(blockedPrefKey(groupId), emptySet())
    private fun saveBlockedSet(groupId: String, set: Set<String>)
        = WePrefs.putStringSet(blockedPrefKey(groupId), set)

    private fun getKeywords(groupId: String): Set<String>
        = WePrefs.getStringSetOrDef(keywordsPrefKey(groupId), emptySet())
    private fun saveKeywords(groupId: String, set: Set<String>)
        = WePrefs.putStringSet(keywordsPrefKey(groupId), set)

    private fun getKeywordTargets(groupId: String): Set<String>
        = WePrefs.getStringSetOrDef(targetsPrefKey(groupId), emptySet())
    private fun saveKeywordTargets(groupId: String, set: Set<String>)
        = WePrefs.putStringSet(targetsPrefKey(groupId), set)

    private fun getKeywordBlockEnabled(groupId: String): Boolean
        = WePrefs.getBoolOrDef(keywordsPrefKey(groupId) + "_enabled", false)
    private fun setKeywordBlockEnabled(groupId: String, enabled: Boolean)
        = WePrefs.putBool(keywordsPrefKey(groupId) + "_enabled", enabled)

    // 解除屏蔽：恢复 View 可见性，但保留在缓存中
    private fun unblockMember(groupId: String, wxId: String) {
        val set = getBlockedSet(groupId).toMutableSet()
        set.remove(wxId)
        saveBlockedSet(groupId, set)
        // 恢复该发送者所有已缓存 View 的可见性
        synchronized(hiddenSendersCache) {
            hiddenSendersCache[groupId]?.get(wxId)?.forEach { view ->
                try { view.visibility = View.VISIBLE } catch (_: Exception) { }
            }
        }
    }

    // 屏蔽成员：除了保存到 pref 外，还遍历缓存重新隐藏已有 View
    private fun blockMembersNow(groupId: String, wxIds: Set<String>) {
        val set = getBlockedSet(groupId).toMutableSet()
        set.addAll(wxIds)
        saveBlockedSet(groupId, set)
        // 对已缓存的该发送者的所有 View 重新隐藏
        synchronized(hiddenSendersCache) {
            for (wxId in wxIds) {
                hiddenSendersCache[groupId]?.get(wxId)?.forEach { view ->
                    try { view.visibility = View.GONE } catch (_: Exception) { }
                }
            }
        }
    }

    // ── 主管理界面 ──

    fun showBlockManager(context: Context, groupId: String) {
        showComposeDialog(context) {
            var tab by remember { mutableIntStateOf(0) }

            AlertDialogContent(
                title = { Text("群聊屏蔽功能", fontWeight = FontWeight.Bold) },
                text = {
                    Column(Modifier.size(360.dp, 480.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                            TextButton(onClick = { tab = 0 }) {
                                Text("屏蔽成员", fontSize = 13.sp,
                                    fontWeight = if (tab == 0) FontWeight.Bold else FontWeight.Normal)
                            }
                            TextButton(onClick = { tab = 1 }) {
                                Text("屏蔽关键词", fontSize = 13.sp,
                                    fontWeight = if (tab == 1) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                        HorizontalDivider()
                        Spacer(Modifier.height(4.dp))

                        when (tab) {
                            0 -> BlockMemberTab(context, groupId)
                            1 -> KeywordTab(context, groupId)
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
            )
        }
    }

    // ═══════════════════════════════════════════════
    // TAB 1: 屏蔽成员
    // ═══════════════════════════════════════════════

    @Composable
    private fun BlockMemberTab(context: Context, groupId: String) {
        var innerTab by remember { mutableIntStateOf(0) }
        val blockedSet = remember { mutableStateOf(getBlockedSet(groupId)) }
        val members = remember { mutableStateOf<List<WeContact>>(emptyList()) }
        var loaded by remember { mutableStateOf(false) }
        var searchQuery by remember { mutableStateOf("") }
        val selectedToBlock = remember { mutableStateListOf<String>() }

        LaunchedEffect(Unit) {
            val m = try { WeDatabaseApi.getGroupMembers(groupId) } catch (_: Exception) { emptyList() }
            members.value = m; loaded = true
        }

        val filteredMembers = remember(searchQuery, members.value, blockedSet.value) {
            members.value.filter { it.wxId !in blockedSet.value && (searchQuery.isBlank() ||
                (it.displayName.ifEmpty { it.nickname }).lowercase().contains(searchQuery.lowercase()) ||
                it.wxId.lowercase().contains(searchQuery.lowercase())) }
        }

        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
            TextButton(onClick = { innerTab = 0 }) {
                Text("已屏蔽 (${blockedSet.value.size})", fontSize = 12.sp,
                    fontWeight = if (innerTab == 0) FontWeight.Bold else FontWeight.Normal)
            }
            TextButton(onClick = { innerTab = 1 }) {
                Text("添加屏蔽", fontSize = 12.sp,
                    fontWeight = if (innerTab == 1) FontWeight.Bold else FontWeight.Normal)
            }
        }
        HorizontalDivider()
        Spacer(Modifier.height(4.dp))

        when (innerTab) {
            0 -> {
                if (blockedSet.value.isEmpty()) {
                    Text("暂无已屏蔽的成员", modifier = Modifier.padding(16.dp), color = ComposeColor.Gray)
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        items(blockedSet.value.toList().sorted(), { it }) { wxId ->
                            val contact = try { WeDatabaseApi.getFriend(wxId) } catch (_: Exception) { null }
                            val name = contact?.displayName ?: contact?.nickname ?: wxId
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = contact?.avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)),
                                    imageLoader = GlobalImageLoader
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(name, fontSize = 14.sp)
                                    Text(wxId, fontSize = 11.sp, color = ComposeColor.Gray)
                                }
                                TextButton(onClick = {
                                    unblockMember(groupId, wxId)
                                    blockedSet.value = getBlockedSet(groupId)
                                    showToast(context, "已解除屏蔽")
                                }) { Text("解除", fontSize = 12.sp) }
                            }
                        }
                    }
                }
            }
            1 -> {
                if (!loaded) {
                    Text("加载成员中...", modifier = Modifier.padding(16.dp))
                } else if (members.value.isEmpty()) {
                    Text("无法获取群成员列表", modifier = Modifier.padding(16.dp), color = ComposeColor.Gray)
                } else {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("搜索成员") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("群成员 ${members.value.size} 人 | 已选 ${selectedToBlock.size}", fontSize = 11.sp, color = ComposeColor.Gray)
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(Modifier.weight(1f)) {
                        items(filteredMembers, { it.wxId }) { m ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (m.wxId in selectedToBlock) selectedToBlock.remove(m.wxId)
                                    else selectedToBlock.add(m.wxId)
                                }.padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = m.wxId in selectedToBlock,
                                    onCheckedChange = { c -> if (c) selectedToBlock.add(m.wxId) else selectedToBlock.remove(m.wxId) }
                                )
                                Spacer(Modifier.width(8.dp))
                                AsyncImage(
                                    model = m.avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)),
                                    imageLoader = GlobalImageLoader
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(m.displayName.ifEmpty { m.nickname }, fontSize = 14.sp)
                                    Text(m.wxId, fontSize = 11.sp, color = ComposeColor.Gray)
                                }
                            }
                        }
                    }
                    if (selectedToBlock.isNotEmpty()) {
                        Button(onClick = {
                            blockMembersNow(groupId, selectedToBlock.toSet())
                            blockedSet.value = getBlockedSet(groupId)
                            val count = selectedToBlock.size
                            selectedToBlock.clear()
                            innerTab = 0
                            showToast(context, "已屏蔽 $count 人")
                        }, Modifier.fillMaxWidth()) { Text("屏蔽选中 (${selectedToBlock.size})", fontSize = 13.sp) }
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════
    // TAB 2: 屏蔽关键词
    // ═══════════════════════════════════════════════
    @Composable
    private fun KeywordTab(context: Context, groupId: String) {
        var innerTab by remember { mutableIntStateOf(0) }
        val keywords = remember { mutableStateOf(getKeywords(groupId)) }
        val enabled = remember { mutableStateOf(getKeywordBlockEnabled(groupId)) }
        val members = remember { mutableStateOf<List<WeContact>>(emptyList()) }
        val keywordTargets = remember { mutableStateOf(getKeywordTargets(groupId)) }
        var loaded by remember { mutableStateOf(false) }
        var searchQuery by remember { mutableStateOf("") }
        val selectedTargets = remember { mutableStateListOf<String>() }
        val selectedForAddKw = remember { mutableStateListOf<String>() }
        var newKeyword by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            val m = try { WeDatabaseApi.getGroupMembers(groupId) } catch (_: Exception) { emptyList() }
            members.value = m; loaded = true
        }

        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
            TextButton(onClick = { innerTab = 0 }) {
                Text("关键词列表 (${keywords.value.size})", fontSize = 12.sp,
                    fontWeight = if (innerTab == 0) FontWeight.Bold else FontWeight.Normal)
            }
            TextButton(onClick = { innerTab = 1 }) {
                Text("添加关键词", fontSize = 12.sp,
                    fontWeight = if (innerTab == 1) FontWeight.Bold else FontWeight.Normal)
            }
            TextButton(onClick = { innerTab = 2 }) {
                Text("监控对象", fontSize = 12.sp,
                    fontWeight = if (innerTab == 2) FontWeight.Bold else FontWeight.Normal)
            }
        }
        HorizontalDivider()
        Spacer(Modifier.height(4.dp))

        when (innerTab) {
            0 -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("自动屏蔽", fontSize = 13.sp)
                    Spacer(Modifier.width(6.dp))
                    Switch(
                        checked = enabled.value,
                        onCheckedChange = {
                            enabled.value = it
                            setKeywordBlockEnabled(groupId, it)
                            showToast(context, if (it) "关键词屏蔽已开启" else "关键词屏蔽已关闭")
                        }
                    )
                }
                Spacer(Modifier.height(4.dp))
                if (keywords.value.isEmpty()) {
                    Text("暂无关键词", modifier = Modifier.padding(16.dp), color = ComposeColor.Gray)
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        items(keywords.value.toList().sorted(), { it }) { kw ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🔑 ", fontSize = 14.sp)
                                Text(kw, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    val set = getKeywords(groupId).toMutableSet()
                                    set.remove(kw)
                                    saveKeywords(groupId, set)
                                    keywords.value = set
                                    showToast(context, "已删除关键词: $kw")
                                }) { Text("删除", fontSize = 12.sp, color = ComposeColor(0xFFF44336)) }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    if (enabled.value) {
                        val targetCount = keywordTargets.value.size
                        val totalMembers = members.value.size
                        Text(
                            if (targetCount == 0) "监控对象: 全部成员 ($totalMembers 人)"
                            else "监控对象: $targetCount/$totalMembers 人",
                            fontSize = 11.sp, color = ComposeColor.Gray
                        )
                    }
                }
            }

            1 -> {
                OutlinedTextField(
                    value = newKeyword,
                    onValueChange = { newKeyword = it },
                    label = { Text("输入关键词") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
                Spacer(Modifier.height(8.dp))
                if (newKeyword.isNotBlank()) {
                    Button(onClick = {
                        val set = getKeywords(groupId).toMutableSet()
                        set.add(newKeyword.trim())
                        saveKeywords(groupId, set)
                        keywords.value = set
                        newKeyword = ""
                        showToast(context, "已添加关键词")
                    }) { Text("添加", fontSize = 13.sp) }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Text("也可批量从已屏蔽成员中添加", fontSize = 12.sp, color = ComposeColor.Gray)
                Spacer(Modifier.height(4.dp))

                val blockedSet = getBlockedSet(groupId)
                val blockedMembers = remember(blockedSet, members.value) {
                    members.value.filter { it.wxId in blockedSet }
                }

                if (blockedMembers.isEmpty()) {
                    Text("暂无已屏蔽成员可提取关键词", fontSize = 12.sp, color = ComposeColor.Gray, modifier = Modifier.padding(8.dp))
                } else {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("搜索已屏蔽成员") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val filteredBlocked = remember(searchQuery, blockedMembers) {
                        if (searchQuery.isBlank()) blockedMembers
                        else blockedMembers.filter {
                            it.displayName.lowercase().contains(searchQuery.lowercase()) ||
                            it.wxId.lowercase().contains(searchQuery.lowercase())
                        }
                    }
                    LazyColumn(Modifier.weight(1f)) {
                        items(filteredBlocked, { it.wxId }) { m ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (m.wxId in selectedForAddKw) selectedForAddKw.remove(m.wxId)
                                    else selectedForAddKw.add(m.wxId)
                                }.padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = m.wxId in selectedForAddKw,
                                    onCheckedChange = { c -> if (c) selectedForAddKw.add(m.wxId) else selectedForAddKw.remove(m.wxId) }
                                )
                                Spacer(Modifier.width(8.dp))
                                AsyncImage(
                                    model = m.avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)),
                                    imageLoader = GlobalImageLoader
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(m.displayName.ifEmpty { m.nickname }, fontSize = 13.sp)
                                    Text(m.wxId, fontSize = 10.sp, color = ComposeColor.Gray)
                                }
                            }
                        }
                    }
                    if (selectedForAddKw.isNotEmpty()) {
                        Button(onClick = {
                            val currentKws = getKeywords(groupId).toMutableSet()
                            selectedForAddKw.forEach { wxId ->
                                val contact = members.value.find { it.wxId == wxId }
                                val kw = contact?.nickname?.take(6) ?: wxId.take(8)
                                if (kw.isNotBlank()) currentKws.add(kw)
                            }
                            saveKeywords(groupId, currentKws)
                            keywords.value = currentKws
                            selectedForAddKw.clear()
                            showToast(context, "已添加关键词")
                        }, Modifier.fillMaxWidth()) { Text("以昵称为关键词添加 (${selectedForAddKw.size})", fontSize = 13.sp) }
                    }
                }
            }

            2 -> {
                if (!loaded) {
                    Text("加载成员中...", modifier = Modifier.padding(16.dp))
                } else if (members.value.isEmpty()) {
                    Text("无法获取群成员列表", modifier = Modifier.padding(16.dp), color = ComposeColor.Gray)
                } else {
                    Text("选择要监控的成员（不选则监控全部）", fontSize = 12.sp, color = ComposeColor.Gray)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("搜索成员") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                        TextButton(onClick = {
                            selectedTargets.addAll(members.value.map { it.wxId })
                            keywordTargets.value = selectedTargets.toSet()
                        }) { Text("全选", fontSize = 12.sp) }
                        TextButton(onClick = {
                            selectedTargets.clear()
                            keywordTargets.value = emptySet()
                        }) { Text("清空(监控全部)", fontSize = 12.sp) }
                    }

                    val filteredMembers = remember(searchQuery, members.value) {
                        if (searchQuery.isBlank()) members.value
                        else members.value.filter {
                            it.displayName.lowercase().contains(searchQuery.lowercase()) ||
                            it.wxId.lowercase().contains(searchQuery.lowercase())
                        }
                    }

                    Spacer(Modifier.height(2.dp))
                    Text("已选 ${selectedTargets.size} 人", fontSize = 11.sp, color = ComposeColor(0xFF4CAF50))
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(Modifier.weight(1f)) {
                        items(filteredMembers, { it.wxId }) { m ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (m.wxId in selectedTargets) selectedTargets.remove(m.wxId)
                                    else selectedTargets.add(m.wxId)
                                }.padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = m.wxId in selectedTargets,
                                    onCheckedChange = { c -> if (c) selectedTargets.add(m.wxId) else selectedTargets.remove(m.wxId) }
                                )
                                Spacer(Modifier.width(8.dp))
                                AsyncImage(
                                    model = m.avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)),
                                    imageLoader = GlobalImageLoader
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(m.displayName.ifEmpty { m.nickname }, fontSize = 14.sp)
                                    Text(m.wxId, fontSize = 11.sp, color = ComposeColor.Gray)
                                }
                            }
                        }
                    }
                    Button(onClick = {
                        saveKeywordTargets(groupId, selectedTargets.toSet())
                        keywordTargets.value = selectedTargets.toSet()
                        showToast(context, "已保存监控对象（${selectedTargets.size} 人）")
                    }, Modifier.fillMaxWidth()) { Text("保存监控对象", fontSize = 13.sp) }
                }
            }
        }
    }
}