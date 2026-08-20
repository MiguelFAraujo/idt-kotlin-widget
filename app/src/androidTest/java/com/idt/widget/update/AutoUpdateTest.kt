package com.idt.widget.update

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.idt.widget.data.remote.UpdateChecker
import com.idt.widget.BuildConfig
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
                    thread(isDaemon = true) connection@{
                        try {
                            if (s.getInputStream().bufferedReader().readLine() == null) return@connection
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
        // O fixture precisa ser maior que a versão do APK sob teste; não fixar
        // 0.0.x evita que o teste fique falso quando o app evoluir.
        val nextCode = BuildConfig.VERSION_CODE + 1
        val body = """{"versionName":"test-next","versionCode":$nextCode,"apkUrl":"http://127.0.0.1:1/x.apk","changelog":"teste"}"""
        serve(body) { url ->
            runBlocking {
                val checker = UpdateChecker(url)
                val update = checker.check(ctx)
                assertNotNull("manifest válido deveria retornar UpdateInfo", update)
                update!!
                assertTrue("a versão futura deve ser identificada", update.isNewerThan(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
                assertTrue("a versão futura não é mais nova que ela mesma", !update.isNewerThan("test-next", nextCode))
            }
        }
    }

    @Test
    fun autoUpdateManifestInvalidoRetornaNull() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        serve("not json at all") { url ->
            runBlocking {
                val checker = UpdateChecker(url)
                assertNull("manifest inválido deveria retornar null", checker.check(ctx))
            }
        }
    }
}
