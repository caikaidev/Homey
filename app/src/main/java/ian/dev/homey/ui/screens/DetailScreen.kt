package ian.dev.homey.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ian.dev.homey.data.model.TrackingMode
import ian.dev.homey.data.model.categoryEnum
import ian.dev.homey.domain.prediction.ItemStatus
import ian.dev.homey.domain.prediction.PredictionEngine
import ian.dev.homey.ui.HomeyViewModel
import ian.dev.homey.ui.Screen
import ian.dev.homey.ui.components.ChoiceTile
import ian.dev.homey.ui.components.NumberStepper
import ian.dev.homey.ui.components.SectionCard
import ian.dev.homey.ui.components.SettingRow
import ian.dev.homey.ui.components.toneInk
import ian.dev.homey.ui.components.toneSoft
import ian.dev.homey.ui.theme.DisplayFont
import ian.dev.homey.ui.theme.Homey
import ian.dev.homey.ui.theme.Motion
import kotlin.math.roundToInt

private val LEVELS = listOf(100 to "满", 75 to "75%", 50 to "50%", 25 to "25%", 0 to "用完")

@Composable
fun DetailScreen(viewModel: HomeyViewModel, id: String) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val current = items?.firstOrNull { it.id == id }
    val c = Homey.colors
    var confirmDelete by remember { mutableStateOf(false) }

    // 物品被删除后页面会滑出，这段时间里继续显示最后一次的内容，而不是闪成空白
    var lastSeen by remember { mutableStateOf<ItemStatus?>(null) }
    if (current != null && current != lastSeen) lastSeen = current
    val item = current ?: lastSeen

    // 物品被删除（或恢复备份后不存在了）时自动返回
    LaunchedEffect(items, id) {
        if (items != null && current == null) viewModel.backFrom(Screen.Detail(id))
    }
    if (item == null) return
    val p = item.product
    val unit = p.unit

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.backFrom(Screen.Detail(id)) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = c.ink)
            }
            Box(Modifier.weight(1f))
            TextButton(onClick = { viewModel.open(Screen.Edit(id)) }) { Text("编辑", color = c.green, fontSize = 15.sp) }
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val meta = listOfNotNull(p.categoryEnum.label, p.location.ifBlank { null }, item.mode.label)
                Text(meta.joinToString(" · "), fontSize = 13.sp, color = c.muted)
                Text(p.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.ink)
            }

            StatusCard(item)

            SectionCard {
                when (item.mode) {
                    TrackingMode.LEVEL -> {
                        Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("现在大概还剩", fontSize = 15.sp, color = c.ink)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                LEVELS.forEach { (value, label) ->
                                    ChoiceTile(
                                        selected = item.levelPercent == value,
                                        onClick = { viewModel.setLevel(id, value) },
                                        modifier = Modifier.weight(1f).height(44.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.ink, modifier = Modifier.align(Alignment.CenterHorizontally))
                                    }
                                }
                            }
                        }
                    }
                    else -> SettingRow("现在有", subtitle = "点数字可直接输入") {
                        NumberStepper(
                            value = item.quantity.roundToInt(),
                            onValueChange = { viewModel.setQuantity(id, it) },
                            unit = unit,
                            label = "现在的数量",
                            commitWhileTyping = false,
                            onStep = { delta -> viewModel.adjust(id, delta.toDouble()) }
                        )
                    }
                }
                when (item.mode) {
                    TrackingMode.COUNT -> SettingRow("每天大约用") {
                        Value(if (p.dailyUsage <= 0) "常备，不预测" else "${PredictionEngine.formatQty(p.dailyUsage)} $unit")
                    }
                    TrackingMode.LEVEL -> SettingRow("一整${unit}大约用") { Value("${p.lifeDays} 天") }
                    TrackingMode.EXPIRY -> SettingRow("买回来大约能放") { Value("${p.lifeDays} 天") }
                }
                SettingRow("提前几天提醒我买", showDivider = false) { Value("${p.remindDaysAhead} 天") }
            }
        }

        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(
                onClick = { viewModel.restock(id) },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.green, contentColor = c.onGreen)
            ) { Text(restockLabel(item), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text("不再记录这件物品", color = c.urgent, fontSize = 14.sp)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("不再记录「${p.name}」？") },
            text = { Text("它会从列表里消失。之后可以在提示里撤销，备份文件里也会保留记录。") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete(id, p.name) }) { Text("确定", color = c.urgent) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun Value(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Homey.colors.ink)
}

@Composable
private fun StatusCard(item: ItemStatus) {
    val c = Homey.colors
    val ink = toneInk(item.tone)
    val p = item.product
    val (headline, big, bigUnit) = when (item.mode) {
        TrackingMode.COUNT -> when {
            item.quantity <= 0 -> Triple("已经", "用完", "")
            item.daysLeft == null -> Triple("现在有", PredictionEngine.formatQty(item.quantity), p.unit)
            item.needsBuy -> Triple("只够用", item.daysLeft.toString(), "天")
            else -> Triple("还够用", item.daysLeft.toString(), "天")
        }
        TrackingMode.LEVEL -> Triple("还剩", item.levelPercent.toString(), "%")
        TrackingMode.EXPIRY -> {
            val d = item.expiresInDays
            when {
                item.quantity <= 0 -> Triple("已经", "吃完", "")
                d == null -> Triple("现在有", PredictionEngine.formatQty(item.quantity), p.unit)
                d < 0 -> Triple("已经过期", (-d).toString(), "天")
                d == 0 -> Triple("今天", "到期", "")
                else -> Triple("离到期还有", d.toString(), "天")
            }
        }
    }
    val progress: Float? = when (item.mode) {
        TrackingMode.COUNT -> if (p.dailyUsage > 0) {
            val full = (p.restockAmount + p.dailyUsage * (p.remindDaysAhead + 7)).coerceAtLeast(1.0)
            (item.quantity / full).toFloat().coerceIn(0f, 1f)
        } else null
        TrackingMode.LEVEL -> item.levelPercent / 100f
        TrackingMode.EXPIRY -> null
    }

    val cardBg by animateColorAsState(toneSoft(item.tone), Motion.state(400), label = "statusBg")
    val animatedInk by animateColorAsState(ink, Motion.state(400), label = "statusInk")
    val animatedProgress by animateFloatAsState(progress ?: 0f, Motion.state(500), label = "progress")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(cardBg)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(headline, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = animatedInk)
            // 数字变化时上下滚动：变大从下往上，变小从上往下
            AnimatedContent(
                targetState = big to bigUnit,
                transitionSpec = {
                    val up = (targetState.first.toIntOrNull() ?: 0) >= (initialState.first.toIntOrNull() ?: 0)
                    (slideInVertically(Motion.enter(Motion.STATE_MS + 60)) { if (up) it / 2 else -it / 2 } + fadeIn(Motion.enter(Motion.STATE_MS)))
                        .togetherWith(slideOutVertically(Motion.exit(Motion.STATE_MS)) { if (up) -it / 2 else it / 2 } + fadeOut(Motion.exit(Motion.FADE_MS)))
                },
                label = "bigNumber"
            ) { (number, numberUnit) ->
                Text(
                    buildAnnotatedString {
                        append(number)
                        if (numberUnit.isNotEmpty()) withStyle(SpanStyle(fontSize = 22.sp)) { append(" $numberUnit") }
                    },
                    fontFamily = DisplayFont,
                    fontSize = 56.sp,
                    lineHeight = 62.sp,
                    fontWeight = FontWeight.Bold,
                    color = animatedInk
                )
            }
        }
        if (progress != null) {
            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(c.ink.copy(alpha = 0.1f))) {
                Box(Modifier.fillMaxWidth(animatedProgress).height(8.dp).clip(CircleShape).background(animatedInk))
            }
        }
        Text(adviceText(item), fontSize = 13.sp, color = c.ink.copy(alpha = 0.8f))
    }
}

private fun adviceText(item: ItemStatus): String {
    val remind = item.product.remindDaysAhead
    return when {
        item.mode == TrackingMode.EXPIRY && item.quantity <= 0 -> "已加入「该买了」"
        item.mode == TrackingMode.EXPIRY && (item.expiresInDays ?: 99) < 0 -> "已经过期了，注意检查还能不能吃"
        item.expiringSoon -> "快过期了，优先吃掉它"
        item.needsBuy -> "已加入「该买了」，建议尽快下单，到货前不会断"
        item.runsOutSoon && item.daysLeft != null -> "约 ${(item.daysLeft - remind).coerceAtLeast(1)} 天后提醒你下单"
        item.mode == TrackingMode.EXPIRY -> "到期前 3 天会提醒你"
        item.daysLeft == null -> "常备物品，用完时会提醒你"
        else -> "暂时不用买，到时候会提醒你"
    }
}

private fun restockLabel(item: ItemStatus): String {
    val p = item.product
    return when (item.mode) {
        TrackingMode.LEVEL -> "买到了 · 换一${p.unit}新的"
        else -> "买到了 · 补 ${PredictionEngine.formatQty(p.restockAmount)} ${p.unit}"
    }
}
