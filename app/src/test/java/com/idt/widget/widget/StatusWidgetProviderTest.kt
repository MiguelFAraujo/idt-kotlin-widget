package com.idt.widget.widget

import com.idt.widget.data.model.ServiceCheckResult
import com.idt.widget.data.model.ServiceEndpoint
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusWidgetProviderTest {

    private fun ep(id: String, name: String) = ServiceEndpoint(id, name, "100.104.13.42", 22)

    private fun result(endpoint: ServiceEndpoint, ok: Boolean) =
        ServiceCheckResult(endpoint, ok = ok, roundUsed = "TCP", latencyMs = 5, message = "ok")

    @Test
    fun `vazio mostra mensagem padrao`() {
        assertEquals("Sem endpoints configurados", StatusWidgetProvider.statusText(emptyList()))
    }

    @Test
    fun `todos online mostra contagem e nomes`() {
        val r = listOf(
            result(ep("a", "Ollama"), true),
            result(ep("b", "n8n"), true),
            result(ep("c", "Grafana"), true),
        )
        val text = StatusWidgetProvider.statusText(r)
        assertEquals("3/3 online • Ollama • n8n • Grafana", text)
    }

    @Test
    fun `online limita a 3 nomes`() {
        val r = (1..5).map { result(ep("$it", "S$it"), true) }
        val text = StatusWidgetProvider.statusText(r)
        assertEquals("5/5 online • S1 • S2 • S3", text)
    }

    @Test
    fun `offline lista ate 2 nomes e mostra total restante`() {
        val r = listOf(
            result(ep("a", "OmniRoute"), false),
            result(ep("b", "Netdata"), false),
            result(ep("c", "Grafana"), false),
            result(ep("d", "Portainer"), true),
        )
        val text = StatusWidgetProvider.statusText(r)
        assertEquals("1/4 online • Portainer ⛔OmniRoute ⛔Netdata ⛔+1", text)
    }

    @Test
    fun `offline exato mostra sem sufixo de mais`() {
        val r = listOf(
            result(ep("a", "OmniRoute"), false),
            result(ep("b", "Netdata"), false),
            result(ep("c", "Portainer"), true),
        )
        val text = StatusWidgetProvider.statusText(r)
        assertEquals("1/3 online • Portainer ⛔OmniRoute ⛔Netdata", text)
    }

    @Test
    fun `mescla online e offline na ordem esperada`() {
        val r = listOf(
            result(ep("a", "Ollama"), true),
            result(ep("b", "OmniRoute"), false),
            result(ep("c", "n8n"), true),
            result(ep("d", "Netdata"), false),
        )
        val text = StatusWidgetProvider.statusText(r)
        assertEquals("2/4 online • Ollama • n8n ⛔OmniRoute ⛔Netdata", text)
    }
}
