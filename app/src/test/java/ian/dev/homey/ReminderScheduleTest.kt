package ian.dev.homey

import ian.dev.homey.domain.worker.ReminderSchedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class ReminderScheduleTest {

    private fun at(hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        set(2026, Calendar.SEPTEMBER, 23, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val hourMs = 3_600_000L

    @Test
    fun `before today's time - fires later today`() {
        assertEquals(2 * hourMs, ReminderSchedule.delayUntilNext(at(7, 0), 9, 0))
    }

    @Test
    fun `after today's time - fires tomorrow`() {
        assertEquals(22 * hourMs, ReminderSchedule.delayUntilNext(at(11, 0), 9, 0))
    }

    @Test
    fun `exactly at the time - schedules tomorrow, not immediately again`() {
        assertEquals(24 * hourMs, ReminderSchedule.delayUntilNext(at(9, 0), 9, 0))
    }
}
