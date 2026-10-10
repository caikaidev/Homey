package ian.dev.zaizai.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BizDateTest {
    // 2026-10-09T15:59:00Z = 北京时间 10-09 23:59；一分钟后到 10-10 00:00
    private val beforeMidnight = 1_791_561_540_000L

    @Test
    fun `23点59和00点01分属两天，与手机时区无关`() {
        assertEquals("2026-10-09", BizDate.date(beforeMidnight))
        assertEquals("23:59", BizDate.hhmm(beforeMidnight))
        assertEquals("2026-10-10", BizDate.date(beforeMidnight + 2 * 60_000))
        assertEquals("00:01", BizDate.hhmm(beforeMidnight + 2 * 60_000))
    }

    @Test
    fun `跨年和闰年`() {
        assertEquals("1970-01-01", BizDate.date(0))
        assertEquals("2024-02-29", BizDate.date(1_709_136_000_000L)) // 2024-02-28T16:00Z
        assertEquals("2027-01-01", BizDate.date(1_798_732_800_000L)) // 2026-12-31T16:00Z
    }

    @Test
    fun monthDay() {
        assertEquals("10月10日", BizDate.monthDay("2026-10-10"))
        assertEquals("1月2日", BizDate.monthDay("2027-01-02"))
        assertEquals("bad", BizDate.monthDay("bad"))
    }

    @Test
    fun weekday() {
        assertEquals("星期四", BizDate.weekday(0))
        // 2026-10-10 是星期六；北京时间 00:30 时 UTC 还是前一天
        assertEquals("星期六", BizDate.weekday(1_791_639_060_000L))
        assertEquals("星期六", BizDate.weekday(1_791_563_400_000L))
    }
}
