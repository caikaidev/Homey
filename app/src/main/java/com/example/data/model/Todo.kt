package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TodoStatus(val label: String) {
    PENDING("待处理"),
    IN_PROGRESS("进行中"),
    COMPLETED("已完成")
}

@Entity(tableName = "todos")
data class Todo(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val category: String = "家庭事务", // 宝宝采购, 家电维护, 物业行政, 日常待办
    val dueAt: Long? = null,
    val completedAt: Long? = null,
    val reminderEnabled: Boolean = true,
    val calendarEventId: Long? = null,
    val status: TodoStatus = TodoStatus.PENDING,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
