package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Todo
import com.example.data.model.TodoStatus
import com.example.ui.SupplyViewModel
import com.example.ui.components.TagBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    viewModel: SupplyViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allTodos by viewModel.allTodos.collectAsState()
    var selectedFilterTab by remember { mutableIntStateOf(0) } // 0: 待处理, 1: 进行中, 2: 已完成, 3: 全部
    var showAddTodoDialog by remember { mutableStateOf(false) }

    val filteredTodos = when (selectedFilterTab) {
        0 -> allTodos.filter { it.status == TodoStatus.PENDING }
        1 -> allTodos.filter { it.status == TodoStatus.IN_PROGRESS }
        2 -> allTodos.filter { it.status == TodoStatus.COMPLETED }
        else -> allTodos
    }

    val dateFormat = SimpleDateFormat("M月d日 到期", Locale.getDefault())

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddTodoDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("todo_fab_add")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "新增家庭待办")
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "家庭协同待办",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "“老婆说过一次，但不应该再负责记住的事情”",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Filter chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabs = listOf(
                        "待处理 (${allTodos.count { it.status == TodoStatus.PENDING }})",
                        "进行中 (${allTodos.count { it.status == TodoStatus.IN_PROGRESS }})",
                        "已完成 (${allTodos.count { it.status == TodoStatus.COMPLETED }})",
                        "全部"
                    )
                    tabs.forEachIndexed { index, title ->
                        ElevatedFilterChip(
                            selected = selectedFilterTab == index,
                            onClick = { selectedFilterTab = index },
                            label = { Text(title, fontSize = 12.sp) },
                            colors = FilterChipDefaults.elevatedFilterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            if (filteredTodos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📋", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (selectedFilterTab == 0) "太棒了！所有家庭待办均已处理" else "此分类下暂无事务",
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredTodos, key = { it.id }) { todo ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("todo_item_${todo.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { viewModel.toggleTodo(todo) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = when (todo.status) {
                                            TodoStatus.COMPLETED -> Icons.Default.CheckCircle
                                            TodoStatus.IN_PROGRESS -> Icons.Default.Schedule
                                            TodoStatus.PENDING -> Icons.Default.RadioButtonUnchecked
                                        },
                                        contentDescription = "切换状态",
                                        tint = when (todo.status) {
                                            TodoStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                                            TodoStatus.IN_PROGRESS -> MaterialTheme.colorScheme.secondary
                                            TodoStatus.PENDING -> MaterialTheme.colorScheme.outline
                                        },
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = todo.title,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        textDecoration = if (todo.status == TodoStatus.COMPLETED) TextDecoration.LineThrough else TextDecoration.None,
                                        color = if (todo.status == TodoStatus.COMPLETED) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                    )

                                    if (todo.note.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = todo.note,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TagBadge(
                                            text = todo.category,
                                            color = MaterialTheme.colorScheme.primary,
                                            containerColor = MaterialTheme.colorScheme.primaryContainer
                                        )

                                        if (todo.dueAt != null) {
                                            Text(
                                                text = dateFormat.format(Date(todo.dueAt)),
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.secondary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        TagBadge(
                                            text = todo.status.label,
                                            color = when (todo.status) {
                                                TodoStatus.PENDING -> MaterialTheme.colorScheme.outline
                                                TodoStatus.IN_PROGRESS -> MaterialTheme.colorScheme.secondary
                                                TodoStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                                            },
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    }
                                }

                                Row {
                                    IconButton(
                                        onClick = { viewModel.syncTodoToCalendar(context, todo) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarMonth,
                                            contentDescription = "同步到系统日历",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.deleteTodo(todo.id) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "删除",
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Todo Dialog
    if (showAddTodoDialog) {
        var todoTitle by remember { mutableStateOf("") }
        var todoCategory by remember { mutableStateOf("宝宝供应链") }
        var todoNote by remember { mutableStateOf("") }
        var dueDays by remember { mutableStateOf("3") }

        AlertDialog(
            onDismissRequest = { showAddTodoDialog = false },
            title = { Text("新增家庭待办", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = todoTitle,
                        onValueChange = { todoTitle = it },
                        label = { Text("待办内容 *") },
                        placeholder = { Text("如：给宝宝买夏季透气凉鞋") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_todo_title")
                    )

                    OutlinedTextField(
                        value = todoCategory,
                        onValueChange = { todoCategory = it },
                        label = { Text("事务分类 (如：宝宝供应链 / 家电维护)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = dueDays,
                        onValueChange = { dueDays = it },
                        label = { Text("截止期限 (几天后)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = todoNote,
                        onValueChange = { todoNote = it },
                        label = { Text("补充细节/老婆嘱咐") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (todoTitle.isNotBlank()) {
                            val days = dueDays.toLongOrNull() ?: 3
                            val dueAt = System.currentTimeMillis() + days * 86400000L
                            viewModel.addTodo(
                                title = todoTitle,
                                category = todoCategory,
                                dueAt = dueAt,
                                note = todoNote
                            )
                            showAddTodoDialog = false
                        }
                    },
                    modifier = Modifier.testTag("btn_confirm_add_todo")
                ) {
                    Text("添加")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTodoDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
