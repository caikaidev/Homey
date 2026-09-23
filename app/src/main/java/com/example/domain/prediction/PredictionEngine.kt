package com.example.domain.prediction

import com.example.data.model.Inventory
import com.example.data.model.Product
import com.example.data.model.TrackingMode
import com.example.data.model.mode
import java.util.Calendar
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

enum class Tone { URGENT, WARNING, OK }

/** 一件物品在"此刻"的状态，界面和通知都只读这个。纯计算，不访问数据库。 */
data class ItemStatus(
    val product: Product,
    val mode: TrackingMode,
    val quantity: Double,
    val levelPercent: Int,
    /** COUNT/LEVEL：大约还能用几天；null 表示不预测（常备品）。 */
    val daysLeft: Int?,
    /** EXPIRY：最近一批还有几天到期（负数表示已过期）。 */
    val expiresInDays: Int?,
    val tone: Tone,
    /** 进"该买了"。 */
    val needsBuy: Boolean,
    /** 进"快过期，先吃掉"。 */
    val expiringSoon: Boolean,
    /** 进"一周内会用完"。 */
    val runsOutSoon: Boolean,
    /** 列表右侧的短状态，如"约 3 天"、"剩 25%"、"明天到期"。 */
    val statusText: String,
    /** 首页一行的原因说明，如"剩 12 片，约 1 天用完"。 */
    val reasonText: String
) {
    val id: String get() = product.id
}

object PredictionEngine {

    const val DAY_MS = 86_400_000L

    fun evaluate(product: Product, inventories: List<Inventory>, now: Long = System.currentTimeMillis()): ItemStatus =
        when (product.mode) {
            TrackingMode.COUNT -> count(product, inventories)
            TrackingMode.LEVEL -> level(product, inventories)
            TrackingMode.EXPIRY -> expiry(product, inventories, now)
        }

    private fun count(p: Product, inv: List<Inventory>): ItemStatus {
        val qty = inv.sumOf { it.quantity }.coerceAtLeast(0.0)
        val q = formatQty(qty) + p.unit
        if (p.dailyUsage <= 0.0) {
            val out = qty <= 0.0
            return ItemStatus(
                product = p, mode = TrackingMode.COUNT, quantity = qty, levelPercent = 100,
                daysLeft = null, expiresInDays = null,
                tone = if (out) Tone.URGENT else Tone.OK,
                needsBuy = out, expiringSoon = false, runsOutSoon = false,
                statusText = if (out) "已用完" else "剩 $q",
                reasonText = if (out) "已经用完了" else "剩 $q"
            )
        }
        val days = floor(qty / p.dailyUsage).toInt()
        val needsBuy = qty <= 0.0 || days <= p.remindDaysAhead
        val soon = !needsBuy && days <= p.remindDaysAhead + 7
        return ItemStatus(
            product = p, mode = TrackingMode.COUNT, quantity = qty, levelPercent = 100,
            daysLeft = days, expiresInDays = null,
            tone = when { needsBuy -> Tone.URGENT; soon -> Tone.WARNING; else -> Tone.OK },
            needsBuy = needsBuy, expiringSoon = false, runsOutSoon = soon,
            statusText = if (qty <= 0.0) "已用完" else "约 $days 天",
            reasonText = when {
                qty <= 0.0 -> "已经用完了"
                days == 0 -> "剩 $q，今天就会用完"
                else -> "剩 $q，约 $days 天用完"
            }
        )
    }

    private fun level(p: Product, inv: List<Inventory>): ItemStatus {
        val level = (inv.minByOrNull { it.createdAt }?.levelPercent ?: 0).coerceIn(0, 100)
        val days = floor(level / 100.0 * max(1, p.lifeDays)).toInt()
        val needsBuy = level <= 25 || days <= p.remindDaysAhead
        val soon = !needsBuy && (days <= p.remindDaysAhead + 7 || level <= 50)
        return ItemStatus(
            product = p, mode = TrackingMode.LEVEL, quantity = inv.sumOf { it.quantity }, levelPercent = level,
            daysLeft = days, expiresInDays = null,
            tone = when { needsBuy -> Tone.URGENT; soon -> Tone.WARNING; else -> Tone.OK },
            needsBuy = needsBuy, expiringSoon = false, runsOutSoon = soon,
            statusText = if (level <= 0) "已用完" else "剩 $level%",
            reasonText = if (level <= 0) "已经用完了" else "余量约 $level%，约 $days 天用完"
        )
    }

    private fun expiry(p: Product, inv: List<Inventory>, now: Long): ItemStatus {
        val live = inv.filter { it.quantity > 0.0 }
        val qty = live.sumOf { it.quantity }
        val nearest = live.mapNotNull { it.expiresAt }.minOrNull()
        val inDays = nearest?.let { daysBetween(now, it) }
        val out = qty <= 0.0
        val soon = !out && inDays != null && inDays <= 3
        val status = when {
            out -> "吃完了"
            inDays == null -> "剩 ${formatQty(qty)}${p.unit}"
            inDays < 0 -> "已过期"
            inDays == 0 -> "今天到期"
            inDays == 1 -> "明天到期"
            else -> "$inDays 天后到期"
        }
        return ItemStatus(
            product = p, mode = TrackingMode.EXPIRY, quantity = qty, levelPercent = 100,
            daysLeft = inDays, expiresInDays = inDays,
            tone = when {
                out -> Tone.URGENT
                inDays != null && inDays <= 1 -> Tone.URGENT
                soon -> Tone.WARNING
                else -> Tone.OK
            },
            needsBuy = out, expiringSoon = soon, runsOutSoon = false,
            statusText = status,
            reasonText = if (out) "已经吃完了" else "剩 ${formatQty(qty)}${p.unit}"
        )
    }

    /** 两个时间点相差的自然日（按本地时区）。 */
    fun daysBetween(from: Long, to: Long): Int =
        ((startOfDay(to) - startOfDay(from)).toDouble() / DAY_MS).roundToInt()

    fun startOfDay(time: Long): Long = Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun formatQty(value: Double): String =
        if (value == floor(value)) value.toLong().toString() else String.format(Locale.US, "%.1f", value)

    /** 首页排序：越紧急越靠前。 */
    fun urgencyKey(s: ItemStatus): Int = when (s.tone) {
        Tone.URGENT -> 0
        Tone.WARNING -> 1000
        Tone.OK -> 2000
    } + (s.daysLeft ?: 999).coerceIn(-100, 999)
}
