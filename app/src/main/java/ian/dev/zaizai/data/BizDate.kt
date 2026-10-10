package ian.dev.zaizai.data

import java.util.Locale

/**
 * 业务日期按北京时间（固定 UTC+8，无夏令时）计算，不受手机时区影响。
 * 不用 java.time：minSdk 24 上没有它，也不想为此开 desugaring。
 */
object BizDate {
    private const val OFFSET_MS = 8 * 3_600_000L
    private const val DAY_MS = 86_400_000L

    /** 时间戳 → `YYYY-MM-DD`。 */
    fun date(ms: Long): String {
        // Howard Hinnant 的 civil_from_days
        val z = Math.floorDiv(ms + OFFSET_MS, DAY_MS) + 719_468
        val era = Math.floorDiv(z, 146_097L)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val day = doy - (153 * mp + 2) / 5 + 1
        val month = if (mp < 10) mp + 3 else mp - 9
        val year = yoe + era * 400 + if (month <= 2) 1 else 0
        return String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day)
    }

    /** 时间戳 → `HH:MM`（北京时间）。 */
    fun hhmm(ms: Long): String {
        val minutes = Math.floorMod(ms + OFFSET_MS, DAY_MS) / 60_000
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)
    }

    /** 时间戳 → `星期六`（北京时间）。1970-01-01 是星期四。 */
    fun weekday(ms: Long): String {
        val days = Math.floorDiv(ms + OFFSET_MS, DAY_MS)
        return "星期" + "日一二三四五六"[Math.floorMod(days + 4, 7L).toInt()]
    }

    /** `2026-10-10` → `10月10日`。 */
    fun monthDay(date: String): String {
        val parts = date.split('-')
        val month = parts.getOrNull(1)?.toIntOrNull() ?: return date
        val day = parts.getOrNull(2)?.toIntOrNull() ?: return date
        return "${month}月${day}日"
    }
}
