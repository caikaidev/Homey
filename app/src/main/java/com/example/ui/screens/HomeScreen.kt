package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.TrackingMode
import com.example.domain.prediction.ItemStatus
import com.example.domain.prediction.PredictionEngine
import com.example.ui.HomeyViewModel
import com.example.ui.Screen
import com.example.ui.components.SectionCard
import com.example.ui.components.SectionTitle
import com.example.ui.components.StatusPill
import com.example.ui.components.toneInk
import com.example.ui.theme.Homey
import java.util.Calendar

@Composable
fun HomeScreen(viewModel: HomeyViewModel) {
    val state by viewModel.home.collectAsStateWithLifecycle()
    val checked by viewModel.checked.collectAsStateWithLifecycle()
    val c = Homey.colors
    val dateText = remember { todayText() }
    val checkedCount = state.shopping.count { it.id in checked }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(dateText, fontSize = 13.sp, color = c.muted)
                    val title = when {
                        !state.loaded -> AnnotatedString(" ")
                        !state.hasAnyItem -> AnnotatedString("欢迎使用 Homey")
                        state.total == 0 -> AnnotatedString("今天没什么要处理的")
                        else -> buildAnnotatedString {
                            append("今天要处理 ")
                            withStyle(SpanStyle(color = c.coral)) { append(state.total.toString()) }
                            append(" 件事")
                        }
                    }
                    Text(title, style = MaterialTheme.typography.headlineLarge, color = c.ink)
                    if (state.hasAnyItem) {
                        Text(summaryText(state.shopping.size, state.expiring.size), fontSize = 14.sp, color = c.muted)
                    }
                }
            }

            if (state.loaded && !state.hasAnyItem) {
                item { EmptyHome(onAdd = { viewModel.open(Screen.Edit(null)) }, onSamples = viewModel::addSamples) }
            }

            if (state.shopping.isNotEmpty()) {
                item {
                    SectionCard {
                        SectionTitle("该买了", trailing = "买到后勾选，自动入库")
                        state.shopping.forEachIndexed { index, item ->
                            if (index > 0) HorizontalDivider(color = c.line)
                            ShoppingRow(
                                item = item,
                                checked = item.id in checked,
                                onToggle = { viewModel.toggleChecked(item.id) },
                                onOpen = { viewModel.open(Screen.Detail(item.id)) }
                            )
                        }
                    }
                }
            }

            if (state.expiring.isNotEmpty()) {
                item {
                    SectionCard {
                        SectionTitle("快过期，先吃掉")
                        state.expiring.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 56.dp)
                                    .clickable { viewModel.open(Screen.Detail(item.id)) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(item.product.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink)
                                    Text(subLine(item), fontSize = 12.sp, color = c.muted)
                                }
                                StatusPill(item.statusText, item.tone)
                            }
                        }
                    }
                }
            }

            if (state.soon.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("一周内会用完", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.muted, modifier = Modifier.padding(start = 4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(state.soon, key = { it.id }) { item ->
                                Column(
                                    modifier = Modifier
                                        .width(160.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(c.surfaceMuted)
                                        .clickable { viewModel.open(Screen.Detail(item.id)) }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(item.product.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(soonText(item), fontSize = 12.sp, color = c.muted)
                                }
                            }
                        }
                    }
                }
            }

            if (state.loaded && state.hasAnyItem && state.total == 0 && state.soon.isEmpty()) {
                item {
                    Text("家里的东西都够用，有情况会提前提醒你。", fontSize = 14.sp, color = c.muted)
                }
            }
        }

        if (checkedCount > 0) {
            Button(
                onClick = viewModel::stockInChecked,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.green, contentColor = c.onGreen)
            ) {
                Text("已买到 $checkedCount 件，一键入库", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ShoppingRow(item: ItemStatus, checked: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    val c = Homey.colors
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 60.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
            contentAlignment = Alignment.Center
        ) {
            val shape = RoundedCornerShape(8.dp)
            if (checked) {
                Box(Modifier.size(26.dp).clip(shape).background(c.green), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, contentDescription = "已买到${item.product.name}", tint = c.onGreen, modifier = Modifier.size(18.dp))
                }
            } else {
                Box(Modifier.size(26.dp).border(2.dp, c.checkboxBorder, shape))
            }
        }
        Column(
            modifier = Modifier.weight(1f).clickable(onClick = onOpen).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                item.product.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (checked) c.muted else c.ink,
                textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(item.reasonText, fontSize = 12.sp, color = if (checked) c.muted else toneInk(item.tone))
        }
        Text(suggestText(item), fontSize = 13.sp, color = c.muted)
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun EmptyHome(onAdd: () -> Unit, onSamples: () -> Unit) {
    val c = Homey.colors
    SectionCard {
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("先把家里常用、容易断货的几样东西加进来。", fontSize = 15.sp, color = c.ink)
            Text("加好以后，这里会告诉你今天该买什么、什么快过期。", fontSize = 13.sp, color = c.muted)
            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.green, contentColor = c.onGreen)
            ) { Text("添加第一件物品", fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = onSamples,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("先放几件示例看看", color = c.green) }
        }
    }
}

private fun summaryText(buy: Int, expiring: Int): String = when {
    buy > 0 && expiring > 0 -> "$buy 件该买了，$expiring 件快过期"
    buy > 0 -> "$buy 件该买了"
    expiring > 0 -> "$expiring 件快过期"
    else -> "都够用"
}

private fun subLine(item: ItemStatus): String {
    val loc = item.product.location
    val qty = "剩 ${PredictionEngine.formatQty(item.quantity)} ${item.product.unit}"
    return if (loc.isBlank()) qty else "$loc · $qty"
}

private fun soonText(item: ItemStatus): String = when (item.mode) {
    TrackingMode.LEVEL -> "剩 ${item.levelPercent}% · 约 ${item.daysLeft} 天"
    else -> "约 ${item.daysLeft} 天后"
}

private fun suggestText(item: ItemStatus): String {
    val p = item.product
    return when (item.mode) {
        TrackingMode.LEVEL -> "1 ${p.unit}"
        else -> "${PredictionEngine.formatQty(p.restockAmount)} ${p.unit}"
    }
}

private fun todayText(): String {
    val cal = Calendar.getInstance()
    val week = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")[cal.get(Calendar.DAY_OF_WEEK) - 1]
    return "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日 · $week"
}
