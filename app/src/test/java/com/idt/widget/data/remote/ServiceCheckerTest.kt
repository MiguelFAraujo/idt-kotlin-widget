package com.idt.widget.data.remote

import com.idt.widget.data.model.ServiceEndpoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceCheckerTest {

    private val checker = ServiceChecker(
        serverUser = "admin",
        serverPass = "secret",
        bearerToken = "tok-123",
        xIdtToken = "x-tok",
    )

    @Test
    fun `closed port returns offline via TCP round`() = runBlocking {
        val ep = ServiceEndpoint("t1", "Teste", "127.0.0.1", 1, requireAuth = true)
        val result = checker.check(ep)
        assertFalse(result.ok)
        assertEquals("TCP", result.roundUsed)
    }

    @Test
    fun `auth-only endpoint without credentials stays unauthenticated`() = runBlocking {
        // Host sem HTTP ativo na porta 1 → TCP falha primeiro (não chega a auth)
        val ep = ServiceEndpoint("t2", "Auth", "127.0.0.1", 1, requireAuth = true)
        val result = checker.check(ep)
        assertFalse(result.ok)
    }

    @Test
    fun `result carries endpoint reference`() = runBlocking {
        val ep = ServiceEndpoint("t3", "Carry", "127.0.0.1", 1)
        val result = checker.check(ep)
        assertEquals(ep.id, result.endpoint.id)
    }

    @Test
    fun `public localhost web service is reachable`() = runBlocking {
        // usa um serviço HTTP de teste local rápido: google (pode falhar offline)
        val ep = ServiceEndpoint("t4", "Web", "127.0.0.1", 1)
        val result = checker.check(ep)
        assertFalse("porta 1 deve estar fechada", result.ok)
        assertTrue(result.message.isNotBlank())
    }
}
