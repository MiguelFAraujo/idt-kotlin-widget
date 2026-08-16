package com.idt.widget.update

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.idt.widget.data.remote.UpdateChecker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class AutoUpdateTest {

    /** Serve [body] num socket local e devolve a URL. Fecha ao terminar. */
    private fun serve(body: String, block: (String) -> Unit) {
        val server = ServerSocket(0)
        thread(isDaemon = true) {
            while (!server.isClosed) {
                try {
                    val s = server.accept()
                    thread(isDaemon = true) {
                        try {
                            val req = s.getInputStream().bufferedReader().readLine() ?: return@thread
                            val httpBody = body.toByteArray(StandardCharsets.UTF_8)
                            s.getOutputStream().use { out ->
                                out.write(
                                    ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                                        "Content-Length: ${httpBody.size}\r\n\r\n").toByteArray()
                                )
                                out.write(httpBody)
                                out.flush()
                            }
                        } catch (e: Exception) {
                        } finally {
                            try { s.close() } catch (e: Exception) {}
                        }
                    }
                } catch (e: Exception) {
                }
            }
        }
        try {
            block("http://127.0.0.1:${server.localPort}/update.json")
        } finally {
            server.close()
        }
    }

    @Test
    fun autoUpdateDetectaVersaoNovaDoManifest() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val body = """{"versionName":"0.0.7","versionCode":7,"apkUrl":"http://127.0.0.1:1/x.apk","changelog":"teste"}"""
        serve(body) { url ->
            runBlocking {
                val checker = UpdateChecker(url)
                val update = checker.check()
                assertNotNull("manifest válido deveria retornar UpdateInfo", update)
                update!!
                assertTrue("0.0.7 deve ser mais nova que 0.0.6", update.isNewerThan("0.0.6", 6))
                assertTrue("0.0.7 não é mais nova que ela mesma", !update.isNewerThan("0.0.7", 7))
            }
        }
    }

    @Test
    fun autoUpdateManifestInvalidoRetornaNull() {
        serve("not json at all") { url ->
            runBlocking {
                val checker = UpdateChecker(url)
                assertNull("manifest inválido deveria retornar null", checker.check())
            }
        }
    }
}
