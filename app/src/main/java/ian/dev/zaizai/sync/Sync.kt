package ian.dev.zaizai.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ian.dev.zaizai.ZaizaiApp
import ian.dev.zaizai.data.SyncResult
import java.util.concurrent.TimeUnit

/**
 * 后台同步（尽力而为）：每 15 分钟拉一次看板、补传离线登记、到点提醒；
 * 离线登记另外排一个“联网就跑”的一次性任务。系统省电策略可能推迟它们。
 */
object Sync {
    private const val PERIODIC = "sync-periodic"
    private const val UPLOAD = "sync-upload"

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(online)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun requestUpload(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UPLOAD, ExistingWorkPolicy.REPLACE, request)
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as ZaizaiApp
        return when (app.repository.sync()) {
            SyncResult.OK -> {
                Notifier.notifyDue(app, app.repository.state.value)
                Result.success()
            }
            SyncResult.OFFLINE -> Result.retry()
            SyncResult.FAILED, SyncResult.NOT_CONFIGURED -> Result.success()
        }
    }
}
