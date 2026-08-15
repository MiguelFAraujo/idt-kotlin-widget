package com.idt.widget.domain

import com.idt.widget.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh aggregates online count from repository`() = runTest(dispatcher.scheduler) {
        val repo = FakeServiceRepository()
        val vm = DashboardViewModel(repo, FakeHistoryRepository())
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(!state.isLoading)
        assertEquals(state.overallTotal, state.overallOk)
        assertTrue(state.overallTotal >= 0)
        assertEquals(repo.getEndpoints().size, state.overallTotal)
    }
}
