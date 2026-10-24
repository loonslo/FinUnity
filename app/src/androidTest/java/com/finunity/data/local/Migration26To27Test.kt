package com.finunity.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.finunity.data.local.migration.Migration26To27
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration26To27Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun addsPlannerTablesKeepsLedgerRowsAndAppliesOliveDefault() {
        helper.createDatabase("migration-26-27", 26).apply {
            execSQL("INSERT INTO accounts (id,name,type,currency,balance,createdAt,sourceType,externalSourceId,lastSyncedAt,syncState,initialPrincipal,annualInterestRate,dueDayOfMonth,minimumPayment) VALUES ('a','迁移账户','BANK','CNY',0,1,'MANUAL','',NULL,'NOT_APPLICABLE',0,0,0,0)")
            execSQL("INSERT INTO transactions (id,accountId,symbol,type,shares,price,amount,currency,timestamp,note,recordId,balanceAfter,origin,sourceFingerprint,category,importBatchId) VALUES ('t1','a',NULL,'TRANSFER_IN',NULL,NULL,500,'CNY',1,'保留旧流水',NULL,NULL,'CASH_FLOW','migration:t1','TRANSFER','')")
            execSQL("INSERT INTO settings (id,baseCurrency,targetAllocation,rebalanceThreshold,onboarded,amountsVisible,maxAggressiveRatio,themeColor,themeAppearance) VALUES (1,'CNY','DEFENSIVE:0.1,BALANCED:0.6,AGGRESSIVE:0.3',0.05,1,1,0.7,'navy','dark')")
            close()
        }

        helper.runMigrationsAndValidate("migration-26-27", 27, true, Migration26To27.migration).use { db ->
            db.query("SELECT COUNT(*) FROM transactions").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            db.query("SELECT financialGoalId FROM transactions WHERE id='t1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(true, cursor.isNull(0))
            }
            db.query("SELECT themeColor,themeAppearance FROM settings WHERE id=1").use { cursor ->
                cursor.moveToFirst()
                assertEquals("olive", cursor.getString(0))
                assertEquals("light", cursor.getString(1))
            }
            db.query("SELECT COUNT(*) FROM financial_goals").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }
}
