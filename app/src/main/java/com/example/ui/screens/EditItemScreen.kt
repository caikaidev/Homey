package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ProductCategory
import com.example.data.model.TrackingMode
import com.example.data.model.categoryEnum
import com.example.data.model.mode
import com.example.data.repository.ItemDraft
import com.example.domain.prediction.PredictionEngine
import com.example.ui.HomeyViewModel
import com.example.ui.Screen
import com.example.ui.components.ChipButton
import com.example.ui.components.ChoiceTile
import com.example.ui.components.NumberStepper
import com.example.ui.components.SectionCard
import com.example.ui.components.SettingRow
import com.example.ui.theme.Homey
import com.example.ui.theme.Motion

private data class Preset(
    val label: String,
    val mode: TrackingMode,
    val category: ProductCategory,
    val unit: String,
    val daily: Double = 1.0,
    val lifeDays: Int = 30,
    val restock: Double = 1.0
)

private val PRESETS = listOf(
    Preset("纸尿裤", TrackingMode.COUNT, ProductCategory.BABY, "片", daily = 8.0, restock = 64.0),
    Preset("湿巾", TrackingMode.COUNT, ProductCategory.BABY, "包", daily = 0.5, restock = 3.0),
    Preset("奶粉", TrackingMode.LEVEL, ProductCategory.BABY, "罐", lifeDays = 14),
    Preset("洗洁精", TrackingMode.LEVEL, ProductCategory.HOUSEHOLD, "瓶", lifeDays = 40),
    Preset("鲜牛奶", TrackingMode.EXPIRY, ProductCategory.FOOD, "盒", lifeDays = 7, restock = 2.0),
    Preset("水果", TrackingMode.EXPIRY, ProductCategory.FOOD, "份", lifeDays = 5, restock = 1.0),
    Preset("抽纸", TrackingMode.COUNT, ProductCategory.HOUSEHOLD, "包", daily = 0.5, restock = 12.0)
)

private val LEVEL_OPTIONS = listOf(100 to "满", 75 to "75%", 50 to "50%", 25 to "25%")

@Composable
fun EditItemScreen(viewModel: HomeyViewModel, id: String?) {
    val c = Homey.colors
    val isEdit = id != null

    var loaded by rememberSaveable { mutableStateOf(!isEdit) }
    var name by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(TrackingMode.COUNT) }
    var category by rememberSaveable { mutableStateOf(ProductCategory.OTHER) }
    var unit by rememberSaveable { mutableStateOf("件") }
    var quantity by rememberSaveable { mutableStateOf(1) }
    var dailyText by rememberSaveable { mutableStateOf("1") }
    var level by rememberSaveable { mutableStateOf(100) }
    var lifeDays by rememberSaveable { mutableStateOf(30) }
    var expiresIn by rememberSaveable { mutableStateOf(7) }
    var remind by rememberSaveable { mutableStateOf(3) }
    var restockText by rememberSaveable { mutableStateOf("1") }
    var location by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var showMore by rememberSaveable { mutableStateOf(isEdit) }

    LaunchedEffect(id) {
        if (id != null && !loaded) {
            viewModel.loadProduct(id)?.let { p ->
                name = p.name
                mode = p.mode
                category = p.categoryEnum
                unit = p.unit
                dailyText = PredictionEngine.formatQty(p.dailyUsage)
                lifeDays = p.lifeDays
                remind = p.remindDaysAhead
                restockText = PredictionEngine.formatQty(p.restockAmount)
                location = p.location
                note = p.note
            }
            loaded = true
        }
    }

    fun applyPreset(p: Preset) {
        name = p.label
        mode = p.mode
        category = p.category
        unit = p.unit
        dailyText = PredictionEngine.formatQty(p.daily)
        lifeDays = p.lifeDays
        if (p.mode == TrackingMode.EXPIRY) expiresIn = p.lifeDays
        restockText = PredictionEngine.formatQty(p.restock)
    }

    fun save() {
        val daily = dailyText.replace('，', '.').toDoubleOrNull() ?: 0.0
        val restockAmount = restockText.replace('，', '.').toDoubleOrNull() ?: 1.0
        viewModel.saveItem(
            id,
            ItemDraft(
                name = name,
                category = category,
                mode = mode,
                unit = unit,
                quantity = quantity.toDouble(),
                dailyUsage = daily,
                levelPercent = level,
                lifeDays = if (mode == TrackingMode.EXPIRY && !isEdit && !showMore) expiresIn else lifeDays,
                expiresInDays = expiresIn,
                remindDaysAhead = remind,
                restockAmount = restockAmount,
                location = location,
                note = note
            )
        )
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = c.surface,
        unfocusedContainerColor = c.surface,
        unfocusedBorderColor = c.line,
        focusedBorderColor = c.green
    )

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.backFrom(Screen.Edit(id)) }) { Icon(Icons.Filled.Close, contentDescription = "关闭", tint = c.ink) }
            Text(
                if (isEdit) "编辑物品" else "添加物品",
                modifier = Modifier.weight(1f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = c.ink,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Box(Modifier.width(48.dp))
        }

        if (!loaded) return@Column

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            if (!isEdit) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("常用，点一下直接填好", fontSize = 13.sp, color = c.muted)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PRESETS) { p -> ChipButton(p.label, name == p.label, { applyPreset(p) }) }
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("叫什么") },
                placeholder = { Text("如：纸尿裤 L码") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("怎么记", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrackingMode.entries.forEach { m ->
                        ChoiceTile(
                            selected = mode == m,
                            onClick = { mode = m },
                            modifier = Modifier.weight(1f).height(76.dp)
                        ) {
                            Text(m.label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.ink)
                            Text(m.example, fontSize = 11.sp, color = c.muted)
                        }
                    }
                }
            }

            SectionCard {
                when (mode) {
                    TrackingMode.COUNT -> {
                        if (!isEdit) {
                            SettingRow("现在有") {
                                NumberStepper(quantity, { quantity = it }, unit, label = "数量")
                            }
                        }
                        SettingRow("每天大约用", subtitle = "填 0 表示常备品，不预测", showDivider = false) {
                            NumberField(dailyText, { dailyText = it }, unit, fieldColors)
                        }
                    }
                    TrackingMode.LEVEL -> {
                        if (!isEdit) {
                            Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("现在大概还剩", fontSize = 15.sp, color = c.ink)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    LEVEL_OPTIONS.forEach { (value, label) ->
                                        ChoiceTile(
                                            selected = level == value,
                                            onClick = { level = value },
                                            modifier = Modifier.weight(1f).height(44.dp),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.ink, modifier = Modifier.align(Alignment.CenterHorizontally))
                                        }
                                    }
                                }
                                Text("不用算精确，之后随手更新就好", fontSize = 12.sp, color = c.muted)
                            }
                        }
                        SettingRow("一整${unit}大约用", showDivider = false) {
                            NumberStepper(lifeDays, { lifeDays = it }, "天", label = "一整${unit}大约用几天", step = 5, min = 1, max = 999)
                        }
                    }
                    TrackingMode.EXPIRY -> {
                        if (!isEdit) {
                            SettingRow("数量") {
                                NumberStepper(quantity, { quantity = it }, unit, label = "数量")
                            }
                            SettingRow("几天后过期", showDivider = showMore) {
                                NumberStepper(expiresIn, { expiresIn = it }, "天", label = "几天后过期", max = 999)
                            }
                        }
                        if (isEdit || showMore) {
                            SettingRow("每次买回来大约能放", showDivider = false) {
                                NumberStepper(lifeDays, { lifeDays = it }, "天", label = "买回来能放几天", min = 1, max = 999)
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !showMore,
                enter = fadeIn(Motion.enter(Motion.FADE_MS)),
                exit = fadeOut(Motion.exit(Motion.FADE_MS)) + shrinkVertically(Motion.exit())
            ) {
                TextButton(onClick = {
                    showMore = true
                    if (mode == TrackingMode.EXPIRY) lifeDays = expiresIn.coerceAtLeast(1)
                }) {
                    Text("更多设置：分类、位置、提前几天提醒", color = c.green)
                }
            }
            AnimatedVisibility(
                visible = showMore,
                enter = expandVertically(Motion.enter()) + fadeIn(Motion.enter()),
                exit = shrinkVertically(Motion.exit()) + fadeOut(Motion.exit())
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("分类", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.ink)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ProductCategory.entries) { cat -> ChipButton(cat.label, category == cat, { category = cat }) }
                        }
                    }
                    SectionCard {
                        SettingRow("提前几天提醒我买", subtitle = "算上快递路上的时间") {
                            NumberStepper(remind, { remind = it }, "天", label = "提前几天提醒", max = 99)
                        }
                        if (mode != TrackingMode.LEVEL) {
                            SettingRow("每次大约买多少") { NumberField(restockText, { restockText = it }, unit, fieldColors) }
                        }
                        SettingRow("单位", showDivider = false) {
                            OutlinedTextField(
                                value = unit,
                                onValueChange = { unit = it.take(4) },
                                singleLine = true,
                                modifier = Modifier.width(96.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = fieldColors
                            )
                        }
                    }
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("放在哪（选填）") },
                        placeholder = { Text("如：主卧衣柜、冷藏室") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("备注（选填）") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors
                    )
                }
            }
            Box(Modifier.height(8.dp))
        }

        Button(
            onClick = { save() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.green, contentColor = c.onGreen)
        ) { Text("保存", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    unit: String,
    colors: androidx.compose.material3.TextFieldColors
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter { it.isDigit() || it == '.' || it == '，' }.take(6)) },
        singleLine = true,
        suffix = { Text(unit) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.width(120.dp),
        shape = RoundedCornerShape(12.dp),
        colors = colors
    )
}
