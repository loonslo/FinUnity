package com.finunity.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.finunity.data.local.AppDatabase
import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AccountType
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetSnapshot
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.RiskBucket
import com.finunity.data.local.entity.Transaction
import com.finunity.data.local.entity.TransactionType
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: BackupRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = BackupRepository(db)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun exportAndRestoreRemovesSnapshotsCreatedAfterExport() = runBlocking {
        val account = sampleAccount()
        val record = sampleRecord(account.id)
        db.accountDao().insert(account)
        db.assetRecordDao().insert(record)
        db.assetSnapshotDao().insert(sampleSnapshot("snapshot-1", 1000L))

        val json = repository.export()
        db.assetSnapshotDao().insert(sampleSnapshot("snapshot-2", 2000L))
        assertEquals(2, db.assetSnapshotDao().getSnapshotCount())

        val result = repository.import(json)

        assertTrue(result.isSuccess)
        assertEquals(1, db.accountDao().getAllAccounts().first().size)
        assertEquals(1, db.assetRecordDao().getAllRecords().first().size)
        assertEquals(1, db.assetSnapshotDao().getSnapshotCount())
        assertEquals("snapshot-1", db.assetSnapshotDao().getLatestSnapshot()?.id)
        assertEquals(9, repository.summarize(json).getOrThrow().version)
    }

    @Test
    fun invalidForeignKeyDoesNotMutateExistingDatabase() = runBlocking {
        val existing = sampleAccount("existing-account")
        db.accountDao().insert(existing)
        db.assetSnapshotDao().insert(sampleSnapshot("existing-snapshot", 1000L))
        val invalidBackup = BackupData(
            accounts = listOf(existing),
            transactions = listOf(
                Transaction(
                    id = "invalid-transaction",
                    accountId = "missing-account",
                    symbol = null,
                    type = TransactionType.DEPOSIT,
                    shares = null,
                    price = null,
                    amount = 100.0,
                    currency = "CNY"
                )
            )
        )

        val result = repository.import(Gson().toJson(invalidBackup))

        assertFalse(result.isSuccess)
        assertEquals(listOf(existing), db.accountDao().getAllAccounts().first())
        assertEquals(1, db.assetSnapshotDao().getSnapshotCount())
        assertEquals("existing-snapshot", db.assetSnapshotDao().getLatestSnapshot()?.id)
    }

    @Test
    fun futureBackupVersionIsRejectedWithoutMutatingExistingDatabase() = runBlocking {
        val existing = sampleAccount("future-version-account")
        db.accountDao().insert(existing)
        val futureBackup = BackupData(version = 10, accounts = emptyList())

        val result = repository.import(Gson().toJson(futureBackup))

        assertFalse(result.isSuccess)
        assertEquals(listOf(existing), db.accountDao().getAllAccounts().first())
    }

    private fun sampleAccount(id: String = "backup-account") = Account(
        id = id,
        name = "虚构备份账户",
        type = AccountType.BROKER,
        currency = "CNY",
        balance = 0.0
    )

    private fun sampleRecord(accountId: String) = AssetRecord(
        id = "backup-record",
        accountId = accountId,
        assetType = AssetType.ETF,
        riskBucket = RiskBucket.AGGRESSIVE,
        name = "虚构 ETF",
        securityCode = "510300.SS",
        quantity = 10.0,
        cost = 400.0,
        currentPrice = 42.0,
        currency = "CNY"
    )

    private fun sampleSnapshot(id: String, timestamp: Long) = AssetSnapshot(
        id = id,
        timestamp = timestamp,
        totalAssets = 420.0,
        cashAssets = 0.0,
        stockAssets = 420.0,
        stockRatio = 1.0,
        baseCurrency = "CNY",
        totalCost = 400.0,
        grossAssets = 420.0,
        aggressiveAssets = 420.0,
        calculationVersion = "three-bucket-v1"
    )
}
