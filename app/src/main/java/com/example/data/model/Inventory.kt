package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inventories",
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
data class Inventory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val productId: Long,
    val quantity: Double = 0.0,       // COUNT / PACKAGE 数量
    val levelPercent: Int = 100,      // LEVEL 模式 (100, 75, 50, 25, 0)
    val purchasedAt: Long = System.currentTimeMillis(),
    val openedAt: Long? = null,       // 开封时间
    val expiresAt: Long? = null,      // 过期时间
    val batchNote: String = ""        // 批次说明，如 "主卧卫生间"、"厨房"
)
