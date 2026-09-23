package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.categoryEnum
import com.example.domain.prediction.ItemStatus
import com.example.domain.prediction.PredictionEngine
import com.example.data.model.TrackingMode
import com.example.ui.HomeyViewModel
import com.example.ui.Screen
import com.example.ui.components.ChipButton
import com.example.ui.components.SectionCard
import com.example.ui.components.ToneDot
import com.example.ui.components.toneInk
import com.example.ui.theme.Homey

@Composable
fun InventoryScreen(viewModel: HomeyViewModel) {
    val list by viewModel.inventory.collectAsStateWithLifecycle()
    val all by viewModel.items.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val categories by viewModel.usedCategories.collectAsStateWithLifecycle()
    val c = Homey.colors

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("家里的东西", style = MaterialTheme.typography.headlineLarge, color = c.ink, modifier = Modifier.weight(1f))
                Text("共 ${all.orEmpty().size} 件", fontSize = 13.sp, color = c.muted)
                IconButton(onClick = { viewModel.open(Screen.Backup) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Storage, contentDescription = "数据与备份", tint = c.ink)
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索物品") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = c.surface,
                    unfocusedContainerColor = c.surface,
                    unfocusedBorderColor = c.line,
                    focusedBorderColor = c.green
                )
            )
        }
        if (categories.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { ChipButton("全部", category == null, { viewModel.setCategory(null) }) }
                    items(categories) { cat ->
                        ChipButton(cat.label, category == cat, { viewModel.setCategory(cat) })
                    }
                }
            }
        }
        item {
            if (list.isEmpty()) {
                Text(
                    if (all.orEmpty().isEmpty()) "还没有物品，点下方 + 添加" else "没有找到匹配的物品",
                    fontSize = 14.sp,
                    color = c.muted,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                SectionCard {
                    list.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(color = c.line)
                        InventoryRow(item) { viewModel.open(Screen.Detail(item.id)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryRow(item: ItemStatus, onClick: () -> Unit) {
    val c = Homey.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ToneDot(item.tone)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.product.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(inventorySubLine(item), fontSize = 12.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(item.statusText, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = toneInk(item.tone))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.checkboxBorder)
    }
}

private fun inventorySubLine(item: ItemStatus): String {
    val p = item.product
    val parts = mutableListOf(p.categoryEnum.label, item.mode.label)
    if (item.mode == TrackingMode.COUNT) parts += "剩 ${PredictionEngine.formatQty(item.quantity)} ${p.unit}"
    if (p.location.isNotBlank()) parts += p.location
    return parts.joinToString(" · ")
}
