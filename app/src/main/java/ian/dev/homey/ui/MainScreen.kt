package ian.dev.homey.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import ian.dev.homey.ui.screens.BackupScreen
import ian.dev.homey.ui.screens.DetailScreen
import ian.dev.homey.ui.screens.EditItemScreen
import ian.dev.homey.ui.screens.HomeScreen
import ian.dev.homey.ui.screens.InventoryScreen
import ian.dev.homey.ui.theme.Homey
import ian.dev.homey.ui.theme.Motion
import kotlinx.coroutines.withTimeoutOrNull

/** 带「撤销/撤回」按钮的提示显示多久；Material 默认的 Long 是 10 秒，太久了。 */
private const val ACTION_SNACKBAR_MS = 4_000L
private const val PLAIN_SNACKBAR_MS = 2_000L

private val TAB_BAR_HEIGHT = 76.dp

@Composable
fun MainScreen(viewModel: HomeyViewModel) {
    val stack by viewModel.stack.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val top = stack.last()
    val onTabs = top.isTab()

    BackHandler(enabled = stack.size > 1 || top != Screen.Home) { viewModel.back() }

    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        try {
            // 超时后协程取消，提示条随之收起；新提示到来时也会顶掉旧的
            withTimeoutOrNull(if (m.actionLabel != null) ACTION_SNACKBAR_MS else PLAIN_SNACKBAR_MS) {
                val result = snackbar.showSnackbar(
                    message = m.text,
                    actionLabel = m.actionLabel,
                    duration = SnackbarDuration.Indefinite
                )
                if (result == SnackbarResult.ActionPerformed) m.action?.invoke()
            }
        } finally {
            viewModel.consumeMessage(m.key)
        }
    }

    Scaffold(
        containerColor = Homey.colors.background,
        snackbarHost = {
            SnackbarHost(snackbar, modifier = Modifier.padding(bottom = if (onTabs) TAB_BAR_HEIGHT else 0.dp))
        }
    ) { padding ->
        AnimatedContent(
            targetState = stack,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentKey = { routeKey(it.last()) },
            transitionSpec = { navigationTransition() },
            label = "navigation"
        ) { target ->
            // 每个页面都铺满不透明背景，滑动时不会和下面的页面叠字
            Box(Modifier.fillMaxSize().background(Homey.colors.background)) {
                when (val screen = target.last()) {
                    Screen.Home, Screen.Inventory -> TabsHost(viewModel)
                    is Screen.Detail -> DetailScreen(viewModel, screen.id)
                    is Screen.Edit -> EditItemScreen(viewModel, screen.id)
                    Screen.Backup -> BackupScreen(viewModel)
                }
            }
        }
    }
}

private fun Screen.isTab() = this == Screen.Home || this == Screen.Inventory

/** 两个 Tab 共用一个 key：Tab 之间切换由 [TabsHost] 内部淡入淡出，底部栏保持不动。 */
private fun routeKey(screen: Screen): Any = if (screen.isTab()) "tabs" else screen

/**
 * 页面切换动效：
 * - 进入下一级（详情、数据与备份）：新页面从右侧滑入，旧页面向左轻移并淡出（视差）；
 * - 返回：当前页面向右滑出，盖在上一页上方离开；
 * - 添加/编辑：从底部升起，关闭时落回底部，像一张表单卡片。
 */
private fun AnimatedContentTransitionScope<List<Screen>>.navigationTransition(): ContentTransform {
    val from = initialState
    val to = targetState
    val forward = to.size > from.size
    val backward = to.size < from.size
    return when {
        forward && to.last() is Screen.Edit -> ContentTransform(
            targetContentEnter = slideInVertically(Motion.enter()) { it } + fadeIn(Motion.enter(Motion.FADE_MS)),
            initialContentExit = fadeOut(Motion.exit(Motion.PAGE_ENTER_MS)),
            sizeTransform = null
        )
        backward && from.last() is Screen.Edit -> ContentTransform(
            targetContentEnter = fadeIn(Motion.enter(Motion.FADE_MS)),
            initialContentExit = slideOutVertically(Motion.exit()) { it } + fadeOut(Motion.exit()),
            targetContentZIndex = -1f,
            sizeTransform = null
        )
        forward -> ContentTransform(
            targetContentEnter = slideInHorizontally(Motion.enter()) { it } + fadeIn(Motion.enter(Motion.FADE_MS)),
            initialContentExit = slideOutHorizontally(Motion.enter()) { -it / 4 } + fadeOut(Motion.enter()),
            sizeTransform = null
        )
        backward -> ContentTransform(
            targetContentEnter = slideInHorizontally(Motion.enter()) { -it / 4 } + fadeIn(Motion.enter()),
            initialContentExit = slideOutHorizontally(Motion.exit()) { it } + fadeOut(Motion.exit()),
            targetContentZIndex = -1f,
            sizeTransform = null
        )
        else -> fadeIn(Motion.enter(Motion.FADE_MS)) togetherWith fadeOut(Motion.exit(Motion.FADE_MS))
    }
}

/** 今天 / 物品 两个 Tab 与底部栏。Tab 是返回栈的根，所以当前 Tab 就是 stack.first()。 */
@Composable
private fun TabsHost(viewModel: HomeyViewModel) {
    val stack by viewModel.stack.collectAsStateWithLifecycle()
    val tab = stack.first()
    Column(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                fadeIn(Motion.enter(Motion.STATE_MS)) + slideInVertically(Motion.enter(Motion.STATE_MS)) { it / 40 } togetherWith
                    fadeOut(Motion.exit(Motion.FADE_MS))
            },
            label = "tabs"
        ) { current ->
            Box(Modifier.fillMaxSize().background(Homey.colors.background)) {
                if (current == Screen.Inventory) InventoryScreen(viewModel) else HomeScreen(viewModel)
            }
        }
        BottomBar(
            current = tab,
            onHome = { viewModel.switchTab(Screen.Home) },
            onInventory = { viewModel.switchTab(Screen.Inventory) },
            onAdd = { viewModel.open(Screen.Edit(null)) }
        )
    }
}

@Composable
private fun BottomBar(current: Screen, onHome: () -> Unit, onInventory: () -> Unit, onAdd: () -> Unit) {
    val c = Homey.colors
    Column(Modifier.fillMaxWidth().background(c.surface)) {
        HorizontalDivider(color = c.line)
        Row(
            modifier = Modifier.fillMaxWidth().height(TAB_BAR_HEIGHT).padding(horizontal = 24.dp),
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
