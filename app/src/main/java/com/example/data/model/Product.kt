package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val category: ProductCategory,
    val barcode: String = "",
    val trackingMode: TrackingMode,
    val safetyDays: Int = 3,       // 安全备用天数
    val leadTimeDays: Int = 2,     // 购买后送达天数
    val defaultDailyBurnRate: Double = 1.0, // COUNT/PACKAGE 模式日均消耗
    val defaultCycleDays: Int = 60, // CYCLE 模式默认预期使用天数
    val unit: String = "件",       // 片, 包, 瓶, 罐, 盒, 个
    val iconName: String = "package",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
