package com.idt.widget.util

import android.net.TrafficStats
import android.os.SystemClock

data class SpeedSample(
    val rxBytesPerSec: Double,
    val txBytesPerSec: Double,
    val totalRxBytes: Long,
    val totalTxBytes: Long,
) {
    companion object {
        fun zero() = SpeedSample(0.0, 0.0, 0L, 0L)
    }
}

/**
 * Mede velocidade de rede (up/down) do dispositivo a partir dos contadores
 * globais do TrafficStats, por amostragem. Deve ser chamado ~1x/segundo.
 */
object NetworkSpeedMonitor {

    private var lastRx = 0L
    private var lastTx = 0L
    private var lastSampleAt = 0L
    private var running = false

    fun sample(): SpeedSample {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val now = SystemClock.elapsedRealtime()

        if (running && lastSampleAt > 0) {
            val dt = (now - lastSampleAt) / 1000.0
            val rxPerSec = if (dt > 0) (rx - lastRx).coerceAtLeast(0) / dt else 0.0
            val txPerSec = if (dt > 0) (tx - lastTx).coerceAtLeast(0) / dt else 0.0
            lastRx = rx; lastTx = tx; lastSampleAt = now
            return SpeedSample(rxPerSec, txPerSec, rx, tx)
        }
        lastRx = rx; lastTx = tx; lastSampleAt = now
        running = true
        return SpeedSample(0.0, 0.0, rx, tx)
    }

    fun reset() {
        running = false
        lastRx = 0L
        lastTx = 0L
        lastSampleAt = 0L
    }

    fun format(bytesPerSec: Double): String {
        if (bytesPerSec < 1024) return "%.0f B/s".format(bytesPerSec)
        val kb = bytesPerSec / 1024
        if (kb < 1024) return "%.1f KB/s".format(kb)
        val mb = kb / 1024
        if (mb < 1024) return "%.2f MB/s".format(mb)
        return "%.2f GB/s".format(mb / 1024)
    }
}
