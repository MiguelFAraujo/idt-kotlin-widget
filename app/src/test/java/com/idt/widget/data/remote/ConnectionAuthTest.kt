package com.idt.widget.data.remote

import com.idt.widget.data.model.ServiceEndpoint
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import org.junit.Test

/**
 * Rodadas de autenticação em cascata (R1 anônimo, R2 Basic, R3 Bearer,
 * R4 WebDAV PROPFIND, R5 X-IDT-Token), com credencial certa, errada,
 * credencial padrão de configuração e sem credencial. 12 testes, 4 estágios cada.
 */
class ConnectionAuthTest {

    private val checker = ServiceChecker(
        defaultUser = "admin",
        defaultPass = "secret",
        defaultBearerToken = "tok-123",
        defaultXIdtToken = "x-tok",
    )
    private val bare = ServiceChecker() // sem credenciais padrão (para rounds profundos)

    private val validator = ConnectionValidator(checker)
    private val bareValidator = ConnectionValidator(bare)

    private fun ep(
        id: String,
        port: Int,
        requireAuth: Boolean = false,
        authType: ServiceEndpoint.AuthType = ServiceEndpoint.AuthType.NONE,
        user: String = "",
        pass: String = "",
        bearer: String = "",
        xidt: String = "",
    ) = ServiceEndpoint(id, "Auth", "127.0.0.1", port, requireAuth = requireAuth, authType = authType,
        username = user, password = pass, bearerToken = bearer, xIdtToken = xidt)

    @Test
    fun `anonimo aceito responde no round R1`() = runBlocking {
        LocalHttpServer(HttpServerSpec(status = 200)).use { server ->
            validator.validate(server, ep("a1", server.port, requireAuth = true), FourStageExpectation(
                tcpConnected = true, httpObserved = true, responseStatus = 200,
                round = "R1", ok = true, messagePrefix = "HTTP 200",
            ))
        }
    }

    @Test
    fun `basic correto avanca para round R2`() = runBlocking {
        val gate = AuthGate(acceptAuthorization = Credentials.basic("admin", "secret"))
        LocalHttpServer(HttpServerSpec(status = 200, authGate = gate)).use { server ->
            validator.validate(
                server,
                ep("a2", server.port, requireAuth = true, ServiceEndpoint.AuthType.BASIC, "admin", "secret"),
                FourStageExpectation(tcpConnected = true, httpObserved = true, responseStatus = 200,
                    round = "R2", ok = true, messagePrefix = "HTTP 200"),
            )
        }
    }

    @Test
    fun `basic errado recebe 401 no round R2`() = runBlocking {
        val gate = AuthGate(acceptAuthorization = Credentials.basic("admin", "secret"))
        LocalHttpServer(HttpServerSpec(status = 200, authGate = gate)).use { server ->
            validator.validate(
                server,
                ep("a3", server.port, requireAuth = true, ServiceEndpoint.AuthType.BASIC, "admin", "errada"),
                FourStageExpectation(tcpConnected = true, httpObserved = true, responseStatus = 401,
                    round = "R2", ok = true, messagePrefix = "HTTP 401"),
            )
        }
    }

    @Test
    fun `bearer correto avanca para round R3`() = runBlocking {
        val gate = AuthGate(acceptAuthorization = "Bearer tok-123")
        LocalHttpServer(HttpServerSpec(status = 200, authGate = gate)).use { server ->
            bareValidator.validate(
                server,
                ep("a4", server.port, requireAuth = true, ServiceEndpoint.AuthType.BEARER, bearer = "tok-123"),
                FourStageExpectation(tcpConnected = true, httpObserved = true, responseStatus = 200,
                    round = "R3", ok = true, messagePrefix = "HTTP 200"),
            )
        }
    }

    @Test
    fun `bearer errado recebe 401 no round R3`() = runBlocking {
        val gate = AuthGate(acceptAuthorization = "Bearer tok-123")
        LocalHttpServer(HttpServerSpec(status = 200, authGate = gate)).use { server ->
            bareValidator.validate(
                server,
                ep("a5", server.port, requireAuth = true, ServiceEndpoint.AuthType.BEARER, bearer = "tok-errado"),
                FourStageExpectation(tcpConnected = true, httpObserved = true, responseStatus = 401,
                    round = "R3", ok = true, messagePrefix = "HTTP 401"),
            )
        }
    }

    @Test
    fun `webdav responde no round R4 com PROPFIND`() = runBlocking {
        val gate = AuthGate(
            acceptAuthorization = Credentials.basic("admin", "secret"),
            acceptMethod = "PROPFIND",
        )
        LocalHttpServer(HttpServerSpec(status = 207, body = "<d:multistatus/>", authGate = gate)).use { server ->
            bareValidator.validate(
                server,
                ep("a6", server.port, requireAuth = true, ServiceEndpoint.AuthType.WEBDAV, "admin", "secret"),
                FourStageExpectation(tcpConnected = true, httpObserved = true, responseStatus = 207,
                    round = "R4", ok = true, messagePrefix = "HTTP 207"),
            )
        }
    }

    @Test
    fun `token customizado responde no round R5`() = runBlocking {
        val gate = AuthGate(acceptHeader = "x-idt-token" to "x-tok")
        LocalHttpServer(HttpServerSpec(status = 200, authGate = gate)).use { server ->
            bareValidator.validate(
                server,
                ep("a7", server.port, requireAuth = true, ServiceEndpoint.AuthType.CUSTOM, xidt = "x-tok"),
                FourStageExpectation(tcpConnected = true, httpObserved = true, responseStatus = 200,
                    round = "R5", ok = true, messagePrefix = "HTTP 200"),
            )
        }
    }

    @Test
    fun `credencial padrao da config entra no round R2`() = runBlocking {
        val gate = AuthGate(acceptAuthorization = Credentials.basic("admin", "secret"))
        LocalHttpServer(HttpServerSpec(status = 200, authGate = gate)).use { server ->
            validator.validate(
                server,
                ep("a8", server.port, requireAuth = true, ServiceEndpoint.AuthType.WEBDAV),
                FourStageExpectation(tcpConnected = true, httpObserved = true, responseStatus = 200,
                    round = "R2", ok = true, messagePrefix = "HTTP 200"),
            )
        }
    }

    @Test
    fun `sem requireAuth nao tenta rounds de auth`() = runBlocking {
        val gate = AuthGate(acceptAuthorization = Credentials.basic("admin", "secret"))
        LocalHttpServer(HttpServerSpec(status = 200, authGate = gate)).use { server ->
            validator.validate(
                server,
                ep("a9", server.port, requireAuth = false, ServiceEndpoint.AuthType.BASIC, "admin", "secret"),
                FourStageExpectation(tcpConnected = true, httpObserved = true,
                    round = "TCP", ok = true, messagePrefix = "porta aberta"),
            )
        }
    }

    @Test
    fun `401 anonimo ja qualifica como online no R1`() = runBlocking {
        LocalHttpServer(HttpServerSpec(status = 401)).use { server ->
            validator.validate(server, ep("a10", server.port, requireAuth = true), FourStageExpectation(
                tcpConnected = true, httpObserved = true, responseStatus = 401,
                round = "R1", ok = true, messagePrefix = "HTTP 401",
            ))
        }
    }

    @Test
    fun `servidor que derruba tudo nao produz HTTP`() = runBlocking {
        LocalHttpServer(HttpServerSpec(closeImmediately = true)).use { server ->
            validator.validate(server, ep("a11", server.port, requireAuth = true), FourStageExpectation(
                tcpConnected = true, httpObserved = false,
                round = "TCP", ok = true, messagePrefix = "porta aberta",
            ))
        }
    }

    @Test
    fun `basic aceito mas servidor responde 500`() = runBlocking {
        val gate = AuthGate(acceptAuthorization = Credentials.basic("admin", "secret"))
        LocalHttpServer(HttpServerSpec(status = 500, body = "boom", authGate = gate)).use { server ->
            validator.validate(
                server,
                ep("a12", server.port, requireAuth = true, ServiceEndpoint.AuthType.BASIC, "admin", "secret"),
                FourStageExpectation(tcpConnected = true, httpObserved = true, responseStatus = 500,
                    round = "R2", ok = true, messagePrefix = "HTTP 500"),
            )
        }
    }
}
