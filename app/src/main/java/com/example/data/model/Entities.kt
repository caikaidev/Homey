package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

/**
 * 物品。主键是全局唯一的字符串 ID，导入/合并备份时不会和别的设备冲突。
 * 删除只打 [deletedAt] 标记（软删除），合并备份时不会把删掉的东西"复活"。
 * 任何会影响这件物品的改动（包括库存变化）都要刷新 [updatedAt]，合并时以它判断谁更新。
 */
@Entity(tableName = "products")
data class Product(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val category: String = ProductCategory.OTHER.name,
    val trackingMode: String = TrackingMode.COUNT.name,
    val unit: String = "件",
    /** COUNT：每天大约用多少个单位；0 表示常备品，不预测。 */
    val dailyUsage: Double = 1.0,
    /** LEVEL：一整瓶大约用多少天；EXPIRY：买回来大约能放多少天。 */
    val lifeDays: Int = 30,
    /** 提前几天提醒我买（原 送达天数 + 安全天数）。 */
    val remindDaysAhead: Int = 3,
    /** 点"买到了"时默认补多少。 */
    val restockAmount: Double = 1.0,
    val location: String = "",
    val note: String = "",
    val barcode: String = "",
    val isSample: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val deletedAt: Long? = null
)

// 用扩展属性而不是类内属性，避免 Room 把它们当成数据库列。
val Product.mode: TrackingMode get() = TrackingMode.fromCode(trackingMode)
val Product.categoryEnum: ProductCategory get() = ProductCategory.fromCode(category)

/** 一批库存。COUNT/LEVEL 通常只有一行；EXPIRY 每次买回来是一批，各自有到期日。 */
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
    @PrimaryKey val id: String = newId(),
    val productId: String,
    val quantity: Double = 0.0,
    val levelPercent: Int = 100,
    /** 到期当天 00:00 的时间戳。 */
    val expiresAt: Long? = null,
    val openedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
)

/** 库存流水：只追加、不修改。当前库存可以据此核对，以后也能用来学习真实消耗速度。 */
@Entity(
    tableName = "stock_logs",
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
data class StockLog(
    @PrimaryKey val id: String = newId(),
    val productId: String,
    val type: String,
    val delta: Double = 0.0,
    val levelPercent: Int? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** V1 暂不提供待办界面，但保留数据表，老用户的数据不会丢，并会一起导出。 */
@Entity(tableName = "todos")
data class Todo(
    @PrimaryKey val id: String = newId(),
    val title: String,
    val category: String = "家庭事务",
    val dueAt: Long? = null,
    val completedAt: Long? = null,
    val status: String = TodoStatus.PENDING.name,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val deletedAt: Long? = null
)
