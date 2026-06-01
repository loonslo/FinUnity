package com.finunity.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.finunity.MainActivity
import java.util.concurrent.TimeUnit

/**
 * 月度复盘提醒 Worker
 * 每 30 天检查一次，发送通知提醒用户做月度复盘
 */
class ReviewReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting review reminder check")
        return try {
            createNotificationChannel()
            sendNotification()
            Log.d(TAG, "Review reminder notification sent")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Review reminder failed: ${e.message}")
            Result.retry()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "月度复盘提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "每月提醒你做一次资产复盘"
            }
            val notificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendNotification() {
        // 检查通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Notification permission not granted, skipping")
                return
            }
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra("open", "review")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("该做月度复盘了")
            .setContentText("看看这个月资产变化，调整一下目标配置")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "ReviewReminderWorker"
        private const val WORK_NAME = "review_reminder"
        private const val CHANNEL_ID = "review"
        private const val NOTIFICATION_ID = 2001

        /**
         * 安排月度复盘提醒（每 30 天）
         */
        fun scheduleMonthly(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<ReviewReminderWorker>(
                30, TimeUnit.DAYS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
        }
    }
}
