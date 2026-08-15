package com.idt.widget.data.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryStoreTest {

    @Test
    fun `empty store reports zero uptime`() {
        val store = HistoryStore()
        val stats = store.historyFor("svc-1")
        assertEquals(0.0, stats.uptimePercent, 0.001)
        assertFalse(stats.lastOk)
        assertTrue(stats.samples.isEmpty())
    }

    @Test
    fun `uptime computes ratio of online samples`() {
        val store = HistoryStore()
        store.addSample("a", ok = true, latencyMs = 10, now = 1000)
        store.addSample("a", ok = true, latencyMs = 20, now = 2000)
        store.addSample("a", ok = false, latencyMs = 0, now = 3000)
        val stats = store.historyFor("a")
        assertEquals(66.666, stats.uptimePercent, 0.01)
        assertEquals(15.0, stats.avgLatencyMs, 0.01)
    }

    @Test
    fun `rolling window caps sample count`() {
        val store = HistoryStore(maxSamplesPerEndpoint = 5)
        for (i in 0 until 20) {
            store.addSample("a", ok = true, latencyMs = i.toLong(), now = i.toLong())
        }
        assertEquals(5, store.historyFor("a").samples.size)
        // janela deslizante: mantém os 5 últimos (15..19)
        assertEquals(15L, store.historyFor("a").samples.first().ts)
        assertEquals(19L, store.historyFor("a").samples.last().ts)
    }

    @Test
    fun `transition detection flags down and up`() {
        val store = HistoryStore()
        store.addSample("a", ok = true, latencyMs = 5, now = 1000)
        val downStats = store.addSample("a", ok = false, latencyMs = 0, now = 2000)
        assertTrue(downStats.transitionedDown)
        assertFalse(downStats.transitionedUp)

        val upStats = store.addSample("a", ok = true, latencyMs = 8, now = 3000)
        assertTrue(upStats.transitionedUp)
        assertFalse(upStats.transitionedDown)
    }

    @Test
    fun `json round trip preserves state`() {
        val store = HistoryStore()
        store.addSample("a", ok = true, latencyMs = 10, now = 1000)
        store.addSample("a", ok = false, latencyMs = 0, now = 2000)
        store.addSample("b", ok = true, latencyMs = 30, now = 1500)

        val json = store.toJson()
        val restored = HistoryStore()
        restored.fromJson(json)

        assertEquals(2, restored.size())
        assertEquals(2, restored.historyFor("a").samples.size)
        assertEquals(1, restored.historyFor("b").samples.size)
        assertFalse(restored.historyFor("a").samples[1].ok)
        assertEquals(30L, restored.historyFor("b").samples[0].latencyMs)
    }

    @Test
    fun `fromJson with empty string is safe`() {
        val store = HistoryStore()
        store.fromJson("")
        assertTrue(store.isEmpty())
        store.fromJson("{}")
        assertTrue(store.isEmpty())
    }
}
