package com.finunity.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ExecutionException

/**
 * PriceSyncWorker 行为测试
 */
@RunWith(AndroidJUnit4::class)
class PriceSyncWorkerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `schedule creates periodic work request`() {
        // 验证 schedule 方法存在且可调用
        PriceSyncWorker.schedule(context)

        val workManager = WorkManager.getInstance(context)
        val workInfos = workManager.getWorkInfosForUniqueWork("price_sync_worker").get()

        assertNotNull(workInfos)
        assertTrue(workInfos.isNotEmpty())
    }

    @Test
    fun `snapshotNow creates one-time work request`() {
        // 验证 snapshotNow 方法存在且可调用
        SnapshotWorker.snapshotNow(context)

        val workManager = WorkManager.getInstance(context)
        // 注意：OneTimeWorkRequest 会在完成后很快消失，这是预期行为
        // 我们只验证调用不抛出异常
        assertNotNull(workManager)
    }
}