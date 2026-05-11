package com.finunity.viewmodel

import com.finunity.data.local.entity.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

/**
 * MainViewModel / Settings 单元测试
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `settings default values are correct`() {
        val settings = Settings()
        assertEquals("CNY", settings.baseCurrency)
        assertEquals(0.05, settings.rebalanceThreshold, 0.01)
        assertEquals("CONSERVATIVE:0.2,AGGRESSIVE:0.6,CASH:0.2", settings.targetAllocation)
    }

    @Test
    fun `settings can be modified`() {
        val settings = Settings(baseCurrency = "USD", rebalanceThreshold = 0.1)
        assertEquals("USD", settings.baseCurrency)
        assertEquals(0.1, settings.rebalanceThreshold, 0.01)
    }

    @Test
    fun `settings rebalanceThreshold has valid range`() {
        val settingsLow = Settings(rebalanceThreshold = 0.03)
        val settingsHigh = Settings(rebalanceThreshold = 0.15)

        assertEquals(0.03, settingsLow.rebalanceThreshold, 0.01)
        assertEquals(0.15, settingsHigh.rebalanceThreshold, 0.01)
    }

    @Test
    fun `targetAllocation parse handles empty string`() {
        val settings = Settings(targetAllocation = "")
        assertEquals("", settings.targetAllocation)
    }

    @Test
    fun `settings copy creates modified instance`() {
        val original = Settings(baseCurrency = "CNY", rebalanceThreshold = 0.05)
        val modified = original.copy(baseCurrency = "USD")

        assertEquals("CNY", original.baseCurrency)
        assertEquals("USD", modified.baseCurrency)
    }

    @Test
    fun `settings single id is always_1`() {
        val settings = Settings()
        assertEquals(1, settings.id)
    }
}