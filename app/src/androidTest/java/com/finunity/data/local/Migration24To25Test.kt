package com.finunity.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.finunity.data.local.migration.Migration24To25
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration24To25Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun existingRowsReceiveEmptyServiceMetadataWithoutChangingValues() {
        helper.createDatabase("migration-24-25", 24).apply {
            execSQL("INSERT INTO accounts (id,name,type,currency,balance,createdAt,sourceType,externalSourceId,lastSyncedAt,syncState,initialPrincipal,annualInterestRate,dueDayOfMonth,minimumPayment) VALUES ('a','虚构账户','BROKER','USD',0,1,'MANUAL','',NULL,'NOT_APPLICABLE',0,0,0,0)")
            execSQL("INSERT INTO asset_records (id,accountId,assetType,riskBucket,name,securityCode,quantity,cost,currentPrice,currency,subCategory,industryTag,purchaseRestricted,peRatio,dividendYield,premiumRate,locked,createdAt,updatedAt,sourceType,sourceAccountId,sourceRecordId,importBatchId,sourceFingerprint,syncedAt) VALUES ('r','a','STOCK','AGGRESSIVE','虚构资产','AAPL',2,100,60,'USD','','',0,NULL,NULL,NULL,0,1,1,'MANUAL','','','','',NULL)")
            execSQL("INSERT INTO prices (symbol,price,previousClose,currency,updatedAt,isFallback) VALUES ('AAPL',60,59,'USD',1,0)")
            close()
        }

        helper.runMigrationsAndValidate(
            "migration-24-25",
            25,
            true,
            Migration24To25.migration
        ).use { db ->
            db.query("SELECT instrumentId,currentPrice FROM asset_records WHERE id='r'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("", cursor.getString(0))
                assertEquals(60.0, cursor.getDouble(1), 0.001)
            }
            db.query("SELECT instrumentId,source,sourceTime,receivedAt,quality,valueType,errorCode,price FROM prices WHERE symbol='AAPL'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("", cursor.getString(0))
                assertEquals("", cursor.getString(1))
                assertEquals(true, cursor.isNull(2))
                assertEquals(true, cursor.isNull(3))
                assertEquals("UNKNOWN", cursor.getString(4))
                assertEquals("MARKET_PRICE", cursor.getString(5))
                assertEquals(true, cursor.isNull(6))
                assertEquals(60.0, cursor.getDouble(7), 0.001)
            }
        }
    }
}
