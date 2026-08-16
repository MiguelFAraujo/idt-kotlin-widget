package com.idt.widget.domain

import com.idt.widget.data.model.ServiceCheckResult
import com.idt.widget.data.model.ServiceEndpoint
import com.idt.widget.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTimeoutTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private class HangingRepository : FakeServiceRepository() {
        override suspend fun checkService(endpoint: ServiceEndpoint): ServiceCheckResult =
            kotlinx.coroutines.suspendCancellableCoroutine { /* nunca completa */ }
    }

    @Test
    fun `check que trava nunca deixa o dashboard preso em loading`() = runTest(dispatcher.scheduler) {
        val vm = DashboardViewModel(
            HangingRepository(),
            FakeHistoryRepository(),
            checkTimeoutMs = 500,
        )
        dispatcher.scheduler.advanceTimeBy(2_000)
        dispatcher.scheduler.runCurrent()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
    }

    @Test
    fun `check normal conclui sem erro`() = runTest(dispatcher.scheduler) {
        val vm = DashboardViewModel(
            FakeServiceRepository(),
            FakeHistoryRepository(),
            checkTimeoutMs = 500,
        )
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.error == null)
        assertTrue(state.overallTotal > 0)
    }
}
