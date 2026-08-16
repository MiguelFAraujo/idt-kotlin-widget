package com.idt.widget.ui.scan

import com.idt.widget.data.model.ServiceEndpoint
import com.idt.widget.data.remote.PortScanner
import com.idt.widget.domain.FakeServiceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private class FakePortScanner(
        private val openPorts: Set<Int>,
    ) : PortScanner {
        override val commonPorts: List<Int> = listOf(22, 80, 443, 8080)
        override val fullScanPorts: List<Int> = commonPorts

        override suspend fun scanPorts(
            host: String,
            ports: List<Int>,
            timeoutMs: Int,
            concurrency: Int,
            onProgress: (done: Int, total: Int) -> Unit,
        ): List<Int> {
            onProgress(ports.size, ports.size)
            return ports.filter { it in openPorts }
        }
    }

    private fun scanVm(openPorts: Set<Int> = emptySet(), repo: FakeServiceRepository = FakeServiceRepository()) =
        ScanViewModel(repo, FakePortScanner(openPorts))

    @Test
    fun `scan pre-seleciona somente portas abertas`() = runTest(dispatcher.scheduler) {
        val vm = scanVm(openPorts = setOf(22, 443))
        vm.setHost("100.104.13.42")
        vm.scan()
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.scanning)
        assertEquals(4, state.ports.size)
        val selected = state.ports.filter { it.selected }
        assertEquals(setOf(22, 443), selected.map { it.port }.toSet())
        assertEquals(2, state.selectedCount)
    }

    @Test
    fun `scan mostra portas fechadas como OFF e selecionáveis`() = runTest(dispatcher.scheduler) {
        val vm = scanVm(openPorts = setOf(22))
        vm.setHost("100.104.13.42")
        vm.scan()
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        val off = state.ports.filter { !it.open }
        assertTrue(off.isNotEmpty())
        assertEquals(3, off.size)
        assertTrue(off.all { !it.selected })
        // O checkbox de uma porta OFF deve existir na lista para ser selecionado
        assertTrue(state.ports.any { it.port == 80 && !it.open })
    }

    @Test
    fun `toggle seleciona uma porta OFF`() = runTest(dispatcher.scheduler) {
        val vm = scanVm(openPorts = setOf(22))
        vm.setHost("100.104.13.42")
        vm.scan()
        dispatcher.scheduler.advanceUntilIdle()

        val offItem = vm.uiState.value.ports.first { it.port == 80 }
        vm.toggle(offItem.copy(selected = true))
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(2, state.selectedCount)
        assertTrue(state.ports.first { it.port == 80 }.selected)
    }

    @Test
    fun `toggle desmarca uma porta aberta`() = runTest(dispatcher.scheduler) {
        val vm = scanVm(openPorts = setOf(22, 80))
        vm.setHost("100.104.13.42")
        vm.scan()
        dispatcher.scheduler.advanceUntilIdle()

        val openItem = vm.uiState.value.ports.first { it.port == 22 }
        vm.toggle(openItem.copy(selected = false))
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.selectedCount)
        assertFalse(state.ports.first { it.port == 22 }.selected)
    }

    @Test
    fun `scan sem host informa erro`() = runTest(dispatcher.scheduler) {
        val vm = scanVm()
        vm.setHost("   ")
        vm.scan()
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.scanning)
    }

    @Test
    fun `scan reporta progresso ate 100 porcento`() = runTest(dispatcher.scheduler) {
        val vm = scanVm(openPorts = setOf(22))
        vm.setHost("100.104.13.42")
        vm.scan()
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(100, state.progress)
        assertEquals(4, state.scanned)
        assertEquals(4, state.total)
    }

    @Test
    fun `scanFull usa faixa completa de portas`() = runTest(dispatcher.scheduler) {
        val vm = scanVm(openPorts = setOf(22, 443))
        vm.setHost("100.104.13.42")
        vm.scanFull()
        dispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.fullScan)
        assertFalse(state.scanning)
        assertEquals(4, state.total)
        assertEquals(setOf(22, 443), state.ports.filter { it.open }.map { it.port }.toSet())
    }

    @Test
    fun `addSelected adiciona endpoints para portas selecionadas`() = runTest(dispatcher.scheduler) {
        val repo = FakeServiceRepository()
        val vm = scanVm(openPorts = setOf(22, 443), repo = repo)
        vm.setHost("10.0.0.5")
        vm.scan()
        dispatcher.scheduler.advanceUntilIdle()

        vm.addSelected()
        dispatcher.scheduler.advanceUntilIdle()

        val endpoints = repo.getEndpoints()
        val added = endpoints.filter { it.host == "10.0.0.5" }
        assertEquals(2, added.size)
        assertEquals(setOf(22, 443), added.map { it.port }.toSet())
    }

    @Test
    fun `addSelected nao duplica host porta ja existente`() = runTest(dispatcher.scheduler) {
        val repo = FakeServiceRepository()
        repo.addEndpoint(
            ServiceEndpoint(id = "existing-22", name = "SSH", host = "10.0.0.5", port = 22)
        )
        val vm = scanVm(openPorts = setOf(22, 443), repo = repo)
        vm.setHost("10.0.0.5")
        vm.scan()
        dispatcher.scheduler.advanceUntilIdle()

        vm.addSelected()
        dispatcher.scheduler.advanceUntilIdle()

        val added = repo.getEndpoints().filter { it.host == "10.0.0.5" }
        // 22 já existia (não duplica) + 443 novo
        assertEquals(2, added.size)
        assertEquals(1, added.count { it.port == 22 })
        assertEquals(1, added.count { it.port == 443 })
    }
}
