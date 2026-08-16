package com.idt.widget.data.remote

import com.idt.widget.data.model.ServiceEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fonte de verdade = endereço fornecido pelo usuário. Endpoints de outro host
 * são obsoletos, e configuração vazia nunca cai em host inventado.
 */
class EndpointPolicyTest {

    @Test
    fun `extrai host de url com protocolo e porta`() {
        assertEquals("100.104.13.42", EndpointPolicy.hostFromUrl("http://100.104.13.42:8081/x"))
    }

    @Test
    fun `extrai host de url https`() {
        assertEquals("lab.local", EndpointPolicy.hostFromUrl("https://lab.local/path"))
    }

    @Test
    fun `extrai host de endereco puro`() {
        assertEquals("192.168.1.9", EndpointPolicy.hostFromUrl("192.168.1.9"))
    }

    @Test
    fun `endereco em branco retorna vazio`() {
        assertEquals("", EndpointPolicy.hostFromUrl(""))
        assertEquals("", EndpointPolicy.hostFromUrl("   "))
    }

    @Test
    fun `mesmo host nao exige resync`() {
        assertFalse(EndpointPolicy.needsResync("100.104.13.42", "100.104.13.42"))
        assertFalse(EndpointPolicy.needsResync("  Lab.LOCAL  ", "lab.local"))
    }

    @Test
    fun `host diferente exige resync`() {
        assertTrue(EndpointPolicy.needsResync("100.104.13.42", "192.168.1.9"))
        assertTrue(EndpointPolicy.needsResync("100.104.13.42", ""))
    }

    @Test
    fun `sem host configurado nunca exige resync`() {
        assertFalse(EndpointPolicy.needsResync("", ""))
        assertFalse(EndpointPolicy.needsResync("", "100.104.13.42"))
    }

    @Test
    fun `mantem somente endpoints do host configurado`() {
        val stale = ServiceEndpoint("a", "A", "100.104.13.42", 1)
        val current = ServiceEndpoint("b", "B", "192.168.1.9", 2)
        val kept = EndpointPolicy.filterStale(listOf(stale, current), "192.168.1.9")
        assertEquals(listOf("b"), kept.map { it.id })
    }

    @Test
    fun `configuracao em branco descarta tudo`() {
        val any = ServiceEndpoint("a", "A", "100.104.13.42", 1)
        assertTrue(EndpointPolicy.filterStale(listOf(any), "").isEmpty())
    }

    @Test
    fun `endpoints vazios continuam vazios`() {
        assertTrue(EndpointPolicy.filterStale(emptyList(), "192.168.1.9").isEmpty())
    }
}
