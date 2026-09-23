package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.AddProductScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.ProductDetailScreen
import com.example.ui.screens.TodoScreen

@Composable
fun MainScreen(
    viewModel: SupplyViewModel,
    modifier: Modifier = Modifier
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val selectedProductId by viewModel.selectedProductId.collectAsState()
    val homeSummary by viewModel.homeSummary.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600

    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    if (currentScreen == "detail" && selectedProductId != null) {
        ProductDetailScreen(
            viewModel = viewModel,
            productId = selectedProductId!!,
            onBack = { viewModel.navigateTo("inventory") }
        )
        return
    }

    if (isWideScreen) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                NavigationRailItem(
                    selected = currentScreen == "home",
                    onClick = { viewModel.navigateTo("home") },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (homeSummary.urgentCount > 0) {
                                    Badge { Text("${homeSummary.urgentCount}") }
                                }
                            }
                        ) {
                            Icon(if (currentScreen == "home") Icons.Default.Home else Icons.Outlined.Home, contentDescription = "首页")
                        }
                    },
                    label = { Text("首页") }
                )

                NavigationRailItem(
                    selected = currentScreen == "inventory",
                    onClick = { viewModel.navigateTo("inventory") },
                    icon = {
                        Icon(if (currentScreen == "inventory") Icons.Default.Inventory2 else Icons.Outlined.Inventory2, contentDescription = "库存")
                    },
                    label = { Text("库存") }
                )

                NavigationRailItem(
                    selected = currentScreen == "add",
                    onClick = { viewModel.navigateTo("add") },
                    icon = {
                        Icon(if (currentScreen == "add") Icons.Default.AddCircle else Icons.Outlined.AddCircleOutline, contentDescription = "录入")
                    },
                    label = { Text("录入") }
                )

                NavigationRailItem(
                    selected = currentScreen == "todo",
                    onClick = { viewModel.navigateTo("todo") },
                    icon = {
                        Icon(if (currentScreen == "todo") Icons.Default.FactCheck else Icons.Outlined.FactCheck, contentDescription = "待办")
                    },
                    label = { Text("待办") }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when (currentScreen) {
                    "home" -> HomeScreen(viewModel = viewModel)
                    "inventory" -> InventoryScreen(viewModel = viewModel)
                    "add" -> AddProductScreen(viewModel = viewModel, onBack = { viewModel.navigateTo("home") })
                    "todo" -> TodoScreen(viewModel = viewModel)
                    else -> HomeScreen(viewModel = viewModel)
                }
            }
        }
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.testTag("main_bottom_nav")
                ) {
                    NavigationBarItem(
                        selected = currentScreen == "home",
                        onClick = { viewModel.navigateTo("home") },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (homeSummary.urgentCount > 0) {
                                        Badge { Text("${homeSummary.urgentCount}") }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (currentScreen == "home") Icons.Default.Home else Icons.Outlined.Home,
                                    contentDescription = "首页",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        },
                        label = { Text("首页", fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )

                    NavigationBarItem(
                        selected = currentScreen == "inventory",
                        onClick = { viewModel.navigateTo("inventory") },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == "inventory") Icons.Default.Inventory2 else Icons.Outlined.Inventory2,
                                contentDescription = "库存",
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = { Text("库存", fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )

                    NavigationBarItem(
                        selected = currentScreen == "add",
                        onClick = { viewModel.navigateTo("add") },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == "add") Icons.Default.AddCircle else Icons.Outlined.AddCircleOutline,
                                contentDescription = "录入",
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = { Text("录入", fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )

                    NavigationBarItem(
                        selected = currentScreen == "todo",
                        onClick = { viewModel.navigateTo("todo") },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == "todo") Icons.Default.FactCheck else Icons.Outlined.FactCheck,
                                contentDescription = "待办",
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = { Text("待办", fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            },
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentScreen) {
                    "home" -> HomeScreen(viewModel = viewModel)
                    "inventory" -> InventoryScreen(viewModel = viewModel)
                    "add" -> AddProductScreen(viewModel = viewModel, onBack = { viewModel.navigateTo("home") })
                    "todo" -> TodoScreen(viewModel = viewModel)
                    else -> HomeScreen(viewModel = viewModel)
                }
            }
        }
    }
}
