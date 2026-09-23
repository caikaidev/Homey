package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.BackupScreen
import com.example.ui.screens.DetailScreen
import com.example.ui.screens.EditItemScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.theme.Homey

@Composable
fun MainScreen(viewModel: HomeyViewModel) {
    val stack by viewModel.stack.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val screen = stack.last()

    BackHandler(enabled = stack.size > 1 || screen != Screen.Home) { viewModel.back() }

    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = m.text,
            actionLabel = m.actionLabel,
            duration = if (m.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) m.action?.invoke()
        viewModel.consumeMessage(m.key)
    }

    val showTabs = screen == Screen.Home || screen == Screen.Inventory
    Scaffold(
        containerColor = Homey.colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showTabs) {
                BottomBar(
                    current = screen,
                    onHome = { viewModel.switchTab(Screen.Home) },
                    onInventory = { viewModel.switchTab(Screen.Inventory) },
                    onAdd = { viewModel.open(Screen.Edit(null)) }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                Screen.Home -> HomeScreen(viewModel)
                Screen.Inventory -> InventoryScreen(viewModel)
                is Screen.Detail -> DetailScreen(viewModel, screen.id)
                is Screen.Edit -> EditItemScreen(viewModel, screen.id)
                Screen.Backup -> BackupScreen(viewModel)
            }
        }
    }
}

@Composable
private fun BottomBar(current: Screen, onHome: () -> Unit, onInventory: () -> Unit, onAdd: () -> Unit) {
    val c = Homey.colors
    Column(Modifier.fillMaxWidth().background(c.surface).windowInsetsPadding(WindowInsets.navigationBars)) {
        HorizontalDivider(color = c.line)
        Row(
            modifier = Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabItem("今天", current == Screen.Home, Icons.Filled.Home, Icons.Outlined.Home, onHome)
            FilledIconButton(
                onClick = onAdd,
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.coral, contentColor = c.onCoral)
            ) { Icon(Icons.Filled.Add, contentDescription = "添加物品", modifier = Modifier.size(28.dp)) }
            TabItem("物品", current == Screen.Inventory, Icons.Filled.Inventory2, Icons.Outlined.Inventory2, onInventory)
        }
    }
}

@Composable
private fun TabItem(label: String, selected: Boolean, on: ImageVector, off: ImageVector, onClick: () -> Unit) {
    val c = Homey.colors
    val color = if (selected) c.green else c.muted
    TextButton(onClick = onClick, modifier = Modifier.width(80.dp).height(56.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(if (selected) on else off, contentDescription = null, tint = color)
            Text(label, fontSize = 11.sp, color = color, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}
