package com.finunity

import android.app.Application
import android.util.Log
import com.finunity.worker.PriceSyncWorker

class FinUnityApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 初始化 WorkManager 价格同步
        // 注意：这里只负责调度，实际执行在 PriceSyncWorker 中
        // 真正的调度在 MainActivity 中进行，以避免在 Application 中做太多事
        Log.d(TAG, "FinUnityApplication initialized")
    }

    companion object {
        private const val TAG = "FinUnityApp"
    }
}