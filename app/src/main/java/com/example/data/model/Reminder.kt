package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ReminderType {
    REORDER,    // 补货提醒
    EXPIRY,     // 即将过期提醒
    TODO        // 待办提醒
}

enum class ReminderStatus {
    ACTIVE,
    SNOOZED,
    DISMISSED,
    ACTIONED
}

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: ReminderType,
    val targetId: Long,             // productId 或 todoId
    val scheduledAt: Long,
    val calendarEventId: Long? = null,
    val status: ReminderStatus = ReminderStatus.ACTIVE,
    val title: String,
    val description: String,
    val createdAt: Long = System.currentTimeMillis()
)
