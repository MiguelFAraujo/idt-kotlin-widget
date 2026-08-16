package com.idt.widget.ui.dashboard

import com.idt.widget.data.model.AppConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RefreshSchedulerTest {

    @Test
    fun `dispara refresh repetidamente enquanto autoRefresh=true`() = runTest {
        val config = MutableStateFlow(
            AppConfig(autoRefresh = true, refreshIntervalSeconds = 10)
        )
        var count = 0
        val scheduler = RefreshScheduler(config, backgroundScope, onRefresh = { count++ })
        scheduler.start()

        advanceTimeBy(10_000); runCurrent()
        assertEquals(1, count)

        advanceTimeBy(10_000); runCurrent()
        assertEquals(2, count)

        advanceTimeBy(30_000); runCurrent()
        assertEquals(5, count)
    }

    @Test
    fun `nunca dispara enquanto autoRefresh=false`() = runTest {
        val config = MutableStateFlow(
            AppConfig(autoRefresh = false, refreshIntervalSeconds = 10)
        )
        var count = 0
        val scheduler = RefreshScheduler(config, backgroundScope, onRefresh = { count++ })
        scheduler.start()

        advanceTimeBy(120_000); advanceUntilIdle()
        assertEquals(0, count)
    }

    @Test
    fun `para quando autoRefresh e desligado no meio do intervalo`() = runTest {
        val config = MutableStateFlow(
            AppConfig(autoRefresh = true, refreshIntervalSeconds = 10)
        )
        var count = 0
        val scheduler = RefreshScheduler(config, backgroundScope, onRefresh = { count++ })
        scheduler.start()

        advanceTimeBy(10_000); runCurrent()
        assertEquals(1, count)

        config.value = AppConfig(autoRefresh = false, refreshIntervalSeconds = 10)
        advanceTimeBy(120_000); advanceUntilIdle()
        assertEquals(1, count)
    }

    @Test
    fun `respeita o intervalo configurado`() = runTest {
        val config = MutableStateFlow(
            AppConfig(autoRefresh = true, refreshIntervalSeconds = 30)
        )
        var count = 0
        val scheduler = RefreshScheduler(config, backgroundScope, onRefresh = { count++ })
        scheduler.start()

        advanceTimeBy(29_000); runCurrent()
        assertEquals(0, count)

        advanceTimeBy(1_000); runCurrent()
        assertEquals(1, count)
    }
}
