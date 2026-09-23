package com.example.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class ProductWithDetails(
    @Embedded val product: Product,
    @Relation(
        parentColumn = "id",
        entityColumn = "productId"
    )
    val inventories: List<Inventory> = emptyList(),
    @Relation(
        parentColumn = "id",
        entityColumn = "productId"
    )
    val usageCycles: List<UsageCycle> = emptyList(),
    @Relation(
        parentColumn = "id",
        entityColumn = "productId"
    )
    val purchases: List<Purchase> = emptyList()
)

data class PredictionResult(
    val product: Product,
    val totalStockQuantity: Double,
    val activeLevelPercent: Int?,
    val isUrgentReorder: Boolean,
    val isExpiringSoon: Boolean,
    val isExpired: Boolean,
    val daysUntilExpiry: Int?,
    val remainingDays: Int?,
    val remainingDaysRangeText: String, // e.g. "约 8 天", "约 2 周 (10/10～10/17)", "今天优先吃"
    val reorderPoint: Double,
    val suggestedActionText: String, // e.g. "建议今天购买", "今天优先食用", "库存充足", "已开封使用中"
    val statusTone: StatusTone, // URGENT_RED, WARNING_YELLOW, EXPIRY_ALERT, NORMAL_GREEN
    val openedItem: Inventory?,
    val expiringItem: Inventory?
)

enum class StatusTone {
    URGENT_RED,      // 🔴 缺货/紧急补货/今天过期
    WARNING_YELLOW,  // 🟡 预计缺货（7~14天内）
    EXPIRY_ALERT,    // ⚠️ 即将过期（1~3天内）
    NORMAL_GREEN     // 🟢 充足正常
}
