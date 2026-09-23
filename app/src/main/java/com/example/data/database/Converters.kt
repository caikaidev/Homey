package com.example.data.database

import androidx.room.TypeConverter
import com.example.data.model.ProductCategory
import com.example.data.model.ReminderStatus
import com.example.data.model.ReminderType
import com.example.data.model.TodoStatus
import com.example.data.model.TrackingMode

class Converters {
    @TypeConverter
    fun fromTrackingMode(value: TrackingMode?): String? = value?.name

    @TypeConverter
    fun toTrackingMode(value: String?): TrackingMode? =
        value?.let { runCatching { TrackingMode.valueOf(it) }.getOrDefault(TrackingMode.COUNT) }

    @TypeConverter
    fun fromProductCategory(value: ProductCategory?): String? = value?.name

    @TypeConverter
    fun toProductCategory(value: String?): ProductCategory? =
        value?.let { ProductCategory.fromString(it) }

    @TypeConverter
    fun fromTodoStatus(value: TodoStatus?): String? = value?.name

    @TypeConverter
    fun toTodoStatus(value: String?): TodoStatus? =
        value?.let { runCatching { TodoStatus.valueOf(it) }.getOrDefault(TodoStatus.PENDING) }

    @TypeConverter
    fun fromReminderType(value: ReminderType?): String? = value?.name

    @TypeConverter
    fun toReminderType(value: String?): ReminderType? =
        value?.let { runCatching { ReminderType.valueOf(it) }.getOrDefault(ReminderType.REORDER) }

    @TypeConverter
    fun fromReminderStatus(value: ReminderStatus?): String? = value?.name

    @TypeConverter
    fun toReminderStatus(value: String?): ReminderStatus? =
        value?.let { runCatching { ReminderStatus.valueOf(it) }.getOrDefault(ReminderStatus.ACTIVE) }
}
