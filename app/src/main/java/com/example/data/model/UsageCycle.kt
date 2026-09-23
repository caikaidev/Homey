package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "usage_cycles",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["productId"])]
)
data class UsageCycle(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val productId: Long,
    val openedAt: Long,
    val usedUpAt: Long,
    val durationDays: Int
)
