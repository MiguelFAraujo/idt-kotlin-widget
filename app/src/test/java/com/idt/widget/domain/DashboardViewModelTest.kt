package com.idt.widget.domain

import com.idt.widget.update.UpdateInfo
import com.idt.widget.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Test
    fun `refresh registra timestamp de ultima atualizacao`() = runTest(dispatcher.scheduler) {
        val vm = DashboardViewModel(FakeServiceRepository(), FakeHistoryRepository())
        dispatcher.scheduler.advanceUntilIdle()
        assertNotNull(vm.uiState.value.lastUpdate)
    }

    @Test
    fun `refresh conta somente endpoints habilitados`() = runTest(dispatcher.scheduler) {
        val repo = FakeServiceRepository()
        val disabled = repo.getEndpoints().first().copy(enabled = false)
        repo.updateEndpoint(disabled)
        val vm = DashboardViewModel(repo, FakeHistoryRepository())
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        val enabled = repo.getEndpoints().count { it.enabled }
        assertEquals(enabled, state.overallTotal)
    }

    @Test
    fun `refresh calcula uptime como fracao de ok`() = runTest(dispatcher.scheduler) {
        val vm = DashboardViewModel(FakeServiceRepository(), FakeHistoryRepository())
        dispatcher.scheduler.advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(state.overallOk.toFloat() / state.overallTotal, state.overallUptime, 0.001f)
    }

    @Test
    fun `onUpdateChecked propaga atualizacao disponivel`() = runTest(dispatcher.scheduler) {
        val vm = DashboardViewModel(FakeServiceRepository(), FakeHistoryRepository())
        dispatcher.scheduler.advanceUntilIdle()
        vm.onUpdateChecked(UpdateInfo("0.0.6", 6, "http://x/a.apk", "", 0L))
        assertEquals("0.0.6", vm.uiState.value.updateAvailable?.versionName)
    }
}
