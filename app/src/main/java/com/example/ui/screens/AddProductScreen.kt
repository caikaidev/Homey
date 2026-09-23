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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ProductCategory
import com.example.data.model.TrackingMode
import com.example.ui.SupplyViewModel
import com.example.ui.components.PresetLibrary
import com.example.ui.components.QuickTemplateItem
import com.example.ui.components.TagBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductScreen(
    viewModel: SupplyViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("📷 拍照OCR", "▣ 扫码录入", "⚡ 快捷模板", "✏️ 手动录入")

    // Form states
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ProductCategory.BABY) }
    var trackingMode by remember { mutableStateOf(TrackingMode.COUNT) }
    var unit by remember { mutableStateOf("片") }
    var initialQty by remember { mutableStateOf("40") }
    var barcode by remember { mutableStateOf("") }
    var safetyDays by remember { mutableStateOf("3") }
    var leadTimeDays by remember { mutableStateOf("2") }
    var burnRate by remember { mutableStateOf("5") }
    var note by remember { mutableStateOf("") }
    var batchNote by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("录入家庭用品", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_add_back")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontSize = 13.sp, maxLines = 1) }
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    // OCR Snap recognition simulation & fast capture
                    OcrCaptureView(
                        onExtracted = { tName, tCat, tMode, tUnit, tQty, tBarcode, tNote ->
                            name = tName
                            category = tCat
                            trackingMode = tMode
                            unit = tUnit
                            initialQty = tQty
                            barcode = tBarcode
                            note = tNote
                            selectedTab = 3 // Switch to manual confirm
                        }
                    )
                }

                1 -> {
                    // Barcode scanner simulation & match
                    BarcodeScannerView(
                        onBarcodeScanned = { tName, tCat, tMode, tUnit, tQty, tBarcode, tNote ->
                            name = tName
                            category = tCat
                            trackingMode = tMode
                            unit = tUnit
                            initialQty = tQty
                            barcode = tBarcode
                            note = tNote
                            selectedTab = 3 // Switch to manual confirm
                        }
                    )
                }

                2 -> {
                    // Presets
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text(
                                text = "常用家庭与宝宝用品模板（点击一键载入）",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        items(PresetLibrary.items) { item ->
                            PresetItemCard(
                                item = item,
                                onClick = {
                                    name = item.name
                                    category = item.category
                                    trackingMode = item.trackingMode
                                    unit = item.unit
                                    initialQty = item.defaultQty.toInt().toString()
                                    safetyDays = item.safetyDays.toString()
                                    leadTimeDays = item.leadTimeDays.toString()
                                    burnRate = item.dailyBurnRate.toString()
                                    barcode = item.barcode
                                    note = item.note
                                    selectedTab = 3
                                }
                            )
                        }
                    }
                }

                3 -> {
                    // Manual Form
                    ManualAddForm(
                        name = name,
                        onNameChange = { name = it },
                        category = category,
                        onCategoryChange = { category = it },
                        trackingMode = trackingMode,
                        onTrackingModeChange = {
                            trackingMode = it
                            unit = when (it) {
                                TrackingMode.COUNT -> "片"
                                TrackingMode.PACKAGE -> "包"
                                TrackingMode.CYCLE -> "支"
                                TrackingMode.LEVEL -> "瓶"
                                TrackingMode.EXPIRY -> "盒"
                                TrackingMode.NONE -> "件"
                            }
                        },
                        unit = unit,
                        onUnitChange = { unit = it },
                        initialQty = initialQty,
                        onQtyChange = { initialQty = it },
                        barcode = barcode,
                        onBarcodeChange = { barcode = it },
                        safetyDays = safetyDays,
                        onSafetyDaysChange = { safetyDays = it },
                        leadTimeDays = leadTimeDays,
                        onLeadTimeDaysChange = { leadTimeDays = it },
                        burnRate = burnRate,
                        onBurnRateChange = { burnRate = it },
                        note = note,
                        onNoteChange = { note = it },
                        batchNote = batchNote,
                        onBatchNoteChange = { batchNote = it },
                        onSubmit = {
                            if (name.isNotBlank()) {
                                viewModel.addProduct(
                                    name = name,
                                    category = category,
                                    trackingMode = trackingMode,
                                    unit = unit,
                                    initialQty = initialQty.toDoubleOrNull() ?: 1.0,
                                    safetyDays = safetyDays.toIntOrNull() ?: 3,
                                    leadTimeDays = leadTimeDays.toIntOrNull() ?: 2,
                                    dailyBurnRate = burnRate.toDoubleOrNull() ?: 1.0,
                                    barcode = barcode,
                                    batchNote = batchNote,
                                    note = note,
                                    expiresAt = if (trackingMode == TrackingMode.EXPIRY) System.currentTimeMillis() + 86400000L * 3 else null
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun OcrCaptureView(
    onExtracted: (String, ProductCategory, TrackingMode, String, String, String, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mock Camera Viewfinder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E2421)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "对准商品包装或标签文字",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
                Text(
                    text = "系统将自动提取商品名称、规格与建议消耗方式",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "常见样品 OCR 快速识别测试：",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(10.dp))

        val ocrSamples = listOf(
            Triple("花王妙而舒纸尿裤 L 44片", "44", TrackingMode.COUNT) to ("4901301230882" to ProductCategory.BABY),
            Triple("贝亲柔湿巾 80抽纯水", "3", TrackingMode.PACKAGE) to ("6927902800115" to ProductCategory.BABY),
            Triple("蓝臻智利新鲜蓝莓 125g", "1", TrackingMode.EXPIRY) to ("8410293810023" to ProductCategory.FOOD),
            Triple("丝塔芙大白罐保湿霜 550g", "1", TrackingMode.CYCLE) to ("3499320008549" to ProductCategory.BABY)
        )

        ocrSamples.forEach { (first, second) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable {
                        onExtracted(first.first, second.second, first.third, if (first.third == TrackingMode.COUNT) "片" else "件", first.second, second.first, "OCR 识别入库")
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(first.first, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("识别模式：${first.third.label} · 规格：${first.second}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = {
                            onExtracted(first.first, second.second, first.third, if (first.third == TrackingMode.COUNT) "片" else "件", first.second, second.first, "OCR 识别入库")
                        },
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("载入确认", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun BarcodeScannerView(
    onBarcodeScanned: (String, ProductCategory, TrackingMode, String, String, String, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1B221E)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(54.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "将条形码置于扫描框内",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "常用家庭条码库快速模拟：",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(8.dp))

        PresetLibrary.items.take(4).forEach { item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable {
                        onBarcodeScanned(
                            item.name,
                            item.category,
                            item.trackingMode,
                            item.unit,
                            item.defaultQty.toInt().toString(),
                            item.barcode,
                            item.note
                        )
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(item.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("条码：${item.barcode}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("点击扫入", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun PresetItemCard(
    item: QuickTemplateItem,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.category.icon, fontSize = 22.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(item.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        text = "${item.category.label} · 推荐模式：${item.trackingMode.label}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TagBadge(
                text = "+ 填入",
                color = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        }
    }
}

@Composable
fun ManualAddForm(
    name: String,
    onNameChange: (String) -> Unit,
    category: ProductCategory,
    onCategoryChange: (ProductCategory) -> Unit,
    trackingMode: TrackingMode,
    onTrackingModeChange: (TrackingMode) -> Unit,
    unit: String,
    onUnitChange: (String) -> Unit,
    initialQty: String,
    onQtyChange: (String) -> Unit,
    barcode: String,
    onBarcodeChange: (String) -> Unit,
    safetyDays: String,
    onSafetyDaysChange: (String) -> Unit,
    leadTimeDays: String,
    onLeadTimeDaysChange: (String) -> Unit,
    burnRate: String,
    onBurnRateChange: (String) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    batchNote: String,
    onBatchNoteChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("商品/用品名称 *") },
                placeholder = { Text("如：花王妙而舒纸尿裤 L") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_add_name")
            )
        }

        item {
            Text("所属家庭场景：", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ProductCategory.entries.take(4).forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { onCategoryChange(cat) },
                        label = { Text("${cat.icon} ${cat.label}", fontSize = 11.sp) }
                    )
                }
            }
        }

        item {
            Text("消耗追踪模式：", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TrackingMode.entries.forEach { mode ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (trackingMode == mode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrackingModeChange(mode) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = mode.label,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = if (trackingMode == mode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = mode.description,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (trackingMode == mode) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = initialQty,
                    onValueChange = onQtyChange,
                    label = { Text("初始库存量") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = onUnitChange,
                    label = { Text("计量单位") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = leadTimeDays,
                    onValueChange = onLeadTimeDaysChange,
                    label = { Text("快递送达(天)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = safetyDays,
                    onValueChange = onSafetyDaysChange,
                    label = { Text("安全库存备用(天)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            OutlinedTextField(
                value = barcode,
                onValueChange = onBarcodeChange,
                label = { Text("条形码 (选填)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = batchNote,
                onValueChange = onBatchNoteChange,
                label = { Text("存放位置 (如：主卧卫生间/冷藏室)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                label = { Text("备注说明") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_submit_add_product"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("确认添加到管家", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
