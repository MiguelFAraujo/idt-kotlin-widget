package com.idt.widget.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.idt.widget.util.NetworkSpeedMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkSpeedMonitorTest {

    @Test
    fun sampleReportaVelocidadeAposDuasAmostras() = runBlocking {
        // Primeira amostra inicializa a linha de base (velocidade 0)
        val first = NetworkSpeedMonitor.sample()
        assertTrue(first.totalRxBytes >= 0)
        assertTrue(first.totalTxBytes >= 0)

        // Aguarda e amostra de novo — deve produzir velocidade >= 0
        delay(1100)
        val second = NetworkSpeedMonitor.sample()
        assertTrue("rx deve ser >= 0, foi ${second.rxBytesPerSec}", second.rxBytesPerSec >= 0.0)
        assertTrue("tx deve ser >= 0, foi ${second.txBytesPerSec}", second.txBytesPerSec >= 0.0)
    }

    @Test
    fun formatApresentaUnidades() {
        assertTrue(NetworkSpeedMonitor.format(0.0).contains("B/s"))
        assertTrue(NetworkSpeedMonitor.format(500.0).contains("B/s"))
        assertTrue(NetworkSpeedMonitor.format(2048.0).contains("KB/s"))
        assertTrue(NetworkSpeedMonitor.format(5 * 1024 * 1024.0).contains("MB/s"))
    }
}
