package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.backup.BackupPreview
import com.example.data.backup.RestoreMode
import com.example.ui.HomeyViewModel
import com.example.ui.components.ChoiceTile
import com.example.ui.components.SectionCard
import com.example.ui.components.SettingRow
import com.example.ui.theme.Homey
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun BackupScreen(viewModel: HomeyViewModel) {
    val state by viewModel.backupState.collectAsStateWithLifecycle()
    val pending by viewModel.pendingRestore.collectAsStateWithLifecycle()
    val c = Homey.colors

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportTo(uri)
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.exportCsvTo(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.pickRestoreFile(uri)
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.setBackupFolder(uri)
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.back() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = c.ink)
            }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.green)

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("数据与备份", style = MaterialTheme.typography.headlineLarge, color = c.ink)

            StatusBanner(state.lastBackupAt, state.autoEnabled, state.productCount, state.logCount, onBackupNow = viewModel::backupNow)

            SectionCard {
                SettingRow("每天自动备份", subtitle = "保留最近 7 份") {
                    Switch(
                        checked = state.autoEnabled,
                        onCheckedChange = viewModel::setAutoBackup,
                        colors = SwitchDefaults.colors(checkedTrackColor = c.green, checkedThumbColor = c.onGreen)
                    )
                }
                SettingRow(
                    "备份位置",
                    subtitle = if (state.folderLabel == null) "目前只存在 App 内部，卸载会一起删除" else "App 内部 + 你选的文件夹",
                    showDivider = false
                ) {
                    TextButton(onClick = { folderLauncher.launch(null) }) {
                        Text(state.folderLabel ?: "选择文件夹", color = c.green, maxLines = 1)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("换手机或出问题时", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.muted, modifier = Modifier.padding(start = 4.dp))
                ActionTile(Icons.Filled.FileUpload, "导出完整备份", "一个 .json 文件，可发给自己或存网盘", enabled = !state.busy) {
                    exportLauncher.launch(viewModel.suggestedBackupName())
                }
                ActionTile(Icons.Filled.FileDownload, "从备份恢复", "选择之前导出或自动备份的文件", enabled = !state.busy) {
                    restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }
                ActionTile(Icons.Filled.TableChart, "导出为表格", ".csv，用 Excel 或 WPS 查看", enabled = !state.busy, outlined = false) {
                    csvLauncher.launch(viewModel.suggestedCsvName())
                }
                if (state.canUndoRestore) {
                    TextButton(onClick = viewModel::undoRestore, enabled = !state.busy) {
                        Text("撤回上一次恢复", color = c.urgent)
                    }
                }
            }

            if (state.sampleCount > 0) {
                TextButton(onClick = viewModel::clearSamples) { Text("清除 ${state.sampleCount} 件示例物品", color = c.green) }
            }

            Text(
                "数据只存在这台手机上。App 升级不会清空数据；换手机前请先导出一份。",
                fontSize = 12.sp,
                lineHeight = 20.sp,
                color = c.muted,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
        }
    }

    pending?.let { preview ->
        RestoreSheet(
            preview = preview,
            onCancel = viewModel::cancelRestore,
            onConfirm = viewModel::confirmRestore
        )
    }
}

@Composable
private fun StatusBanner(lastBackupAt: Long, autoEnabled: Boolean, products: Int, logs: Int, onBackupNow: () -> Unit) {
    val c = Homey.colors
    val ok = lastBackupAt > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (ok) c.greenSoft else c.warningSoft)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (ok) c.green else c.warning),
            contentAlignment = Alignment.Center
        ) {
            Icon(if (ok) Icons.Filled.Check else Icons.Filled.PriorityHigh, contentDescription = null, tint = c.surface)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                when {
                    ok -> "${formatTime(lastBackupAt)} 已备份"
                    autoEnabled -> "还没有备份过"
                    else -> "自动备份已关闭"
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = c.ink
            )
            Text("$products 件物品 · $logs 条库存记录", fontSize = 13.sp, color = c.muted)
        }
        TextButton(onClick = onBackupNow) { Text("立即备份", color = if (ok) c.green else c.warning) }
    }
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    outlined: Boolean = true,
    onClick: () -> Unit
) {
    val c = Homey.colors
    val shape = RoundedCornerShape(16.dp)
    val base = Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 64.dp)
        .clip(shape)
    Row(
        modifier = (if (outlined) base.background(c.surface).border(1.dp, c.line, shape) else base)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (outlined) c.green else c.muted)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = if (outlined) FontWeight.Medium else FontWeight.Normal, color = c.ink)
            Text(subtitle, fontSize = 12.sp, color = c.muted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestoreSheet(preview: BackupPreview, onCancel: () -> Unit, onConfirm: (RestoreMode) -> Unit) {
    val c = Homey.colors
    var mode by remember { mutableStateOf(RestoreMode.MERGE) }
    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("恢复这份备份？", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.ink)
                val date = if (preview.data.exportedAt > 0) " · 备份于 " + formatTime(preview.data.exportedAt) else ""
                Text(
                    "${preview.fileName}$date · ${preview.productCount} 件物品 · ${preview.logCount} 条记录",
                    fontSize = 13.sp,
                    color = c.muted
                )
            }
            RestoreOption("合并", "保留手机上现有的，补上备份里没有的；同一件物品以较新的为准", mode == RestoreMode.MERGE) { mode = RestoreMode.MERGE }
            RestoreOption("覆盖", "用备份完全替换现在的数据", mode == RestoreMode.REPLACE) { mode = RestoreMode.REPLACE }
            Text("恢复前会先自动备份当前数据，恢复错了也能撤回。", fontSize = 12.sp, color = c.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.stepperBg, contentColor = c.ink)
                ) { Text("取消") }
                Button(
                    onClick = { onConfirm(mode) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.green, contentColor = c.onGreen)
                ) { Text("恢复", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun RestoreOption(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    val c = Homey.colors
    ChoiceTile(selected = selected, onClick = onClick, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.ink)
        Text(desc, fontSize = 12.sp, color = c.muted)
    }
}

private fun formatTime(time: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = time }
    val hm = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val dayDiff = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> "今天 $hm"
        sameYear && dayDiff == 1 -> "昨天 $hm"
        sameYear -> SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(time))
        else -> SimpleDateFormat("yyyy年M月d日", Locale.getDefault()).format(Date(time))
    }
}
