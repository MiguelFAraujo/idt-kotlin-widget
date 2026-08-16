package com.idt.widget.data.remote

import com.idt.widget.data.model.ServiceEndpoint
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Qualquer status HTTP (2xx-5xx) significa serviço de pé: o probe HTTP qualifica
 * a resposta, o uptime é dado pelo TCP. 12 status testados no fio, 4 estágios cada.
 */
class ConnectionHttpStatusTest {

    private val checker = ServiceChecker()
    private val validator = ConnectionValidator(checker)

    private fun ep(id: String, port: Int) = ServiceEndpoint(id, "Http", "127.0.0.1", port)

    private fun assertStatus(status: Int, body: String = "b") = runBlocking {
        LocalHttpServer(HttpServerSpec(status = status, body = body)).use { server ->
            validator.validate(server, ep("h$status", server.port), FourStageExpectation(
                tcpConnected = true,
                httpObserved = true,
                responseStatus = status,
                round = "R1",
                ok = true,
                messagePrefix = "HTTP $status",
            ))
        }
    }

    @Test fun `status 200 e online`() = assertStatus(200)
    @Test fun `status 201 e online`() = assertStatus(201)
    @Test fun `status 204 e online`() = assertStatus(204, "")
    @Test fun `status 301 e online`() = assertStatus(301)
    @Test fun `status 302 e online`() = assertStatus(302)
    @Test fun `status 307 e online`() = assertStatus(307)
    @Test fun `status 400 e online`() = assertStatus(400)
    @Test fun `status 401 e online`() = assertStatus(401)
    @Test fun `status 403 e online`() = assertStatus(403)
    @Test fun `status 404 e online`() = assertStatus(404)
    @Test fun `status 500 e online`() = assertStatus(500)
    @Test fun `status 503 e online`() = assertStatus(503)
}
