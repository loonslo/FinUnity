package com.finunity.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.finunity.data.local.migration.Migration22To23
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration22To23Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun v22LegacyBucketsMigrateWithoutChangingRowCounts() {
        helper.createDatabase("migration-22-23", 22).apply {
            execSQL("INSERT INTO accounts (id,name,type,currency,balance,createdAt,sourceType,externalSourceId,lastSyncedAt,syncState,initialPrincipal,annualInterestRate,dueDayOfMonth,minimumPayment) VALUES ('a','虚构账户','BROKER','CNY',0,1,'MANUAL','',NULL,'NOT_APPLICABLE',0,0,0,0)")
            execSQL("INSERT INTO asset_records (id,accountId,assetType,riskBucket,name,securityCode,quantity,cost,currentPrice,currency,subCategory,industryTag,purchaseRestricted,peRatio,dividendYield,premiumRate,locked,createdAt,updatedAt,sourceType,sourceAccountId,sourceRecordId,importBatchId,sourceFingerprint,syncedAt) VALUES ('r1','a','INSURANCE_POLICY','INSURANCE','虚构保单','',1,1000,1000,'CNY','','',0,NULL,NULL,NULL,0,1,1,'MANUAL','','','','',NULL)")
            execSQL("INSERT INTO positions (id,accountId,symbol,shares,totalCost,currency,createdAt) VALUES ('p1','a','600519.SH',2,3000,'CNY',1)")
            execSQL("INSERT INTO prices (symbol,price,previousClose,currency,updatedAt,isFallback) VALUES ('600519.SS',1500,1490,'CNY',1,0)")
            execSQL("INSERT INTO price_history (id,recordId,price,cost,timestamp) VALUES ('h1','r1',1000,1000,1)")
            execSQL("INSERT INTO transactions (id,accountId,symbol,type,shares,price,amount,currency,timestamp,note,recordId,balanceAfter,origin,sourceFingerprint,category,importBatchId) VALUES ('t1','a','600519.SS','BUY',1,1000,1000,'CNY',1,'迁移测试','r1',9000,'TRADE','fixture:t1','INVESTMENT','batch-1')")
            execSQL("INSERT INTO asset_snapshots (id,timestamp,totalAssets,cashAssets,stockAssets,stockRatio,baseCurrency,totalCost,notes) VALUES ('s1',1,10000,1000,9000,0.9,'CNY',8000,'迁移测试')")
            execSQL("INSERT INTO recurring_rules (id,accountId,type,amount,currency,category,note,dayOfMonth,enabled,lastGeneratedAt,createdAt) VALUES ('rr1','a','INCOME',100,'CNY','OTHER_INCOME','迁移测试',1,1,NULL,1)")
            execSQL("INSERT INTO allocation_targets (subCategory,riskBucket,targetAmount,capAmount,stopNote,updatedAt) VALUES ('保障','CONSERVATIVE',6000,0,'',1)")
            execSQL("INSERT INTO settings (id,baseCurrency,targetAllocation,rebalanceThreshold,onboarded,amountsVisible,maxAggressiveRatio) VALUES (1,'CNY','CONSERVATIVE:0.4,AGGRESSIVE:0.3,INSURANCE:0.2,CASH:0.1',0.05,1,1,0.7)")
            close()
        }

        helper.runMigrationsAndValidate("migration-22-23", 23, true, Migration22To23.migration).use { db ->
            assertEquals("BALANCED", db.query("SELECT riskBucket FROM asset_records WHERE id='r1'").use { it.moveToFirst(); it.getString(0) })
            assertEquals("BALANCED", db.query("SELECT riskBucket FROM allocation_targets WHERE subCategory='保障'").use { it.moveToFirst(); it.getString(0) })
            assertEquals("DEFENSIVE:0.1,BALANCED:0.6000000000000001,AGGRESSIVE:0.3", db.query("SELECT targetAllocation FROM settings WHERE id=1").use { it.moveToFirst(); it.getString(0) })
            assertEquals(1, db.query("SELECT COUNT(*) FROM accounts").use { it.moveToFirst(); it.getInt(0) })
            assertEquals(1, db.query("SELECT COUNT(*) FROM asset_records").use { it.moveToFirst(); it.getInt(0) })
            assertEquals(1, db.query("SELECT COUNT(*) FROM positions").use { it.moveToFirst(); it.getInt(0) })
            assertEquals(1, db.query("SELECT COUNT(*) FROM prices").use { it.moveToFirst(); it.getInt(0) })
            assertEquals(1, db.query("SELECT COUNT(*) FROM price_history").use { it.moveToFirst(); it.getInt(0) })
            assertEquals(1, db.query("SELECT COUNT(*) FROM transactions").use { it.moveToFirst(); it.getInt(0) })
            assertEquals(1, db.query("SELECT COUNT(*) FROM asset_snapshots").use { it.moveToFirst(); it.getInt(0) })
            assertEquals(1, db.query("SELECT COUNT(*) FROM recurring_rules").use { it.moveToFirst(); it.getInt(0) })
        }
    }

    @Test
    fun unknownRiskBucketFailsMigration() {
        helper.createDatabase("migration-22-23-unknown", 22).apply {
            execSQL("INSERT INTO accounts (id,name,type,currency,balance,createdAt,sourceType,externalSourceId,lastSyncedAt,syncState,initialPrincipal,annualInterestRate,dueDayOfMonth,minimumPayment) VALUES ('a','虚构账户','BROKER','CNY',0,1,'MANUAL','',NULL,'NOT_APPLICABLE',0,0,0,0)")
            execSQL("INSERT INTO asset_records (id,accountId,assetType,riskBucket,name,securityCode,quantity,cost,currentPrice,currency,subCategory,industryTag,purchaseRestricted,peRatio,dividendYield,premiumRate,locked,createdAt,updatedAt,sourceType,sourceAccountId,sourceRecordId,importBatchId,sourceFingerprint,syncedAt) VALUES ('r-unknown','a','STOCK','UNKNOWN','虚构资产','UNKNOWN',1,1,1,'CNY','','',0,NULL,NULL,NULL,0,1,1,'MANUAL','','','','',NULL)")
            close()
        }

        val failure = runCatching {
            helper.runMigrationsAndValidate("migration-22-23-unknown", 23, true, Migration22To23.migration).close()
        }.exceptionOrNull()

        assertTrue("未知风险桶必须阻止迁移", failure != null)
    }
}
