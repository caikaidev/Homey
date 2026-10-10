package ian.dev.zaizai.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ian.dev.zaizai.R
import ian.dev.zaizai.data.BizDate
import ian.dev.zaizai.data.Board
import ian.dev.zaizai.data.BoardItem
import ian.dev.zaizai.data.BoardLogic
import ian.dev.zaizai.data.BoardState
import ian.dev.zaizai.data.ItemStatus
import ian.dev.zaizai.data.TomorrowItem
import ian.dev.zaizai.ui.theme.ZaiZai
import kotlinx.coroutines.delay

/** 今日照护：打开就是它。未登记在前，大按钮“确认已喂”，2 分钟内可撤销；明天只读。 */
@Composable
fun BoardScreen(
    state: BoardState,
    serverNow: () -> Long,
    onCheckin: (BoardItem) -> Unit,
    onUndo: (BoardItem) -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
) {
    // 每秒走一下，撤销倒计时和“过期就消失”靠它
    var now by remember { mutableLongStateOf(serverNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = serverNow()
            delay(1_000)
        }
    }
    val board = state.board

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZaiZai.colors.background)
            .safeDrawingPadding(),
    ) {
        Header(board, BoardLogic.greeting(BizDate.hhmm(now)), onRefresh, onSettings)
        StatusBar(state, now)
        if (board == null) {
            EmptyState(state, onRefresh)
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Summary(board) }
            items(board.today, key = { board.key(it) }) { item ->
                ItemCard(
                    item = item,
                    queued = board.key(item) in state.queued,
                    praise = BoardLogic.praise(board.key(item)),
                    undoMs = BoardLogic.undoRemainingMs(item, state.config.recorder, now),
                    onCheckin = { onCheckin(item) },
                    onUndo = { onUndo(item) },
                )
            }
            if (board.tomorrow.isNotEmpty()) {
                item {
                    Text(
                        text = "明天 ${BizDate.monthDay(board.tomorrowDate)}（只看）",
                        style = MaterialTheme.typography.titleMedium,
                        color = ZaiZai.colors.muted,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                items(board.tomorrow, key = { "t|${it.taskId}|${it.slot}" }) { TomorrowRow(it) }
            }
        }
    }
}

@Composable
private fun Header(board: Board?, greeting: String, onRefresh: () -> Unit, onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = board?.let { "${it.nickname}今天的事" } ?: "今天的事",
                style = MaterialTheme.typography.headlineMedium,
                color = ZaiZai.colors.ink,
            )
            if (board != null) {
                Text(
                    text = "${BizDate.monthDay(board.date)} ${board.weekday} · $greeting",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ZaiZai.colors.muted,
                )
            }
        }
        IconButton(onClick = onRefresh, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.Refresh, contentDescription = "刷新", tint = ZaiZai.colors.muted)
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.Settings, contentDescription = "设置", tint = ZaiZai.colors.muted)
        }
    }
}

/** 同步状态：离线时明确写“离线 · 最后同步 HH:mm”，不假装是最新。 */
@Composable
private fun StatusBar(state: BoardState, now: Long) {
    val synced = state.syncedAt?.let { BizDate.hhmm(it + state.clockOffset) }
    val board = state.board
    val (text, strong) = when {
        state.problem != null -> state.problem to true
        state.offline -> "离线 · 最后同步 ${synced ?: "--:--"}" to true
        board != null && board.date != BizDate.date(now) ->
            "这是${BizDate.monthDay(board.date)}的看板，正在换到今天…" to true
        state.syncing && synced == null -> "正在同步…" to false
        synced != null -> "已同步 $synced" to false
        else -> return
    }
    if (strong) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = ZaiZai.colors.onOffline,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(ZaiZai.colors.offline, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    } else {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = ZaiZai.colors.muted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun EmptyState(state: BoardState, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = if (state.syncing) "正在拿今天的看板…" else "还没拿到看板，请检查网络后重试",
            style = MaterialTheme.typography.bodyLarge,
            color = ZaiZai.colors.ink,
        )
        if (!state.syncing) {
            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Text("重试", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** 顶上一句话 + 一排小爱心（做完一件点亮一颗）；还有事时小牛崽在旁边晃着等，都做完了它开心地蹦。 */
@Composable
private fun Summary(board: Board) {
    val pending = board.pendingCount
    val total = board.today.size
    val colors = ZaiZai.colors
    if (pending == 0) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            CalfAnimation(CALF_DANCE, 350, Modifier.fillMaxWidth().height(180.dp))
            Text(
                text = if (total == 0) "今天没有要做的事" else "今天的都做完了",
                fontSize = 40.sp,
                lineHeight = 48.sp,
                fontWeight = FontWeight.Bold,
                color = colors.done,
                textAlign = TextAlign.Center,
            )
            if (total > 0) Hearts(done = total, total = total, modifier = Modifier.padding(top = 8.dp))
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        CalfAnimation(CALF_WAIT, 700, Modifier.size(96.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = "还剩 $pending 件",
                fontSize = 40.sp,
                lineHeight = 48.sp,
                fontWeight = FontWeight.Bold,
                color = colors.pending,
            )
            Hearts(done = total - pending, total = total, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private val HEART = Color(0xFFE8556D)
private const val MAX_HEARTS = 10

@Composable
private fun Hearts(done: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.semantics { contentDescription = "做完 $done 件，共 $total 件" },
    ) {
        val shown = total.coerceAtMost(MAX_HEARTS)
        val lit = (done * shown + total - 1) / total.coerceAtLeast(1)
        repeat(shown) { i ->
            Icon(
                imageVector = if (i < lit) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = null,
                tint = if (i < lit) HEART else ZaiZai.colors.muted,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/** 小牛崽的帧，和小组件用同一组：跳舞四帧都用，等待只左右歪头。 */
private val CALF_DANCE = intArrayOf(R.drawable.calf_0, R.drawable.calf_1, R.drawable.calf_2, R.drawable.calf_3)
private val CALF_WAIT = intArrayOf(R.drawable.calf_0, R.drawable.calf_2)

@Composable
private fun CalfAnimation(frames: IntArray, intervalMs: Long, modifier: Modifier = Modifier) {
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(frames) {
        while (true) {
            delay(intervalMs)
            frame = (frame + 1) % frames.size
        }
    }
    Image(
        painter = painterResource(frames[frame % frames.size]),
        contentDescription = null,
        modifier = modifier,
    )
}

@Composable
private fun ItemCard(
    item: BoardItem,
    queued: Boolean,
    praise: String,
    undoMs: Long,
    onCheckin: () -> Unit,
    onUndo: () -> Unit,
) {
    val colors = ZaiZai.colors
    val pending = item.status == ItemStatus.PENDING
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(if (pending) 2.dp else 1.dp, if (pending) colors.pending else colors.line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.slot,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (pending) colors.pending else colors.muted,
                )
                Spacer(Modifier.weight(1f))
                StatusTag(item.status)
            }
            Text(text = item.title, fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, color = colors.ink)
            if (item.note.isNotBlank()) {
                Text(text = item.note, style = MaterialTheme.typography.bodyLarge, color = colors.muted)
            }
            if (pending) {
                Button(
                    onClick = onCheckin,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.action, contentColor = colors.onAction),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("确认已喂", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                item.completion?.let { c ->
                    val what = if (item.status == ItemStatus.DONE) "登记" else "跳过"
                    val reason = if (c.reason.isNotBlank()) "：${c.reason}" else ""
                    val upload = if (queued) "（还没传上去，联网后自动补传）" else ""
                    Text(
                        text = "${c.recordedBy} · ${BizDate.hhmm(c.recordedAt)} $what$reason$upload",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.muted,
                    )
                }
                // 刚登记完弹出一句夸奖，撤销时间过了就收起
                AnimatedVisibility(
                    visible = item.status == ItemStatus.DONE && undoMs > 0,
                    enter = scaleIn(spring(dampingRatio = Spring.DampingRatioHighBouncy)) + fadeIn(),
                    exit = fadeOut(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = HEART, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(praise, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = colors.done)
                    }
                }
                if (undoMs > 0) {
                    OutlinedButton(
                        onClick = onUndo,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    ) {
                        Text("点错了，撤销（${(undoMs + 999) / 1000} 秒）", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

/** 状态用图标 + 文字，不只靠颜色。 */
@Composable
private fun StatusTag(status: ItemStatus) {
    val colors = ZaiZai.colors
    val (label, fg, bg) = when (status) {
        ItemStatus.PENDING -> Triple("待喂", colors.pending, colors.pendingSoft)
        ItemStatus.DONE -> Triple("已喂", colors.done, colors.doneSoft)
        ItemStatus.SKIPPED -> Triple("已跳过", colors.skipped, colors.skippedSoft)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { contentDescription = label },
    ) {
        when (status) {
            ItemStatus.PENDING -> Box(Modifier.size(22.dp).border(3.dp, fg, CircleShape))
            ItemStatus.DONE -> TagIcon(Icons.Filled.CheckCircle, fg)
            ItemStatus.SKIPPED -> TagIcon(Icons.Filled.Close, fg)
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = fg)
    }
}

@Composable
private fun TagIcon(icon: ImageVector, tint: Color) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
}

@Composable
private fun TomorrowRow(item: TomorrowItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(item.slot, style = MaterialTheme.typography.bodyLarge, color = ZaiZai.colors.muted, modifier = Modifier.width(88.dp))
        Text(item.title, style = MaterialTheme.typography.bodyLarge, color = ZaiZai.colors.ink)
    }
}
