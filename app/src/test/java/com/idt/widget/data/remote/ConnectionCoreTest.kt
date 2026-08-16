package com.idt.widget.data.remote

import com.idt.widget.data.model.ServiceEndpoint
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Núcleo de conexão: porta aberta/fechada, host certo/errado, TCP puro sem HTTP.
 * 8 testes, cada um com validação em 4 estágios (TCP / HTTP / Auth / Veredito).
 */
class ConnectionCoreTest {

    private val checker = ServiceChecker()

    private fun ep(id: String, port: Int, host: String = "127.0.0.1") =
        ServiceEndpoint(id, "Teste", host, port)

    private fun validator() = ConnectionValidator(checker)

    @Test
    fun `porta fechada retorna offline no estagio TCP`() = runBlocking {
        val server = LocalHttpServer() // nunca usado: o endpoint aponta para porta morta
        server.use {
            val e = ep("c1", 1)
            validator().validate(server, e, FourStageExpectation(
                tcpConnected = false,
                httpObserved = false,
                round = "TCP",
                ok = false,
                messagePrefix = "porta fechada",
            ))
        }
    }

    @Test
    fun `porta liberada e fechada retorna offline`() = runBlocking {
        val tmp = java.net.ServerSocket(0)
        val released = tmp.localPort
        tmp.close()
        val server = LocalHttpServer()
        server.use {
            val e = ep("c2", released)
            validator().validate(server, e, FourStageExpectation(
                tcpConnected = false,
                httpObserved = false,
                round = "TCP",
                ok = false,
            ))
        }
    }

    @Test
    fun `host errado nao conecta apesar da porta correta`() = runBlocking {
        val server = LocalHttpServer()
        server.use {
            // mesmo port, mas o host 127.0.0.2 não tem nada escutando
            val e = ep("c3", server.port, host = "127.0.0.2")
            validator().validate(server, e, FourStageExpectation(
                tcpConnected = false,
                httpObserved = false,
                round = "TCP",
                ok = false,
                messagePrefix = "porta fechada",
            ))
        }
    }

    @Test
    fun `outro octeto de loopback sem servico retorna offline`() = runBlocking {
        val server = LocalHttpServer()
        server.use {
            val e = ep("c4", server.port, host = "127.0.0.3")
            validator().validate(server, e, FourStageExpectation(
                tcpConnected = false,
                httpObserved = false,
                round = "TCP",
                ok = false,
            ))
        }
    }

    @Test
    fun `porta aberta sem HTTP e considerada online via round TCP`() = runBlocking {
        LocalHttpServer(HttpServerSpec(closeImmediately = true)).use { server ->
            val e = ep("c5", server.port)
            validator().validate(server, e, FourStageExpectation(
                tcpConnected = true,
                httpObserved = false,
                round = "TCP",
                ok = true,
                messagePrefix = "porta aberta",
            ))
        }
    }

    @Test
    fun `http 200 conecta e responde`() = runBlocking {
        LocalHttpServer(HttpServerSpec(status = 200, body = "ok")).use { server ->
            val e = ep("c6", server.port)
            validator().validate(server, e, FourStageExpectation(
                tcpConnected = true,
                httpObserved = true,
                responseStatus = 200,
                round = "R1",
                ok = true,
                messagePrefix = "HTTP 200",
            ))
        }
    }

    @Test
    fun `http 404 ainda significa servico online`() = runBlocking {
        LocalHttpServer(HttpServerSpec(status = 404, body = "nada")).use { server ->
            val e = ep("c7", server.port)
            validator().validate(server, e, FourStageExpectation(
                tcpConnected = true,
                httpObserved = true,
                responseStatus = 404,
                round = "R1",
                ok = true,
                messagePrefix = "HTTP 404",
            ))
        }
    }

    @Test
    fun `http 204 sem corpo e online`() = runBlocking {
        LocalHttpServer(HttpServerSpec(status = 204, body = "")).use { server ->
            val e = ep("c8", server.port)
            validator().validate(server, e, FourStageExpectation(
                tcpConnected = true,
                httpObserved = true,
                responseStatus = 204,
                round = "R1",
                ok = true,
                messagePrefix = "HTTP 204",
            ))
        }
    }
}
