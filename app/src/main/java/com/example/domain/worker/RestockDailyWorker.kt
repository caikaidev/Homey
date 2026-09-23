package com.example.domain.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.database.AppDatabase
import com.example.data.model.StatusTone
import com.example.domain.calendar.CalendarHelper
import com.example.domain.notification.NotificationHelper
import com.example.domain.prediction.PredictionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class RestockDailyWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val database = AppDatabase.getDatabase(applicationContext, kotlinx.coroutines.GlobalScope)
            val productsWithDetails = database.productDao().getAllProductsWithDetails().first()
            val now = System.currentTimeMillis()

            val predictions = productsWithDetails.map { PredictionEngine.calculate(it, now) }

            val urgentShopping = predictions.filter { it.isUrgentReorder || it.statusTone == StatusTone.URGENT_RED }
            val expiring = predictions.filter { it.isExpiringSoon || it.isExpired }

            // 1. Notification for Expiry
            if (expiring.isNotEmpty()) {
                val todayExpiring = expiring.first()
                NotificationHelper.showExpiryAlert(
                    applicationContext,
                    "⚠️ ${todayExpiring.product.name} 临期提醒",
                    "${todayExpiring.product.name} ${todayExpiring.suggestedActionText}！"
                )
            }

            // 2. Notification for Urgent Restock
            if (urgentShopping.isNotEmpty()) {
                val names = urgentShopping.take(3).joinToString("、") { it.product.name }
                NotificationHelper.showRestockAlert(
                    applicationContext,
                    "🛒 家庭采购提醒 (${urgentShopping.size}项需补货)",
                    "$names 等家庭消耗品即将缺货，建议本周采购补充。"
                )

                // 3. Update Calendar Event if permission granted
                CalendarHelper.syncConsolidatedShoppingEvent(applicationContext, urgentShopping)
            }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "FamilySupplyDailyCheck"

        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<RestockDailyWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
