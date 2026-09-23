package com.example.domain.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.backup.BackupManager
import com.example.data.backup.BackupPrefs
import com.example.data.database.AppDatabase
import java.util.concurrent.TimeUnit

/** 每天自动备份一次（用户可在"数据与备份"里关闭）。 */
class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = BackupPrefs(applicationContext)
        if (!prefs.autoBackupEnabled) return Result.success()
        return try {
            val ok = BackupManager(applicationContext, AppDatabase.get(applicationContext), prefs).autoBackup()
            if (ok) Result.success() else Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "HomeyAutoBackup"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
