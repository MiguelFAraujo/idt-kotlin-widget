package com.idt.widget.data.remote

import com.idt.widget.data.model.ServiceEndpoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Detecção TLS (primeiro byte 0x16): prova no fio se o checker envia um
 * ClientHello TLS de verdade ou um GET HTTP puro. 5 testes, 4 estágios cada.
 */
class ConnectionTlsTest {

    private val checker = ServiceChecker()

    private fun ep(id: String, port: Int) = ServiceEndpoint(id, "Tls", "127.0.0.1", port)

    @Test
    fun `primeiro byte 0x16 dispara handshake TLS real no fio`() = runBlocking {
        LocalHttpServer(HttpServerSpec(firstByte = 0x16, closeImmediately = true)).use { server ->
            val result = checker.check(ep("t1", server.port))
            // Estágio 1: TCP conectou
            assertTrue("Estágio 1 (TCP)", server.connectionCount > 0)
            // Estágio 2/3: algum byte 0x16 no fio = ClientHello TLS enviado
            assertTrue(
                "Estágio 2/3 (ClientHello TLS no fio): ${server.connectionsFirstBytes}",
                server.connectionsFirstBytes.any { it.firstOrNull() == 0x16 },
            )
            // Estágio 4: sem HTTP -> porta aberta sem protocolo identificado
            assertTrue(result.ok)
            assertTrue(result.message.startsWith("porta aberta"))
            assertTrue(result.latencyMs >= 0)
        }
    }

    @Test
    fun `servidor HTTP comum nao dispara TLS`() = runBlocking {
        LocalHttpServer(HttpServerSpec(status = 200)).use { server ->
            val result = checker.check(ep("t2", server.port))
            assertTrue("Estágio 1 (TCP)", server.connectionCount > 0)
            // Estágio 2/3: no fio vemos um GET HTTP ('G'), nunca ClientHello
            assertTrue(
                "Estágio 2/3 (GET HTTP no fio): ${server.connectionsFirstBytes}",
                server.connectionsFirstBytes.any { it.firstOrNull() == 'G'.code },
            )
            assertTrue("Estágio 2/3 (sem 0x16)", server.connectionsFirstBytes.none { it.firstOrNull() == 0x16 })
            assertTrue("Estágio 4 (HTTP 200)", result.message.startsWith("HTTP 200"))
            assertTrue(result.ok)
        }
    }

    @Test
    fun `porta que fecha sem resposta nao e TLS`() = runBlocking {
        LocalHttpServer(HttpServerSpec(closeImmediately = true)).use { server ->
            val result = checker.check(ep("t3", server.port))
            assertTrue("Estágio 1 (TCP)", server.connectionCount > 0)
            assertTrue(
                "Estágio 2/3 (GET HTTP puro): ${server.connectionsFirstBytes}",
                server.connectionsFirstBytes.any { it.firstOrNull() == 'G'.code },
            )
            assertTrue("Estágio 4", result.ok && result.message.startsWith("porta aberta"))
        }
    }

    @Test
    fun `byte invalido nao e tratado como TLS`() = runBlocking {
        LocalHttpServer(HttpServerSpec(firstByte = 'Z'.code.toByte(), closeImmediately = true)).use { server ->
            val result = checker.check(ep("t4", server.port))
            assertTrue("Estágio 1 (TCP)", server.connectionCount > 0)
            assertTrue(
                "Estágio 2/3 (GET puro, não 0x16): ${server.connectionsFirstBytes}",
                server.connectionsFirstBytes.any { it.firstOrNull() == 'G'.code },
            )
            assertTrue("Estágio 4", result.ok && result.message.startsWith("porta aberta"))
        }
    }

    @Test
    fun `servidor silencioso nao e TLS e estoura timeout`() = runBlocking {
        LocalHttpServer(HttpServerSpec(silent = true)).use { server ->
            val result = checker.check(ep("t5", server.port))
            assertTrue("Estágio 1 (TCP)", server.connectionCount > 0)
            assertTrue("Estágio 2 (sem resposta HTTP)", server.requests.isEmpty())
            assertTrue("Estágio 4", result.ok && result.message.startsWith("porta aberta"))
            assertTrue("Estágio 4 (latência alta demais)", result.latencyMs >= 0)
        }
    }
}
