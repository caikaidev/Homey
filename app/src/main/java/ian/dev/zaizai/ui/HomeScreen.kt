package ian.dev.zaizai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ian.dev.zaizai.data.BizDate
import ian.dev.zaizai.data.BoardLogic
import ian.dev.zaizai.data.BoardState
import ian.dev.zaizai.data.CardType
import ian.dev.zaizai.data.HomeCard
import ian.dev.zaizai.ui.theme.ZaiZai
import kotlinx.coroutines.delay

/** 首页：每一类一张大卡片（补剂、药品、宝宝屋），有数据的才出现。点进去看那一类。 */
@Composable
fun HomeScreen(
    state: BoardState,
    serverNow: () -> Long,
    onOpen: (HomeCard) -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
) {
    var now by remember { mutableLongStateOf(serverNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = serverNow()
            delay(30_000)
        }
    }
    val board = state.board
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZaiZai.colors.background)
            .safeDrawingPadding(),
    ) {
        Header(board, board?.let { "${it.nickname}今天的事" } ?: "今天的事", BoardLogic.greeting(BizDate.hhmm(now)), onRefresh, onSettings, syncing = state.syncing)
        StatusBar(state, now)
        if (board == null) {
            EmptyState(state, onRefresh)
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.cards.isEmpty()) {
                item {
                    Text(
                        "今天没有要做的事",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZaiZai.colors.muted,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            items(state.cards, key = { it.key }) { card -> BigCard(card) { onOpen(card) } }
        }
    }
}

@Composable
private fun BigCard(card: HomeCard, onClick: () -> Unit) {
    val colors = ZaiZai.colors
    val (line, tint) = when {
        card.type == CardType.IMAGE -> "点开给工作人员扫码" to colors.action
        card.total == 0 -> "今天没有要做的" to colors.muted
        card.pending == 0 -> "今天的都做完了" to colors.done
        else -> "还剩 ${card.pending} 件" to colors.pending
    }
    val attention = card.type == CardType.TASKS && card.pending > 0
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = colors.surface,
        border = BorderStroke(if (attention) 3.dp else 1.dp, if (attention) colors.pending else colors.line),
        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
    ) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(card.title, fontSize = 44.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold, color = colors.ink)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (card.type == CardType.TASKS && card.total > 0 && card.pending == 0) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.done, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(line, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = tint)
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}
