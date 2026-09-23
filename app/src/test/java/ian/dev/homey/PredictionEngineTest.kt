package ian.dev.homey

import ian.dev.homey.data.model.Inventory
import ian.dev.homey.data.model.Product
import ian.dev.homey.data.model.TrackingMode
import ian.dev.homey.domain.prediction.PredictionEngine
import ian.dev.homey.domain.prediction.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictionEngineTest {

    private val now = 1_790_000_000_000L
    private val day = PredictionEngine.DAY_MS

    private fun product(mode: TrackingMode, daily: Double = 1.0, life: Int = 30, remind: Int = 3) = Product(
        id = "p", name = "测试", trackingMode = mode.name, unit = "片",
        dailyUsage = daily, lifeDays = life, remindDaysAhead = remind, createdAt = now
    )

    @Test
    fun `count - within remind window needs buy`() {
        val s = PredictionEngine.evaluate(product(TrackingMode.COUNT, daily = 8.0), listOf(Inventory(productId = "p", quantity = 12.0)), now)
        assertEquals(1, s.daysLeft)
        assertTrue(s.needsBuy)
        assertEquals(Tone.URGENT, s.tone)
        assertEquals("剩 12片，约 1 天用完", s.reasonText)
    }

    @Test
    fun `count - within a week after remind window runs out soon`() {
        val s = PredictionEngine.evaluate(product(TrackingMode.COUNT, daily = 1.0), listOf(Inventory(productId = "p", quantity = 6.0)), now)
        assertFalse(s.needsBuy)
        assertTrue(s.runsOutSoon)
        assertEquals(Tone.WARNING, s.tone)
    }

    @Test
    fun `count - zero daily usage is a staple and never predicted`() {
        val s = PredictionEngine.evaluate(product(TrackingMode.COUNT, daily = 0.0), listOf(Inventory(productId = "p", quantity = 2.0)), now)
        assertNull(s.daysLeft)
        assertFalse(s.needsBuy)
        val empty = PredictionEngine.evaluate(product(TrackingMode.COUNT, daily = 0.0), listOf(Inventory(productId = "p", quantity = 0.0)), now)
        assertTrue(empty.needsBuy)
    }

    @Test
    fun `quantities are summed across batches`() {
        val s = PredictionEngine.evaluate(
            product(TrackingMode.COUNT, daily = 1.0),
            listOf(Inventory(productId = "p", quantity = 10.0), Inventory(productId = "p", quantity = 10.0)),
            now
        )
        assertEquals(20, s.daysLeft)
        assertEquals(Tone.OK, s.tone)
    }

    @Test
    fun `level - 25 percent needs buy, 50 percent warns`() {
        val low = PredictionEngine.evaluate(product(TrackingMode.LEVEL, life = 40), listOf(Inventory(productId = "p", levelPercent = 25)), now)
        assertTrue(low.needsBuy)
        assertEquals("剩 25%", low.statusText)
        val half = PredictionEngine.evaluate(product(TrackingMode.LEVEL, life = 100), listOf(Inventory(productId = "p", levelPercent = 50)), now)
        assertFalse(half.needsBuy)
        assertTrue(half.runsOutSoon)
    }

    @Test
    fun `expiry - tomorrow is urgent and expiring soon, not a shopping item`() {
        val exp = PredictionEngine.startOfDay(now) + day
        val s = PredictionEngine.evaluate(product(TrackingMode.EXPIRY), listOf(Inventory(productId = "p", quantity = 1.0, expiresAt = exp)), now)
        assertEquals(1, s.expiresInDays)
        assertTrue(s.expiringSoon)
        assertFalse(s.needsBuy)
        assertEquals(Tone.URGENT, s.tone)
        assertEquals("明天到期", s.statusText)
    }

    @Test
    fun `expiry - nearest batch with stock wins, empty batches ignored`() {
        val start = PredictionEngine.startOfDay(now)
        val s = PredictionEngine.evaluate(
            product(TrackingMode.EXPIRY),
            listOf(
                Inventory(productId = "p", quantity = 0.0, expiresAt = start - day),
                Inventory(productId = "p", quantity = 2.0, expiresAt = start + 5 * day)
            ),
            now
        )
        assertEquals(5, s.expiresInDays)
        assertFalse(s.expiringSoon)
        assertEquals(Tone.OK, s.tone)
    }

    @Test
    fun `expiry - eaten up goes to shopping list`() {
        val s = PredictionEngine.evaluate(product(TrackingMode.EXPIRY), listOf(Inventory(productId = "p", quantity = 0.0)), now)
        assertTrue(s.needsBuy)
        assertEquals("吃完了", s.statusText)
    }

    @Test
    fun `legacy v1 modes map explicitly`() {
        assertEquals(TrackingMode.COUNT, TrackingMode.fromCode("PACKAGE"))
        assertEquals(TrackingMode.COUNT, TrackingMode.fromCode("NONE"))
        assertEquals(TrackingMode.LEVEL, TrackingMode.fromCode("CYCLE"))
        assertEquals(TrackingMode.EXPIRY, TrackingMode.fromCode("EXPIRY"))
    }

    @Test
    fun `format quantity`() {
        assertEquals("12", PredictionEngine.formatQty(12.0))
        assertEquals("0.5", PredictionEngine.formatQty(0.5))
    }
}
