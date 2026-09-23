package com.example.domain.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.database.AppDatabase
import com.example.data.repository.HomeyRepository
import com.example.domain.notification.NotificationHelper
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * 每日巡检：有该买的或快过期的，就发一条通知。
 * 类名保持不变——WorkManager 按类名找已排队的任务，改名会让老用户的定时任务失效。
 */
class RestockDailyWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        val repository = HomeyRepository(AppDatabase.get(applicationContext))
        val items = repository.observeItems().first()
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
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }

    companion object {
        private const val WORK_NAME = "FamilySupplyDailyCheck"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RestockDailyWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
