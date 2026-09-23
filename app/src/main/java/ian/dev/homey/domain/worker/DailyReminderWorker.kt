package ian.dev.homey.domain.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ian.dev.homey.data.database.AppDatabase
import ian.dev.homey.data.repository.HomeyRepository
import ian.dev.homey.domain.notification.NotificationHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 每日提醒：在用户设定的时间（默认 9:00）检查一次，有该买的或快过期的就发通知。
 *
 * 用"一次性任务 + 每次执行完预约下一次"而不是 24 小时周期任务：
 * 周期任务会随系统省电策略逐日漂移，几天后可能在半夜提醒。
 */
class DailyReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = ReminderPrefs(applicationContext)
        if (!prefs.enabled) return Result.success()
        return try {
            notifyIfNeeded()
            Result.success()
        } catch (_: Exception) {
            Result.success() // 失败也不重试到别的时间点，明天照常提醒
        } finally {
            // 当前任务还在运行，用 APPEND_OR_REPLACE 把明天的任务排在它后面（REPLACE 会把自己取消掉）
            schedule(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
    }

    private suspend fun notifyIfNeeded() {
        val repository = HomeyRepository(AppDatabase.get(applicationContext))
        val items = repository.observeItems(flowOf(System.currentTimeMillis())).first()
        val toBuy = items.filter { it.needsBuy }
        val expiring = items.filter { it.expiringSoon }

        if (expiring.isNotEmpty()) {
            val first = expiring.first()
            NotificationHelper.showExpiryAlert(
                applicationContext,
                "${first.product.name}${first.statusText}",
                if (expiring.size > 1) "还有 ${expiring.size - 1} 件也快过期了，记得先吃掉" else "记得先吃掉"
            )
        }
        if (toBuy.isNotEmpty()) {
            val names = toBuy.take(3).joinToString("、") { it.product.name }
            NotificationHelper.showRestockAlert(
                applicationContext,
                "有 ${toBuy.size} 件该买了",
                if (toBuy.size > 3) "$names 等" else names
            )
        }
    }

    companion object {
        private const val WORK_NAME = "DailyReminder"

        /**
         * 预约下一次提醒。
         * - App 启动时用 KEEP：已经预约过就不动；
         * - 修改提醒时间用 REPLACE：按新时间重新预约；
         * - 执行完一次后用 APPEND_OR_REPLACE：接着预约明天。
         */
        fun schedule(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
            val prefs = ReminderPrefs(context)
            val workManager = WorkManager.getInstance(context)
            if (!prefs.enabled) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val delay = ReminderSchedule.delayUntilNext(System.currentTimeMillis(), prefs.hour, prefs.minute)
            val request = OneTimeWorkRequestBuilder<DailyReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            workManager.enqueueUniqueWork(WORK_NAME, policy, request)
        }
    }
}

/** 纯计算，方便单元测试。 */
object ReminderSchedule {
    /** 距离下一个 hour:minute 还有多少毫秒；今天的时间点已过（或正好是现在）就算明天的。 */
    fun delayUntilNext(now: Long, hour: Int, minute: Int): Long {
        val next = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis - now
    }
}

/** 提醒设置。 */
class ReminderPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("reminder_prefs", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    var hour: Int
        get() = prefs.getInt("hour", 9)
        set(value) = prefs.edit().putInt("hour", value.coerceIn(0, 23)).apply()

    var minute: Int
        get() = prefs.getInt("minute", 0)
        set(value) = prefs.edit().putInt("minute", value.coerceIn(0, 59)).apply()
}
