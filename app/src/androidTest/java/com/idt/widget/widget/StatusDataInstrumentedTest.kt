package com.idt.widget.widget

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.idt.widget.data.model.ServiceCheckResult
import com.idt.widget.data.model.ServiceEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatusDataInstrumentedTest {

    @Test
    fun writeReadRoundtripPreservesResults() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val ep = ServiceEndpoint("e1", "Ollama", "100.104.13.42", 11434)
        val results = listOf(
            ServiceCheckResult(ep, ok = true, roundUsed = "HTTP", latencyMs = 120, message = "HTTP 200"),
            ServiceCheckResult(ep.copy(id = "e2", name = "Netdata", port = 19999), ok = false, roundUsed = "TCP", latencyMs = 30, message = "porta fechada"),
        )
        StatusData.write(ctx, results, 123456789L)

        val read = StatusData.read(ctx)
        assertEquals(2, read.results.size)
        assertEquals(123456789L, read.timestamp)
        assertEquals("Ollama", read.results[0].endpoint.name)
        assertTrue(read.results[0].ok)
        assertEquals(120L, read.results[0].latencyMs)
        assertEquals("HTTP 200", read.results[0].message)
        assertTrue(!read.results[1].ok)
    }

    @Test
    fun readSemDadosRetornaVazio() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // garante cache limpo para não depender de estado anterior
        ctx.getSharedPreferences("idt_widget_cache", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        val read = StatusData.read(ctx)
        assertTrue(read.results.isEmpty())
        assertEquals(0L, read.timestamp)
    }
}
