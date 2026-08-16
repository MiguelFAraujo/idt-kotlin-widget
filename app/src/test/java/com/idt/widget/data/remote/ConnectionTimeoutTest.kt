package com.idt.widget.data.remote

import com.idt.widget.data.model.ServiceEndpoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Timeouts e respostas lentas: o checker nunca fica preso e nunca inventa status.
 * 4 testes, 4 estágios de validação cada.
 */
class ConnectionTimeoutTest {

    private val checker = ServiceChecker()

    private fun ep(id: String, port: Int) = ServiceEndpoint(id, "Tmo", "127.0.0.1", port)

    @Test
    fun `buracos negros estouram o timeout e viram porta aberta`() = runBlocking {
        LocalHttpServer(HttpServerSpec(silent = true)).use { server ->
            val result = checker.check(ep("tm1", server.port))
            assertEquals("Estágio 1 (TCP conectou)", true, server.connectionCount > 0)
            assertTrue("Estágio 2 (sem HTTP)", server.requests.isEmpty())
            assertEquals("Estágio 3 (round TCP)", "TCP", result.roundUsed)
            assertEquals("Estágio 4 (ok)", true, result.ok)
            assertTrue(result.message.startsWith("porta aberta"))
            assertTrue(result.latencyMs >= 0)
        }
    }

    @Test
    fun `resposta dentro do timeout e aceita`() = runBlocking {
        LocalHttpServer(HttpServerSpec(status = 200, delayMs = 1500)).use { server ->
            val result = checker.check(ep("tm2", server.port))
            assertEquals("Estágio 1", true, server.connectionCount > 0)
            assertEquals("Estágio 2 (HTTP recebido)", true, server.requests.isNotEmpty())
            assertEquals("Estágio 2 (status 200 no fio)", true, 200 in server.responseStatuses)
            assertEquals("Estágio 3 (round R1)", "R1", result.roundUsed)
            assertEquals("Estágio 4", true, result.ok)
            assertTrue(result.message.startsWith("HTTP 200"))
            assertTrue("latência >= 1500", result.latencyMs >= 1500)
        }
    }

    @Test
    fun `resposta alem do timeout vira porta aberta sem HTTP`() = runBlocking {
        LocalHttpServer(HttpServerSpec(status = 200, delayMs = 3500)).use { server ->
            val result = checker.check(ep("tm3", server.port))
            assertEquals("Estágio 1", true, server.connectionCount > 0)
            assertTrue("Estágio 2 (requisição enviada)", server.requests.isNotEmpty())
            assertEquals("Estágio 3 (round TCP)", "TCP", result.roundUsed)
            assertTrue("Estágio 4 (sem HTTP inventado)", result.message.startsWith("porta aberta"))
            assertTrue(result.ok)
        }
    }

    @Test
    fun `resposta parcial corrompida vira porta aberta`() = runBlocking {
        LocalHttpServer(HttpServerSpec(partialResponse = true)).use { server ->
            val result = checker.check(ep("tm4", server.port))
            assertEquals("Estágio 1", true, server.connectionCount > 0)
            assertEquals("Estágio 2 (sem HTTP válido)", true, server.requests.isEmpty())
            assertEquals("Estágio 3 (round TCP)", "TCP", result.roundUsed)
            assertTrue("Estágio 4", result.ok && result.message.startsWith("porta aberta"))
            assertTrue(result.latencyMs >= 0)
        }
    }
}
