package com.finunity.viewmodel

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.finunity.data.local.AppDatabase
import com.finunity.data.local.entity.Account
import com.finunity.data.local.entity.AccountType
import com.finunity.data.local.entity.AssetRecord
import com.finunity.data.local.entity.AssetType
import com.finunity.data.local.entity.RiskBucket
import com.finunity.data.local.entity.Transaction
import com.finunity.data.local.entity.TransactionType
import com.finunity.data.repository.PriceRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainViewModelReconciliationTest {

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun closeDb() {
        // MainViewModel owns long-lived Flow collectors. Closing the in-memory database here
        // races those collectors; the instrumentation process releases it after the class.
    }

    @Test
    fun missingOpeningBalanceIsReportedAsInsufficientData() = runBlocking {
        val account = sampleAccount()
        db.accountDao().insert(account)
        val viewModel = createViewModel()

        val result = viewModel.reconcileAccountBalance(account.id)

        assertEquals(ReconciliationStatus.INSUFFICIENT_DATA, result.status)
    }

    @Test
    fun cashBalanceWithOpeningTransactionIsConsistent() = runBlocking {
        val account = sampleAccount("consistent-account")
        db.accountDao().insert(account)
        db.assetRecordDao().insert(cashRecord(account.id, 100.0))
        db.transactionDao().insert(deposit("consistent-transaction", account.id, 100.0))
        val viewModel = createViewModel()

        val result = viewModel.reconcileAccountBalance(account.id)

        assertEquals(ReconciliationStatus.CONSISTENT, result.status)
        assertEquals(0.0, result.difference, 0.001)
    }

    @Test
    fun cashBalanceDifferenceIsReportedAsInconsistent() = runBlocking {
        val account = sampleAccount("inconsistent-account")
        db.accountDao().insert(account)
        db.assetRecordDao().insert(cashRecord(account.id, 90.0))
        db.transactionDao().insert(deposit("inconsistent-transaction", account.id, 100.0))
        val viewModel = createViewModel()

        val result = viewModel.reconcileAccountBalance(account.id)

        assertEquals(ReconciliationStatus.INCONSISTENT, result.status)
        assertEquals(10.0, result.difference, 0.001)
    }

    private fun sampleAccount(id: String = "reconciliation-account") = Account(
        id = id,
        name = "虚构对账账户",
        type = AccountType.BANK,
        currency = "CNY",
        balance = 0.0
    )

    private fun cashRecord(accountId: String, amount: Double) = AssetRecord(
        id = "cash-$accountId",
        accountId = accountId,
        assetType = AssetType.CASH,
        riskBucket = RiskBucket.DEFENSIVE,
        name = "现金",
        quantity = amount,
        cost = amount,
        currentPrice = 1.0,
        currency = "CNY"
    )

    private fun deposit(id: String, accountId: String, amount: Double) = Transaction(
        id = id,
        accountId = accountId,
        symbol = null,
        type = TransactionType.DEPOSIT,
        shares = null,
        price = null,
        amount = amount,
        currency = "CNY",
        balanceAfter = amount
    )

    private fun createViewModel(): MainViewModel =
        MainViewModel(db, PriceRepository(db.priceDao()))
}
