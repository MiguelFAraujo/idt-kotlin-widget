package com.idt.widget.data.remote

import com.idt.widget.data.model.ServiceEndpoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Veredito final do [ServiceChecker]: ok, round, mensagem e latência sempre
 * consistentes com o que aconteceu de verdade na rede. 9 testes, 4 estágios cada.
 */
class ConnectionVerdictTest {

    private val checker = ServiceChecker()

    private fun ep(id: String, port: Int) = ServiceEndpoint(id, "Vd", "127.0.0.1", port)

    @Test
    fun `offline reporta mensagem exata de porta fechada`() = runBlocking {
        val result = checker.check(ep("v1", 1))
        assertEquals("Estágio 1 (TCP falhou)", "TCP", result.roundUsed)
        assertEquals("Estágio 4", false, result.ok)
        assertEquals("Estágio 4 (mensagem exata)", "porta fechada / sem resposta", result.message)
    }

    @Test
    fun `offline marca ok=false com latencia real`() = runBlocking {
        val result = checker.check(ep("v2", 1))
        assertEquals(false, result.ok)
        assertTrue("latência registrada", result.latencyMs >= 0)
    }

    @Test
    fun `online reporta codigo HTTP real no round R1`() = runBlocking {
        LocalHttpServer(HttpServerSpec(status = 200)).use { server ->
            val result = checker.check(ep("v3", server.port))
            assertEquals("R1", result.roundUsed)
            assertEquals("HTTP 200", result.message)
            assertTrue(result.ok)
        }
    }

    @Test
    fun `porta aberta sem HTTP usa round TCP e mensagem propria`() = runBlocking {
        LocalHttpServer(HttpServerSpec(closeImmediately = true)).use { server ->
            val result = checker.check(ep("v4", server.port))
            assertEquals("TCP", result.roundUsed)
            assertEquals("porta aberta (sem HTTP)", result.message)
            assertTrue(result.ok)
        }
    }

    @Test
    fun `latencia online fica em faixa plausivel`() = runBlocking {
        LocalHttpServer(HttpServerSpec(status = 200)).use { server ->
            val result = checker.check(ep("v5", server.port))
            assertTrue("latência entre 0 e 5000", result.latencyMs in 0..5000)
        }
    }

    @Test
    fun `latencia offline fica em faixa plausivel`() = runBlocking {
        val result = checker.check(ep("v6", 1))
        assertTrue("latência entre 0 e 10000", result.latencyMs in 0..10_000)
    }

    @Test
    fun `resultado carrega o endpoint completo de origem`() = runBlocking {
        LocalHttpServer(HttpServerSpec(status = 200)).use { server ->
            val e = ep("v7", server.port)
            val result = checker.check(e)
            assertEquals(e.id, result.endpoint.id)
            assertEquals(e.host, result.endpoint.host)
            assertEquals(e.port, result.endpoint.port)
        }
    }

    @Test
    fun `mensagem de offline nunca e vazia`() = runBlocking {
        val result = checker.check(ep("v8", 1))
        assertFalse(result.message.isBlank())
    }

    @Test
    fun `mensagem de online nunca e vazia`() = runBlocking {
        LocalHttpServer(HttpServerSpec(status = 200)).use { server ->
            val result = checker.check(ep("v9", server.port))
            assertFalse(result.message.isBlank())
        }
    }
}
