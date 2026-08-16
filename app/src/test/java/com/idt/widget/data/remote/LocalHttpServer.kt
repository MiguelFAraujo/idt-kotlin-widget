package com.idt.widget.data.remote

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

/** Requisição HTTP capturada no fio (evidência real de rede). */
data class CapturedRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
)

/**
 * Comportamento do servidor local: sem mocks, socket TCP de verdade em 127.0.0.1.
 */
class HttpServerSpec(
    val status: Int = 200,
    val body: String = "ok",
    val extraHeaders: Map<String, String> = emptyMap(),
    /** Primeiro byte enviado no accept (0x16 = simula handshake TLS real). */
    val firstByte: Byte? = null,
    /** Aceita a conexão e fecha sem responder (EOF -> checker avança de round). */
    val closeImmediately: Boolean = false,
    /** Aceita e nunca responde (estoura o timeout de leitura). */
    val silent: Boolean = false,
    /** Envia uma linha HTTP incompleta e fecha (resposta parcial -> erro de parsing). */
    val partialResponse: Boolean = false,
    val delayMs: Long = 0L,
    val authGate: AuthGate? = null,
)

/**
 * Portão de autenticação: define o que o servidor aceita antes de responder.
 * Requisições que não atendem são DERRUBADAS (sem resposta) para o checker
 * avançar de round (R1 -> R2 -> ...); credencial presente mas errada vira [rejectStatus].
 */
class AuthGate(
    val acceptAuthorization: String? = null,
    val acceptMethod: String? = null,
    val acceptHeader: Pair<String, String>? = null,
    val rejectStatus: Int = 401,
)

/**
 * Servidor HTTP de verdade (ServerSocket) para exercitar o [ServiceChecker]
 * contra a rede local. Registra cada requisição e os primeiros bytes de cada
 * conexão (permite provar no fio um ClientHello TLS real).
 */
class LocalHttpServer(
    private val spec: HttpServerSpec = HttpServerSpec(),
) : AutoCloseable {

    val requests: MutableList<CapturedRequest> = CopyOnWriteArrayList()
    val connectionsFirstBytes: MutableList<List<Int>> = CopyOnWriteArrayList()
    val responseStatuses: MutableList<Int> = CopyOnWriteArrayList()
    @Volatile var connectionCount = 0

    private val serverSocket = ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"))
    private val running = java.util.concurrent.atomic.AtomicBoolean(true)
    private val thread = Thread { acceptLoop() }.apply { name = "local-http-server"; start() }

    val port: Int get() = serverSocket.localPort
    val host: String get() = "127.0.0.1"

    private fun acceptLoop() {
        while (running.get()) {
            val socket = try {
                serverSocket.accept()
            } catch (e: Exception) {
                if (!running.get()) break
                continue
            }
            connectionCount++
            Thread { handleConnection(socket) }.start()
        }
    }

    private fun handleConnection(socket: Socket) {
        socket.soTimeout = 5000
        try {
            socket.use { s ->
                val out = s.getOutputStream()
                // Envia byte de handshake simulado imediatamente (detecção TLS no checker)
                spec.firstByte?.let { out.write(it.toInt()) }

                // Captura os primeiros bytes ENVIADOS pelo checker (ClientHello TLS ou "GET ")
                // ANTES de decidir fechar/silenciar: evidência de rede para os testes de TLS.
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.ISO_8859_1))
                val rawBytes = readFirstBytes(reader)
                connectionsFirstBytes.add(rawBytes)

                if (spec.silent) {
                    Thread.sleep(10_000)
                    return
                }
                if (spec.closeImmediately) return
                if (spec.partialResponse) {
                    out.write("HTTP/1.1 2".toByteArray())
                    out.flush()
                    return
                }

                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0]
                val path = parts[1]

                val headers = linkedMapOf<String, String>()
                var line = reader.readLine()
                while (!line.isNullOrBlank()) {
                    val idx = line.indexOf(':')
                    if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                    line = reader.readLine()
                }

                requests.add(CapturedRequest(method, path, headers))

                if (spec.delayMs > 0) Thread.sleep(spec.delayMs)
                respond(socket, out)
            }
        } catch (_: Exception) {
            // Conexão derrubada pelo checker (round avançou) — normal.
        }
    }

    private fun readFirstBytes(reader: BufferedReader): List<Int> {
        reader.mark(64)
        val bytes = mutableListOf<Int>()
        val buf = CharArray(4)
        val read = try {
            reader.read(buf, 0, 4)
        } catch (e: Exception) {
            -1
        }
        if (read > 0) for (i in 0 until read) bytes.add(buf[i].code)
        try {
            reader.reset()
        } catch (_: Exception) {
        }
        return bytes
    }

    private fun respond(socket: Socket, out: OutputStream) {
        val gate = spec.authGate
        val request = requests.lastOrNull() ?: return
        if (gate != null) {
            val authOk = gate.acceptAuthorization == null ||
                request.headers["authorization"] == gate.acceptAuthorization
            val methodOk = gate.acceptMethod == null || request.method == gate.acceptMethod
            val headerName = gate.acceptHeader?.first?.lowercase()
            val headerOk = gate.acceptHeader == null ||
                request.headers[headerName] == gate.acceptHeader?.second

            when {
                authOk && methodOk && headerOk ->
                    writeResponse(socket, out, spec.status, spec.body, spec.extraHeaders)
                gate.acceptAuthorization != null && request.headers["authorization"] == null &&
                    gate.acceptMethod == null && gate.acceptHeader == null ->
                    closeQuietly(socket) // anônimo: avança de round
                gate.acceptMethod != null && request.method != gate.acceptMethod ->
                    closeQuietly(socket) // método errado: avança de round
                gate.acceptHeader != null && request.headers[headerName] == null ->
                    closeQuietly(socket) // header esperado ausente: avança de round
                else ->
                    writeResponse(socket, out, gate.rejectStatus, "auth-failed", emptyMap())
            }
        } else {
            writeResponse(socket, out, spec.status, spec.body, spec.extraHeaders)
        }
    }

    private fun writeResponse(socket: Socket, out: OutputStream, status: Int, body: String, extra: Map<String, String>) {
        responseStatuses.add(status)
        val reason = when (status) {
            200 -> "OK"; 201 -> "Created"; 204 -> "No Content"; 206 -> "Partial Content"
            301 -> "Moved Permanently"; 302 -> "Found"; 307 -> "Temporary Redirect"
            400 -> "Bad Request"; 401 -> "Unauthorized"; 403 -> "Forbidden"; 404 -> "Not Found"
            500 -> "Internal Server Error"; 503 -> "Service Unavailable"
            else -> "Status"
        }
        val sb = StringBuilder()
        sb.append("HTTP/1.1 $status $reason\r\n")
        sb.append("Content-Type: text/plain\r\n")
        extra.forEach { (k, v) -> sb.append("$k: $v\r\n") }
        sb.append("Content-Length: ${body.toByteArray().size}\r\n")
        sb.append("Connection: close\r\n\r\n")
        out.write(sb.toString().toByteArray())
        out.write(body.toByteArray())
        out.flush()
    }

    private fun closeQuietly(socket: Socket) {
        try {
            socket.close()
        } catch (_: Exception) {
        }
    }

    override fun close() {
        running.set(false)
        try {
            serverSocket.close()
        } catch (_: Exception) {
        }
    }
}
