package com.idt.widget.data.remote

import com.idt.widget.data.model.ServiceEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Expectativa dos 4 estágios de validação de uma conexão real:
 *
 *   Estágio 1 (TCP)  — o host:porta aceitou a conexão no fio (ou recusou).
 *   Estágio 2 (HTTP) — o servidor recebeu uma requisição HTTP real e respondeu
 *                      com o status esperado (bytes de verdade, sem mock).
 *   Estágio 3 (Auth) — o round de autenticação usado (R1..R5 ou "TCP") bate
 *                      com o que aconteceu na rede.
 *   Estágio 4 (Veredito) — ServiceCheckResult.ok/mensagem/latência consistentes
 *                      com o resultado real, nada inventado.
 */
data class FourStageExpectation(
    val tcpConnected: Boolean,
    val httpObserved: Boolean,
    val responseStatus: Int? = null,
    val round: String? = null,
    val ok: Boolean,
    val messagePrefix: String? = null,
)

class ConnectionValidator(private val checker: ServiceChecker) {

    suspend fun validate(
        server: LocalHttpServer,
        endpoint: ServiceEndpoint,
        exp: FourStageExpectation,
    ) {
        val result = checker.check(endpoint)

        // Estágio 1 — TCP
        assertEquals("Estágio 1 (TCP no fio)", exp.tcpConnected, server.connectionCount > 0)

        // Estágio 2 — HTTP real observado
        assertEquals("Estágio 2 (HTTP recebido)", exp.httpObserved, server.requests.isNotEmpty())
        exp.responseStatus?.let { expected ->
            assertTrue(
                "Estágio 2 (status enviado pelo servidor: $expected, vistos: ${server.responseStatuses})",
                expected in server.responseStatuses,
            )
        }

        // Estágio 3 — round de autenticação (ou TCP puro)
        exp.round?.let { assertEquals("Estágio 3 (round usado)", it, result.roundUsed) }

        // Estágio 4 — veredito consistente com a realidade
        assertEquals("Estágio 4 (ok)", exp.ok, result.ok)
        exp.messagePrefix?.let { p ->
            assertTrue("Estágio 4 (mensagem '$p') veio '${result.message}'", result.message.startsWith(p))
        }
        assertTrue("Estágio 4 (latência >= 0)", result.latencyMs >= 0)
    }
}
