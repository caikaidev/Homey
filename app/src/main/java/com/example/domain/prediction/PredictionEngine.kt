package com.example.domain.prediction

import com.example.data.model.Inventory
import com.example.data.model.PredictionResult
import com.example.data.model.ProductWithDetails
import com.example.data.model.StatusTone
import com.example.data.model.TrackingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

object PredictionEngine {

    private const val ONE_DAY_MS = 86_400_000L

    fun calculate(item: ProductWithDetails, now: Long = System.currentTimeMillis()): PredictionResult {
        val product = item.product
        val inventories = item.inventories
        val usageCycles = item.usageCycles

        val totalStock = inventories.sumOf { it.quantity }
        val activeLevel = inventories.firstOrNull()?.levelPercent
        val openedItem = inventories.firstOrNull { it.openedAt != null }
        val expiringItem = inventories.filter { it.expiresAt != null }.minByOrNull { it.expiresAt!! }

        val dateFormat = SimpleDateFormat("M月d日", Locale.getDefault())

        when (product.trackingMode) {
            TrackingMode.COUNT -> {
                val dailyBurn = if (product.defaultDailyBurnRate > 0) product.defaultDailyBurnRate else 1.0
                val remainingDays = (totalStock / dailyBurn).toInt()
                val reorderPoint = dailyBurn * (product.leadTimeDays + product.safetyDays)
                val isUrgent = totalStock <= reorderPoint
                val isExpiringSoon = false

                val tone = when {
                    isUrgent || remainingDays <= 3 -> StatusTone.URGENT_RED
                    remainingDays <= 14 -> StatusTone.WARNING_YELLOW
                    else -> StatusTone.NORMAL_GREEN
                }

                val actionText = when {
                    totalStock <= 0 -> "已断货，请立即购买！"
                    isUrgent -> "建议今天购买 (库存已低于安全线${reorderPoint.toInt()}${product.unit})"
                    remainingDays <= 7 -> "近期需补货"
                    else -> "库存充足"
                }

                val rangeText = if (totalStock <= 0) "无库存" else "约剩 $remainingDays 天"

                return PredictionResult(
                    product = product,
                    totalStockQuantity = totalStock,
                    activeLevelPercent = activeLevel,
                    isUrgentReorder = isUrgent,
                    isExpiringSoon = isExpiringSoon,
                    isExpired = false,
                    daysUntilExpiry = null,
                    remainingDays = remainingDays,
                    remainingDaysRangeText = rangeText,
                    reorderPoint = reorderPoint,
                    suggestedActionText = actionText,
                    statusTone = tone,
                    openedItem = openedItem,
                    expiringItem = expiringItem
                )
            }

            TrackingMode.PACKAGE -> {
                val cycleDays = if (product.defaultCycleDays > 0) product.defaultCycleDays else 7
                val remainingDays = (totalStock * cycleDays).toInt()
                val reorderThresholdDays = product.leadTimeDays + product.safetyDays
                val isUrgent = remainingDays <= reorderThresholdDays || totalStock <= 1.0

                val tone = when {
                    isUrgent || totalStock <= 1.0 -> StatusTone.URGENT_RED
                    remainingDays <= 14 -> StatusTone.WARNING_YELLOW
                    else -> StatusTone.NORMAL_GREEN
                }

                val actionText = when {
                    totalStock <= 0 -> "无库存，立即补货"
                    isUrgent -> "建议今天购买 (仅剩 ${totalStock.toInt()}${product.unit})"
                    remainingDays <= 14 -> "消耗中，注意备货"
                    else -> "库存充裕"
                }

                return PredictionResult(
                    product = product,
                    totalStockQuantity = totalStock,
                    activeLevelPercent = activeLevel,
                    isUrgentReorder = isUrgent,
                    isExpiringSoon = false,
                    isExpired = false,
                    daysUntilExpiry = null,
                    remainingDays = remainingDays,
                    remainingDaysRangeText = "约剩 $remainingDays 天 (${totalStock.toInt()}${product.unit})",
                    reorderPoint = 1.0,
                    suggestedActionText = actionText,
                    statusTone = tone,
                    openedItem = openedItem,
                    expiringItem = expiringItem
                )
            }

            TrackingMode.CYCLE -> {
                // Median or average cycle duration
                val estimatedCycleDays = if (usageCycles.isNotEmpty()) {
                    val sorted = usageCycles.map { it.durationDays }.sorted()
                    sorted[sorted.size / 2]
                } else {
                    product.defaultCycleDays
                }

                val daysSinceOpen = if (openedItem?.openedAt != null) {
                    ((now - openedItem.openedAt) / ONE_DAY_MS).toInt().coerceAtLeast(0)
                } else 0

                val remainingDays = max(0, estimatedCycleDays - daysSinceOpen)
                val reorderDays = product.leadTimeDays + product.safetyDays
                val isUrgent = remainingDays <= reorderDays

                // Non-fake precision text
                val weeks = (remainingDays / 7).coerceAtLeast(0)
                val rangeText = when {
                    remainingDays <= 0 -> "即将用完"
                    weeks == 0 -> "约剩 $remainingDays 天"
                    weeks == 1 -> "约剩 1 周"
                    else -> "约还能用 $weeks 周"
                }

                val targetDate = now + remainingDays * ONE_DAY_MS
                val buyWindowStart = targetDate - (product.safetyDays * ONE_DAY_MS)
                val buyDateStr = dateFormat.format(Date(buyWindowStart))

                val tone = when {
                    isUrgent || remainingDays <= 5 -> StatusTone.URGENT_RED
                    remainingDays <= 14 -> StatusTone.WARNING_YELLOW
                    else -> StatusTone.NORMAL_GREEN
                }

                val actionText = when {
                    isUrgent -> "建议购买：${buyDateStr}左右"
                    remainingDays <= 14 -> "预计${buyDateStr}需采购"
                    else -> "正常使用中 (周期约${estimatedCycleDays}天)"
                }

                return PredictionResult(
                    product = product,
                    totalStockQuantity = if (openedItem != null) 1.0 else totalStock,
                    activeLevelPercent = activeLevel,
                    isUrgentReorder = isUrgent,
                    isExpiringSoon = false,
                    isExpired = false,
                    daysUntilExpiry = null,
                    remainingDays = remainingDays,
                    remainingDaysRangeText = rangeText,
                    reorderPoint = 1.0,
                    suggestedActionText = actionText,
                    statusTone = tone,
                    openedItem = openedItem,
                    expiringItem = expiringItem
                )
            }

            TrackingMode.LEVEL -> {
                val level = activeLevel ?: 100
                val isUrgent = level <= 25

                val tone = when {
                    level <= 25 -> StatusTone.URGENT_RED
                    level <= 50 -> StatusTone.WARNING_YELLOW
                    else -> StatusTone.NORMAL_GREEN
                }

                val actionText = when {
                    level <= 0 -> "已空瓶，请购买补货"
                    level <= 25 -> "余量仅剩 ${level}%，建议本周购买"
                    level <= 50 -> "余量半瓶 (${level}%)，保持关注"
                    else -> "余量充足 (${level}%)"
                }

                return PredictionResult(
                    product = product,
                    totalStockQuantity = totalStock,
                    activeLevelPercent = level,
                    isUrgentReorder = isUrgent,
                    isExpiringSoon = false,
                    isExpired = false,
                    daysUntilExpiry = null,
                    remainingDays = null,
                    remainingDaysRangeText = "剩余 $level%",
                    reorderPoint = 25.0,
                    suggestedActionText = actionText,
                    statusTone = tone,
                    openedItem = openedItem,
                    expiringItem = expiringItem
                )
            }

            TrackingMode.EXPIRY -> {
                val expiryTime = expiringItem?.expiresAt
                val daysUntil = if (expiryTime != null) {
                    val diff = expiryTime - now
                    if (diff < 0) {
                        -(( -diff / ONE_DAY_MS ).toInt() + 1)
                    } else {
                        (diff / ONE_DAY_MS).toInt()
                    }
                } else null

                val isExpired = daysUntil != null && daysUntil < 0
                val isExpiringSoon = daysUntil != null && daysUntil <= 2

                val tone = when {
                    isExpired -> StatusTone.URGENT_RED
                    daysUntil == 0 || daysUntil == 1 -> StatusTone.URGENT_RED
                    daysUntil != null && daysUntil <= 3 -> StatusTone.EXPIRY_ALERT
                    else -> StatusTone.NORMAL_GREEN
                }

                val actionText = when {
                    isExpired -> "已过期 ${-daysUntil!!} 天，请及时清理"
                    daysUntil == 0 -> "⚠️ 今天到期，优先食用！"
                    daysUntil == 1 -> "⚠️ 明天到期，建议今天优先吃"
                    daysUntil != null && daysUntil <= 3 -> "还有 $daysUntil 天到期，尽快食用"
                    else -> "保质期内良好"
                }

                val rangeText = when {
                    daysUntil == null -> "未设保质期"
                    isExpired -> "已过期 ${-daysUntil}天"
                    daysUntil == 0 -> "今天到期"
                    daysUntil == 1 -> "明天到期"
                    else -> "剩 $daysUntil 天"
                }

                return PredictionResult(
                    product = product,
                    totalStockQuantity = totalStock,
                    activeLevelPercent = activeLevel,
                    isUrgentReorder = false,
                    isExpiringSoon = isExpiringSoon,
                    isExpired = isExpired,
                    daysUntilExpiry = daysUntil,
                    remainingDays = daysUntil,
                    remainingDaysRangeText = rangeText,
                    reorderPoint = 0.0,
                    suggestedActionText = actionText,
                    statusTone = tone,
                    openedItem = openedItem,
                    expiringItem = expiringItem
                )
            }

            TrackingMode.NONE -> {
                return PredictionResult(
                    product = product,
                    totalStockQuantity = totalStock,
                    activeLevelPercent = activeLevel,
                    isUrgentReorder = false,
                    isExpiringSoon = false,
                    isExpired = false,
                    daysUntilExpiry = null,
                    remainingDays = null,
                    remainingDaysRangeText = "长期在库",
                    reorderPoint = 0.0,
                    suggestedActionText = "常备用品",
                    statusTone = StatusTone.NORMAL_GREEN,
                    openedItem = openedItem,
                    expiringItem = expiringItem
                )
            }
        }
    }
}
