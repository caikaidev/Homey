package com.example.domain.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.data.model.PredictionResult
import com.example.data.model.Todo
import java.util.Calendar
import java.util.TimeZone

object CalendarHelper {

    fun hasCalendarPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_CALENDAR
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getPrimaryCalendarId(context: Context): Long? {
        if (!hasCalendarPermission(context)) return null
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.VISIBLE
        )
        val uri: Uri = CalendarContract.Calendars.CONTENT_URI
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, null, null, null)
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val isPrimary = cursor.getInt(1)
                    if (isPrimary == 1) {
                        return id
                    }
                }
                // If no explicitly primary, return the first visible calendar
                if (cursor.moveToFirst()) {
                    return cursor.getLong(0)
                }
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
        return 1L // Default fallback
    }

    /**
     * Merge items needing restock into a single consolidated calendar event: "🛒 家庭采购"
     * If an existing calendarEventId is provided, updates that event instead of creating a duplicate.
     */
    fun syncConsolidatedShoppingEvent(
        context: Context,
        urgentItems: List<PredictionResult>,
        existingEventId: Long? = null
    ): Long? {
        if (urgentItems.isEmpty()) return null

        val title = "🛒 家庭采购清单 (${urgentItems.size}项待买)"
        val descBuilder = StringBuilder("家庭补给管家自动同步的采购清单：\n\n")
        urgentItems.forEach { item ->
            descBuilder.append("□ ${item.product.name} (")
            when {
                item.remainingDays != null -> descBuilder.append("剩${item.remainingDays}天")
                item.activeLevelPercent != null -> descBuilder.append("余${item.activeLevelPercent}%")
                else -> descBuilder.append(item.remainingDaysRangeText)
            }
            descBuilder.append("，${item.suggestedActionText})\n")
        }
        descBuilder.append("\n由「家庭补给管家」接管维护，购买后在App中一键入库。")

        // Schedule for this coming weekend or tomorrow evening at 19:30
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 19)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
        }
        val startTime = cal.timeInMillis
        val endTime = startTime + 3600_000L // 1 hour duration

        if (hasCalendarPermission(context)) {
            val resolver = context.contentResolver
            val calendarId = getPrimaryCalendarId(context) ?: 1L

            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startTime)
                put(CalendarContract.Events.DTEND, endTime)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, descBuilder.toString())
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.HAS_ALARM, 1)
            }

            // Update existing event if present
            if (existingEventId != null && existingEventId > 0) {
                val updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingEventId)
                val rows = try {
                    resolver.update(updateUri, values, null, null)
                } catch (_: Exception) { 0 }
                if (rows > 0) {
                    return existingEventId
                }
            }

            // Otherwise insert new
            return try {
                val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
                uri?.lastPathSegment?.toLongOrNull()
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    /**
     * Create or update a calendar event for a single Todo
     */
    fun syncTodoCalendarEvent(
        context: Context,
        todo: Todo,
        existingEventId: Long? = null
    ): Long? {
        val title = "📋 家庭待办：${todo.title}"
        val desc = "分类：${todo.category}\n备注：${todo.note.ifBlank { "无" }}\n\n「家庭补给管家」代为提醒"
        val startTime = todo.dueAt ?: (System.currentTimeMillis() + 86400_000L)
        val endTime = startTime + 3600_000L

        if (hasCalendarPermission(context)) {
            val resolver = context.contentResolver
            val calendarId = getPrimaryCalendarId(context) ?: 1L

            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startTime)
                put(CalendarContract.Events.DTEND, endTime)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, desc)
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.HAS_ALARM, 1)
            }

            if (existingEventId != null && existingEventId > 0) {
                val updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingEventId)
                val rows = try {
                    resolver.update(updateUri, values, null, null)
                } catch (_: Exception) { 0 }
                if (rows > 0) return existingEventId
            }

            return try {
                val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
                uri?.lastPathSegment?.toLongOrNull()
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    /**
     * Delete calendar event by id
     */
    fun deleteCalendarEvent(context: Context, eventId: Long): Boolean {
        if (!hasCalendarPermission(context) || eventId <= 0) return false
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        return try {
            val count = context.contentResolver.delete(uri, null, null)
            count > 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Build system Intent to open Calendar app directly to add the shopping event
     */
    fun createShoppingCalendarIntent(urgentItems: List<PredictionResult>): Intent {
        val title = "🛒 家庭采购清单 (${urgentItems.size}项)"
        val descBuilder = StringBuilder("家庭采购待买：\n")
        urgentItems.forEach { item ->
            descBuilder.append("• ${item.product.name} (${item.remainingDaysRangeText})\n")
        }

        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 19)
            set(Calendar.MINUTE, 30)
        }

        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.DESCRIPTION, descBuilder.toString())
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, cal.timeInMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, cal.timeInMillis + 3600_000L)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
