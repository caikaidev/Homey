package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Reminder
import com.example.data.model.ReminderStatus
import com.example.data.model.ReminderType
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE status = 'ACTIVE' ORDER BY scheduledAt ASC")
    fun getActiveReminders(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE type = :type AND targetId = :targetId LIMIT 1")
    suspend fun getReminderByTypeAndTarget(type: ReminderType, targetId: Long): Reminder?

    @Query("SELECT * FROM reminders WHERE calendarEventId = :eventId LIMIT 1")
    suspend fun getReminderByCalendarEventId(eventId: Long): Reminder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: Reminder): Long

    @Update
    suspend fun updateReminder(reminder: Reminder)

    @Delete
    suspend fun deleteReminder(reminder: Reminder)

    @Query("UPDATE reminders SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ReminderStatus)
}
