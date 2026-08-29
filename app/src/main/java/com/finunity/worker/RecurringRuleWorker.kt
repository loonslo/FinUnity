package com.finunity.worker

import android.content.Context
import androidx.work.*
import com.finunity.data.local.AppDatabase
import com.finunity.data.repository.RecurringRuleRepository
import java.util.concurrent.TimeUnit

class RecurringRuleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        RecurringRuleRepository(AppDatabase.getDatabase(applicationContext)).generateDue()
        Result.success()
    }.getOrElse { Result.retry() }

    companion object {
        private const val WORK_NAME = "recurring_rule_worker"

        fun scheduleDaily(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RecurringRuleWorker>(1, TimeUnit.DAYS).build()
            )
        }
    }
}
